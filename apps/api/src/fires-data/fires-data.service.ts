import { Injectable, Logger } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { FiresDataRepository } from './fires-data.repository';
import { FiresDataEntity, FireRecord } from './fires-data.entity';
import { FirmsFireRecord } from './fires.model';
import { FIRMS_API, FIRES_DATABASE } from './fires.constant';

/**
 * Service for fires data operations:
 * - Syncing data from NASA FIRMS API
 * - Transforming database records into GeoJSON format
 * - Serving data to frontend
 */
@Injectable()
export class FiresDataService {
  private readonly logger = new Logger(FiresDataService.name);
  private firmsApiKey: string;

  constructor(
    private readonly configService: ConfigService,
    private readonly firesDataRepository: FiresDataRepository,
  ) {
    this.firmsApiKey = this.configService.get<string>('FIRMS_API_KEY');
  }

  /**
   * Retrieves fires data for a bounding box and transforms it into GeoJSON format
   *
   * @param xmin Minimum longitude
   * @param xmax Maximum longitude
   * @param ymin Minimum latitude
   * @param ymax Maximum latitude
   * @param hours Time range in hours (default: 48)
   * @param confidence Optional confidence filter
   * @returns Fires data in GeoJSON FeatureCollection format
   */
  async getFiresDataInArea(
    xmin: number,
    xmax: number,
    ymin: number,
    ymax: number,
    hours: number = 48,
    confidence?: string,
  ): Promise<FiresDataEntity> {
    // Fetch fires data records from database
    const records = await this.firesDataRepository.getFiresDataInArea(
      xmin,
      xmax,
      ymin,
      ymax,
      hours,
      confidence,
    );

    // Transform records into GeoJSON format
    return this.transformToGeoJSON(records);
  }

  /**
   * Transforms database records into GeoJSON FeatureCollection format
   *
   * @param records Fire data records from database
   * @returns Formatted GeoJSON entity
   */
  private transformToGeoJSON(records: FireRecord[]): FiresDataEntity {
    if (records.length === 0) {
      return new FiresDataEntity({
        type: 'FeatureCollection',
        features: [],
        count: 0,
        timeRange: {
          start: null,
          end: null,
        },
      });
    }

    // Transform each record into GeoJSON feature
    const features = records.map(record => ({
      type: 'Feature',
      geometry: {
        type: 'Point',
        coordinates: [record.longitude, record.latitude] as [number, number],
      },
      properties: {
        latitude: record.latitude,
        longitude: record.longitude,
        acq_date: record.acq_date,
        acq_time: record.acq_time,
        confidence: record.confidence,
        frp: record.frp,
        bright_ti4: record.bright_ti4,
        bright_ti5: record.bright_ti5,
        satellite: record.satellite,
        daynight: record.daynight,
        scan: record.scan,
        track: record.track,
        version: record.version,
      },
    }));

    // Calculate time range
    const dates = records.map(r => r.acq_date);
    const startDate = dates.reduce((min, date) => (date < min ? date : min), dates[0]);
    const endDate = dates.reduce((max, date) => (date > max ? date : max), dates[0]);

    this.logger.log(
      `Transformed ${records.length} fire records to GeoJSON, date range: ${startDate} to ${endDate}`,
    );

    return new FiresDataEntity({
      type: 'FeatureCollection',
      features,
      count: records.length,
      timeRange: {
        start: startDate,
        end: endDate,
      },
    });
  }

  /**
   * Syncs fires data from NASA FIRMS API
   * Called by TasksService cron job
   */
  async syncFirmsData(): Promise<void> {
    if (!this.firmsApiKey) {
      this.logger.error('FIRMS_API_KEY not configured, skipping sync');
      return;
    }

    const before = Date.now();
    this.logger.log('Starting FIRMS fires data sync');

    try {
      // Fetch from FIRMS API
      const url = `${FIRMS_API.BASE_URL}/${this.firmsApiKey}/${FIRMS_API.DEFAULT_DATA_SOURCE}/world/${FIRMS_API.DEFAULT_DAY_RANGE}`;
      this.logger.log(`Fetching FIRMS data from: ${url}`);

      const controller = new AbortController();
      const timeoutId = setTimeout(() => controller.abort(), FIRMS_API.REQUEST_TIMEOUT);

      const response = await fetch(url, {
        signal: controller.signal,
      });

      clearTimeout(timeoutId);

      if (!response.ok) {
        throw new Error(`FIRMS API failed: ${response.status} ${response.statusText}`);
      }

      const csvData = await response.text();

      // Parse CSV
      const allFires = this.parseFirmsCSV(csvData);

      if (allFires.length === 0) {
        this.logger.warn('No fires data received from FIRMS API');
        return;
      }

      // Filter fires to only include those from the last N hours
      const fires = this.filterRecentFires(allFires, FIRMS_API.SYNC_WINDOW_HOURS);

      if (fires.length === 0) {
        this.logger.log(
          `No fires within last ${FIRMS_API.SYNC_WINDOW_HOURS} hours (total fetched: ${allFires.length})`,
        );
        return;
      }

      this.logger.log(
        `Filtered ${fires.length} fires from last ${FIRMS_API.SYNC_WINDOW_HOURS} hours (total fetched: ${allFires.length})`,
      );

      // Insert to database
      await this.batchUpsertFires(fires);

      const duration = Date.now() - before;
      this.logger.log(
        `FIRMS sync completed successfully: ${fires.length} records in ${duration}ms`,
      );
    } catch (error) {
      const duration = Date.now() - before;
      this.logger.error(`FIRMS sync failed after ${duration}ms:`, error.message);
      this.logger.error('Error details:', {
        name: error.name,
        message: error.message,
        cause: error.cause,
        stack: error.stack?.split('\n').slice(0, 3).join('\n'),
      });
      throw error;
    }
  }

  /**
   * Filter fires to only include those within the specified time window
   * @param fires Array of fire records
   * @param hours Number of hours to look back
   * @returns Filtered array of recent fires
   */
  private filterRecentFires(fires: FirmsFireRecord[], hours: number): FirmsFireRecord[] {
    const now = new Date();
    const cutoffTime = new Date(now.getTime() - hours * 60 * 60 * 1000);

    return fires.filter(fire => {
      try {
        // Parse acq_date (YYYY-MM-DD) and acq_time (HHMM)
        const dateStr = fire.acq_date; // e.g., "2025-11-27"
        const timeStr = fire.acq_time.padStart(4, '0'); // e.g., "1430" or "0045"

        const hours = parseInt(timeStr.substring(0, 2), 10);
        const minutes = parseInt(timeStr.substring(2, 4), 10);

        // Construct fire detection time
        const fireTime = new Date(dateStr);
        fireTime.setUTCHours(hours, minutes, 0, 0);

        // Check if fire is within the time window
        return fireTime >= cutoffTime;
      } catch (error) {
        this.logger.warn(`Failed to parse fire time: ${fire.acq_date} ${fire.acq_time}`, error);
        // Include fires with parsing errors to avoid losing data
        return true;
      }
    });
  }

  /**
   * Convert MODIS time format (minutes since midnight) to HHMM format
   * MODIS provides time as minutes (e.g., "644" = 10:44)
   * VIIRS provides time as HHMM (e.g., "1044")
   */
  private normalizeAcqTime(acqTime: string): string {
    // If already in HHMM format (4 digits), return as-is
    if (acqTime.length === 4 && !isNaN(Number(acqTime))) {
      return acqTime;
    }

    // Convert minutes since midnight to HHMM
    const minutes = parseInt(acqTime, 10);
    if (isNaN(minutes)) {
      return '0000';
    }

    const hours = Math.floor(minutes / 60);
    const mins = minutes % 60;
    return hours.toString().padStart(2, '0') + mins.toString().padStart(2, '0');
  }

  /**
   * Parse CSV data from FIRMS API
   */
  private parseFirmsCSV(csvData: string): FirmsFireRecord[] {
    const lines = csvData.trim().split('\n');

    if (lines.length < 2) {
      this.logger.warn('CSV data is empty or has no records');
      return [];
    }

    const headers = lines[0].split(',').map(h => h.trim());

    const fires = lines.slice(1).map(line => {
      const values = line.split(',');
      const fire: any = {};

      headers.forEach((header, i) => {
        fire[header] = values[i]?.trim();
      });

      return {
        latitude: parseFloat(fire.latitude),
        longitude: parseFloat(fire.longitude),
        // MODIS uses 'brightness' and 'bright_t31', VIIRS uses 'bright_ti4' and 'bright_ti5'
        bright_ti4: parseFloat(fire.bright_ti4 || fire.brightness),
        scan: parseFloat(fire.scan),
        track: parseFloat(fire.track),
        acq_date: fire.acq_date,
        acq_time: this.normalizeAcqTime(fire.acq_time), // Normalize time format
        satellite: fire.satellite,
        confidence: fire.confidence,
        version: fire.version,
        bright_ti5: parseFloat(fire.bright_ti5 || fire.bright_t31),
        frp: parseFloat(fire.frp),
        daynight: fire.daynight,
      };
    });

    // Filter out invalid records
    return fires.filter(
      f => !isNaN(f.latitude) && !isNaN(f.longitude) && f.acq_date && f.acq_time,
    );
  }

  /**
   * Batch upsert fires data to database
   */
  private async batchUpsertFires(fires: FirmsFireRecord[]): Promise<void> {
    let totalInserted = 0;

    for (let i = 0; i < fires.length; i += FIRES_DATABASE.BATCH_SIZE) {
      const batch = fires.slice(i, i + FIRES_DATABASE.BATCH_SIZE);

      await this.firesDataRepository.upsertFires(batch);

      totalInserted += batch.length;
      this.logger.log(
        `Inserted batch ${Math.floor(i / FIRES_DATABASE.BATCH_SIZE) + 1}: ${batch.length} records (total: ${totalInserted}/${fires.length})`,
      );
    }
  }
}
