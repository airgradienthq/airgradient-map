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

  async retrieveLocations(offset: number = 0, limit: number = 100): Promise<LocationEntity[]> {
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
      throw new InternalServerErrorException({
        message: 'LOC_001: Failed to retrieve locations',
        operation: 'retrieveLocations',
        parameters: { offset, limit },
        error: error.message,
        code: 'LOC_001',
      });
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
          l.data_source AS "dataSource",
          m.pm25,
          m.rhum,
          m.measured_at AS "measuredAt"
        FROM location l
        LEFT JOIN LATERAL (
          SELECT m.pm25, m.rhum, m.measured_at
          FROM measurement m
            WHERE m.location_id = l.id
              AND (m.is_pm25_outlier = false)
              AND m.measured_at >= NOW() - (
                CASE WHEN l.data_source = 'AirGradient'
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

  async retrieveLastMeasuresByLocationId(id: number) {
    const query = `
            WITH latest_measurement AS (
              SELECT *
              FROM measurement
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
                l.data_source AS "dataSource"
            FROM latest_measurement m
            JOIN location l ON m.location_id = l.id
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
                l.data_source AS "dataSource",
                $4::text AS unused_bucket_param -- Make sure that $4 always be used
            FROM measurement m
            JOIN location l on m.location_id = l.id
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

  private buildAveragesQuery(measureType: MeasureType, periods?: string[]): string {
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
      FROM measurement
      WHERE location_id = $1
        AND ${measureType} IS NOT NULL
        AND measured_at >= NOW() - INTERVAL '${longestInterval}'
        ${excludeOutliersQuery}
        ${validationQuery}
      GROUP BY location_id
    `;
  }

  private buildAveragesQueryWithEPA(measureType: MeasureType, periods?: string[]): string {
    // Use default predefined periods if none specified
    const defaultPeriods = Object.values(PM25Period);
    const requestedPeriods = periods || defaultPeriods;

    // EPA correction formula as inline CASE statement
    const epaCorrection = `
      CASE
        WHEN l.data_source != 'AirGradient' THEN m.pm25
        WHEN m.pm25 IS NULL OR m.rhum IS NULL THEN NULL
        WHEN m.pm25 = 0 THEN 0
        WHEN m.pm25 < 30 THEN
          GREATEST(ROUND((0.524 * m.pm25 - 0.0862 * m.rhum + 5.75)::NUMERIC, 1), 0)
        WHEN m.pm25 < 50 THEN
          GREATEST(ROUND((
            (0.786 * (m.pm25 / 20.0 - 1.5) + 0.524 * (1 - (m.pm25 / 20.0 - 1.5))) * m.pm25
            - 0.0862 * m.rhum
            + 5.75
          )::NUMERIC, 1), 0)
        WHEN m.pm25 < 210 THEN
          GREATEST(ROUND((0.786 * m.pm25 - 0.0862 * m.rhum + 5.75)::NUMERIC, 1), 0)
        WHEN m.pm25 < 260 THEN
          GREATEST(ROUND((
            (0.69 * (m.pm25 / 50.0 - 4.2) + 0.786 * (1 - (m.pm25 / 50.0 - 4.2))) * m.pm25
            - 0.0862 * m.rhum * (1 - (m.pm25 / 50.0 - 4.2))
            + 2.966 * (m.pm25 / 50.0 - 4.2)
            + 5.75 * (1 - (m.pm25 / 50.0 - 4.2))
            + 0.000884 * m.pm25 * m.pm25 * (m.pm25 / 50.0 - 4.2)
          )::NUMERIC, 1), 0)
        ELSE
          GREATEST(ROUND((2.966 + 0.69 * m.pm25 + 0.000884 * m.pm25 * m.pm25)::NUMERIC, 1), 0)
      END
    `;

    const periodCases = requestedPeriods
      .map(period => {
        const interval = periods
          ? this.convertPeriodToInterval(period)
          : PM25PeriodConfig[period as PM25Period].interval;

        // For PM25, use EPA correction; for other measures, use raw value
        const measureColumn = measureType === MeasureType.PM25 ? epaCorrection : `m.${measureType}`;

        return `AVG(CASE WHEN m.measured_at >= NOW() - INTERVAL '${interval}' THEN ${measureColumn} END) as "${period}"`;
      })
      .join(',\n      ');

    // Find the longest interval to optimize the query
    let longestInterval: string;
    if (periods) {
      longestInterval = this.findLongestInterval(periods);
    } else {
      longestInterval = PM25PeriodConfig[PM25Period.DAYS_90].interval;
    }

    const { minVal, maxVal, hasValidation } = getMeasureValidValueRange(measureType);
    const validationQuery = hasValidation
      ? `AND m.${measureType} BETWEEN ${minVal} AND ${maxVal}`
      : '';

    const excludeOutliersQuery =
      measureType === MeasureType.PM25 ? 'AND m.is_pm25_outlier = false' : '';

    return `
      SELECT
        $1::integer as location_id,
        ${periodCases}
      FROM measurement m
      JOIN location l ON m.location_id = l.id
      WHERE m.location_id = $1
        AND m.${measureType} IS NOT NULL
        AND m.measured_at >= NOW() - INTERVAL '${longestInterval}'
        ${excludeOutliersQuery}
        ${validationQuery}
      GROUP BY m.location_id
    `;
  }

  async retrieveAveragesByLocationId(
    id: number,
    measureType: MeasureType,
    periods?: string[],
  ): Promise<MeasurementAveragesResult> {
    const query = this.buildAveragesQuery(measureType, periods);

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

  async retrieveEPACorrectedAveragesByLocationId(
    id: number,
    measureType: MeasureType,
    periods?: string[],
  ): Promise<MeasurementAveragesResult> {
    const query = this.buildAveragesQueryWithEPA(measureType, periods);

    // Debug logging
    this.logger.debug(`Generated EPA-corrected query for periods ${JSON.stringify(periods)}:`);

    try {
      const result = await this.databaseService.runQuery(query, [id]);

      if (result.rows.length === 0) {
        throw new NotFoundException({
          message: 'LOC_011: No data found for location',
          operation: 'retrieveEPACorrectedAveragesByLocationId',
          parameters: { id },
          code: 'LOC_011',
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
        message: 'LOC_012: Failed to retrieve EPA-corrected averages',
        operation: 'retrieveEPACorrectedAveragesByLocationId',
        parameters: { id },
        error: error.message,
        code: 'LOC_012',
      });
    }
  }

  async isLocationIdExist(id: number): Promise<void> {
    try {
      const query = 'SELECT 1 FROM location WHERE id = $1';
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
}

export default LocationRepository;
