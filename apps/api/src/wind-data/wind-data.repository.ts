import {
  Injectable,
  InternalServerErrorException,
  Logger,
  NotFoundException,
} from '@nestjs/common';
import DatabaseService from "../database/database.service"

/**
 * Raw wind data record from database
 */
export interface WindDataRecord {
  longitude: number;
  latitude: number;
  forecast_time: Date;
  u_component: number;
  v_component: number;
}

/**
 * Repository for wind data database operations
 */
@Injectable()
export class WindDataRepository {
  private readonly logger = new Logger(WindDataRepository.name);

  constructor(private readonly databaseService: DatabaseService) {}

  /**
   * Retrieves the latest forecast timestamp from the database
   * @returns Latest forecast time or null if no data exists
   */
  async getLatestForecastTime(): Promise<Date | null> {
    const query = `
      SELECT MAX(forecast_time) as latest_forecast
      FROM wind_data
    `;

    try {
      const result = await this.databaseService.runQuery(query);

      if (!result.rows[0] || !result.rows[0].latest_forecast) {
        return null;
      }

      return new Date(result.rows[0].latest_forecast);
    } catch (error) {
      this.logger.error(error);
      throw new InternalServerErrorException({
        message: 'WIND_001: Failed to retrieve latest forecast time',
        operation: 'getLatestForecastTime',
        error: error.message,
        code: 'WIND_001',
      });
    }
  }

  /**
   * Retrieves wind data within a bounding box for the latest forecast
   * Data is ordered by latitude (north to south) then longitude (west to east)
   * to match grib2json grid format
   *
   * @param xmin Minimum longitude (western bound)
   * @param xmax Maximum longitude (eastern bound)
   * @param ymin Minimum latitude (southern bound)
   * @param ymax Maximum latitude (northern bound)
   * @returns Array of wind data records
   */
  async getWindDataInArea(
    xmin: number,
    xmax: number,
    ymin: number,
    ymax: number,
  ): Promise<WindDataRecord[]> {
    // Return all data globally (bounds filtering removed)
    const query = `
      SELECT
        longitude,
        latitude,
        forecast_time,
        u_component,
        v_component
      FROM wind_data
      WHERE
        forecast_time = (SELECT MAX(forecast_time) FROM wind_data)
      ORDER BY latitude DESC, longitude ASC
    `;

    try {
      const result = await this.databaseService.runQuery(query);

      if (result.rows.length === 0) {
        throw new NotFoundException({
          message: 'WIND_002: No wind data found for the specified area',
          operation: 'getWindDataInArea',
          parameters: { xmin, xmax, ymin, ymax },
          code: 'WIND_002',
        });
      }

      return result.rows as WindDataRecord[];
    } catch (error) {
      if (error instanceof NotFoundException) {
        throw error;
      }

      this.logger.error(error);
      throw new InternalServerErrorException({
        message: 'WIND_003: Failed to retrieve wind data',
        operation: 'getWindDataInArea',
        parameters: { xmin, xmax, ymin, ymax },
        error: error.message,
        code: 'WIND_003',
      });
    }
  }
}
