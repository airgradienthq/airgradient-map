import * as cron from 'node-cron';
import { testConnection } from './config/database.config';
import { schedulerLogger } from './utils/logger';
import { WindDataProcessingService } from './services/wind-data-processing.service';

/**
 * Wind Service - Main orchestrator for scheduled wind data processing
 * Responsibilities:
 * - Database connection management
 * - Cron scheduling (every 6 hours at 05:05, 11:05, 17:05, 23:05 UTC)
 * - Conditional startup execution
 * - Error handling for unhandled exceptions
 */
class WindService {
  private processor = new WindDataProcessingService();

  /**
   * Starts the wind service with database validation and cron scheduling
   */
  async start(): Promise<void> {
    // Validate database connection before starting
    await this.validateDatabaseConnection();

    // Wrap processing in error handler
    const runSafe = async () => {
      try {
        await this.processor.processWindData();
      } catch (error) {
        schedulerLogger.error('Unhandled error in wind data processing', {
          error: error instanceof Error ? error.message : 'Unknown error',
        });
      }
    };

    schedulerLogger.info(
      'Wind Service Starting - Every 6 hours at 05:05, 11:05, 17:05, 23:05 UTC (5h after model runs)'
    );

    // Conditionally run initial fetch if needed
    await this.conditionalStartupFetch(runSafe);

    // Schedule recurring wind data updates
    this.scheduleCronJob(runSafe);

    schedulerLogger.info('Wind service initialized and running');
  }

  /**
   * Validates database connection, exits process if connection fails
   */
  private async validateDatabaseConnection(): Promise<void> {
    try {
      await testConnection();
      schedulerLogger.info('Database connection validated');
    } catch (error) {
      schedulerLogger.error('Database connection failed, service cannot start', {
        error: error instanceof Error ? error.message : 'Unknown error',
      });
      process.exit(1);
    }
  }

  /**
   * Conditionally fetches wind data on startup if new data is available
   */
  private async conditionalStartupFetch(runSafe: () => Promise<void>): Promise<void> {
    const shouldFetch = await this.processor.shouldFetchNewData();

    if (shouldFetch) {
      schedulerLogger.info('Running initial wind data fetch (startup trigger - new data available)');
      await runSafe();
    } else {
      schedulerLogger.info('Skipping initial fetch - recent data already exists');
    }
  }

  /**
   * Sets up cron schedule for wind data updates
   * Runs at 05:05, 11:05, 17:05, 23:05 UTC (5 hours after GFS model runs)
   */
  private scheduleCronJob(runSafe: () => Promise<void>): void {
    cron.schedule('5 5,11,17,23 * * *', () => {
      schedulerLogger.info('Scheduled wind data fetch triggered');
      runSafe();
    });
  }
}

// Start the service
const service = new WindService();
service.start();
