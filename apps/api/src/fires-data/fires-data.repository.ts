import {
  Injectable,
  InternalServerErrorException,
  Logger,
} from '@nestjs/common';
import DatabaseService from '../database/database.service';
import { FireRecord } from './fires-data.entity';
import { FirmsFireRecord } from './fires.model';
import { FIRES_QUERY } from './fires.constant';

/**
 * Repository for fires data database operations
 */
@Injectable()
export class FiresDataRepository {
  private readonly logger = new Logger(FiresDataRepository.name);

  constructor(private readonly databaseService: DatabaseService) {}

  /**
   * Retrieves fires data within a bounding box for the specified time range
   * Data is ordered by date/time (most recent first)
   *
   * @param xmin Minimum longitude (western bound)
   * @param xmax Maximum longitude (eastern bound)
   * @param ymin Minimum latitude (southern bound)
   * @param ymax Maximum latitude (northern bound)
   * @param hours Time range in hours (default: 48)
   * @param confidence Optional confidence filter ('low', 'nominal', 'high')
   * @returns Array of fire records
   */
  async getFiresDataInArea(
    xmin: number,
    xmax: number,
    ymin: number,
    ymax: number,
    hours: number = FIRES_QUERY.DEFAULT_HOURS,
    confidence?: string,
  ): Promise<FireRecord[]> {
    // Add small buffer to ensure edge coverage
    const buffer = FIRES_QUERY.SPATIAL_BUFFER;
    const bufXmin = xmin - buffer;
    const bufXmax = xmax + buffer;
    const bufYmin = Math.max(-90, ymin - buffer);
    const bufYmax = Math.min(90, ymax + buffer);

    // Check if viewport spans more than 360° (global view)
    const longitudeSpan = bufXmax - bufXmin;
    const isGlobalView = longitudeSpan >= 360;

    let query: string;
    let params: any[];

    if (isGlobalView) {
      // Return all data globally (no longitude filtering)
      this.logger.log('Global view detected, returning all fires data');

      if (confidence) {
        query = `
          SELECT
            latitude,
            longitude,
            acq_date,
            acq_time,
            confidence,
            frp,
            bright_ti4,
            bright_ti5,
            satellite,
            daynight,
            scan,
            track,
            version
          FROM fires_data
          WHERE
            latitude >= $1
            AND latitude <= $2
            AND acq_date >= CURRENT_DATE - INTERVAL '1 hour' * $3
            AND confidence = $4
          ORDER BY acq_date DESC, acq_time DESC
        `;
        params = [bufYmin, bufYmax, hours, confidence];
      } else {
        query = `
          SELECT
            latitude,
            longitude,
            acq_date,
            acq_time,
            confidence,
            frp,
            bright_ti4,
            bright_ti5,
            satellite,
            daynight,
            scan,
            track,
            version
          FROM fires_data
          WHERE
            latitude >= $1
            AND latitude <= $2
            AND acq_date >= CURRENT_DATE - INTERVAL '1 hour' * $3
          ORDER BY acq_date DESC, acq_time DESC
        `;
        params = [bufYmin, bufYmax, hours];
      }
    } else {
      // Normal case: bounding box filtering
      if (confidence) {
        query = `
          SELECT
            latitude,
            longitude,
            acq_date,
            acq_time,
            confidence,
            frp,
            bright_ti4,
            bright_ti5,
            satellite,
            daynight,
            scan,
            track,
            version
          FROM fires_data
          WHERE
            longitude >= $1
            AND longitude <= $2
            AND latitude >= $3
            AND latitude <= $4
            AND acq_date >= CURRENT_DATE - INTERVAL '1 hour' * $5
            AND confidence = $6
          ORDER BY acq_date DESC, acq_time DESC
        `;
        params = [bufXmin, bufXmax, bufYmin, bufYmax, hours, confidence];
      } else {
        query = `
          SELECT
            latitude,
            longitude,
            acq_date,
            acq_time,
            confidence,
            frp,
            bright_ti4,
            bright_ti5,
            satellite,
            daynight,
            scan,
            track,
            version
          FROM fires_data
          WHERE
            longitude >= $1
            AND longitude <= $2
            AND latitude >= $3
            AND latitude <= $4
            AND acq_date >= CURRENT_DATE - INTERVAL '1 hour' * $5
          ORDER BY acq_date DESC, acq_time DESC
        `;
        params = [bufXmin, bufXmax, bufYmin, bufYmax, hours];
      }
    }

    try {
      const result = await this.databaseService.runQuery(query, params);

      if (result.rows.length === 0) {
        this.logger.log(
          `No fires data found for bounds: [${xmin.toFixed(2)}, ${ymin.toFixed(2)}] to [${xmax.toFixed(2)}, ${ymax.toFixed(2)}], hours: ${hours}`,
        );
        // Return empty array instead of throwing error (fires might not exist in area)
        return [];
      }

      this.logger.log(
        `Fetched ${result.rows.length} fire records for bounds: ` +
          `[${xmin.toFixed(2)}, ${ymin.toFixed(2)}] to [${xmax.toFixed(2)}, ${ymax.toFixed(2)}] ` +
          `(${isGlobalView ? 'global view' : `with ${buffer}° buffer`}), hours: ${hours}`,
      );

      return result.rows as FireRecord[];
    } catch (error) {
      this.logger.error(error);
      throw new InternalServerErrorException({
        message: 'FIRES_003: Failed to retrieve fires data',
        operation: 'getFiresDataInArea',
        parameters: { xmin, xmax, ymin, ymax, hours, confidence },
        error: error.message,
        code: 'FIRES_003',
      });
    }
  }

  /**
   * Upsert fires data records to database
   * Uses ON CONFLICT to handle duplicates
   *
   * @param fires Array of fire records to insert/update
   */
  async upsertFires(fires: FirmsFireRecord[]): Promise<void> {
    if (fires.length === 0) {
      this.logger.warn('No fires to upsert');
      return;
    }

    // Build bulk INSERT with ON CONFLICT
    const values = fires
      .map((_, idx) => {
        const offset = idx * 13;
        return `($${offset + 1}, $${offset + 2}, $${offset + 3}, $${offset + 4}, $${offset + 5}, $${offset + 6}, $${offset + 7}, $${offset + 8}, $${offset + 9}, $${offset + 10}, $${offset + 11}, $${offset + 12}, $${offset + 13})`;
      })
      .join(',');

    const params = fires.flatMap(f => [
      f.longitude,
      f.latitude,
      f.acq_date,
      f.acq_time,
      f.satellite,
      f.confidence,
      f.bright_ti4,
      f.bright_ti5,
      f.frp,
      f.scan,
      f.track,
      f.version,
      f.daynight,
    ]);

    const query = `
      INSERT INTO fires_data (
        longitude, latitude, acq_date, acq_time, satellite,
        confidence, bright_ti4, bright_ti5, frp, scan,
        track, version, daynight
      ) VALUES ${values}
      ON CONFLICT (longitude, latitude, acq_date, acq_time, satellite)
      DO UPDATE SET
        confidence = EXCLUDED.confidence,
        frp = EXCLUDED.frp,
        bright_ti4 = EXCLUDED.bright_ti4,
        bright_ti5 = EXCLUDED.bright_ti5
    `;

    try {
      await this.databaseService.runQuery(query, params);
      this.logger.log(`Upserted ${fires.length} fire records`);
    } catch (error) {
      this.logger.error(`Failed to upsert fires:`, error.message);
      throw new InternalServerErrorException({
        message: 'FIRES_004: Failed to upsert fires data',
        operation: 'upsertFires',
        error: error.message,
        code: 'FIRES_004',
      });
    }
  }
}
