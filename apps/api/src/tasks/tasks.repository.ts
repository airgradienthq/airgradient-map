import { Injectable, NotFoundException } from '@nestjs/common';
import DatabaseService from 'src/database/database.service';
import { AirgradientModel } from './tasks.model';
import { Logger } from '@nestjs/common';

@Injectable()
class TasksRepository {
  constructor(private readonly databaseService: DatabaseService) {}

  private readonly logger = new Logger(TasksRepository.name);

  async getAll() {
    const result = await this.databaseService.runQuery('SELECT * FROM measurement;');
    return result.rows;
  }

  async insertAg(raw: AirgradientModel[]) {
    // Retrieve ownerId of airgradient from database first
    var ownerId = -1;
    try {
      const query = `SELECT id FROM owner WHERE owner_name = 'airgradient'`;
      const result = await this.databaseService.runQuery(query);
      if (result.rowCount === 0) {
        throw new NotFoundException('Airgradient record not found in owner table');
      }

      ownerId = result.rows[0].id;
    } catch (error) {
      this.logger.error(error);
      return false;
    }

    this.logger.debug(`Airgradient owner_id is ${ownerId}`);

    try {
      // Insert per 200 data
      const chunk = 200;
      for (let idx = 0; idx < raw.length; idx += chunk) {
        const data = raw.slice(idx, idx + chunk);

        // TODO: Need to do an offline check, if offline, don't insert?
        // Insert into location table
        const escapeSingleQuote = (str: string) => {
          if (str === null) {
            return null;
          }
          return str.replace(/'/g, "''");
        };

        const locationValues = data
          .flatMap(({ locationId, locationName, timezone, latitude, longitude }) => {
            // Skip row if coordinate empty
            if (latitude === null && longitude === null) {
              return [];
            }
            // Build postgis point value then return formatted row
            const geometry = `'POINT(${latitude} ${longitude})'`;
            const licensesFmt = `ARRAY['CC BY-SA 4.0']`;
            return `(${locationId},'Small Sensor',${licensesFmt},'${escapeSingleQuote(locationName)}','${timezone}',${geometry},'AirGradient')`;
          })
          .join(',');

        const locationQuery = `
            INSERT INTO public."location" (owner_id, reference_id, sensor_type, licenses, location_name, timezone, coordinate, provider)
            SELECT 
                ${ownerId}, t.reference_id, t.sensor_type::sensor_type_enum, t.licenses, t.location_name, t.timezone, t.coordinate, t.provider
            FROM (
                VALUES ${locationValues}  
            ) AS t(reference_id, sensor_type, licenses, location_name, timezone, coordinate, provider)
            ON CONFLICT (reference_id, data_source) DO NOTHING;
        `;

        const measurementValues = data
          .map(
            ({ locationId, pm02, pm10, atmp, rhum, rco2, timestamp }) =>
              `(${locationId}, ${pm02}, ${pm10}, ${atmp}, ${rhum}, ${rco2}, '${timestamp}')`,
          )
          .join(', ');

        const measurementQuery = `
            INSERT INTO public."measurement" (location_id, pm25, pm10, atmp, rhum, rco2, measured_at)
            SELECT
                location.id, t.pm25, t.pm10, t.atmp, t.rhum, t.rco2, t.measured_at::timestamp
            FROM (
                VALUES ${measurementValues}
            ) AS t(reference_id, pm25, pm10, atmp, rhum, rco2, measured_at)
            JOIN public."location"
            ON location.data_source = 'AirGradient' AND t.reference_id = location.reference_id
            ON CONFLICT (location_id, measured_at) DO NOTHING;
        `;
        // TODO: Is unique key needed for measurements??? Is it affecting performance?
        // TODO: Maybe can use postgres prepare statement later on?

        await this.databaseService.runQuery('BEGIN');
        await this.databaseService.runQuery(locationQuery);
        await this.databaseService.runQuery(measurementQuery);
        await this.databaseService.runQuery('COMMIT');
      }

      return true;
    } catch (error) {
      await this.databaseService.runQuery('ROLLBACK');
      this.logger.error(error);
      return false;
    }
  }

  async upsertOpenAQLocations(locations: any[]) {
    const escapeSingleQuote = (str: string) => {
      if (str === null) {
        return null;
      }
      return str.replace(/'/g, "''");
    };

    try {
      const locationValues = locations
        .flatMap(
          ({
            referenceId,
            locationName,
            providerName,
            ownerName,
            sensorType,
            timezone,
            coordinate,
            licenses,
          }) => {
            // Build postgis point value then return formatted row
            const geometry = `'POINT(${coordinate[0]} ${coordinate[1]})'`;
            // Build licenses data type
            const licensesFmt = licenses !== null ? `ARRAY[${licenses}]` : 'ARRAY[]::VARCHAR[]';
            return `('${ownerName}','${escapeSingleQuote(locationName)}',${referenceId},'${sensorType}','${timezone}',${licensesFmt},'OpenAQ','${providerName}',${geometry})`;
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

export default TasksRepository;
