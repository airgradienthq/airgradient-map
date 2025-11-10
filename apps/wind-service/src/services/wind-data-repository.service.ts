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
      const forecastTime = new Date(latestForecast);
      const dataAvailableTime = new Date(forecastTime.getTime() + (5 * 60 * 60 * 1000));

      // Check if more than 6 hours have passed since data became available
      const now = new Date();
      const hoursSinceAvailable = (now.getTime() - dataAvailableTime.getTime()) / (1000 * 60 * 60);

      const shouldFetch = hoursSinceAvailable >= 6;

      logger.info('wind-data-repository', 'Checked if new data fetch needed', {
        forecastTime: forecastTime.toISOString(),
        dataAvailableTime: dataAvailableTime.toISOString(),
        now: now.toISOString(),
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

  /**
   * Deletes old forecast data, keeping only the most recent forecast
   * This ensures the database only stores current data for API serving
   * while historical data is preserved in S3
   *
   * @returns Number of records deleted
   */
  async deleteOldForecasts(): Promise<number> {
    const client = await pool.connect();

    try {
      const query = `
        DELETE FROM wind_data
        WHERE forecast_time < (
          SELECT MAX(forecast_time) FROM wind_data
        )
      `;

      const result = await client.query(query);
      const deletedCount = result.rowCount || 0;

      logger.info('wind-data-repository', 'Cleaned up old forecast data', {
        deletedRecords: deletedCount
      });

      return deletedCount;

    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : 'Unknown error';

      logger.error('wind-data-repository', 'Failed to delete old forecasts', {
        error: errorMessage
      });

      return 0;
    } finally {
      client.release();
    }
  }

  /**
   * Transforms GFS wind data JSON into database records
   * windData format: [uComponent, vComponent]
   * Each component has: { header: { refTime, ... }, data: [values...] }
   *
   * Validates all required header fields before transformation.
   * Returns empty array if any validation fails to prevent data corruption.
   * No default/fallback values are used for grid parameters.
   */
  public transformWindData(windData: any[]): WindDataRecord[] {
    const [uComponent, vComponent] = windData;

    // Validate basic structure
    if (!uComponent?.data || !vComponent?.data) {
      logger.error('wind-data-repository', 'Invalid wind data format: missing data arrays');
      return [];
    }

    if (!uComponent.header || !vComponent.header) {
      logger.error('wind-data-repository', 'Invalid wind data format: missing headers');
      return [];
    }

    // Validate required header fields - no defaults allowed
    const requiredFields = ['refTime', 'nx', 'ny', 'la1', 'lo1', 'dx', 'dy'];
    const missingFields = requiredFields.filter(field =>
      uComponent.header[field] === undefined || uComponent.header[field] === null
    );

    if (missingFields.length > 0) {
      logger.error('wind-data-repository', 'Missing required header fields', {
        missingFields: missingFields.join(', ')
      });
      return [];
    }

    // Extract validated grid parameters
    const forecastTime = new Date(uComponent.header.refTime);
    const nx = uComponent.header.nx;
    const ny = uComponent.header.ny;
    const la1 = uComponent.header.la1;
    const lo1 = uComponent.header.lo1;
    const dx = uComponent.header.dx;
    const dy = uComponent.header.dy;

    // Validate grid parameters are sensible
    if (nx <= 0 || ny <= 0 || dx <= 0 || dy <= 0) {
      logger.error('wind-data-repository', 'Invalid grid parameters', {
        nx, ny, dx, dy
      });
      return [];
    }

    // Validate data length matches grid size
    const expectedLength = nx * ny;
    if (uComponent.data.length !== expectedLength || vComponent.data.length !== expectedLength) {
      logger.error('wind-data-repository', 'Data length mismatch', {
        expected: expectedLength,
        uLength: uComponent.data.length,
        vLength: vComponent.data.length
      });
      return [];
    }

    const records: WindDataRecord[] = [];

    for (let i = 0; i < uComponent.data.length; i++) {
      const u = uComponent.data[i];
      const v = vComponent.data[i];

      if (u === null || v === null) continue;

      // Calculate lat/lon from grid index
      const row = Math.floor(i / nx);
      const col = i % nx;
      const latitude = la1 - row * dy;
      const longitude = lo1 + col * dx;

      records.push({
        longitude,
        latitude,
        forecast_time: forecastTime,
        u_component: u,
        v_component: v,
      });
    }

    logger.info('wind-data-repository', 'Transformed wind data', {
      totalRecords: records.length,
      forecastTime: forecastTime.toISOString(),
      gridSize: `${nx}x${ny}`,
      resolution: `${dx}°x${dy}°`
    });

    return records;
  }
}

