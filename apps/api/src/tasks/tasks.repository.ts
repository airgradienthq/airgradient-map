import { Injectable } from '@nestjs/common';
import { Logger } from '@nestjs/common';

import DatabaseService from 'src/database/database.service';
import { OWNER_REFERENCE_ID_PREFIXES } from 'src/constants/owner-reference-id-prefixes';
import { OutlierService } from 'src/outlier/outlier.service';
import { DataSource, InsertLatestMeasuresInput, UpsertLocationOwnerInput } from 'src/types';

@Injectable()
export class TasksRepository {
  constructor(
    private readonly databaseService: DatabaseService,
    private readonly outlierService: OutlierService,
  ) {}

  private readonly logger = new Logger(TasksRepository.name);
  private readonly batchSize = 1000;

  async upsertLocationsAndOwners(
    dataSource: DataSource,
    allowApiAccess: boolean,
    dataSourceUrl: string,
    locationOwnerInput: UpsertLocationOwnerInput[],
  ): Promise<void> {
    for (let i = 0; i < locationOwnerInput.length; i += this.batchSize) {
      this.logger.debug(`Inserting location owner batch idx ${i}`);
      await this._upsertLocationsAndOwners(
        dataSource,
        allowApiAccess,
        dataSourceUrl,
        locationOwnerInput.slice(i, i + this.batchSize),
      );
      // Small delay between batches to reduce contention
      if (i + this.batchSize < locationOwnerInput.length) {
        await new Promise(resolve => setTimeout(resolve, 100));
      }
    }
  }

  private async _upsertLocationsAndOwners(
    dataSource: DataSource,
    allowApiAccess: boolean,
    dataSourceUrl: string,
    locationOwnerInput: UpsertLocationOwnerInput[],
  ): Promise<void> {
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
        allowApiAccesses,
        dataSourceUrls,
        providers,
        coordinates,
      } = locationOwnerInput.reduce(
        (acc, r) => {
          // Filter invalid rows
          if (r.coordinateLatitude === null || r.coordinateLongitude === null) return acc;
          if (r.locationName === null) return acc;

          // Restructure and collect data for each column
          acc.ownerNames.push(r.ownerName || null);
          acc.ownerUrls.push(r.ownerUrl || null);
          acc.ownerRefIds.push(`${OWNER_REFERENCE_ID_PREFIXES[dataSource]}${r.ownerReferenceId}`);
          acc.locationNames.push(r.locationName);
          acc.locationRefIds.push(r.locationReferenceId);
          acc.sensorTypes.push(r.sensorType);
          acc.timezones.push(r.timezone);
          acc.licensesJson.push(r.licenses ? JSON.stringify(r.licenses) : null);
          acc.dataSources.push(dataSource);
          acc.allowApiAccesses.push(allowApiAccess);
          acc.dataSourceUrls.push(dataSourceUrl);
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
          allowApiAccesses: [] as boolean[],
          dataSourceUrls: [] as string[],
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
        allowApiAccesses,
        dataSourceUrls,
        providers,
        coordinates,
      ];

      // WHY UNNEST: Converts columnar arrays back into rows that SQL can work with
      // Example: unnest(['A','B'], [1,2]) creates rows: ('A',1), ('B',2)
      //
      // 1. batch_data: Uses unnest() to reconstruct rows from our columnar arrays
      // 2. insert_owner: Upserts owners first (locations need owner_id foreign key)
      // 3. insert_data_source: Upserts data_source (locations need data_source_id foreign key)
      // 4. location_data: Joins location data with newly created owner IDs
      //    - ST_GeomFromText(coordinate, 4326): Converts "POINT(lng lat)" string to PostGIS geometry
      //      - SRID 4326: (long, lat) (Unit: Degree) <- we use this
      //      - SRID 3857 (Web Mercator): Standard projection for web mapping, matches coordinate system (x, y) (Unit: Metre)
      //    - JSON licenses: Converts JSON strings back to PostgreSQL varchar[] arrays using jsonb functions
      //      WHY JSON: unnest() can't handle JavaScript arrays like ["item1","item2"] directly
      // 5. Final INSERT: Bulk insert locations with conflict resolution (ON CONFLICT DO UPDATE)
      //
      // Here basically ON CONFLICT UPDATE for both upsert owner and locations will always overwrite
      //   even though there's no changes. The performance has no difference than adding another WHERE clause to check first
      const query = `
        WITH batch_data AS (
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
              $10::boolean[],
              $11::text[],
              $12::text[],
              $13::text[]
          )
          AS t(owner_name, owner_url, owner_reference_id, location_name, location_reference_id, sensor_type, timezone, licenses_json, data_source_name, allow_api_access, data_source_url, provider, coordinate)
      ),
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
      ),
      insert_data_source AS (
        INSERT INTO data_source (name, allow_api_access, url)
        SELECT DISTINCT
          b.data_source_name,
          b.allow_api_access,
          b.data_source_url
        FROM batch_data b
        ON CONFLICT (name) DO UPDATE
        SET
            allow_api_access = EXCLUDED.allow_api_access,
            url = EXCLUDED.url
        RETURNING id, name
      ),
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
          ST_GeomFromText(b.coordinate, 4326) AS coordinate,
          ids.id AS data_source_id,
          b.provider
        FROM batch_data b
        JOIN insert_owner io ON b.owner_reference_id = io.reference_id
        JOIN insert_data_source ids ON b.data_source_name = ids.name
      )
      INSERT INTO location (
        location_name, owner_id, reference_id, sensor_type, licenses, timezone, coordinate, data_source_id, provider
      )
      SELECT
        ld.location_name,
        ld.owner_id,
        ld.reference_id,
        ld.sensor_type::sensor_type_enum,
        ld.licenses::varchar[],
        ld.timezone,
        ld.coordinate,
        ld.data_source_id,
        ld.provider
      FROM location_data ld
      ON CONFLICT (reference_id, data_source_id) DO UPDATE
      SET
        location_name = EXCLUDED.location_name,
        owner_id = EXCLUDED.owner_id,
        sensor_type = EXCLUDED.sensor_type,
        licenses = EXCLUDED.licenses::varchar[],
        timezone = EXCLUDED.timezone,
        coordinate = EXCLUDED.coordinate,
        provider = EXCLUDED.provider;`;

      // Execute the batch operation in single operation
      await this.databaseService.runQuery(query, values);
    } catch (error) {
      this.logger.error('Failed to upsert locations and owners', {
        error: (error as Error).message,
      });
    }
  }

  async retrieveLocationIds(dataSource: DataSource): Promise<Record<string, number>> {
    try {
      const query = `
        SELECT json_object_agg(l.reference_id::TEXT, l.id) 
        FROM location l
        JOIN data_source ds ON l.data_source_id = ds.id
        WHERE ds.name = '${dataSource}';`;
      const result = await this.databaseService.runQuery(query);
      if (result.rowCount === 0 || result.rows[0].json_object_agg === null) {
        return {};
      }
      // {"<locationReferenceId>": locationId}
      return result.rows[0].json_object_agg as Record<string, number>;
    } catch (error) {
      this.logger.error(error);
      return {};
    }
  }

  async insertLatestMeasures(
    dataSource: DataSource,
    locationIdAvailable: boolean,
    latestMeasuresInput: InsertLatestMeasuresInput[],
  ): Promise<void> {
    for (let i = 0; i < latestMeasuresInput.length; i += this.batchSize) {
      this.logger.debug(`Inserting latest measures batch idx ${i}`);
      await this._insertLatestMeasures(
        dataSource,
        locationIdAvailable,
        latestMeasuresInput.slice(i, i + this.batchSize),
      );

      // Small delay between batches to reduce contention
      if (i + this.batchSize < latestMeasuresInput.length) {
        await new Promise(resolve => setTimeout(resolve, 100));
      }
    }
  }

  private async _insertLatestMeasures(
    dataSource: DataSource,
    locationIdAvailable: boolean,
    latestMeasuresInput: InsertLatestMeasuresInput[],
  ): Promise<void> {
    try {
      // Calculate outlier status for all measurements in batch
      const outlierResults = await this.outlierService.calculateBatchIsPm25Outlier(
        dataSource,
        latestMeasuresInput.map(dp => ({
          locationReferenceId: dp.locationReferenceId,
          pm25: dp.pm25,
          measuredAt: dp.measuredAt,
        })),
      );

      // Map into values query with pre-calculated outlier status
      const latestValues = latestMeasuresInput
        .map(dataPoint => {
          const { locationId, locationReferenceId, pm25, pm10, atmp, rhum, rco2, measuredAt } =
            dataPoint;
          const key = `${locationReferenceId}_${measuredAt}`;
          const isPm25Outlier = outlierResults.get(key) ?? false;
          const locId = locationIdAvailable ? locationId : locationReferenceId;
          return `(${locId}, ${pm25}, ${pm10}, ${atmp}, ${rhum}, ${rco2}, '${measuredAt}', ${isPm25Outlier})`;
        })
        .join(', ');

      // Prepare query based on if locationId available or not
      let query = '';
      if (locationIdAvailable) {
        query = `
          INSERT INTO public."measurement" (
            location_id, pm25, pm10, atmp, rhum, rco2, measured_at, is_pm25_outlier
          )
          VALUES
            ${latestValues}
          ON CONFLICT (location_id, measured_at)
          DO NOTHING;
        `;
      } else {
        query = `
          WITH ds AS (
            SELECT *
            FROM public.data_source
            WHERE name = '${dataSource}'
          )
          INSERT INTO public."measurement" (
            location_id, pm25, pm10, atmp, rhum, rco2, measured_at, is_pm25_outlier
          )
          SELECT
            loc.id AS location_id,
            m.pm25::float8,
            m.pm10::float8,
            m.atmp::float8,
            m.rhum::float8,
            m.rco2::int4,
            m.measured_at::timestamp,
            m.is_pm25_outlier::boolean
          FROM ds
          JOIN public."location" loc 
            ON loc.data_source_id = ds.id
          JOIN(
            VALUES ${latestValues}
          ) AS m(reference_id, pm25, pm10, atmp, rhum, rco2, measured_at, is_pm25_outlier)
            ON loc.reference_id = m.reference_id
          ON CONFLICT (location_id, measured_at) DO NOTHING;
        `;
      }

      // Execute query
      await this.databaseService.runQuery(query);
    } catch (error) {
      this.logger.error(error);
    }
  }
}
