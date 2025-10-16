import { Injectable, Logger } from '@nestjs/common';
import DatabaseService from 'src/database/database.service';

@Injectable()
export class OutlierRepository {
  private readonly logger = new Logger(OutlierRepository.name);

  constructor(private readonly databaseService: DatabaseService) {}

  public async getLast24HoursPm25Measurements(dataPoint: {
    locationId: number;
    pm25: number;
    measuredAt: string;
  }) {
    try {
      return await this.databaseService.runQuery(
        `SELECT measured_at, pm25 
          FROM public.measurement
          WHERE location_id = '${dataPoint.locationId}' 
          AND measured_at >= (TIMESTAMP '${dataPoint.measuredAt}' - INTERVAL '24 HOURS');`,
      );
    } catch (error) {
      this.logger.error('[getLast24HoursPm25Measurements] Failed to', { error: error.message });
      throw error;
    }
  }

  public async getSpatialZScoreStats(
    dataPoint: {
      locationId: number;
      pm25: number;
      measuredAt: string;
    },
    radiusMeters: number,
    measuredAtIntervalHours: number,
    minNearbyCount: number,
  ) {
    try {      
      return await this.databaseService.runQuery(
        `WITH nearby AS (
          SELECT DISTINCT ON (l2.id) m.pm25
          FROM location l1
          JOIN location l2
            ON ST_DWithin(l1.coordinate, l2.coordinate, '${radiusMeters}')
          JOIN measurement m ON m.location_id = l2.id
          WHERE l1.id = '${dataPoint.locationId}'
            AND m.measured_at BETWEEN '${dataPoint.measuredAt}'::timestamptz - INTERVAL '${measuredAtIntervalHours} HOURS'
                                AND '${dataPoint.measuredAt}'::timestamptz + INTERVAL '${measuredAtIntervalHours} HOURS'
          ORDER BY l2.id, ABS(EXTRACT(EPOCH FROM (m.measured_at - '${dataPoint.measuredAt}'::timestamptz)))
        ),
        filtered AS (
            SELECT pm25
            FROM nearby
            LIMIT ${minNearbyCount} -- This ensures we only proceed if enough nearby points exist
        )
        SELECT
          AVG(pm25) AS mean,
          STDDEV_SAMP(pm25) AS stddev
        FROM filtered
        HAVING COUNT(*) >= ${minNearbyCount};`,
      );
    } catch (error) {
      this.logger.error('[getSpatialZScoreStats] Failed to', { error: error.message });
      throw error;
    }
  }
}
