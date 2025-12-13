import {
  Injectable,
  InternalServerErrorException,
  Logger,
  NotFoundException,
} from '@nestjs/common';
import DatabaseService from '../database/database.service';

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
   * Retrieves wind data within a bounding box for the latest forecast
   * Data is ordered by latitude (north to south) then longitude (west to east)
   * to match grib2json grid format
   *
   * Adds a buffer around the requested bounds to ensure smooth visualization
   * at viewport edges and account for 1° grid resolution.
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
    // Add 2° buffer on each side to ensure smooth edges and account for 1° grid resolution
    const buffer = 2;
    const bufXmin = xmin - buffer;
    const bufXmax = xmax + buffer;
    const bufYmin = Math.max(-90, ymin - buffer); // Clamp latitude to valid range
    const bufYmax = Math.min(90, ymax + buffer);

    // Check if viewport spans more than 360° (zoomed out to see whole world multiple times)
    const longitudeSpan = bufXmax - bufXmin;
    const isGlobalView = longitudeSpan >= 360;

    let query: string;
    let params: number[];

    if (isGlobalView) {
      // Return all data globally (no longitude filtering)
      this.logger.log('Global view detected, returning all wind data');
      query = `
        SELECT
          longitude,
          latitude,
          forecast_time,
          u_component,
          v_component
        FROM wind_data
        WHERE
          forecast_time = (SELECT MAX(forecast_time) FROM wind_data)
          AND latitude >= $1
          AND latitude <= $2
        ORDER BY latitude DESC, longitude ASC
      `;
      params = [bufYmin, bufYmax];
    } else {
      // Normalize longitude to 0-360 range for database query
      const normalizeLongitude = (lon: number): number => {
        let normalized = ((lon + 180) % 360) - 180;
        if (normalized < -180) normalized += 360;
        if (normalized > 180) normalized -= 360;
        return normalized < 0 ? normalized + 360 : normalized;
      };

      const normalizedXmin = normalizeLongitude(bufXmin);
      const normalizedXmax = normalizeLongitude(bufXmax);

      // Handle antimeridian crossing (when west > east in normalized coordinates)
      const crossesAntimeridian = normalizedXmin > normalizedXmax;

      if (crossesAntimeridian) {
        // Query needs to match: longitude >= xmin OR longitude <= xmax
        query = `
          SELECT
            longitude,
            latitude,
            forecast_time,
            u_component,
            v_component
          FROM wind_data
          WHERE
            forecast_time = (SELECT MAX(forecast_time) FROM wind_data)
            AND (longitude >= $1 OR longitude <= $2)
            AND latitude >= $3
            AND latitude <= $4
          ORDER BY latitude DESC, longitude ASC
        `;
        params = [normalizedXmin, normalizedXmax, bufYmin, bufYmax];
      } else {
        // Normal case: longitude between xmin and xmax
        query = `
          SELECT
            longitude,
            latitude,
            forecast_time,
            u_component,
            v_component
          FROM wind_data
          WHERE
            forecast_time = (SELECT MAX(forecast_time) FROM wind_data)
            AND longitude >= $1
            AND longitude <= $2
            AND latitude >= $3
            AND latitude <= $4
          ORDER BY latitude DESC, longitude ASC
        `;
        params = [normalizedXmin, normalizedXmax, bufYmin, bufYmax];
      }
    }

    try {
      const result = await this.databaseService.runQuery(query, params);

      if (result.rows.length === 0) {
        throw new NotFoundException({
          message: 'WIND_002: No wind data found for the specified area',
          operation: 'getWindDataInArea',
          parameters: { xmin, xmax, ymin, ymax },
          code: 'WIND_002',
        });
      }

      this.logger.log(
        `Fetched ${result.rows.length} wind data points for bounds: ` +
          `[${xmin.toFixed(2)}, ${ymin.toFixed(2)}] to [${xmax.toFixed(2)}, ${ymax.toFixed(2)}] ` +
          `(${isGlobalView ? 'global view' : `with ${buffer}° buffer`})`,
      );

      // Convert longitude back from 0-360° (database) to -180 to 180° (Leaflet)
      const records = result.rows.map((row: any) => ({
        ...row,
        longitude: row.longitude > 180 ? row.longitude - 360 : row.longitude,
      }));

      // Ensure ordering matches grib expectation after longitude normalization.
      // Crossing the antimeridian changes sort order, so re-sort by latitude DESC,
      // longitude ASC to keep the grid continuous.
      records.sort((a, b) => {
        if (a.latitude === b.latitude) {
          return a.longitude - b.longitude;
        }
        return b.latitude - a.latitude;
      });

      return records as WindDataRecord[];
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
