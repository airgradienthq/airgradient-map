import * as cron from 'node-cron';
import { GFSDownloaderService } from './services/gfs-downloader.service';
import { GribConverterService } from './services/grib-converter.service';
import { S3UploaderService } from './services/s3-uploader.service';
import { logger, schedulerLogger } from './utils/logger';

class WindService {
  private downloader = new GFSDownloaderService();
  private converter = new GribConverterService();
  private uploader = new S3UploaderService();

  /**
   * Processes wind data from NOAA GFS
   * Runs immediately on startup + every 6 hours (00:00, 06:00, 12:00, 18:00 UTC)
   * Aligns with GFS model update schedule
   */
  async processWindData(): Promise<void> {
    const startTime = Date.now();
    logger.info('wind-service', 'Wind data update started');

    try {
      // Step 1: Find grib2json
      const grib2jsonPath = await this.converter.findGrib2Json();
      if (!grib2jsonPath) {
        logger.warn('wind-service', 'grib2json not found, uploading fallback data');
        await this.uploader.uploadFallbackData();
        return;
      }

      // Step 2: Download GFS data
      const gribFile = await this.downloader.downloadGFSData();
      if (!gribFile) {
        logger.warn('wind-service', 'GFS download failed, using fallback data');
        await this.uploader.uploadFallbackData();
        return;
      }

      // Step 3: Convert GRIB to JSON
      const windData = await this.converter.convertGribToJson(grib2jsonPath, gribFile);
      if (!windData) {
        logger.warn('wind-service', 'GRIB conversion failed, using fallback data');
        await this.uploader.uploadFallbackData();
        return;
      }

      // Step 4: Upload to S3
      const uploadUrl = await this.uploader.uploadWindData(windData);
      
      const duration = Date.now() - startTime;
      logger.info('wind-service', 'Wind data update completed successfully', {
        duration: `${duration}ms`,
        uploadUrl,
        dataPoints: windData[0]?.data?.length || 0
      });

    } catch (error) {
      const duration = Date.now() - startTime;
      logger.error('wind-service', 'Wind data update failed', {
        error: error instanceof Error ? error.message : 'Unknown error',
        duration: `${duration}ms`
      });
      
      try {
        await this.uploader.uploadFallbackData();
      } catch (fallbackError) {
        logger.error('wind-service', 'Failed to upload fallback data', {
          error: fallbackError instanceof Error ? fallbackError.message : 'Unknown error'
        });
      }
    }
  }

  start(): void {
    const runSafe = async () => {
      try {
        await this.processWindData();
      } catch (error) {
        schedulerLogger.error('Unhandled error in processWindData', {
          error: error instanceof Error ? error.message : 'Unknown error'
        });
        
        try {
          await this.uploader.uploadFallbackData();
        } catch (uploaderErr) {
          schedulerLogger.error('Failed to upload emergency fallback data', {
            error: uploaderErr instanceof Error ? uploaderErr.message : 'Unknown error'
          });
        }
      }
    };

    schedulerLogger.info('Wind Service Starting - Every 6 hours at 00:00, 06:00, 12:00, 18:00 UTC');

    schedulerLogger.info('Running initial wind data fetch (startup trigger)');
    runSafe();

    cron.schedule('0 */6 * * *', () => {
      schedulerLogger.info('Scheduled wind data fetch triggered');
      runSafe();
    });

    schedulerLogger.info('Wind service initialized and running');
  }
}

// Start the service
const service = new WindService();
service.start();