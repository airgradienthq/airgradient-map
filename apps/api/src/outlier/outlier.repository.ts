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

  public async getBatchLast24HoursPm25Measurements(
    locationReferenceIds: number[],
    measuredAts: string[],
  ): Promise<Map<number, PM25DataPointEntity[]>> {
    try {
      /**
       * Batch query to fetch historical PM2.5 data for multiple locations at once.
       *
       * 1. Use ANY($1::int[]) to match all locations in one WHERE clause
       * 2. Find the earliest timestamp and fetch data from (earliest - 24h) onwards
       * 3. Return all data grouped by reference_id for in-memory filtering per measurement
       *
       * Trade-off: Fetches slightly more data than needed, but reduces bunch of queries to 1.
       */
      const query = `
        SELECT
          l.reference_id,
          m.measured_at,
          m.pm25
        FROM public.measurement m
        JOIN public.location l ON m.location_id = l.id
        WHERE l.reference_id = ANY($1::int[])
          AND m.measured_at >= (
            SELECT MIN(ts)::timestamp - INTERVAL '24 HOURS'
            FROM unnest($2::text[]) AS ts
          )
        ORDER BY l.reference_id, m.measured_at;
      `;
      const result = await this.databaseService.runQuery(query, [
        locationReferenceIds,
        measuredAts,
      ]);

      // Group results by reference_id
      const dataMap = new Map<number, PM25DataPointEntity[]>();
      result.rows.forEach((r: any) => {
        const refId = Number(r.reference_id);
        if (!dataMap.has(refId)) {
          dataMap.set(refId, []);
        }
        dataMap.get(refId).push(
          new PM25DataPointEntity({
            measuredAt: new Date(r.measured_at),
            pm25: Number(r.pm25),
          }),
        );
      });

      return dataMap;
    } catch (error) {
      this.logger.error(error);
      throw new InternalServerErrorException({
        message: 'OUT_003: Failed to retrieve batch last 24 Hours pm25',
        operation: 'getBatchLast24HoursPm25Measurements',
        parameters: {
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

  public async getBatchSpatialZScoreStats(
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
       * 3. Calculates mean & stddev for each pair's neighborhood
       * 4. Returns all results in one round-trip
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
              ON ST_DWithin(l1.coordinate::geography, l2.coordinate::geography, $3)
            JOIN measurement m
              ON m.location_id = l2.id
            WHERE l1.reference_id = input.reference_id
              AND m.is_pm25_outlier = false
              AND m.measured_at BETWEEN input.measured_at::timestamp - make_interval(hours => $4::int)
                                  AND input.measured_at::timestamp + make_interval(hours => $4::int)
            ORDER BY l2.id, ABS(EXTRACT(EPOCH FROM (m.measured_at - input.measured_at::timestamp)))
          )
          SELECT
            AVG(pm25) AS mean,
            STDDEV_SAMP(pm25) AS stddev
          FROM nearby
          WHERE (SELECT COUNT(*) FROM nearby) >= $5
        ) AS stats;
      `;
      const value = [
        locationReferenceIds,
        measuredAts,
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
