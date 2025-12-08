import { Injectable, Logger, InternalServerErrorException } from '@nestjs/common';
import DatabaseService from 'src/database/database.service';
import { NearbyPm25Stats } from './nearby-pm25-stats.entity';

@Injectable()
export class OutlierRepository {
  private readonly logger = new Logger(OutlierRepository.name);

  constructor(private readonly databaseService: DatabaseService) {}

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
       * 1. unnest() creates virtual table of (referenceId, measuredAt, pm25) input tuples
       * 2. For each tuple, subquery checks if last 24h has same value (COUNT(DISTINCT pm25) = 1)
       * 3. Returns only boolean results
       *
       * This eliminates the memory issue by doing computation in DB instead of App.
       */
      // NOTE: future improvement for this query just put to PL/pgSQL function since it will rarely change
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
                JOIN data_source ds ON l.data_source_id = ds.id
                WHERE ds.name = $1
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
        code: 'OUT_001',
      });
    }
  }

  public async getBatchSpatialZScoreStats(
    dataSource: string,
    locationReferenceIds: number[],
    measuredAts: string[],
    radiusMeters: number,
    measuredAtIntervalHours: number,
    minNearbyCount: number,
  ): Promise<Map<string, NearbyPm25Stats>> {
    try {
      /**
       * Batch query to calculate spatial statistics for multiple location/timestamp pairs.
       *
       * Strategy: Uses LATERAL JOIN to execute spatial analysis for each pair in one query:
       * 1. unnest() creates a virtual table of (locationReferenceId, measuredAt) pairs
       * 2. LATERAL processes each pair: finds nearby locations within radius, gets closest measurement per location
       * 3. Filter by data_source to ensure locationReferenceId uniqueness
       * 4. Calculates mean & stddev for each pair's neighborhood
       * 5. Returns all results in one round-trip
       *
       * This reduces individual spatial queries to 1 LATERAL JOIN query.
       */
      const query = `
        SELECT
          input.reference_id,
          input.measured_at,
          stats.mean,
          stats.stddev
        FROM unnest($1::int[], $2::text[]) AS input(reference_id, measured_at)
        CROSS JOIN LATERAL (
          WITH nearby AS (
            SELECT DISTINCT ON (l2.id) m.pm25
            FROM location l1
            JOIN location l2
              ON ST_DWithin(l1.coordinate::geography, l2.coordinate::geography, $4)
            JOIN measurement m
              ON m.location_id = l2.id
            JOIN data_source ds 
              ON l1.data_source_id = ds.id
            WHERE ds.name = $3
              AND l1.reference_id = input.reference_id
              AND m.is_pm25_outlier = false
              AND m.measured_at BETWEEN input.measured_at::timestamp - make_interval(hours => $5::int)
                                  AND input.measured_at::timestamp + make_interval(hours => $5::int)
            ORDER BY l2.id, ABS(EXTRACT(EPOCH FROM (m.measured_at - input.measured_at::timestamp)))
          )
          SELECT
            AVG(pm25) AS mean,
            STDDEV_SAMP(pm25) AS stddev
          FROM nearby
          WHERE (SELECT COUNT(*) FROM nearby) >= $6
        ) AS stats;
      `;
      const value = [
        locationReferenceIds,
        measuredAts,
        dataSource,
        radiusMeters,
        measuredAtIntervalHours,
        minNearbyCount,
      ];
      const result = await this.databaseService.runQuery(query, value);

      // Build map keyed by "referenceId_measuredAt"
      const statsMap = new Map<string, NearbyPm25Stats>();
      result.rows.forEach((r: any) => {
        const key = `${r.reference_id}_${r.measured_at}`;
        statsMap.set(key, new NearbyPm25Stats(r.mean, r.stddev));
      });

      return statsMap;
    } catch (error) {
      this.logger.error(error);
      throw new InternalServerErrorException({
        message: 'OUT_004: Failed to calculate batch spatial Z Score stats',
        operation: 'getBatchSpatialZScoreStats',
        parameters: {
          dataSource,
          locationCount: locationReferenceIds.length,
          timestampCount: measuredAts.length,
          radiusMeters,
          measuredAtIntervalHours,
          minNearbyCount,
        },
        error: error.message,
        code: 'OUT_002',
      });
    }
  }
}
