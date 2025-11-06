import * as cron from 'node-cron';
import { GFSDownloaderService } from './services/gfs-downloader.service';
import { GribConverterService } from './services/grib-converter.service';
import { WindDataRepositoryService } from './services/wind-data-repository.service';
import { testConnection } from './config/database.config';
import { logger, schedulerLogger } from './utils/logger';
import { WindDataRecord } from './types';

class WindService {
  private downloader = new GFSDownloaderService();
  private converter = new GribConverterService();
  private windDataRepo = new WindDataRepositoryService();

  /**
   * Processes wind data from NOAA GFS
   * Runs conditionally on startup + scheduled at 05:05, 11:05, 17:05, 23:05 UTC
   * (5 hours after GFS model runs to account for data availability delay)
   */
  async processWindData(): Promise<void> {
    const startTime = Date.now();
    logger.info('wind-service', 'Wind data update started');

    try {
      // Step 1: Find grib2json
      const grib2jsonPath = await this.converter.findGrib2Json();
      if (!grib2jsonPath) {
        logger.error('wind-service', 'grib2json not found, cannot process wind data');
        return;
      }

      // Step 2: Download GFS data
      const gribFile = await this.downloader.downloadGFSData();
      if (!gribFile) {
        logger.error('wind-service', 'GFS download failed');
        return;
      }

      // Step 3: Convert GRIB to JSON
      const windData = await this.converter.convertGribToJson(grib2jsonPath, gribFile);
      if (!windData) {
        logger.error('wind-service', 'GRIB conversion failed');
        return;
      }

      // Step 4: Transform and insert into database in batches
      const records = this.transformWindData(windData);

      // Insert in chunks of 10,000 records to avoid overwhelming the database
      const BATCH_SIZE = 10000;
      let totalInserted = 0;
      let failed = false;
      let lastError = '';

      for (let i = 0; i < records.length; i += BATCH_SIZE) {
        const batch = records.slice(i, i + BATCH_SIZE);
        const result = await this.windDataRepo.batchInsert(batch);

        if (result.success) {
          totalInserted += result.recordCount;
          logger.info('wind-service', 'Batch inserted', {
            batchNumber: Math.floor(i / BATCH_SIZE) + 1,
            recordsInBatch: result.recordCount,
            totalInserted
          });
        } else {
          failed = true;
          lastError = result.error || 'Unknown error';
          logger.error('wind-service', 'Batch insertion failed', {
            batchNumber: Math.floor(i / BATCH_SIZE) + 1,
            error: lastError
          });
          break;
        }
      }

      const duration = Date.now() - startTime;
      if (!failed) {
        logger.info('wind-service', 'Wind data update completed successfully', {
          duration: `${duration}ms`,
          totalRecords: totalInserted
        });
      } else {
        logger.error('wind-service', 'Wind data insertion failed', {
          duration: `${duration}ms`,
          totalRecords: totalInserted,
          error: lastError
        });
      }

    } catch (error) {
      const duration = Date.now() - startTime;
      logger.error('wind-service', 'Wind data update failed', {
        error: error instanceof Error ? error.message : 'Unknown error',
        duration: `${duration}ms`
      });
    }
  }

  /**
   * Transforms GFS wind data JSON into database records
   * windData format: [uComponent, vComponent]
   * Each component has: { header: { refTime, ... }, data: [values...] }
   */
  private transformWindData(windData: any[]): WindDataRecord[] {
    const [uComponent, vComponent] = windData;

    if (!uComponent?.data || !vComponent?.data) {
      logger.warn('wind-service', 'Invalid wind data format');
      return [];
    }

    const records: WindDataRecord[] = [];
    const forecastTime = new Date(uComponent.header.refTime);

    // Grid parameters from GFS 0.25 degree resolution
    const nx = uComponent.header.nx || 1440; // 360 / 0.25
    const ny = uComponent.header.ny || 721;  // 180 / 0.25 + 1
    const la1 = uComponent.header.la1 || 90;  // Start latitude
    const lo1 = uComponent.header.lo1 || 0;   // Start longitude
    const dx = uComponent.header.dx || 0.25;  // Longitude increment
    const dy = uComponent.header.dy || 0.25;  // Latitude increment

    for (let i = 0; i < uComponent.data.length; i++) {
      const u = uComponent.data[i];
      const v = vComponent.data[i];

      if (u === null || v === null) continue;

      // Calculate lat/lon from grid index
      const row = Math.floor(i / nx);
      const col = i % nx;
      const latitude = la1 - (row * dy);
      const longitude = lo1 + (col * dx);

      records.push({
        longitude,
        latitude,
        forecast_time: forecastTime,
        u_component: u,
        v_component: v
      });
    }

    logger.info('wind-service', 'Transformed wind data', {
      totalRecords: records.length,
      forecastTime: forecastTime.toISOString()
    });

    return records;
  }

  async start(): Promise<void> {
    // Test database connection on startup
    try {
      await testConnection();
    } catch (error) {
      schedulerLogger.error('Database connection failed, service cannot start', {
        error: error instanceof Error ? error.message : 'Unknown error'
      });
      process.exit(1);
    }

    const runSafe = async () => {
      try {
        await this.processWindData();
      } catch (error) {
        schedulerLogger.error('Unhandled error in processWindData', {
          error: error instanceof Error ? error.message : 'Unknown error'
        });
      }
    };

    schedulerLogger.info('Wind Service Starting - Every 6 hours at 05:05, 11:05, 17:05, 23:05 UTC (5h after model runs)');

    // Check if we should fetch on startup
    const shouldFetch = await this.windDataRepo.shouldFetchNewData();
    if (shouldFetch) {
      schedulerLogger.info('Running initial wind data fetch (startup trigger - new data available)');
      runSafe();
    } else {
      schedulerLogger.info('Skipping initial fetch - recent data already exists');
    }


    cron.schedule('5 5,11,17,23 * * *', () => {
      schedulerLogger.info('Scheduled wind data fetch triggered');
      runSafe();
    });

    schedulerLogger.info('Wind service initialized and running');
  }
}

// Start the service
const service = new WindService();
service.start();
