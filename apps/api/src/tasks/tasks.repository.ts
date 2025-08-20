import { Injectable } from '@nestjs/common';
import { Logger } from '@nestjs/common';

import DatabaseService from 'src/database/database.service';
import { AirgradientModel } from './tasks.model';
import { UpsertLocationOwnerInput } from 'src/types/tasks/upsert-location-input';
import { escapeSingleQuote } from 'src/utils/escape-single-quote';

function formatLicenses(arr: string[] | null | undefined): string {
  if (!arr || arr.length === 0) {
    return 'ARRAY[]::VARCHAR[]';
  }

  const quoted = arr.map(value => `'${value}'`);
  return `ARRAY[${quoted.join(',')}]`;
}

@Injectable()
export class TasksRepository {
  constructor(private readonly databaseService: DatabaseService) {}

  private readonly logger = new Logger(TasksRepository.name);

  async getAll() {
    const result = await this.databaseService.runQuery('SELECT * FROM measurement;');
    return result.rows;
  }

  async upsertLocationsAndOwners(
    dataSource: string,
    locationOwnerInput: UpsertLocationOwnerInput[],
  ) {
    try {
      const locationValues = locationOwnerInput
        .flatMap(
          ({
            ownerName,
            locationReferenceId,
            locationName,
            sensorType,
            timezone,
            coordinateLatitude,
            coordinateLongitude,
            licenses,
            provider,
          }) => {
            // Skip row if coordinate empty
            if (coordinateLatitude === null && coordinateLongitude === null) {
              return [];
            }

            // Skip if location name is empty
            if (locationName === null) {
              return [];
            }

            // Validate owner name if its empty and escape single quote if any
            const validatedOwnerName =
              ownerName !== null ? escapeSingleQuote(ownerName) : 'unknown';
            // Build postgis point value then return formatted row
            const geometry = `'POINT(${coordinateLongitude} ${coordinateLatitude})'`;
            // Build licenses data type
            const licensesFmt = formatLicenses(licenses);

            return `('${validatedOwnerName}','${escapeSingleQuote(locationName)}',${locationReferenceId},'${sensorType}','${timezone}',${licensesFmt},'${dataSource}','${provider}',${geometry})`;
          },
        )
        .join(',');

      const query = `
        WITH batch_data AS (
        SELECT *
            FROM (VALUES
                ${locationValues}
            ) AS t(owner_name, location_name, reference_id, sensor_type, timezone, licenses, data_source, provider, coordinate)
        ),
        insert_owner AS (
            INSERT INTO owner (owner_name)
            SELECT owner_name
            FROM batch_data
            ON CONFLICT (owner_name) DO NOTHING
        ),
        existing_owner AS (
            SELECT id AS owner_id, owner_name
            FROM owner
            WHERE owner_name IN (SELECT owner_name FROM batch_data)
        ),
        location_data AS (
            SELECT
                b.location_name,
                eo.owner_id,
                b.reference_id,
                b.sensor_type,
                b.licenses,
                b.timezone,
                b.coordinate,
                b.data_source,
                b.provider
            FROM batch_data b
            JOIN existing_owner eo ON b.owner_name = eo.owner_name
        )
        INSERT INTO location (
            location_name, owner_id, reference_id, sensor_type, licenses, timezone, coordinate, data_source, provider
        )
        SELECT
            ld.location_name,
            ld.owner_id,
            ld.reference_id,
            ld.sensor_type::sensor_type_enum,
            ld.licenses,
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
            licenses = EXCLUDED.licenses,
            timezone = EXCLUDED.timezone,
            coordinate = EXCLUDED.coordinate,
            provider = EXCLUDED.provider;
    `;

      await this.databaseService.runQuery(query);
    } catch (error) {
      this.logger.error(error);
    }
  }

  async insertNewAirgradientLatest(data: AirgradientModel[]) {
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
              ON loc.data_source = 'AirGradient'
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
        `SELECT json_object_agg(reference_id::TEXT, id) FROM "location" WHERE data_source = 'OpenAQ';`,
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

  async insertNewOpenAQLatest(latests: any[]) {
    try {
      const latestValues = latests
        .flatMap(({ locationId, pm25, measuredAt }) => {
          return `(${locationId},${pm25},'${measuredAt}')`;
        })
        .join(',');

      var query = `
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
