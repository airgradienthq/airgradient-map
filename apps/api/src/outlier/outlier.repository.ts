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
}
