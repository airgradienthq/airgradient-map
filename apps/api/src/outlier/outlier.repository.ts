import { Injectable, Logger, InternalServerErrorException } from '@nestjs/common';
import DatabaseService from 'src/database/database.service';
import { PM25DataPointEntity } from './pm25-data-point.entity';
import { NearbyPm25Stats } from './nearby-pm25-stats.entity';

@Injectable()
export class OutlierRepository {
  private readonly logger = new Logger(OutlierRepository.name);

  constructor(private readonly databaseService: DatabaseService) {}

  public async getLast24HoursPm25Measurements(
    locationReferenceId: number,
    measuredAt: string,
  ): Promise<PM25DataPointEntity[]> {
    try {
      const query = `
        SELECT measured_at, pm25
        FROM public.measurement m
        JOIN public.location l ON m.location_id = l.id
        WHERE l.reference_id = $1
          AND m.measured_at >= ($2::timestamp - INTERVAL '24 HOURS');
      `;
      const result = await this.databaseService.runQuery(query, [locationReferenceId, measuredAt]);
      return result.rows.map(
        (r: any) =>
          new PM25DataPointEntity({
            measuredAt: new Date(r.measured_at),
            pm25: Number(r.pm25),
          }),
      );
    } catch (error) {
      this.logger.error(error);
      throw new InternalServerErrorException({
        message: 'OUT_001: Failed to retrieve last 24 Hours pm25 by id',
        operation: 'getLast24HoursPm25Measurements',
        parameters: { locationReferenceId, measuredAt },
        error: error.message,
        code: 'OUT_001',
      });
    }
  }

  public async getBatchSameValue24hCheck(
    dataSource: string,
    locationReferenceIds: number[],
    measuredAts: string[],
    pm25Values: number[],
  ): Promise<Map<string, boolean>> {
    try {
      /**
       * Batch query to check if PM2.5 value has been the same for 24 hours (database-level computation).
       *
       * Strategy: Instead of fetching all historical data to JavaScript:
       * 1. unnest() creates virtual table of (referenceId, measuredAt, pm25) input tuples
       * 2. For each tuple, subquery checks if last 24h has same value (COUNT(DISTINCT pm25) = 1)
       * 3. Returns only boolean results - massive memory savings (200 bools vs 57,600+ rows)
       *
       * This eliminates the memory issue by doing computation in PostgreSQL instead of Node.js.
       */
      const query = `
        SELECT
          input.reference_id,
          input.measured_at,
          CASE
            WHEN input.pm25 != 0
              AND (
                SELECT COUNT(*) >= 3
                  AND COUNT(DISTINCT m.pm25) = 1
                  AND MIN(m.pm25) = input.pm25
                FROM measurement m
                JOIN location l ON m.location_id = l.id
                WHERE l.data_source = $1
                  AND l.reference_id = input.reference_id
                  AND m.measured_at >= input.measured_at::timestamp - INTERVAL '24 HOURS'
                  AND m.measured_at < input.measured_at::timestamp
              )
            THEN true
            ELSE false
          END as is_same_value_outlier
        FROM unnest($2::int[], $3::text[], $4::numeric[])
          AS input(reference_id, measured_at, pm25);
      `;
      const result = await this.databaseService.runQuery(query, [
        dataSource,
        locationReferenceIds,
        measuredAts,
        pm25Values,
      ]);

      // Build map keyed by "referenceId_measuredAt"
      const resultMap = new Map<string, boolean>();
      result.rows.forEach((r: any) => {
        const key = `${r.reference_id}_${r.measured_at}`;
        resultMap.set(key, r.is_same_value_outlier);
      });

      return resultMap;
    } catch (error) {
      this.logger.error(error);
      throw new InternalServerErrorException({
        message: 'OUT_003: Failed to check batch same value for 24 hours',
        operation: 'getBatchSameValue24hCheck',
        parameters: {
          dataSource,
          locationCount: locationReferenceIds.length,
          timestampCount: measuredAts.length,
        },
        error: error.message,
        code: 'OUT_003',
      });
    }
  }

  public async getSpatialZScoreStats(
    locationReferenceId: number,
    measuredAt: string,
    radiusMeters: number,
    measuredAtIntervalHours: number,
    minNearbyCount: number,
  ): Promise<NearbyPm25Stats> {
    try {
      const query = `
        WITH nearby AS (
          SELECT DISTINCT ON (l2.id) m.pm25
          FROM location l1
          JOIN location l2
            ON ST_DWithin(l1.coordinate::geography, l2.coordinate::geography, $1)
          JOIN measurement m
            ON m.location_id = l2.id
          WHERE l1.reference_id = $2
            AND m.is_pm25_outlier = false
            AND m.measured_at BETWEEN $3::timestamp - make_interval(hours => $4::int)
                                AND $3::timestamp + make_interval(hours => $4::int)
          ORDER BY
            l2.id,
            ABS(EXTRACT(EPOCH FROM (m.measured_at - $3::timestamp)))
        )
        SELECT
          AVG(pm25) AS mean,
          STDDEV_SAMP(pm25) AS stddev
        FROM nearby
        WHERE (SELECT COUNT(*) FROM nearby) >= $5;
      `;
      const value = [
        radiusMeters,
        locationReferenceId,
        measuredAt,
        measuredAtIntervalHours,
        minNearbyCount,
      ];
      const result = await this.databaseService.runQuery(query, value);
      const row = result.rows[0];
      return new NearbyPm25Stats(row.mean, row.stddev);
    } catch (error) {
      this.logger.error(error);
      throw new InternalServerErrorException({
        message: 'OUT_002: Failed to calculate the Z Score',
        operation: 'getSpatialZScoreStats',
        parameters: {
          locationReferenceId,
          measuredAt,
          radiusMeters,
          measuredAtIntervalHours,
          minNearbyCount,
        },
        error: error.message,
        code: 'OUT_002',
      });
    }
  }

  public async getBatchSpatialOutlierCheck(
    dataSource: string,
    locationReferenceIds: number[],
    measuredAts: string[],
    pm25Values: number[],
    radiusMeters: number,
    measuredAtIntervalHours: number,
    minNearbyCount: number,
    zScoreThreshold: number,
    absoluteThreshold: number,
  ): Promise<Map<string, boolean>> {
    try {
      /**
       * Batch query to check spatial outliers entirely in database (no LATERAL JOIN).
       *
       * Strategy: Do ALL computation in PostgreSQL instead of fetching stats to JavaScript:
       * 1. unnest() creates virtual table of input (referenceId, measuredAt, pm25) tuples
       * 2. For each tuple, correlated subquery calculates mean/stddev from nearby locations
       * 3. CASE statement applies Z-score or absolute threshold logic directly in SQL
       * 4. Returns only boolean results - massive performance gain
       *
       * This eliminates LATERAL JOIN complexity and memory issues.
       */
      const query = `
        SELECT
          input.reference_id,
          input.measured_at,
          CASE
            WHEN spatial_stats.mean IS NULL OR spatial_stats.stddev IS NULL THEN false
            WHEN spatial_stats.mean >= 50 THEN
              ABS((input.pm25 - spatial_stats.mean) / NULLIF(spatial_stats.stddev, 0)) > $7
            ELSE
              ABS(input.pm25 - spatial_stats.mean) > $8
          END as is_spatial_outlier
        FROM unnest($2::int[], $3::text[], $4::numeric[]) AS input(reference_id, measured_at, pm25)
        CROSS JOIN LATERAL (
          SELECT
            AVG(nearby_pm25) as mean,
            STDDEV_SAMP(nearby_pm25) as stddev
          FROM (
            SELECT DISTINCT ON (l2.id) m.pm25 as nearby_pm25
            FROM location l1
            JOIN location l2
              ON ST_DWithin(l1.coordinate::geography, l2.coordinate::geography, $5)
            JOIN measurement m
              ON m.location_id = l2.id
            WHERE l1.data_source = $1
              AND l1.reference_id = input.reference_id
              AND m.is_pm25_outlier = false
              AND m.measured_at BETWEEN input.measured_at::timestamp - make_interval(hours => $6::int)
                                  AND input.measured_at::timestamp + make_interval(hours => $6::int)
            ORDER BY l2.id, ABS(EXTRACT(EPOCH FROM (m.measured_at - input.measured_at::timestamp)))
          ) nearby_data
          WHERE (SELECT COUNT(*) FROM (
            SELECT DISTINCT ON (l2.id) l2.id
            FROM location l1
            JOIN location l2
              ON ST_DWithin(l1.coordinate::geography, l2.coordinate::geography, $5)
            JOIN measurement m
              ON m.location_id = l2.id
            WHERE l1.data_source = $1
              AND l1.reference_id = input.reference_id
              AND m.is_pm25_outlier = false
              AND m.measured_at BETWEEN input.measured_at::timestamp - make_interval(hours => $6::int)
                                  AND input.measured_at::timestamp + make_interval(hours => $6::int)
          ) count_check) >= $9
        ) AS spatial_stats;
      `;
      const value = [
        dataSource,
        locationReferenceIds,
        measuredAts,
        pm25Values,
        radiusMeters,
        measuredAtIntervalHours,
        zScoreThreshold,
        absoluteThreshold,
        minNearbyCount,
      ];
      const result = await this.databaseService.runQuery(query, value);

      // Build map keyed by "referenceId_measuredAt"
      const resultMap = new Map<string, boolean>();
      result.rows.forEach((r: any) => {
        const key = `${r.reference_id}_${r.measured_at}`;
        resultMap.set(key, r.is_spatial_outlier);
      });

      return resultMap;
    } catch (error) {
      this.logger.error(error);
      throw new InternalServerErrorException({
        message: 'OUT_004: Failed to check batch spatial outliers',
        operation: 'getBatchSpatialOutlierCheck',
        parameters: {
          dataSource,
          locationCount: locationReferenceIds.length,
          timestampCount: measuredAts.length,
          radiusMeters,
          measuredAtIntervalHours,
          minNearbyCount,
        },
        error: error.message,
        code: 'OUT_004',
      });
    }
  }
}
