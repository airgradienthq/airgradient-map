import {
  Injectable,
  InternalServerErrorException,
  Logger,
  NotFoundException,
} from '@nestjs/common';
import DatabaseService from 'src/database/database.service';
import { LocationEntity } from './location.entity';
import { MeasureType, PM25Period, PM25PeriodConfig, MeasurementAveragesResult } from 'src/types';
import { getMeasureValidValueRange } from 'src/utils/measureValueValidation';
import { LatestLocationMeasurementData } from 'src/notifications/notification.model';
import { BucketSize } from 'src/utils/timeSeriesBucket';

@Injectable()
class LocationRepository {
  constructor(private readonly databaseService: DatabaseService) {}
  private readonly logger = new Logger(LocationRepository.name);

  async retrieveLocations(
    hasFullAccess: boolean,
    offset: number = 0,
    limit: number = 100,
  ): Promise<LocationEntity[]> {
    const query = `
            SELECT
                l.id AS "locationId",
                l.location_name AS "locationName",
                ST_X(l.coordinate) AS longitude,
                ST_Y(l.coordinate) AS latitude,
                o.id AS "ownerId",
                o.owner_name AS "ownerName",
                o.url,
                l.sensor_type AS "sensorType",
                l.licenses,
                l.provider,
                d.name AS "dataSource",
                l.timezone
            FROM 
                ${hasFullAccess ? 'location' : 'vw_location_public'} l
            JOIN
                owner o ON l.owner_id = o.id
            JOIN
                data_source d ON l.data_source_id = d.id
            ORDER BY 
                l.id
            OFFSET $1 LIMIT $2; 
        `;

    try {
      const results = await this.databaseService.runQuery(query, [offset, limit]);

      return results.rows.map((location: Partial<LocationEntity>) => new LocationEntity(location));
    } catch (error) {
      this.logger.error(error);
      throw new InternalServerErrorException({
        message: 'LOC_001: Failed to retrieve locations',
        operation: 'retrieveLocations',
        parameters: { offset, limit },
        error: error.message,
        code: 'LOC_001',
      });
    }
  }

  async retrieveLocationById(id: number, hasFullAccess: boolean): Promise<LocationEntity> {
    const query = `
            SELECT
                l.id AS "locationId",
                l.location_name AS "locationName",
                ST_X(l.coordinate) AS longitude,
                ST_Y(l.coordinate) AS latitude,
                o.id AS "ownerId",
                o.owner_name AS "ownerName",
                o.url,
                l.sensor_type AS "sensorType",
                l.licenses,
                l.provider,
                d.name AS "dataSource",
                l.timezone
            FROM 
                ${hasFullAccess ? 'location' : 'vw_location_public'} l
            JOIN
                owner o ON l.owner_id = o.id
            JOIN
                data_source d ON l.data_source_id = d.id
            WHERE
                l.id = $1;
        `;

    try {
      const result = await this.databaseService.runQuery(query, [id]);

      const location = result.rows[0];
      if (!location) {
        throw new NotFoundException({
          message: 'LOC_002: Location not found',
          operation: 'retrieveLocationById',
          parameters: { id },
          code: 'LOC_002',
        });
      }

      return new LocationEntity(location);
    } catch (error) {
      if (error instanceof NotFoundException) {
        throw error;
      }
      this.logger.error(error);
      throw new InternalServerErrorException({
        message: 'LOC_003: Failed to retrieve location by id',
        operation: 'retrieveLocationById',
        parameters: { id },
        error: error.message,
        code: 'LOC_003',
      });
    }
  }

  async retrieveLastPM25ByLocationsList(
    locationIds: number[],
  ): Promise<LatestLocationMeasurementData[]> {
    if (locationIds.length === 0) {
      return [];
    }
    const query = `
        SELECT
          l.id AS "locationId",
          l.location_name AS "locationName",
          l.sensor_type AS "sensorType",
          d.name AS "dataSource",
          m.pm25,
          m.rhum,
          m.measured_at AS "measuredAt"
        FROM location l
        JOIN data_source d ON l.data_source_id = d.id
        LEFT JOIN LATERAL (
          SELECT m.pm25, m.rhum, m.measured_at
          FROM measurement m
            WHERE m.location_id = l.id
              AND (m.is_pm25_outlier = false)
              AND m.measured_at >= NOW() - (
                CASE WHEN d.name = 'AirGradient'
                     THEN INTERVAL '30 minutes'
                     ELSE INTERVAL '90 minutes'
                END
              )
            ORDER BY m.measured_at DESC
            LIMIT 1
        ) m ON TRUE
        WHERE l.id = ANY($1)
        ORDER BY l.id;
    `;
    const results = await this.databaseService.runQuery(query, [locationIds]);
    return results.rows;
  }

  async retrieveLastMeasuresByLocationId(id: number, hasFullAccess: boolean) {
    const query = `
            WITH latest_measurement AS (
              SELECT *
              FROM ${hasFullAccess ? 'measurement' : 'vw_measurement_public'}
              WHERE location_id = $1
              ORDER BY measured_at DESC
              LIMIT 1
            )
            SELECT 
                m.location_id AS "locationId",
                CASE WHEN m.is_pm25_outlier = false THEN m.pm25 ELSE NULL END AS pm25,
                m.pm10,
                m.atmp,
                m.rhum,
                m.rco2,
                m.o3,
                m.no2,
                m.measured_at AS "measuredAt",
                l.sensor_type AS "sensorType",
                d.name AS "dataSource"
            FROM latest_measurement m
            JOIN location l ON m.location_id = l.id
            JOIN data_source d ON l.data_source_id = d.id
            WHERE (
                (m.is_pm25_outlier = false AND m.pm25 IS NOT NULL)  -- pm25 must be present
                OR m.pm10 IS NOT NULL
                OR m.atmp IS NOT NULL
                OR m.rhum IS NOT NULL
                OR m.rco2 IS NOT NULL
                OR m.o3 IS NOT NULL
                OR m.no2 IS NOT NULL
              );
        `;

    try {
      const result = await this.databaseService.runQuery(query, [id]);

      const lastMeasurements = result.rows[0];
      if (!lastMeasurements) {
        throw new NotFoundException({
          message: 'LOC_004: Last measures not found for location',
          operation: 'retrieveLastMeasuresByLocationId',
          parameters: { id },
          code: 'LOC_004',
        });
      }
      return lastMeasurements;
    } catch (error) {
      if (error instanceof NotFoundException) {
        throw error;
      }
      this.logger.error(error);
      throw new InternalServerErrorException({
        message: 'LOC_005: Failed to retrieve last measures by location id',
        operation: 'retrieveLastMeasuresByLocationId',
        parameters: { id },
        error: error.message,
        code: 'LOC_005',
      });
    }
  }

  private getBinClauseFromBucketSize(bucketSize: BucketSize): string {
    switch (bucketSize) {
      case BucketSize.OneMonth: {
        return `date_trunc('month', m.measured_at)`;
      }

      case BucketSize.OneYear: {
        return `date_trunc('year', m.measured_at)`;
      }

      default:
        return `date_bin($4, m.measured_at, $2)`;
    }
  }

  async retrieveLocationMeasuresHistory(
    id: number,
    start: string,
    end: string,
    bucketSize: BucketSize,
    excludeOutliers: boolean,
    measureType: MeasureType,
    hasFullAccess: boolean,
  ) {
    const { minVal, maxVal, hasValidation } = getMeasureValidValueRange(measureType);

    const validationQuery = hasValidation ? `AND m.${measureType} BETWEEN $5 AND $6` : '';

    // For pm25, we need both pm25 and rhum for EPA correction
    const selectClause =
      measureType === MeasureType.PM25
        ? `round(avg(m.pm25)::NUMERIC , 2) AS pm25, round(avg(m.rhum)::NUMERIC , 2) AS rhum`
        : `round(avg(m.${measureType})::NUMERIC , 2) AS value`;
    const binClause = this.getBinClauseFromBucketSize(bucketSize);
    const excludeOutliersQuery = excludeOutliers
      ? measureType === MeasureType.PM25
        ? 'AND m.is_pm25_outlier = false'
        : ''
      : '';

    const query = `
            SELECT
                ${binClause} AT TIME ZONE 'UTC' AS timebucket,
                ${selectClause},
                l.sensor_type AS "sensorType",
                d.name AS "dataSource",
                $4::text AS unused_bucket_param -- Make sure that $4 always be used
            FROM ${hasFullAccess ? 'measurement' : 'vw_measurement_public'} m
            JOIN location l on m.location_id = l.id
            JOIN data_source d ON l.data_source_id = d.id
            WHERE 
                m.location_id = $1 AND 
                m.measured_at BETWEEN $2 AND $3
                ${excludeOutliersQuery}
                ${validationQuery}
            GROUP BY timebucket, "sensorType", "dataSource"
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
      throw new InternalServerErrorException({
        message: 'LOC_006: Failed to retrieve location measures history',
        operation: 'retrieveLocationMeasuresHistory',
        parameters: { id, start, end, bucketSize, excludeOutliers, measureType },
        error: error.message,
        code: 'LOC_006',
      });
    }
  }

  private convertPeriodToInterval(period: string): string {
    const match = period.match(/^(\d+)([mhdw])$/);
    if (!match) {
      throw new Error(`Invalid period format: ${period}`);
    }

    const [, number, unit] = match;
    const unitMap = {
      m: 'minutes',
      h: 'hours',
      d: 'days',
      w: 'weeks',
    };

    return `${number} ${unitMap[unit as keyof typeof unitMap]}`;
  }

  private findLongestInterval(periods: string[]): string {
    // Convert periods to minutes for comparison
    const periodInMinutes = periods.map(period => {
      const match = period.match(/^(\d+)([mhdw])$/);
      if (!match) return 0;

      const [, number, unit] = match;
      const num = parseInt(number);

      switch (unit) {
        case 'm':
          return num;
        case 'h':
          return num * 60;
        case 'd':
          return num * 60 * 24;
        case 'w':
          return num * 60 * 24 * 7;
        default:
          return 0;
      }
    });

    // Find the index of the longest period
    const longestIndex = periodInMinutes.indexOf(Math.max(...periodInMinutes));

    // Return the PostgreSQL interval for the longest period
    return this.convertPeriodToInterval(periods[longestIndex]);
  }

  private buildAveragesQuery(
    measureType: MeasureType,
    hasFullAccess: boolean,
    periods?: string[],
  ): string {
    // Use default predefined periods if none specified
    const defaultPeriods = Object.values(PM25Period);
    const requestedPeriods = periods || defaultPeriods;

    const periodCases = requestedPeriods
      .map(period => {
        const interval = periods
          ? this.convertPeriodToInterval(period)
          : PM25PeriodConfig[period as PM25Period].interval;
        return `AVG(CASE WHEN measured_at >= NOW() - INTERVAL '${interval}' THEN ${measureType} END) as "${period}"`;
      })
      .join(',\n      ');

    // Find the longest interval to optimize the query
    let longestInterval: string;
    if (periods) {
      // For custom periods, find the numerically longest interval
      longestInterval = this.findLongestInterval(periods);
    } else {
      longestInterval = PM25PeriodConfig[PM25Period.DAYS_90].interval;
    }

    const { minVal, maxVal, hasValidation } = getMeasureValidValueRange(measureType);
    const validationQuery = hasValidation
      ? `AND ${measureType} BETWEEN ${minVal} AND ${maxVal}`
      : '';

    const excludeOutliersQuery =
      measureType === MeasureType.PM25 ? 'AND is_pm25_outlier = false' : '';

    return `
      SELECT 
        $1::integer as location_id,
        ${periodCases}
      FROM ${hasFullAccess ? 'measurement' : 'vw_measurement_public'}
      WHERE location_id = $1
        AND ${measureType} IS NOT NULL
        AND measured_at >= NOW() - INTERVAL '${longestInterval}'
        ${excludeOutliersQuery}
        ${validationQuery}
      GROUP BY location_id
    `;
  }

  async retrieveAveragesByLocationId(
    id: number,
    measureType: MeasureType,
    hasFullAccess: boolean,
    periods?: string[],
  ): Promise<MeasurementAveragesResult> {
    const query = this.buildAveragesQuery(measureType, hasFullAccess, periods);

    // Debug logging
    this.logger.debug(`Generated query for periods ${JSON.stringify(periods)}:`);

    try {
      const result = await this.databaseService.runQuery(query, [id]);

      if (result.rows.length === 0) {
        throw new NotFoundException({
          message: 'LOC_007: No data found for location',
          operation: 'retrieveAveragesByLocationId',
          parameters: { id },
          code: 'LOC_007',
        });
      }

      const row = result.rows[0];
      const averages: Record<string, number | null> = {};

      // Use requested periods or default predefined periods if none specified
      const defaultPeriods = Object.values(PM25Period);
      const requestedPeriods = periods || defaultPeriods;

      requestedPeriods.forEach(period => {
        averages[period] = row[period] !== null ? Math.round(row[period] * 10) / 10 : null;
      });

      return {
        locationId: id,
        averages: averages,
      };
    } catch (error) {
      if (error instanceof NotFoundException) {
        throw error;
      }
      this.logger.error(error);
      throw new InternalServerErrorException({
        message: 'LOC_008: Failed to retrieve averages',
        operation: 'retrieveAveragesByLocationId',
        parameters: { id },
        error: error.message,
        code: 'LOC_008',
      });
    }
  }

  async isLocationIdExist(id: number, hasFullAccess: boolean): Promise<void> {
    try {
      const query = `SELECT 1 FROM ${hasFullAccess ? 'location' : 'vw_location_public'} l WHERE id = $1`;
      const result = await this.databaseService.runQuery(query, [id]);
      const location = result.rows[0];
      if (!location) {
        throw new NotFoundException({
          message: 'LOC_009: Location not found',
          operation: 'isLocationIdExist',
          parameters: { id },
          code: 'LOC_009',
        });
      }
    } catch (error) {
      if (error instanceof NotFoundException) {
        throw error;
      }
      this.logger.error(error);
      throw new InternalServerErrorException({
        message: 'LOC_010: Failed to retrieve location',
        operation: 'isLocationIdExist',
        parameters: { id },
        error: error.message,
        code: 'LOC_010',
      });
    }
  }

  async retrieveDailyAveragesForCigarettes(
    id: number,
    start: string,
    end: string,
    hasFullAccess: boolean,
  ): Promise<Array<{ timebucket: string; pm25: number; rhum: number; dataSource: string }>> {
    // Use date_bin with a fixed origin (epoch) to align buckets to midnight UTC
    // This ensures consistent daily buckets regardless of query time
    const query = `
      SELECT
        date_bin('1 day', m.measured_at, TIMESTAMP '2000-01-01 00:00:00 UTC') AT TIME ZONE 'UTC' AS timebucket,
        round(avg(m.pm25)::NUMERIC, 2) AS pm25,
        round(avg(m.rhum)::NUMERIC, 2) AS rhum,
        l.sensor_type AS "sensorType",
        d.name AS "dataSource"
      FROM ${hasFullAccess ? 'measurement' : 'vw_measurement_public'} m
      JOIN location l ON m.location_id = l.id
      JOIN data_source d ON l.data_source_id = d.id
      WHERE m.location_id = $1
        AND m.measured_at BETWEEN $2 AND $3
        AND m.is_pm25_outlier = false
        AND m.pm25 IS NOT NULL
      GROUP BY timebucket, l.sensor_type, d.name
      ORDER BY timebucket
    `;

    try {
      const result = await this.databaseService.runQuery(query, [id, start, end]);
      return result.rows;
    } catch (error) {
      this.logger.error(error);
      throw new InternalServerErrorException({
        message: 'LOC_011: Failed to retrieve daily averages for cigarettes calculation',
        operation: 'retrieveDailyAveragesForCigarettes',
        parameters: { id, start, end },
        error: error.message,
        code: 'LOC_011',
      });
    }
  }
}

export default LocationRepository;
