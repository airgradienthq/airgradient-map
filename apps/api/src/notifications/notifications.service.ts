import {
  Injectable,
  Logger,
  NotFoundException,
  BadRequestException,
  ConflictException,
} from '@nestjs/common';
import { CreateNotificationDto } from './create-notification.dto';
import { NotificationEntity } from './notification.entity';
import {
  NotificationPMUnit,
  NotificationType,
  NotificationJob,
  BatchResult,
  LatestLocationMeasurementData,
} from './notification.model';
import { UpdateNotificationDto } from './update-notification.dto';
import { NotificationsRepository } from './notifications.repository';
import { NotificationBatchProcessor } from './notification-batch.processor';
import LocationRepository from 'src/location/location.repository';
import { convertPmToUsAqi } from 'src/utils/convert-pm-us-aqi';
import { getEPACorrectedPM } from 'src/utils/getEpaCorrectedPM';
import { DataSource } from 'src/types/shared/data-source';
import { NOTIFICATION_UNIT_LABELS } from './notification-unit-label';
import { AQ_LEVELS_COLORS } from 'src/constants/aq-levels-colors';
import { getAQIColor } from 'src/utils/get-aqi-color-by-value';
import { AQILevels } from 'src/types/shared/aq-levels.types';

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
      this.logger.warn('Location lookup failed during notification creation', {
        locationId: notification.location_id,
        playerId: notification.player_id,
        error: error.message,
      });
      throw new NotFoundException(`Location with ID ${notification.location_id} not found`);
    }

    // Check for existing threshold notification for this player and location
    if (notification.alarm_type === NotificationType.THRESHOLD) {
      const existing =
        await this.notificationRepository.getThresholdNotificationByPlayerAndLocation(
          notification.player_id,
          notification.location_id,
        );

      if (existing) {
        throw new ConflictException(
          `A threshold notification already exists for this location. Please update the existing notification (ID: ${existing.id}) or delete it first.`,
        );
      }
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

    // Prevent changes between scheduled and threshold alarm types
    // Check if trying to update fields that don't belong to the current alarm type
    if (notification.alarm_type === NotificationType.SCHEDULED) {
      if (updateDto.threshold_ug_m3 !== undefined || updateDto.threshold_cycle !== undefined) {
        throw new BadRequestException(
          'Cannot set threshold fields on a scheduled notification. Create a new notification instead.',
        );
      }
    } else if (notification.alarm_type === NotificationType.THRESHOLD) {
      if (
        updateDto.scheduled_days !== undefined ||
        updateDto.scheduled_time !== undefined ||
        updateDto.scheduled_timezone !== undefined
      ) {
        throw new BadRequestException(
          'Cannot set scheduled fields on a threshold notification. Create a new notification instead.',
        );
      }
    }

    // Validate timezone if provided
    if (updateDto.scheduled_timezone) {
      try {
        new Intl.DateTimeFormat('en-US', { timeZone: updateDto.scheduled_timezone });
      } catch (error) {
        console.log(error);
        this.logger.warn('Invalid timezone provided in update', {
          timezone: updateDto.scheduled_timezone,
          playerId,
          notificationId: id,
        });
        throw new BadRequestException(
          `Invalid timezone '${updateDto.scheduled_timezone}'. Please provide a valid IANA timezone (e.g., America/New_York, Europe/London)`,
        );
      }
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
   * Process all notifications (scheduled and threshold) - unified processor
   */
  async processAllNotifications(): Promise<BatchResult> {
    const startTime = Date.now();
    this.logger.debug('Processing all notifications...');

    // Get both types of notifications in parallel
    const [scheduledNotifications, thresholdNotifications] = await Promise.all([
      this.notificationRepository.getScheduledNotificationsForNow(),
      this.notificationRepository.getActiveThresholdNotifications(),
    ]);

    const allNotifications = scheduledNotifications.concat(thresholdNotifications);

    if (allNotifications.length === 0) {
      this.logger.debug('No notifications to process');
      return { successful: [], failed: [], totalTime: 0 };
    }

    this.logger.log(
      `Found ${scheduledNotifications.length} scheduled and ${thresholdNotifications.length} threshold notifications`,
    );

    // Fetch measurements for all unique locations at once
    const locationIds = [...new Set(allNotifications.map(n => n.location_id))];
    this.logger.debug(`Fetching measurements for ${locationIds.length} unique locations`);

    const measurements: LatestLocationMeasurementData[] =
      await this.locationRepository.retrieveLastPM25ByLocationsList(locationIds);

    // Apply EPA correction for AirGradient sensors to ensure consistency with display values
    measurements.forEach((measurement: LatestLocationMeasurementData) => {
      if (measurement.pm25) {
        if (measurement.dataSource === DataSource.AIRGRADIENT) {
          measurement.pm25 = getEPACorrectedPM(measurement.pm25, measurement.rhum);
        }
      }
    });

    const measurementMap = new Map<number, LatestLocationMeasurementData>();

    measurements.forEach((measurement: LatestLocationMeasurementData) => {
      measurementMap.set(measurement.locationId, measurement);
    });

    const jobs: NotificationJob[] = [];
    const now = new Date();

    for (const notification of allNotifications) {
      const measurement = measurementMap.get(notification.location_id);
      if (
        !measurement ||
        (!measurement?.pm25 && notification.alarm_type !== NotificationType.SCHEDULED)
      ) {
        this.logger.warn(
          `No measurement for location ${notification.location_id} - skipping notification`,
        );
        continue;
      }

      let androidAccentColor: string =
        AQ_LEVELS_COLORS[
          measurement.pm25 === null ? AQILevels.NO_DATA : getAQIColor(measurement.pm25)
        ];
      androidAccentColor = androidAccentColor.replace('#', 'FF');

      if (
        measurement &&
        measurement.pm25 === null &&
        notification.alarm_type === NotificationType.SCHEDULED
      ) {
        jobs.push({
          playerId: notification.player_id,
          locationName: measurement.locationName,
          value: null,
          unitLabel: NOTIFICATION_UNIT_LABELS[notification.unit],
          unit: notification.unit as NotificationPMUnit,
          imageUrl: this.getImageUrlForAQI(measurement.pm25),
          androidAccentColor,
          isScheduledNotificationNoData: true,
          title: {
            en: 'Scheduled Notification: ' + measurement.locationName,
            de: 'Geplante Benachrichtigung: ' + measurement.locationName,
          },
        });
        continue;
      }

      const pmValueConvertedForUnit =
        notification.unit === NotificationPMUnit.UG
          ? measurement.pm25
          : convertPmToUsAqi(measurement.pm25);

      // For scheduled notifications, always send
      if (notification.alarm_type === NotificationType.SCHEDULED) {
        jobs.push({
          playerId: notification.player_id,
          locationName: measurement.locationName,
          value: pmValueConvertedForUnit,
          unitLabel: NOTIFICATION_UNIT_LABELS[notification.unit],
          unit: notification.unit as NotificationPMUnit,
          imageUrl: this.getImageUrlForAQI(measurement.pm25),
          androidAccentColor,
          title: {
            en: 'Scheduled Notification: ' + measurement.locationName,
            de: 'Geplante Benachrichtigung: ' + measurement.locationName,
          },
        });
        continue;
      }

      // For threshold notifications, check conditions
      if (notification.alarm_type === NotificationType.THRESHOLD) {
        const shouldSend = await this.shouldSendThresholdNotification(
          notification,
          measurement.pm25,
          now,
        );

        if (shouldSend) {
          jobs.push({
            playerId: notification.player_id,
            locationName: measurement.locationName,
            value: pmValueConvertedForUnit,
            unitLabel: NOTIFICATION_UNIT_LABELS[notification.unit],
            unit: notification.unit as NotificationPMUnit,
            imageUrl: this.getImageUrlForAQI(measurement.pm25),
            androidAccentColor,
          });
        } else {
          this.logger.debug('Threshold notification skipped', {
            notificationId: notification.id,
            reason:
              measurement.pm25 === null || measurement.pm25 === undefined
                ? 'missing_pm_value'
                : notification.threshold_ug_m3 === null ||
                    notification.threshold_ug_m3 === undefined
                  ? 'missing_threshold_setting'
                  : measurement.pm25 <= notification.threshold_ug_m3
                    ? 'below_threshold'
                    : 'conditions_not_met',
          });
        }
      }
    }

    if (jobs.length === 0) {
      this.logger.debug('No notifications need to be sent');
      return { successful: [], failed: [], totalTime: 0 };
    }

    const processingTime = Date.now() - startTime;
    this.logger.log(`Sending ${jobs.length} notifications in batch...`, {
      processingTimeMs: processingTime,
      scheduledCount: scheduledNotifications.length,
      thresholdCount: thresholdNotifications.length,
      totalNotificationsEvaluated: allNotifications.length,
      notificationsToSend: jobs.length,
    });

    return this.sendBatchNotifications(jobs);
  }

  /**
   * Determine if a threshold notification should be sent and update state
   */
  private async shouldSendThresholdNotification(
    notification: NotificationEntity,
    currentValue: number,
    now: Date,
  ): Promise<boolean> {
    const thresholdValue = notification.threshold_ug_m3;

    // Handle "once" notifications
    if (notification.threshold_cycle === 'once') {
      if (currentValue > thresholdValue && !notification.was_exceeded) {
        await this.notificationRepository.updateNotificationState(notification.id, {
          was_exceeded: true,
        });
        this.logger.debug(`Threshold exceeded for once notification ${notification.id}`);
        return true;
      } else if (currentValue <= thresholdValue && notification.was_exceeded) {
        await this.notificationRepository.updateNotificationState(notification.id, {
          was_exceeded: false,
        });
        this.logger.debug(`Threshold reset for once notification ${notification.id}`);
      }
      return false;
    }

    // Handle cycle-based notifications
    if (notification.threshold_cycle && currentValue > thresholdValue) {
      const cycleHours = this.parseCycleHours(notification.threshold_cycle);
      if (!cycleHours) {
        this.logger.warn(`Invalid cycle format: ${notification.threshold_cycle}`);
        return false;
      }

      // Check if enough time has passed
      if (!notification.last_notified_at) {
        // Never notified before
        await this.notificationRepository.updateNotificationState(notification.id, {
          last_notified_at: now,
        });
        return true;
      }

      const hoursSince =
        (now.getTime() - new Date(notification.last_notified_at).getTime()) / (1000 * 60 * 60);

      if (hoursSince >= cycleHours) {
        await this.notificationRepository.updateNotificationState(notification.id, {
          last_notified_at: now,
        });
        this.logger.debug(`Cycle threshold met for notification ${notification.id}`);
        return true;
      }
    }

    return false;
  }

  /**
   * Parse cycle string to hours (e.g., "6h" -> 6)
   */
  private parseCycleHours(cycle: string): number | null {
    const match = cycle.match(/^(\d+)h$/);
    return match ? parseInt(match[1], 10) : null;
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
    if (data.alarm_type === NotificationType.THRESHOLD) {
      if (!data.threshold_ug_m3) {
        throw new BadRequestException('Threshold notifications require threshold_ug_m3');
      }

      // Prevent scheduled fields on threshold notifications
      if (data.scheduled_days || data.scheduled_time || data.scheduled_timezone) {
        throw new BadRequestException(
          'Cannot set scheduled fields on a threshold notification. Use alarm_type: "scheduled" instead.',
        );
      }
    }

    if (data.alarm_type === NotificationType.SCHEDULED) {
      if (!data.scheduled_time || !data.scheduled_timezone) {
        throw new BadRequestException(
          'Scheduled notifications require scheduled_time and scheduled_timezone',
        );
      }

      // Prevent threshold fields on scheduled notifications
      if (data.threshold_ug_m3 !== undefined || data.threshold_cycle !== undefined) {
        throw new BadRequestException(
          'Cannot set threshold fields on a scheduled notification. Use alarm_type: "threshold" instead.',
        );
      }

      // Additional runtime timezone validation
      if (data.scheduled_timezone) {
        try {
          new Intl.DateTimeFormat('en-US', { timeZone: data.scheduled_timezone });
        } catch (error) {
          console.log(error);
          throw new BadRequestException(
            `Invalid timezone '${data.scheduled_timezone}'. Please provide a valid IANA timezone (e.g., America/New_York, Europe/London)`,
          );
        }
      }
    }

    if (!data.location_id || data.location_id <= 0) {
      throw new BadRequestException('Valid location_id is required');
    }
  }

  private getImageUrlForAQI(pm25: number): string {
    if (pm25 === null) {
      return 'https://www.airgradient.com/images/alert-icons-mascot/aqi-no-data.png';
    } else if (pm25 <= 9) {
      return 'https://www.airgradient.com/images/alert-icons-mascot/aqi-good.png';
    } else if (pm25 <= 35.4) {
      return 'https://www.airgradient.com/images/alert-icons-mascot/aqi-moderate.png';
    } else if (pm25 <= 55.4) {
      return 'https://www.airgradient.com/images/alert-icons-mascot/aqi-unhealthy-sensitive.png';
    } else if (pm25 <= 125.4) {
      return 'https://www.airgradient.com/images/alert-icons-mascot/aqi-unhealthy.png';
    } else if (pm25 <= 225.4) {
      return 'https://www.airgradient.com/images/alert-icons-mascot/aqi-very-unhealthy.png';
    } else {
      return 'https://www.airgradient.com/images/alert-icons-mascot/aqi-hazardous.png';
    }
  }
}
