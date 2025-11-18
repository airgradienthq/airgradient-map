import { Injectable, Logger } from '@nestjs/common';
import { WindDataRepository, WindDataRecord } from './wind-data.repository';
import { WindDataEntity } from './wind-data.entity';

/**
 * Service for transforming wind data from database records into optimized grid format
 */
@Injectable()
export class WindDataService {
  private readonly logger = new Logger(WindDataService.name);

  constructor(private readonly windDataRepository: WindDataRepository) {}

  /**
   * Retrieves wind data for a bounding box and transforms it into grid format
   *
   * The transformation process:
   * 1. Fetch records from database (ordered by lat DESC, lon ASC)
   * 2. Calculate grid dimensions from actual data bounds
   * 3. Create U and V component arrays in grid order
   * 4. Build response with header metadata and data arrays
   *
   * @param xmin Minimum longitude
   * @param xmax Maximum longitude
   * @param ymin Minimum latitude
   * @param ymax Maximum latitude
   * @returns Wind data in optimized grid format
   */
  async getWindDataInArea(
    xmin: number,
    xmax: number,
    ymin: number,
    ymax: number,
  ): Promise<WindDataEntity> {
    // Fetch wind data records from database
    const records = await this.windDataRepository.getWindDataInArea(xmin, xmax, ymin, ymax);

    // Transform records into grid format
    return this.transformToGridFormat(records);
  }

  /**
   * Transforms database records into optimized grid format
   *
   * Grid format expectations:
   * - Data arrays ordered: north-to-south, west-to-east
   * - Index calculation: index = y * nx + x
   * - Regular grid with fixed dx/dy resolution
   *
   * @param records Wind data records from database
   * @param xmin Requested minimum longitude (unused, kept for future bounds filtering)
   * @param xmax Requested maximum longitude (unused, kept for future bounds filtering)
   * @param ymin Requested minimum latitude (unused, kept for future bounds filtering)
   * @param ymax Requested maximum latitude (unused, kept for future bounds filtering)
   * @returns Formatted wind data entity
   */
  private transformToGridFormat(records: WindDataRecord[]): WindDataEntity {
    if (records.length === 0) {
      throw new Error('No records to transform');
    }

    // Extract forecast time from first record (all records have same forecast_time)
    const forecastTime = records[0].forecast_time.toISOString();

    // Calculate actual bounds from data
    const lons = records.map(r => r.longitude);
    const lats = records.map(r => r.latitude);

    // Use reduce instead of spread operator to avoid stack overflow with large datasets
    const actualWest = lons.reduce((min, lon) => Math.min(min, lon), Infinity);
    const actualEast = lons.reduce((max, lon) => Math.max(max, lon), -Infinity);
    const actualSouth = lats.reduce((min, lat) => Math.min(min, lat), Infinity);
    const actualNorth = lats.reduce((max, lat) => Math.max(max, lat), -Infinity);

    // Calculate grid resolution (assuming uniform spacing)
    // Database stores data at 1° resolution
    const uniqueLons = Array.from(new Set(lons)).sort((a, b) => a - b);
    const uniqueLats = Array.from(new Set(lats)).sort((a, b) => b - a);

    const dx = uniqueLons.length > 1 ? Math.abs(uniqueLons[1] - uniqueLons[0]) : 1.0;
    const dy = uniqueLats.length > 1 ? Math.abs(uniqueLats[0] - uniqueLats[1]) : 1.0;

    const nx = uniqueLons.length;
    const ny = uniqueLats.length;

    this.logger.log(
      `Grid dimensions: ${nx}x${ny}, resolution: ${dx}°x${dy}°, total points: ${records.length}`,
    );

    // Create coordinate to index mapping for fast lookup
    const tolerance = dx / 10; // 10% of grid resolution
    const coordToIndex = new Map<string, number>();

    uniqueLats.forEach((lat, y) => {
      uniqueLons.forEach((lon, x) => {
        const index = y * nx + x;
        const key = `${lat.toFixed(6)},${lon.toFixed(6)}`;
        coordToIndex.set(key, index);
      });
    });

    // Initialize data arrays with null values
    const uData: (number | null)[] = new Array(nx * ny).fill(null);
    const vData: (number | null)[] = new Array(nx * ny).fill(null);

    // Fill data arrays using coordinate mapping with tolerance-based matching
    records.forEach(record => {
      const exactKey = `${record.latitude.toFixed(6)},${record.longitude.toFixed(6)}`;
      let index = coordToIndex.get(exactKey);

      // If exact match fails, find nearest grid point
      if (index === undefined) {
        const closestLat = uniqueLats.find(lat => Math.abs(lat - record.latitude) < tolerance);
        const closestLon = uniqueLons.find(lon => Math.abs(lon - record.longitude) < tolerance);

        if (closestLat !== undefined && closestLon !== undefined) {
          const nearKey = `${closestLat.toFixed(6)},${closestLon.toFixed(6)}`;
          index = coordToIndex.get(nearKey);
        }
      }

      if (index !== undefined) {
        uData[index] = record.u_component;
        vData[index] = record.v_component;
      } else {
        this.logger.warn(
          `No grid point found for coordinate: ${record.latitude}, ${record.longitude}`,
        );
      }
    });

    // Convert null to 0 or filter them out based on requirements
    // For now, replace null with 0 to maintain grid structure
    const uDataFinal = uData.map(v => (v === null ? 0 : v));
    const vDataFinal = vData.map(v => (v === null ? 0 : v));

    // Log statistics for debugging
    const nonNullU = uData.filter(v => v !== null).length;
    const nonNullV = vData.filter(v => v !== null).length;
    this.logger.log(
      `Data coverage: U=${nonNullU}/${uData.length}, V=${nonNullV}/${vData.length} (${((nonNullU / uData.length) * 100).toFixed(1)}%)`,
    );

    return new WindDataEntity({
      header: {
        forecastTime,
        bounds: {
          north: actualNorth,
          south: actualSouth,
          west: actualWest,
          east: actualEast,
        },
        resolution: {
          dx,
          dy,
        },
        grid: {
          nx,
          ny,
        },
      },
      data: {
        u: uDataFinal,
        v: vDataFinal,
      },
    });
  }
}
