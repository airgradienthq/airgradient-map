import { GFSDownloaderService } from './gfs-downloader.service';
import { GribConverterService } from './grib-converter.service';
import { WindDataRepositoryService } from './wind-data-repository.service';
import { logger } from '../utils/logger';
import { WIND_DATA_BATCH_SIZE } from '../config/database.config';

export class WindDataProcessingService {
  private downloader = new GFSDownloaderService();
  private converter = new GribConverterService();
  private repository = new WindDataRepositoryService();

  async processWindData(): Promise<void> {
    const startTime = Date.now();
    logger.info('wind-data-processing', 'Wind data update started');

    try {
      // Step 1: Find grib2json converter tool
      const grib2jsonPath = await this.converter.findGrib2Json();
      if (!grib2jsonPath) {
        logger.error('wind-data-processing', 'grib2json not found, cannot process wind data');
        return;
      }

      // Step 2: Download GFS data from NOAA at 1° resolution
      const gribFile = await this.downloader.downloadGFSData();
      if (!gribFile) {
        logger.error('wind-data-processing', 'GFS download failed');
        return;
      }

      // Step 3: Convert GRIB2 to JSON
      const windData = await this.converter.convertGribToJson(grib2jsonPath, gribFile);
      if (!windData) {
        logger.error('wind-data-processing', 'GRIB conversion failed');
        return;
      }

      // Step 4: Transform and insert into database (historical data retained)
      await this.insertToDatabase(windData, startTime);

    } catch (error) {
      const duration = Date.now() - startTime;
      logger.error('wind-data-processing', 'Wind data update failed', {
        error: error instanceof Error ? error.message : 'Unknown error',
        duration: `${duration}ms`,
      });
    }
  }

  /**
   * Transforms and inserts wind data into PostgreSQL database in batches
   */
  private async insertToDatabase(windData: any[], startTime: number): Promise<void> {
    const records = this.repository.transformWindData(windData);

    if (records.length === 0) {
      logger.warn('wind-data-processing', 'No records to insert');
      return;
    }

    // Get batch size from repository service's config
    const BATCH_SIZE = WIND_DATA_BATCH_SIZE;
    let totalInserted = 0;
    let failed = false;
    let lastError = '';

    logger.info('wind-data-processing', 'Starting database insertion', {
      totalRecords: records.length,
      batchSize: BATCH_SIZE,
    });

    for (let i = 0; i < records.length; i += BATCH_SIZE) {
      const batch = records.slice(i, i + BATCH_SIZE);
      const result = await this.repository.batchInsert(batch);

      if (result.success) {
        totalInserted += result.recordCount;
        logger.info('wind-data-processing', 'Batch inserted', {
          batchNumber: Math.floor(i / BATCH_SIZE) + 1,
          recordsInBatch: result.recordCount,
          totalInserted,
        });
      } else {
        failed = true;
        lastError = result.error || 'Unknown error';
        logger.error('wind-data-processing', 'Batch insertion failed', {
          batchNumber: Math.floor(i / BATCH_SIZE) + 1,
          error: lastError,
        });
        break;
      }
    }

    const duration = Date.now() - startTime;

    if (!failed) {
      logger.info('wind-data-processing', 'Wind data update completed successfully', {
        duration: `${duration}ms`,
        totalRecords: totalInserted,
        note: 'Historical data retained in database',
      });
    } else {
      logger.error('wind-data-processing', 'Wind data insertion failed', {
        duration: `${duration}ms`,
        totalRecords: totalInserted,
        error: lastError,
      });
    }
  }

  /**
   * Checks if new wind data should be fetched
   * Delegates to repository service
   */
  async shouldFetchNewData(): Promise<boolean> {
    return this.repository.shouldFetchNewData();
  }
}
