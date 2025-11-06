import { pool } from '../config/database.config';
import { logger } from '../utils/logger';
import { WindDataRecord, WindDataInsertResult } from '../types';

export class WindDataRepositoryService {

  /**
   * Checks if new wind data should be fetched
   * Returns true if:
   * - Database is empty (no wind data yet)
   * - More than 6 hours have passed since the last forecast data became available
   *
   * Logic: (now - (forecast_time + 5 hours)) >= 6 hours
   * The 5-hour delay accounts for GFS data availability after model run
   */
  async shouldFetchNewData(): Promise<boolean> {
    const client = await pool.connect();

    try {
      const query = `
        SELECT MAX(forecast_time) as latest_forecast
        FROM wind_data
      `;

      const result = await client.query(query);
      const latestForecast = result.rows[0]?.latest_forecast;

      if (!latestForecast) {
        logger.info('wind-data-repository', 'No existing wind data, fetch needed');
        return true;
      }

      // Add 5 hours to forecast_time (data availability delay)
      const dataAvailableTime = new Date(latestForecast).getTime() + (5 * 60 * 60 * 1000);

      // Check if more than 6 hours have passed since data became available
      const hoursSinceAvailable = (Date.now() - dataAvailableTime) / (1000 * 60 * 60);

      const shouldFetch = hoursSinceAvailable >= 6;

      logger.info('wind-data-repository', 'Checked if new data fetch needed', {
        latestForecast,
        hoursSinceAvailable: hoursSinceAvailable.toFixed(2),
        shouldFetch
      });

      return shouldFetch;

    } catch (error) {
      logger.error('wind-data-repository', 'Error checking if fetch needed', {
        error: error instanceof Error ? error.message : 'Unknown error'
      });
      // On error, fetch to be safe
      return true;
    } finally {
      client.release();
    }
  }

  async batchInsert(records: WindDataRecord[]): Promise<WindDataInsertResult> {
    if (records.length === 0) {
      logger.warn('wind-data-repository', 'No records to insert');
      return { recordCount: 0, success: true };
    }

    const client = await pool.connect();

    try {
      await client.query('BEGIN');

      // Build bulk INSERT with ON CONFLICT
      const values = records.map((_, idx) => {
        const offset = idx * 5;
        return `($${offset + 1}, $${offset + 2}, $${offset + 3}, $${offset + 4}, $${offset + 5})`;
      }).join(',');

      const params = records.flatMap(r => [
        r.longitude,
        r.latitude,
        r.forecast_time,
        r.u_component,
        r.v_component,
      ]);

      const query = `
        INSERT INTO wind_data (
          longitude, latitude, forecast_time,
          u_component, v_component
        ) VALUES ${values}
        ON CONFLICT (longitude, latitude, forecast_time)
        DO UPDATE SET
          u_component = EXCLUDED.u_component,
          v_component = EXCLUDED.v_component
      `;

      await client.query(query, params);
      await client.query('COMMIT');

      logger.info('wind-data-repository', 'Batch insert completed', {
        recordCount: records.length
      });

      return {
        recordCount: records.length,
        success: true
      };

    } catch (error) {
      await client.query('ROLLBACK');
      const errorMessage = error instanceof Error ? error.message : 'Unknown error';

      logger.error('wind-data-repository', 'Batch insert failed', {
        error: errorMessage,
        recordCount: records.length
      });

      return {
        recordCount: 0,
        success: false,
        error: errorMessage
      };
    } finally {
      client.release();
    }
  }
}
