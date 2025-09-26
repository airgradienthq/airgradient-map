import { Injectable, Logger } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { OneSignalProvider } from './onesignal.provider';
import { BatchResult, NotificationJob } from './notification.model';
import { NOTIFICATION_BATCH_CONFIG } from 'src/constants/notification-batch.constants';
import { delayHelper, timeoutHelper } from 'src/utils/delay';

@Injectable()
export class NotificationBatchProcessor {
  private readonly logger = new Logger(NotificationBatchProcessor.name);

  // Configuration from constants or environment
  private readonly MAX_CONCURRENT_REQUESTS: number;
  private readonly RETRY_ATTEMPTS: number;
  private readonly DELAY_BETWEEN_BATCHES: number;
  private readonly REQUEST_TIMEOUT: number;

  constructor(
    private readonly oneSignalProvider: OneSignalProvider,
    private readonly configService: ConfigService,
  ) {
    // Allow environment overrides for production tuning
    this.MAX_CONCURRENT_REQUESTS = this.configService.get<number>(
      'NOTIFICATION_MAX_CONCURRENT_REQUESTS',
      NOTIFICATION_BATCH_CONFIG.MAX_CONCURRENT_REQUESTS,
    );
    this.RETRY_ATTEMPTS = this.configService.get<number>(
      'NOTIFICATION_RETRY_ATTEMPTS',
      NOTIFICATION_BATCH_CONFIG.RETRY_ATTEMPTS,
    );
    this.DELAY_BETWEEN_BATCHES = this.configService.get<number>(
      'NOTIFICATION_DELAY_BETWEEN_BATCHES_MS',
      NOTIFICATION_BATCH_CONFIG.DELAY_BETWEEN_BATCHES_MS,
    );
    this.REQUEST_TIMEOUT = this.configService.get<number>(
      'NOTIFICATION_REQUEST_TIMEOUT_MS',
      NOTIFICATION_BATCH_CONFIG.REQUEST_TIMEOUT_MS,
    );
  }

  /**
   * Process notifications in batches with concurrency control
   */
  async processBatch(notifications: NotificationJob[]): Promise<BatchResult> {
    const startTime = Date.now();
    const successful: string[] = [];
    const failed: Array<{ playerId: string; error: string }> = [];

    this.logger.log(`Starting batch processing of ${notifications.length} notifications`);

    // Process in chunks with concurrency control
    for (let i = 0; i < notifications.length; i += this.MAX_CONCURRENT_REQUESTS) {
      const chunk = notifications.slice(i, i + this.MAX_CONCURRENT_REQUESTS);

      const promises = chunk.map(notification =>
        this.processWithRetry(notification)
          .then(() => {
            successful.push(notification.playerId);
            return { success: true, playerId: notification.playerId };
          })
          .catch(error => {
            this.logger.error(
              `Failed to send notification to player ${notification.playerId}: ${error.message}`,
            );
            failed.push({
              playerId: notification.playerId,
              error: error.message || 'Unknown error',
            });
            return { success: false, playerId: notification.playerId };
          }),
      );

      // Wait for current chunk to complete
      await Promise.allSettled(promises);

      // Add delay between batches to avoid rate limiting
      if (i + this.MAX_CONCURRENT_REQUESTS < notifications.length) {
        await delayHelper(this.DELAY_BETWEEN_BATCHES);
      }

      const processed = Math.min(i + this.MAX_CONCURRENT_REQUESTS, notifications.length);
      this.logger.debug(`Processed ${processed}/${notifications.length} notifications`);
    }

    const totalTime = Date.now() - startTime;

    this.logger.log(
      `Batch processing completed: ${successful.length}/${notifications.length} notifications sent successfully, ${failed.length} failed, total time: ${totalTime}ms`,
    );

    return { successful, failed, totalTime };
  }

  /**
   * Process a single notification with retry logic
   */
  private async processWithRetry(
    notification: NotificationJob,
    attempt: number = 1,
  ): Promise<void> {
    try {
      await Promise.race([
        this.oneSignalProvider.sendAirQualityNotification(
          [notification.playerId],
          notification.locationName,
          notification.value,
          notification.imageUrl,
          notification.unitLabel,
        ),
        timeoutHelper(this.REQUEST_TIMEOUT),
      ]);
    } catch (error) {
      if (attempt < this.RETRY_ATTEMPTS) {
        this.logger.debug(
          `Retrying notification for ${notification.playerId} (attempt ${attempt + 1}/${this.RETRY_ATTEMPTS})`,
        );

        await delayHelper(Math.pow(2, attempt) * 100);

        return this.processWithRetry(notification, attempt + 1);
      }

      throw error;
    }
  }
}
