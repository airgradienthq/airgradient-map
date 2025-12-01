import * as cron from 'node-cron';
import { testConnection } from './config/database.config';
import { schedulerLogger } from './utils/logger';
import { WindDataProcessingService } from './services/wind-data-processing.service';

/**
 * Wind Service - Main orchestrator for scheduled wind data processing
 * Responsibilities:
 * - Database connection management
 * - Cron scheduling (every 3 hours at 02:00, 05:00, 08:00, 11:00, 14:00, 17:00, 20:00, 23:00 UTC)
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
      'Wind Service Starting - Every 3 hours at 02:00, 05:00, 08:00, 11:00, 14:00, 17:00, 20:00, 23:00 UTC'
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
    const shouldFetch = await this.processor.shouldInitialFetchRun();

    if (shouldFetch) {
      schedulerLogger.info('Running initial wind data fetch (startup trigger - new data available)');
      await runSafe();
    } else {
      schedulerLogger.info('Skipping initial fetch - recent data already exists');
    }
  }

  /**
   * Sets up cron schedule for wind data updates
   * Runs every 3 hours at 02:00, 05:00, 08:00, 11:00, 14:00, 17:00, 20:00, 23:00 UTC
   * Smart checking prevents redundant downloads when forecast hasn't changed
   */
  private scheduleCronJob(runSafe: () => Promise<void>): void {
    cron.schedule('0 2,5,8,11,14,17,20,23 * * *', async () => {
      schedulerLogger.info('Scheduled wind data check triggered');

      // Check if new data might be available before downloading
      const shouldFetch = await this.processor.shouldInitialFetchRun();

      if (shouldFetch) {
        schedulerLogger.info('Potential new GFS data available, proceeding with fetch');
        await runSafe();
      } else {
        schedulerLogger.info('Wind data is still fresh, skipping fetch');
      }
    });
  }
}

// Start the service
const service = new WindService();
service.start();
