import { Injectable } from '@nestjs/common';
import { Logger } from '@nestjs/common';

import DatabaseService from 'src/database/database.service';
import { AirgradientModel } from './model/airgradient.model';
import { UpsertLocationOwnerInput } from 'src/types/tasks/upsert-location-input';
import { OpenAQLatestData } from '../types/tasks/openaq.types';
import { OWNER_REFERENCE_ID_PREFIXES } from 'src/constants/owner-reference-id-prefixes';
import { DataSource } from 'src/types/shared/data-source';

@Injectable()
export class TasksRepository {
  constructor(private readonly databaseService: DatabaseService) {}

  private readonly logger = new Logger(TasksRepository.name);

  async getAll(): Promise<any[]> {
    const result = await this.databaseService.runQuery('SELECT * FROM measurement;');
    return result.rows;
  }

  async upsertLocationsAndOwners(
    dataSource: DataSource,
    locationOwnerInput: UpsertLocationOwnerInput[],
  ) {
    try {
      // Creating columnar arrays
      // WHY: PostgreSQL's unnest() function works with arrays, not individual records
      // Transform row-based input (array of objects) into column-based arrays for efficient batch processing
      // Each column becomes a separate array: [name1, name2, ...], [lat1, lat2, ...], etc.
      // This allows processing hundreds/thousands of records in a single SQL operation instead of individual INSERTs
      // Also filters out invalid records (null coordinates/location names) and converts licenses to JSON strings
      const {
        ownerNames,
        ownerUrls,
        ownerRefIds,
        locationNames,
        locationRefIds,
        sensorTypes,
        timezones,
        licensesJson,
        dataSources,
        providers,
        coordinates,
      } = locationOwnerInput.reduce(
        (acc, r) => {
          // Filter invalid rows
          if (r.coordinateLatitude === null || r.coordinateLongitude === null) return acc;
          if (r.locationName === null) return acc;

          // Restructure and collect data for each column
          acc.ownerNames.push(r.ownerName ?? 'unknown');
          acc.ownerUrls.push(r.ownerUrl);
          const prefixedOwnerId =
            dataSource === DataSource.AIRGRADIENT
              ? `${OWNER_REFERENCE_ID_PREFIXES.AIRGRADIENT}${r.ownerReferenceId}`
              : `${OWNER_REFERENCE_ID_PREFIXES.OPENAQ}${r.ownerReferenceId}`;
          acc.ownerRefIds.push(prefixedOwnerId);
          acc.locationNames.push(r.locationName);
          acc.locationRefIds.push(r.locationReferenceId);
          acc.sensorTypes.push(r.sensorType);
          acc.timezones.push(r.timezone);
          acc.licensesJson.push(r.licenses ? JSON.stringify(r.licenses) : null);
          acc.dataSources.push(dataSource);
          acc.providers.push(r.provider);
          acc.coordinates.push(`POINT(${r.coordinateLongitude} ${r.coordinateLatitude})`);

          return acc;
        },
        {
          ownerNames: [] as (string | null)[],
          ownerUrls: [] as (string | null)[],
          ownerRefIds: [] as string[],
          locationNames: [] as string[],
          locationRefIds: [] as number[],
          sensorTypes: [] as string[],
          timezones: [] as string[],
          licensesJson: [] as (string | null)[],
          dataSources: [] as string[],
          providers: [] as string[],
          coordinates: [] as string[],
        },
      );

      if (ownerNames.length === 0) {
        this.logger.error('No valid rows to upsert location and owners');
        return;
      }

      // Prepare values array for parameterized query
      // WHY: Parameterized queries are more efficient than string concatenation
      // Package all columnar arrays into a single array that matches the SQL parameter positions ($1, $2, etc.)
      // Order must match the unnest() parameters in the query
      const values = [
        ownerNames,
        ownerUrls,
        ownerRefIds,
        locationNames,
        locationRefIds,
        sensorTypes,
        timezones,
        licensesJson,
        dataSources,
        providers,
        coordinates,
      ];

      // WHY UNNEST: Converts columnar arrays back into rows that SQL can work with
      // Example: unnest(['A','B'], [1,2]) creates rows: ('A',1), ('B',2)
      //
      // OWNER PROTECTION STRATEGY:
      // - AirGradient owners: Create minimal placeholders (empty strings), NEVER update existing
      // - Non-AirGradient owners: Always sync with latest data from external sources
      //
      // MODULAR QUERY CONSTRUCTION:
      // 1. buildBatchDataCTE(): Uses unnest() to reconstruct rows from columnar arrays
      // 2. Owner Logic (selected by DataSource):
      //    - buildAirGradientOwnerLogic(): Protects existing AG owners, creates minimal placeholders
      //    - buildNonAirGradientOwnerLogic(): Traditional upsert with full sync data
      // 3. buildLocationLogic(): Joins location data with owner IDs and inserts/updates locations
      //    - ST_GeomFromText(coordinate, 3857): Converts "POINT(lng lat)" string to PostGIS geometry
      //    - SRID 3857 (Web Mercator): Standard projection for web mapping
      //    - JSON licenses: Converts JSON strings to PostgreSQL varchar[] arrays
      //    - ON CONFLICT DO UPDATE: Handles duplicate locations gracefully
      //
      // Build query by combining modular components based on data source
      const ownerLogic = dataSource === DataSource.AIRGRADIENT
        ? this.buildAirGradientOwnerLogic()
        : this.buildNonAirGradientOwnerLogic();

      const query = `
        WITH ${this.buildBatchDataCTE()},
        ${ownerLogic}
        ${this.buildLocationLogic()}
      `;

      // Execute the batch operation in single operation
      await this.databaseService.runQuery(query, values);
    } catch (error) {
      this.logger.error('Failed to upsert locations and owners', {
        error: (error as Error).message,
        dataSource,
        inputLength: locationOwnerInput.length,
      });
    }
  }

  private buildBatchDataCTE(): string {
    return `
      batch_data AS (
        SELECT *
        FROM unnest(
            $1::text[],
            $2::text[],
            $3::text[],
            $4::text[],
            $5::int[],
            $6::text[],
            $7::text[],
            $8::text[],
            $9::text[],
            $10::text[],
            $11::text[]
        )
        AS t(owner_name, owner_url, owner_reference_id, location_name, location_reference_id, sensor_type, timezone, licenses_json, data_source, provider, coordinate)
      )`;
  }

  private buildAirGradientOwnerLogic(): string {
    return `
      -- Get existing owners for this batch
      existing_owners AS (
        SELECT id, reference_id
        FROM owner
        WHERE reference_id = ANY(SELECT DISTINCT owner_reference_id FROM batch_data)
      ),

      -- Create only missing AG owners with minimal data
      insert_missing_owner AS (
        INSERT INTO owner (owner_name, url, reference_id)
        SELECT DISTINCT 
          '' AS owner_name,
          NULL AS url,
          b.owner_reference_id
        FROM batch_data b
        WHERE NOT EXISTS (
          SELECT 1 FROM existing_owners eo
          WHERE eo.reference_id = b.owner_reference_id
        )
        RETURNING id, reference_id
      ),

      -- Combine existing + newly created owners
      insert_owner AS (
        SELECT id, reference_id FROM existing_owners
        UNION ALL
        SELECT id, reference_id FROM insert_missing_owner
      ),`;
  }

  private buildNonAirGradientOwnerLogic(): string {
    return `
      insert_owner AS (
        INSERT INTO owner (owner_name, url, reference_id)
        SELECT DISTINCT
            b.owner_name,
            b.owner_url,
            b.owner_reference_id
        FROM batch_data b
        ON CONFLICT (reference_id) DO UPDATE
        SET
            owner_name = EXCLUDED.owner_name,
            url = EXCLUDED.url
        RETURNING id, reference_id
      ),`;
  }

  private buildLocationLogic(): string {
    return `
      location_data AS (
        SELECT
          b.location_name,
          io.id AS owner_id,
          b.location_reference_id AS reference_id,
          b.sensor_type,
          CASE 
            WHEN b.licenses_json IS NULL THEN NULL
            ELSE ARRAY(SELECT jsonb_array_elements_text(b.licenses_json::jsonb))
          END AS licenses,
          b.timezone,
          ST_GeomFromText(b.coordinate, 3857) AS coordinate,
          b.data_source,
          b.provider
        FROM batch_data b
        JOIN insert_owner io ON b.owner_reference_id = io.reference_id
      )
      INSERT INTO location (
        location_name, owner_id, reference_id, sensor_type, licenses, timezone, coordinate, data_source, provider
      )
      SELECT
        ld.location_name,
        ld.owner_id,
        ld.reference_id,
        ld.sensor_type::sensor_type_enum,
        ld.licenses::varchar[],
        ld.timezone,
        ld.coordinate,
        ld.data_source,
        ld.provider
      FROM location_data ld
      ON CONFLICT (reference_id, data_source) DO UPDATE
      SET
        location_name = EXCLUDED.location_name,
        owner_id = EXCLUDED.owner_id,
        sensor_type = EXCLUDED.sensor_type,
        licenses = EXCLUDED.licenses::varchar[],
        timezone = EXCLUDED.timezone,
        coordinate = EXCLUDED.coordinate,
        provider = EXCLUDED.provider;`;
  }


  async insertNewAirgradientLatest(data: AirgradientModel[]): Promise<void> {
    try {
      const measurementValues = data
        .map(
          ({ locationId, pm02, pm10, atmp, rhum, rco2, timestamp }) =>
            `(${locationId}, ${pm02}, ${pm10}, ${atmp}, ${rhum}, ${rco2}, '${timestamp}')`,
        )
        .join(', ');

      const query = `
          INSERT INTO public."measurement" (
              location_id, pm25, pm10, atmp, rhum, rco2, measured_at
          )
          SELECT
              loc.id AS location_id,
              m.pm25,
              m.pm10,
              m.atmp,
              m.rhum,
              m.rco2,
              m.measured_at::timestamp
          FROM (
            VALUES ${measurementValues}
          ) AS m(reference_id, pm25, pm10, atmp, rhum, rco2, measured_at)
          JOIN public."location" loc
              ON loc.data_source = '${DataSource.AIRGRADIENT}'
             AND loc.reference_id = m.reference_id
          ON CONFLICT (location_id, measured_at) DO NOTHING;
        `;

      await this.databaseService.runQuery(query);
    } catch (error) {
      this.logger.error(error);
    }
  }

  async retrieveOpenAQLocationId(): Promise<object | null> {
    try {
      const result = await this.databaseService.runQuery(
        `SELECT json_object_agg(reference_id::TEXT, id) FROM "location" WHERE data_source = '${DataSource.OPENAQ}';`,
      );
      if (result.rowCount === 0 || result.rows[0].json_object_agg === null) {
        return {};
      }
      return result.rows[0].json_object_agg;
    } catch (error) {
      this.logger.error(error);
      return {};
    }
  }

  async insertNewOpenAQLatest(latests: OpenAQLatestData[]): Promise<void> {
    try {
      const latestValues = latests
        .flatMap(({ locationId, pm25, measuredAt }) => {
          return `(${locationId},${pm25},'${measuredAt}')`;
        })
        .join(',');

      const query = `
        INSERT INTO measurement (location_id, pm25, measured_at) 
            VALUES ${latestValues} 
        ON CONFLICT (location_id, measured_at)
        DO NOTHING;
      `;

      await this.databaseService.runQuery(query);
    } catch (error) {
      this.logger.error(error);
    }
  }
}
