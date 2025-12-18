import { Injectable, Logger, InternalServerErrorException } from '@nestjs/common';
import DatabaseService from 'src/database/database.service';
import { NearbyStats } from './nearby-stats.entity';
import { DataSource } from 'src/types';
import { MeasureType } from 'src/types';

@Injectable()
export class OutlierRepository {
  private readonly logger = new Logger(OutlierRepository.name);

  constructor(private readonly databaseService: DatabaseService) {}

  public async getBatchSameValue24hCheck(
    dataSource: DataSource,
    measureType: MeasureType,
    locationReferenceIds: number[],
    measuredAts: string[],
    values: number[],
  ): Promise<Map<string, boolean>> {
    try {
      /**
       * Batch query to check if value has been the same for 24 hours (database-level computation).
       *
       * 1. unnest() creates virtual table of (referenceId, measuredAt, value) input tuples
       * 2. For each tuple, subquery checks if last 24h has same value (COUNT(DISTINCT value) = 1)
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
            WHEN input.value != 0 -- value can be 0 for a long time (good air quality)
              AND (
                SELECT COUNT(*) >= 3
                  AND COUNT(DISTINCT m.${measureType}) = 1
                  AND MIN(m.${measureType}) = input.value
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
          AS input(reference_id, measured_at, value);
      `;
      const result = await this.databaseService.runQuery(query, [
        dataSource,
        locationReferenceIds,
        measuredAts,
        values,
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
          measureType,
          locationCount: locationReferenceIds.length,
          timestampCount: measuredAts.length,
        },
        error: error.message,
        code: 'OUT_001',
      });
    }
  }

  public async getBatchSpatialZScoreStats(
    dataSource: DataSource,
    measureType: MeasureType,
    locationReferenceIds: number[],
    measuredAts: string[],
    radiusMeters: number,
    measuredAtIntervalHours: number,
    minNearbyCount: number,
  ): Promise<Map<string, NearbyStats>> {
    try {
      /**
       * Batch query to calculate spatial statistics: ROBUST-Z Score for multiple location/timestamp pairs.
       *
       * Robust Z-score = (value - Median)/Scaled MAD
       *
       * Scaled MAD here is the 1.4826 * Median Absolute Deviation to make it "normal-consistent":
       *   Scaled MAD = 1.4826 * MAD
       *   MAD = median( |x - median(x)| )
       *
       * Strategy: Uses LATERAL JOIN to execute spatial analysis for each pair in one query:
       * 1. unnest() creates a virtual table of (locationReferenceId, measuredAt) pairs
       * 2. LATERAL processes each pair: finds nearby locations within radius, gets closest measurement per location
       *  2.1 Do not exclude outliers, because the most recent value could be incorrectly flagged as an outlier
       *  2.2 Using a robust z-score (median + scaled MAD) makes this safe, since extreme values have much less influence on the neighborhood statistics.
       * 3. Filter by data_source to ensure locationReferenceId uniqueness
       * 4. Calculates median & scaled MAD for each pair's neighborhood
       * 5. Returns all results in one round-trip
       *
       * This reduces individual spatial queries to 1 LATERAL JOIN query.
       */
      const query = `
        SELECT
          input.reference_id,
          input.measured_at,
          stats.median,
          stats.scaled_mad
        FROM unnest($1::int[], $2::text[]) AS input(reference_id, measured_at)
        LEFT JOIN LATERAL (
          WITH nearby AS (
            SELECT DISTINCT ON (l2.id) m.${measureType} AS value
            FROM location l1
            JOIN location l2
              ON ST_DWithin(l1.coordinate::geography, l2.coordinate::geography, $4)
            JOIN measurement m
              ON m.location_id = l2.id
            JOIN data_source ds 
              ON l1.data_source_id = ds.id
            WHERE ds.name = $3
              AND l1.reference_id = input.reference_id
              AND m.measured_at BETWEEN input.measured_at::timestamp - make_interval(hours => $5::int)
                                  AND input.measured_at::timestamp + make_interval(hours => $5::int)
            ORDER BY l2.id, ABS(EXTRACT(EPOCH FROM (m.measured_at - input.measured_at::timestamp)))
          ),
          base_stats AS (
            SELECT
              percentile_cont(0.5) WITHIN GROUP (ORDER BY value) AS median,
              COUNT(*) AS nearby_count
            FROM nearby
          ),
          stats AS (
            SELECT
              base_stats.median,
              1.4826 * (
                SELECT percentile_cont(0.5) WITHIN GROUP (ORDER BY ABS(value - base_stats.median))
                FROM nearby
              ) AS scaled_mad
            FROM base_stats
            WHERE base_stats.nearby_count >= $6
          )
          SELECT
            stats.median,
            stats.scaled_mad
          FROM stats
        ) AS stats ON true;
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
      const statsMap = new Map<string, NearbyStats>();
      result.rows.forEach((r: any) => {
        const key = `${r.reference_id}_${r.measured_at}`;
        statsMap.set(key, new NearbyStats(r.median, r.scaled_mad));
      });

      return statsMap;
    } catch (error) {
      this.logger.error(error);
      throw new InternalServerErrorException({
        message: 'OUT_004: Failed to calculate batch spatial Z Score stats',
        operation: 'getBatchSpatialZScoreStats',
        parameters: {
          dataSource,
          measureType,
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
