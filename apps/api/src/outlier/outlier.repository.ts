import { Injectable, Logger, InternalServerErrorException } from '@nestjs/common';
import DatabaseService from 'src/database/database.service';
import { NearbyStats } from './nearby-stats.entity';
import { DataSource } from 'src/types';
import { MeasureType } from 'src/types';
import { OUTLIER_COLUMN_NAME } from 'src/constants/outlier-column-name';

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
    minNearbyCountSecondCircle: number,
    maxDynamicRadiusMetersSecondCircle: number,
  ): Promise<Map<string, NearbyStats>> {
    try {
      const outlierColumnName = OUTLIER_COLUMN_NAME[measureType];
      if (!outlierColumnName) {
        throw new Error(`Unsupported measureType for outlier detection: ${measureType}`);
      }

      // Small buffer to reduce underfill risk after filtering out invalid measurements.
      const nearestLocationsLimit = Math.max(minNearbyCountSecondCircle, minNearbyCount) + 10;

      /**
       * Batch query to calculate spatial statistics for multiple location/timestamp pairs.
       *
       * Strategy (single LATERAL query for all pairs, KNN-based):
       * 1. `unnest()` creates a virtual table of (locationReferenceId, measuredAt) pairs.
       * 2. For each pair, a LATERAL subquery:
       *    - KNN fetches the closest N locations (buffered above minNearbyCountSecondCircle)
       *      ordered by geographic distance. If fewer than the minimum remain after filtering,
       *      dynamic stats will be null.
        *    - For each nearby location, picks the single closest-in-time measurement
        *      within the configured time window, excluding outliers.
       *
       * 3. From that candidate set it computes **two sets of stats**:
       *    - First circle (fixed radius):
       *      - Uses a fixed radius in meters (radiusMeters).
       *      - Computes mean & stddev of the measureType if the number of nearby
       *        measurements within that radius is >= minNearbyCount.
       *
       *    - Second circle (dynamic radius):
       *      - Orders candidates by distance and finds the radius needed to include
       *        at least minNearbyCountSecondCircle nearby sensors.
       *      - If this second circle radius <= first circle radius -> set second circle radius = first circle radius + 1 km.
       *      - Uses that as a dynamic radius **only if** it is less than
       *        maxDynamicRadiusMetersSecondCircle (e.g. 500 km).
       *      - Computes mean & stddev over all measurements within this dynamic
       *        radius when the above conditions are satisfied.
       *
       * 4. Returns, for each input (locationReferenceId, measuredAt) pair, both:
       *    - Fixed-radius stats (mean/stddev), and
       *    - Dynamic-radius stats (mean/stddev),
       *    with NULLs when the respective minimum-count / radius constraints
       *    are not met.
       *
       * This collapses many spatial/statistical lookups into a single LATERAL
       * query, minimizing round-trips while supporting both fixed and adaptive
       * neighborhood definitions.
       */

      const query = `
        WITH input_pairs AS (
          SELECT
            inp.reference_id,
            inp.measured_at::timestamp AS measured_at,
            l1.coordinate::geography AS coord_geog, -- base geography for KNN distance
            inp.measured_at::timestamp - make_interval(hours => $5::int) AS window_start,
            inp.measured_at::timestamp + make_interval(hours => $5::int) AS window_end
          FROM unnest($1::int[], $2::text[]) AS inp(reference_id, measured_at)
          JOIN data_source ds ON ds.name = $3
          JOIN location l1
            ON l1.reference_id = inp.reference_id
            AND l1.data_source_id = ds.id
        )

        SELECT
          input_pairs.reference_id,
          input_pairs.measured_at,
          stats.first_circle_mean,
          stats.first_circle_stddev,
          stats.second_circle_mean,
          stats.second_circle_stddev
        FROM input_pairs
        CROSS JOIN LATERAL (
          WITH nearest_locations AS (
            SELECT
              l2.id,
              l2.coordinate::geography AS coord_geog
            FROM location l2
            ORDER BY input_pairs.coord_geog <-> l2.coordinate::geography
            LIMIT $9  -- KNN: take closest N locations with buffer
          ),

          -- First circle: use fixed-radius ST_DWithin (no LIMIT) to avoid bias in dense areas
          first_circle_candidates AS (
            SELECT DISTINCT ON (l2.id)
              m.${measureType} AS value
            FROM location l2
            JOIN measurement m
              ON m.location_id = l2.id
            WHERE ST_DWithin(input_pairs.coord_geog, l2.coordinate::geography, $4)
              AND m.${measureType} IS NOT NULL
              AND m.${outlierColumnName} = false
              AND m.measured_at BETWEEN input_pairs.window_start AND input_pairs.window_end
            ORDER BY
              l2.id,
              ABS(EXTRACT(EPOCH FROM (m.measured_at - input_pairs.measured_at)))
          ),

          first_stats AS (
            SELECT
              COUNT(*)      AS first_count,
              AVG(value)    AS first_mean,
              STDDEV_SAMP(value) AS first_stddev
            FROM first_circle_candidates
          ),

          nearby AS (
            SELECT DISTINCT ON (l2.id)
              m.${measureType} AS value,
              ST_Distance(input_pairs.coord_geog, l2.coord_geog) AS distance_m
            FROM nearest_locations l2
            JOIN measurement m
              ON m.location_id = l2.id
            WHERE m.${measureType} IS NOT NULL
              AND m.${outlierColumnName} = false
              AND m.measured_at BETWEEN input_pairs.window_start AND input_pairs.window_end
            ORDER BY
              l2.id,
              ABS(EXTRACT(EPOCH FROM (m.measured_at - input_pairs.measured_at)))
          ),

          -- Order by distance for dynamic-radius logic
          ordered AS (
            SELECT
              value,
              distance_m,
              ROW_NUMBER() OVER (ORDER BY distance_m) AS rn
            FROM nearby
          ),

          -- Radius needed to include the Nth closest sensor (if we have >= N)
          -- If this second circle radius <= first circle radius -> set second circle radius = first circle radius + 1 km.
          radius_choice AS (
            SELECT
              CASE
                WHEN COUNT(*) >= $7 THEN
                  CASE
                    WHEN (MAX(distance_m) FILTER (WHERE rn <= $7)) <= $4
                      THEN $4 + 1000
                    ELSE (MAX(distance_m) FILTER (WHERE rn <= $7))
                  END
              END AS radius_m
            FROM ordered
          ),

          -- Stats for the second (dynamic-radius) circle
          second_stats AS (
            SELECT
              COUNT(*)               AS second_count,
              AVG(o.value)           AS second_mean,
              STDDEV_SAMP(o.value)   AS second_stddev
            FROM ordered o
            CROSS JOIN radius_choice r
            WHERE r.radius_m IS NOT NULL
              AND r.radius_m < $8                -- dynamic radius must be < maxDynamicRadiusMetersSecondCircle (e.g. 500 km)
              AND o.distance_m <= r.radius_m
          )

          SELECT
            -- First circle: only if enough nearby measurements (>= minNearbyCount)
            CASE WHEN first_stats.first_count >= $6
                THEN first_stats.first_mean
            END AS first_circle_mean,
            CASE WHEN first_stats.first_count >= $6
                THEN first_stats.first_stddev
            END AS first_circle_stddev,

            -- Second circle: only if we have >= minNearbyCountSecondCircle sensors AND radius < maxDynamicRadiusMetersSecondCircle
            CASE WHEN second_stats.second_count >= $7
                  AND radius_choice.radius_m < $8
                THEN second_stats.second_mean
            END AS second_circle_mean,
            CASE WHEN second_stats.second_count >= $7
                  AND radius_choice.radius_m < $8
                THEN second_stats.second_stddev
            END AS second_circle_stddev
          FROM first_stats
          LEFT JOIN second_stats   ON TRUE
          LEFT JOIN radius_choice  ON TRUE
        ) AS stats;
      `;

      const values = [
        locationReferenceIds, // $1
        measuredAts, // $2
        dataSource, // $3
        radiusMeters, // $4 (first circle radius)
        measuredAtIntervalHours, // $5
        minNearbyCount, // $6 (first circle minimum points)
        minNearbyCountSecondCircle, // $7 (e.g. 10)
        maxDynamicRadiusMetersSecondCircle, // $8 (e.g. 500000 for 500 km)
        nearestLocationsLimit, // $9 buffered KNN limit
      ];

      const result = await this.databaseService.runQuery(query, values);

      // Build map keyed by "referenceId_measuredAt"
      const statsMap = new Map<string, NearbyStats>();
      result.rows.forEach((r: any) => {
        const key = `${r.reference_id}_${r.measured_at}`;
        statsMap.set(
          key,
          new NearbyStats(
            r.first_circle_mean,
            r.first_circle_stddev,
            r.second_circle_mean,
            r.second_circle_stddev,
          ),
        );
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
