import { Injectable, Logger, NotFoundException, BadRequestException } from '@nestjs/common';
import { CreateNotificationDto } from './create-notification.dto';
import { NotificationEntity } from './notification.entity';
import { NotificationPMUnit, AlarmType, NotificationJob, BatchResult } from './notification.model';
import { UpdateNotificationDto } from './update-notification.dto';
import { NotificationsRepository } from './notifications.repository';
import { NotificationBatchProcessor } from './notification-batch.processor';
import LocationRepository from 'src/location/location.repository';
import { convertPmToUsAqi } from 'src/utils/convert_pm_us_aqi';

@Injectable()
export class NotificationsService {
  private readonly logger = new Logger(NotificationsService.name);

  constructor(
    private readonly notificationRepository: NotificationsRepository,
    private readonly batchProcessor: NotificationBatchProcessor,
    private readonly locationRepository: LocationRepository,
  ) {}

  public async createNotification(
    notification: CreateNotificationDto,
  ): Promise<NotificationEntity> {
    this.validateNotificationData(notification);

    // Verify location exists
    try {
      await this.locationRepository.retrieveLocationById(notification.location_id);
    } catch (error) {
      console.log(error);
      throw new NotFoundException(`Location with ID ${notification.location_id} not found`);
    }

    const newNotification = new NotificationEntity({
      ...notification,
      active: notification.active ?? true,
      scheduled_days: notification.scheduled_days || [],
    });

    const result = await this.notificationRepository.createNotification(newNotification);

    this.logger.log(`Created notification registration for player ${notification.player_id}`);

    return result;
  }

  public async getRegisteredNotifications(
    playerId: string,
    locationId?: number,
  ): Promise<NotificationEntity[]> {
    return (await this.notificationRepository.getNotifications(playerId, locationId)).sort(
      (a, b) => b.created_at.getTime() - a.created_at.getTime(),
    );
  }

  public async deleteRegisteredNotification(playerId: string, id: string): Promise<void> {
    const notification = await this.notificationRepository.getNotificationById(id);

    if (!notification) {
      throw new NotFoundException(`Notification registration ${id} not found`);
    }

    if (notification.player_id !== playerId) {
      throw new BadRequestException('Player ID does not match notification registration');
    }

    await this.notificationRepository.deleteNotificationById(id);

    this.logger.log(`Deleted notification registration ${id} for player ${playerId}`);
  }

  public async updateRegisteredNotification(
    playerId: string,
    id: string,
    updateDto: UpdateNotificationDto,
  ): Promise<NotificationEntity> {
    const notification = await this.notificationRepository.getNotificationById(id);

    if (!notification) {
      throw new NotFoundException(`Notification registration ${id} not found`);
    }

    if (notification.player_id !== playerId) {
      throw new BadRequestException('Player ID does not match notification registration');
    }

    // Only update fields that are actually provided in updateDto
    const updatedNotification = new NotificationEntity({
      ...notification,
      ...Object.fromEntries(Object.entries(updateDto).filter(([, value]) => value !== undefined)),
      updated_at: new Date(),
    });

    const result = await this.notificationRepository.updateNotification(updatedNotification);

    this.logger.log(`Updated notification registration ${id} for player ${playerId}`);

    return result;
  }

  /**
   * Process scheduled notifications with batch processing
   */
  async processScheduledNotifications(): Promise<BatchResult> {
    this.logger.debug('Checking for scheduled notifications due now...');
    const notifications = await this.notificationRepository.getScheduledNotificationsForNow();

    if (notifications.length === 0) {
      this.logger.debug('No scheduled notifications due at this time');
      return { successful: [], failed: [], totalTime: 0 };
    }

    this.logger.log(`Found ${notifications.length} scheduled notifications to process`);

    const locationIds = [...new Set(notifications.map(n => n.location_id))];
    this.logger.debug(
      `Fetching measurements for ${locationIds.length} unique locations: ${locationIds.join(', ')}`,
    );

    const measurements = await this.locationRepository.retrieveLastPM25ByLocationsList(locationIds);

    // Convert to Map for easy lookup
    const measurementMap = new Map<number, { pm25: number; locationName: string }>();
    measurements.forEach((m: any) => {
      measurementMap.set(m.locationId, {
        pm25: m.pm25,
        locationName: m.locationName || `Location ${m.locationId}`,
      });
    });

    this.logger.debug(`Retrieved measurements for ${measurements.length} locations`);

    const jobs: NotificationJob[] = [];

    for (const notification of notifications) {
      const measurement = measurementMap.get(notification.location_id);

      if (!measurement) {
        this.logger.warn(
          `No measurement found for location ${notification.location_id} - skipping notification for player ${notification.player_id}`,
        );
        continue;
      }

      const pmValue =
        notification.unit === NotificationPMUnit.UG
          ? measurement.pm25
          : convertPmToUsAqi(measurement.pm25);

      const job = {
        playerId: notification.player_id,
        locationName: measurement.locationName,
        value: pmValue,
        unit: notification.unit as NotificationPMUnit,
        imageUrl: this.getImageUrlForAQI(measurement.pm25),
      };

      jobs.push(job);
    }

    this.logger.log(`Sending ${jobs.length} notifications...`);
    return this.sendBatchNotifications(jobs);
  }

  async sendBatchNotifications(jobs: NotificationJob[]): Promise<BatchResult> {
    if (jobs.length === 0) {
      return { successful: [], failed: [], totalTime: 0 };
    }

    this.logger.log(`Initiating batch notification processing for ${jobs.length} recipients`);

    // Process notifications asynchronously with concurrency control
    const result = await this.batchProcessor.processBatch(jobs);

    if (result.failed.length > 0) {
      this.logger.warn(
        `Batch processing completed with ${result.failed.length} failures: ${JSON.stringify(result.failed)}`,
      );
    }

    return result;
  }

  private validateNotificationData(data: Partial<CreateNotificationDto>): void {
    // Validate based on alarm type
    if (data.alarm_type === AlarmType.THRESHOLD) {
      if (!data.threshold_ug_m3) {
        throw new BadRequestException('Threshold notifications require threshold_ug_m3');
      }
    }

    if (data.alarm_type === AlarmType.SCHEDULED) {
      if (!data.scheduled_time || !data.scheduled_timezone) {
        throw new BadRequestException(
          'Scheduled notifications require scheduled_time and scheduled_timezone',
        );
      }
    }

    if (!data.location_id || data.location_id <= 0) {
      throw new BadRequestException('Valid location_id is required');
    }
  }

  private getImageUrlForAQI(pm25: number): string {
    if (pm25 <= 9) return 'https://www.airgradient.com/images/alert-icons-mascot/aqi-good.png';
    if (pm25 <= 35.4)
      return 'https://www.airgradient.com/images/alert-icons-mascot/aqi-moderate.png';
    if (pm25 <= 55.4)
      return 'https://www.airgradient.com/images/alert-icons-mascot/aqi-unhealthy-sensitive.png';
    if (pm25 <= 125.4)
      return 'https://www.airgradient.com/images/alert-icons-mascot/aqi-unhealthy.png';
    if (pm25 <= 225.4)
      return 'https://www.airgradient.com/images/alert-icons-mascot/aqi-very-unhealthy.png';
    return 'https://www.airgradient.com/images/alert-icons-mascot/aqi-hazardous.png';
  }
}
