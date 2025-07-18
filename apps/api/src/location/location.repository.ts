import {
  Injectable,
  InternalServerErrorException,
  Logger,
  NotFoundException,
} from '@nestjs/common';
import DatabaseService from 'src/database/database.service';
import { LocationEntity } from './location.entity';
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

  async retrieveCigarettesSmokedByLocationId(id: number) {
    const timeframes = [
      { label: 'last24hours', days: 1 },
      { label: 'last7days', days: 7 },
      { label: 'last30days', days: 30 },
      { label: 'last365days', days: 365 },
    ];
    try {
      const now = new Date();
      const cigaretteData: Record<string, number> = {};
      for (const timeframe of timeframes) {
        const start = new Date(Date.now() - timeframe.days * 24 * 60 * 60 * 1000).toISOString();
        const end = now.toISOString();

        const rows = await this.retrieveLocationMeasuresHistory(id, start, end, '1 day', 'pm25');

        let sum = 0;
        for (const row of rows) {
          sum += parseFloat(row.pm25);
        }
        const cigaretteNumber = Math.round((sum / 22) * 100) / 100;
        cigaretteData[timeframe.label] = cigaretteNumber;
      }
      return cigaretteData;
    } catch (error) {
      this.logger.error(error);
      throw new InternalServerErrorException('Error query retrieve cigarettes smoked');
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

    // For pm25, we need both pm25 and rhum for EPA correction
    const selectClause =
      measure === 'pm25'
        ? `round(avg(m.pm25)::NUMERIC , 2) AS pm25, round(avg(m.rhum)::NUMERIC , 2) AS rhum`
        : `round(avg(m.${measure})::NUMERIC , 2) AS value`;

    const query = `
            SELECT
                date_bin($4, m.measured_at, $2) AS timebucket,
                ${selectClause}
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

  async retrieveLocationDailyAverages(
    id: number,
    start: string,
    end: string,
    measure: string
  ) {
    const params = [id, start, end];
    const query = `
            SELECT 
              ROUND(AVG(m.${measure})::NUMERIC, 2) AS value,
              DATE(m.measured_at) AS date
            FROM measurement m WHERE m.location_id = $1 AND
            DATE(m.measured_at) BETWEEN DATE($2) AND DATE($3)
            GROUP BY date
            ORDER BY date;
          `;
    try {
      const results = await this.databaseService.runQuery(query, params);
      const dataMap = new Map<string, number>();

      for (const row of results.rows) {
        const normalizedDate = new Date(row.date).toISOString().split('T')[0];
        dataMap.set(normalizedDate, parseFloat(row.value));
      }

      const dailyResults: { date: string; [key: string]: string | number | null }[] = [];
      const current = new Date(start);
      const endDate = new Date(end);

      while (current <= endDate) {
        const dateStr = current.toISOString().split('T')[0];
        dailyResults.push({
          date: dateStr,
          [`avg${measure.toUpperCase()}`]: dataMap.get(dateStr) ?? null,
        });

        current.setDate(current.getDate() + 1);
      }

      return dailyResults;
    } catch (error) {
      this.logger.error(error);
      throw new InternalServerErrorException(
        `Error query measures daily average location by id (${error.message})`,
      );
    }
  }
}

export default LocationRepository;
