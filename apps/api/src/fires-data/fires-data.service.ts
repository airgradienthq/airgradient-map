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
      const fires = this.parseFirmsCSV(csvData);

      if (fires.length === 0) {
        this.logger.warn('No fires data received from FIRMS API');
        return;
      }

      // Insert to database
      await this.batchUpsertFires(fires);

      const duration = Date.now() - before;
      this.logger.log(
        `FIRMS sync completed successfully: ${fires.length} records in ${duration}ms`,
      );
    } catch (error) {
      const duration = Date.now() - before;
      this.logger.error(`FIRMS sync failed after ${duration}ms:`, error.message);
      throw error;
    }
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
        bright_ti4: parseFloat(fire.bright_ti4),
        scan: parseFloat(fire.scan),
        track: parseFloat(fire.track),
        acq_date: fire.acq_date,
        acq_time: fire.acq_time,
        satellite: fire.satellite,
        confidence: fire.confidence,
        version: fire.version,
        bright_ti5: parseFloat(fire.bright_ti5),
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
