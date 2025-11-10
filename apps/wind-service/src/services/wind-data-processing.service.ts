import { GFSDownloaderService } from './gfs-downloader.service';
import { GribConverterService } from './grib-converter.service';
import { WindDataRepositoryService } from './wind-data-repository.service';
import { S3UploaderService } from './s3-uploader.service';
import { logger } from '../utils/logger';
import { WIND_DATA_BATCH_SIZE } from '../config/database.config';

/**
 * Service responsible for the complete wind data processing pipeline
 * Orchestrates: Download → Convert → Upload to S3 → Insert to Database
 */
export class WindDataProcessingService {
  private downloader = new GFSDownloaderService();
  private converter = new GribConverterService();
  private repository = new WindDataRepositoryService();
  private uploader = new S3UploaderService();

  /**
   * Executes the full wind data processing pipeline
   *
   * Process flow:
   * 1. Find grib2json converter tool
   * 2. Download GFS GRIB2 data from NOAA
   * 3. Convert GRIB2 to JSON format
   * 4. Upload to S3 (timestamped historical file for archival)
   * 5. Transform and batch insert into PostgreSQL database (current data for API)
   *
   * @returns Promise that resolves when processing is complete
   */
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

      // Step 2: Download GFS data from NOAA
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

      // Extract forecast timestamp from wind data
      const forecastTime = windData[0]?.header?.refTime
        ? new Date(windData[0].header.refTime)
        : null;

      // Step 4: Upload to S3 for historical archival
      if (forecastTime) {
        await this.uploadToS3(windData, forecastTime);
      } else {
        logger.warn('wind-data-processing', 'No forecast time found in wind data, skipping S3 upload');
      }

      // Step 5: Transform and insert into database
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
   * Uploads wind data to S3 for historical archival
   */
  private async uploadToS3(windData: any[], forecastTime: Date): Promise<void> {
    const uploadResult = await this.uploader.uploadWindData(windData, forecastTime);

    if (!uploadResult.success) {
      logger.error('wind-data-processing', 'S3 upload failed', {
        error: uploadResult.error,
      });
      // Continue with database insertion even if S3 upload fails
    } else {
      logger.info('wind-data-processing', 'Wind data archived to S3', {
        url: uploadResult.url,
        key: uploadResult.key,
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
      });

      // Clean up old forecast data after successful insertion
      // Keep only the latest forecast in the database
      await this.cleanupOldData();
    } else {
      logger.error('wind-data-processing', 'Wind data insertion failed', {
        duration: `${duration}ms`,
        totalRecords: totalInserted,
        error: lastError,
      });
    }
  }

  /**
   * Removes old forecast data from the database
   * Keeps only the most recent forecast for API serving
   * Historical data is preserved in S3
   */
  private async cleanupOldData(): Promise<void> {
    try {
      const deletedCount = await this.repository.deleteOldForecasts();

      if (deletedCount > 0) {
        logger.info('wind-data-processing', 'Old forecast data removed', {
          deletedRecords: deletedCount,
        });
      } else {
        logger.debug('wind-data-processing', 'No old forecast data to remove');
      }
    } catch (error) {
      // Log error but don't fail the overall process
      logger.error('wind-data-processing', 'Failed to cleanup old data', {
        error: error instanceof Error ? error.message : 'Unknown error',
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
