import {
  Injectable,
  InternalServerErrorException,
  Logger,
  NotFoundException,
} from '@nestjs/common';
import DatabaseService from 'src/database/database.service';
import LocationEntity from './location.entity';
import { MeasureType } from 'src/utils/measureTypeQuery';
import { getMeasureValidValueRange } from 'src/utils/measureValueValidation';

@Injectable()
class LocationRepository {
  constructor(private readonly databaseService: DatabaseService) {}
  private readonly logger = new Logger(LocationRepository.name);

  async retrieveLocations(offset: number = 0, limit: number = 100): Promise<LocationEntity[]> {
    const query = `
            SELECT
                l.id AS "locationId",
                l.location_name AS "locationName",
                ST_X(l.coordinate) AS longitude,
                ST_Y(l.coordinate) AS latitude,
                o.id AS "ownerId",
                o.owner_name AS "ownerName",
                o.owner_name_display AS "ownerNameDisplay",
                o.description,
                o.url,
                l.sensor_type AS "sensorType",
                l.licenses,
                l.provider,
                l.data_source AS "dataSource",
                l.timezone
            FROM 
                location l
            JOIN
                owner o ON l.owner_id = o.id
            ORDER BY 
                l.id
            OFFSET $1 LIMIT $2; 
        `;

    try {
      const results = await this.databaseService.runQuery(query, [offset, limit]);

      return results.rows.map((location: Partial<LocationEntity>) => new LocationEntity(location));
    } catch (error) {
      this.logger.error(error);
      throw new InternalServerErrorException('Error query locations information');
    }
  }

  async retrieveLocationById(id: number): Promise<LocationEntity> {
    const query = `
            SELECT
                l.id AS "locationId",
                l.location_name AS "locationName",
                ST_X(l.coordinate) AS longitude,
                ST_Y(l.coordinate) AS latitude,
                o.id AS "ownerId",
                o.owner_name AS "ownerName",
                o.owner_name_display AS "ownerNameDisplay",
                o.description,
                o.url,
                l.sensor_type AS "sensorType",
                l.licenses,
                l.provider,
                l.data_source AS "dataSource",
                l.timezone
            FROM 
                location l
            JOIN
                owner o ON l.owner_id = o.id
            WHERE
                l.id = $1;
        `;

    try {
      const result = await this.databaseService.runQuery(query, [id]);

      const location = result.rows[0];
      if (!location) {
        throw new NotFoundException();
      }

      return new LocationEntity(location);
    } catch (error) {
      this.logger.error(error);
      throw new InternalServerErrorException('Error query location information by id');
    }
  }

  async retrieveLastMeasuresByLocationId(id: number) {
    const query = `
            SELECT 
                location_id AS "locationId",
                pm25,
                pm10,
                atmp,
                rhum,
                rco2,
                o3,
                no2,
                measured_at AS "measuredAt"
            FROM measurement
            WHERE location_id = $1
            ORDER BY measured_at DESC 
            LIMIT 1;
        `;

    try {
      const result = await this.databaseService.runQuery(query, [id]);

      const lastMeasurements = result.rows[0];
      if (!lastMeasurements) {
        throw new NotFoundException();
      }
      return lastMeasurements;
    } catch (error) {
      this.logger.error(error);
      throw new InternalServerErrorException(
        'Error query last measures of specific location by id',
      );
    }
  }

  async retrieveLocationMeasuresHistory(
    id: number,
    start: string,
    end: string,
    bucketSize: string,
    measure: string,
  ) {
    const { minVal, maxVal, hasValidation } = getMeasureValidValueRange(measure as MeasureType);

    const validationQuery = hasValidation ? `AND m.${measure} BETWEEN $5 AND $6` : '';

    const query = `
            SELECT
                date_bin($4, m.measured_at, $2) AS timebucket,
                round(avg(m.${measure})::NUMERIC , 2) AS value
            FROM measurement m 
            WHERE 
                m.location_id = $1 AND 
                m.measured_at BETWEEN $2 AND $3 
                ${validationQuery}
            GROUP BY timebucket
            ORDER BY timebucket;
        `;

    const params = hasValidation
      ? [id, start, end, bucketSize, minVal, maxVal]
      : [id, start, end, bucketSize];

    try {
      const results = await this.databaseService.runQuery(query, params);

      return results.rows;
    } catch (error) {
      this.logger.error(error);
      throw new InternalServerErrorException(
        `Error query measures history of specific location by id (${error.message})`,
      );
    }
  }
}

export default LocationRepository;
