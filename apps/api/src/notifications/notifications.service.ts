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
  NotificationDisplayUnit,
  NotificationType,
  NotificationJob,
  BatchResult,
  LatestLocationMeasurementData,
  NotificationParameter,
  PARAMETER_VALID_UNITS,
  MonitorType,
} from './notification.model';
import { UpdateNotificationDto } from './update-notification.dto';
import { NotificationsRepository } from './notifications.repository';
import { NotificationBatchProcessor } from './notification-batch.processor';
import LocationRepository from 'src/location/location.repository';
import { convertPmToUsAqi } from 'src/utils/convert-pm-us-aqi';
import { getPMWithEPACorrectionIfNeeded } from 'src/utils/getEpaCorrectedPM';
import { DataSource } from 'src/types/shared/data-source';
import { NOTIFICATION_UNIT_LABELS } from './notification-unit-label';
import { getMascotImageUrl } from './notification-mascots.util';
import { getAndroidAccentColor } from './notification-colors.util';
import { CoreApiService } from 'src/utils/core-api.service';
import { Request } from 'express';
import { DashboardNotificationPayload } from './dto/dashboard-notification.dto';
import {
  validateAuthenticatedRequest,
  validateNotificationOwnership,
  validateAlarmTypeFieldConsistency,
  validateNotificationData,
  validateOwnedMonitorConstraints,
} from './utils/notifications.validation';
import { DEFAULT_DASHBOARD_NOTIFICATION_PAYLOAD } from './constants/dashboard-notification';

@Injectable()
export class NotificationsService {
  private readonly logger = new Logger(NotificationsService.name);

  constructor(
    private readonly notificationRepository: NotificationsRepository,
    private readonly batchProcessor: NotificationBatchProcessor,
    private readonly locationRepository: LocationRepository,
    private readonly coreApiService: CoreApiService,
  ) {}

  /**
   * Normalize input DTO to use new field names internally
   * Accepts both legacy (threshold_ug_m3, unit) and new (threshold, display_unit) field names
   */
  private normalizeCreateInput(dto: CreateNotificationDto): {
    threshold: number | undefined;
    display_unit: NotificationDisplayUnit | undefined;
  } {
    // New field takes precedence over legacy field
    const threshold = dto.threshold ?? dto.threshold_ug_m3;
    const display_unit = (dto.display_unit ?? dto.unit) as NotificationDisplayUnit | undefined;

    return { threshold, display_unit };
  }

  /**
   * Normalize update DTO to use new field names internally
   */
  private normalizeUpdateInput(dto: UpdateNotificationDto): {
    threshold: number | undefined;
    display_unit: NotificationDisplayUnit | undefined;
  } {
    // New field takes precedence over legacy field
    const threshold = dto.threshold ?? dto.threshold_ug_m3;
    const display_unit = (dto.display_unit ?? dto.unit) as NotificationDisplayUnit | undefined;

    return { threshold, display_unit };
  }

  public async createNotification(
    notification: CreateNotificationDto,
    hasFullAccess: boolean,
    request?: Request,
  ): Promise<NotificationEntity> {
    // Normalize input to use new field names
    const { threshold, display_unit } = this.normalizeCreateInput(notification);


    validateNotificationData(notification, threshold, display_unit);

    if (notification.monitor_type === MonitorType.PUBLIC) {
      // Public monitors only support PM2.5 notifications
      if (notification.parameter !== NotificationParameter.PM25) {
        throw new BadRequestException(
          `Public monitors only support PM2.5 notifications. For other parameters (${notification.parameter}), use monitor_type='owned'.`,
        );
      }

      // Verify location exists
      try {
        await this.locationRepository.retrieveLocationById(notification.location_id, hasFullAccess);
      } catch (error) {
        this.logger.warn('Location lookup failed during notification creation', {
          locationId: notification.location_id,
          playerId: notification.player_id,
          error: error.message,
        });
        throw new NotFoundException(`Location with ID ${notification.location_id} not found`);
      }

    }

    // Check for existing threshold notification for this player, location, and parameter
    if (notification.alarm_type === NotificationType.THRESHOLD) {
      const existing =
        await this.notificationRepository.getThresholdNotificationByPlayerAndLocation(
          notification.player_id,
          notification.location_id,
          notification.parameter,
        );

      if (existing) {
        throw new ConflictException(
          `A threshold notification already exists for this location and parameter. Please update the existing notification (ID: ${existing.id}) or delete it first.`,
        );
      }
    }

    const newNotification = new NotificationEntity({
      player_id: notification.player_id,
      user_id: notification.user_id,
      alarm_type: notification.alarm_type,
      location_id: notification.location_id,
      parameter: notification.parameter,
      threshold,
      threshold_cycle: notification.threshold_cycle,
      scheduled_days: notification.scheduled_days || [],
      scheduled_time: notification.scheduled_time,
      scheduled_timezone: notification.scheduled_timezone,
      active: notification.active ?? true,
      display_unit,
      monitor_type: notification.monitor_type ?? MonitorType.PUBLIC,
      place_id: notification.place_id ?? null,
      external_reference_id: null,
    });

    if (newNotification.monitor_type === MonitorType.OWNED) {
      const externalId = await this.forwardOwnedNotificationToDashboard(newNotification, request);
      newNotification.external_reference_id = externalId;
    }

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

  public async deleteRegisteredNotification(
    playerId: string,
    id: string,
    request?: Request,
  ): Promise<void> {
    const notification = validateNotificationOwnership(
      await this.notificationRepository.getNotificationById(id),
      playerId,
      id,
    );

    if (notification.monitor_type === MonitorType.OWNED) {
      await this.deleteOwnedNotificationFromCoreApi(notification, request);
    }

    await this.notificationRepository.deleteNotificationById(id);

    this.logger.log(`Deleted notification registration ${id} for player ${playerId}`);
  }

  public async updateRegisteredNotification(
    playerId: string,
    id: string,
    updateDto: UpdateNotificationDto,
    request?: Request,
  ): Promise<NotificationEntity> {
    const notification = validateNotificationOwnership(
      await this.notificationRepository.getNotificationById(id),
      playerId,
      id,
    );

    // Normalize input to use new field names
    const { threshold, display_unit } = this.normalizeUpdateInput(updateDto);

    // Prevent changes between scheduled and threshold alarm types
    // Check if trying to update fields that don't belong to the current alarm type
    const hasThresholdFields =
      threshold !== undefined ||
      updateDto.threshold !== undefined ||
      updateDto.threshold_ug_m3 !== undefined ||
      updateDto.threshold_cycle !== undefined;
    const hasScheduledFields =
      updateDto.scheduled_days !== undefined ||
      updateDto.scheduled_time !== undefined ||
      updateDto.scheduled_timezone !== undefined;

    validateAlarmTypeFieldConsistency(
      notification.alarm_type,
      hasThresholdFields,
      hasScheduledFields,
    );

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

    // Build update object with normalized field names
    const updateFields: Partial<NotificationEntity> = {};

    if (updateDto.parameter !== undefined) {
      updateFields.parameter = updateDto.parameter;
    }
    if (threshold !== undefined) {
      updateFields.threshold = threshold;
    }
    if (display_unit !== undefined) {
      updateFields.display_unit = display_unit;
    }
    if (updateDto.threshold_cycle !== undefined) {
      updateFields.threshold_cycle = updateDto.threshold_cycle;
    }
    if (updateDto.scheduled_days !== undefined) {
      updateFields.scheduled_days = updateDto.scheduled_days;
    }
    if (updateDto.scheduled_time !== undefined) {
      updateFields.scheduled_time = updateDto.scheduled_time;
    }
    if (updateDto.scheduled_timezone !== undefined) {
      updateFields.scheduled_timezone = updateDto.scheduled_timezone;
    }
    if (updateDto.active !== undefined) {
      updateFields.active = updateDto.active;
    }
    if (updateDto.monitor_type !== undefined) {
      updateFields.monitor_type = updateDto.monitor_type;
    }
    if (updateDto.place_id !== undefined) {
      updateFields.place_id = updateDto.place_id;
    }

    const updatedNotification = new NotificationEntity({
      ...notification,
      ...updateFields,
      updated_at: new Date(),
    });

    // Validate place_id / alarm_type rules for owned monitors
    validateOwnedMonitorConstraints(updatedNotification);

    // Validate parameter/display_unit combination
    const finalParameter = updatedNotification.parameter;
    const finalDisplayUnit = updatedNotification.display_unit;
    if (finalParameter && finalDisplayUnit) {
      const validUnits = PARAMETER_VALID_UNITS[finalParameter as NotificationParameter];
      if (validUnits && !validUnits.includes(finalDisplayUnit)) {
        throw new BadRequestException(
          `Invalid display_unit '${finalDisplayUnit}' for parameter '${finalParameter}'. ` +
            `Valid units for ${finalParameter}: ${validUnits.join(', ')}`,
        );
      }
    }

    if (
      updatedNotification.monitor_type === MonitorType.OWNED &&
      this.ownedNotificationRequiresCoreApiUpdate(notification, updatedNotification)
    ) {
      await this.updateOwnedNotificationOnCoreApi(notification, updatedNotification, request);
    }

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

    // 1. Fetch all active notifications
    const { allNotifications, scheduledCount, thresholdCount } =
      await this.fetchActiveNotifications();

    if (allNotifications.length === 0) {
      this.logger.debug('No notifications to process');
      return { successful: [], failed: [], totalTime: 0 };
    }

    // 2. Split by monitor type (public vs owned)
    const { publicNotifications, ownedNotifications } =
      this.groupNotificationsByMonitorType(allNotifications);

    // 3. Fetch measurement data based on monitor type
    const measurementMap = await this.fetchMeasurementsByMonitorType(
      publicNotifications,
      ownedNotifications,
    );

    // 4 & 5. Build and send jobs (shared pipeline)
    return this.dispatchNotifications(allNotifications, measurementMap, {
      startTime,
      scheduledCount,
      thresholdCount,

    });
  }

  /**
   * Shared notification dispatch pipeline used by the scheduler and external triggers
   */
  private async dispatchNotifications(
    notifications: NotificationEntity[],
    measurementMap: Map<number, LatestLocationMeasurementData>,
    context: {
      startTime?: number;
      scheduledCount?: number;
      thresholdCount?: number;
    } = {},
  ): Promise<BatchResult> {
    if (notifications.length === 0) {
      this.logger.debug('No notifications provided for dispatch');
      return { successful: [], failed: [], totalTime: 0 };
    }

    const jobs = await this.buildNotificationJobs(notifications, measurementMap, new Date());

    if (jobs.length === 0) {
      this.logger.debug('No notifications need to be sent');
      return { successful: [], failed: [], totalTime: 0 };
    }

    if (context.startTime !== undefined) {
      const processingTime = Date.now() - context.startTime;
      this.logger.log(`Sending ${jobs.length} notifications in batch...`, {
        processingTimeMs: processingTime,
        scheduledCount: context.scheduledCount,
        thresholdCount: context.thresholdCount,
        totalNotificationsEvaluated: notifications.length,
        notificationsToSend: jobs.length,
      });
    } else {
      this.logger.log(`Sending ${jobs.length} notifications (external trigger)`);
    }

    return this.sendBatchNotifications(jobs);
  }

  /**
   * Fetch all active notifications (scheduled and threshold)
   */
  private async fetchActiveNotifications(): Promise<{
    allNotifications: NotificationEntity[];
    scheduledCount: number;
    thresholdCount: number;
  }> {
    const [scheduledNotifications, thresholdNotifications] = await Promise.all([
      this.notificationRepository.getScheduledNotificationsForNow(),
      this.notificationRepository.getActiveThresholdNotifications(),
    ]);

    this.logger.log(
      `Found ${scheduledNotifications.length} scheduled and ${thresholdNotifications.length} threshold notifications`,
    );

    return {
      allNotifications: scheduledNotifications.concat(thresholdNotifications),
      scheduledCount: scheduledNotifications.length,
      thresholdCount: thresholdNotifications.length,
    };
  }

  /**
   * Group notifications by monitor type (public vs owned)
   */
  private groupNotificationsByMonitorType(notifications: NotificationEntity[]): {
    publicNotifications: NotificationEntity[];
    ownedNotifications: NotificationEntity[];
  } {
    const publicNotifications = notifications.filter(n => n.monitor_type === MonitorType.PUBLIC);
    const ownedNotifications = notifications.filter(n => n.monitor_type === MonitorType.OWNED);

    this.logger.debug(
      `Split notifications: ${publicNotifications.length} public, ${ownedNotifications.length} owned`,
    );

    return { publicNotifications, ownedNotifications };
  }

  /**
   * Fetch measurements based on monitor type
   */
  private async fetchMeasurementsByMonitorType(
    publicNotifications: NotificationEntity[],
    ownedNotifications: NotificationEntity[],
  ): Promise<Map<number, LatestLocationMeasurementData>> {
    const measurementMap = new Map<number, LatestLocationMeasurementData>();

    // Fetch public monitor measurements from database
    if (publicNotifications.length > 0) {
      const publicMeasurements = await this.fetchPublicMeasurements(publicNotifications);
      publicMeasurements.forEach(m => measurementMap.set(m.locationId, m));
    }

    // Fetch owned monitor measurements points to Dashboard API (temporarily disabled)
    if (ownedNotifications.length > 0) {
      const ownedMeasurements = await this.fetchOwnedMeasurements(ownedNotifications);
      ownedMeasurements.forEach(m => measurementMap.set(m.locationId, m));
    }

    return measurementMap;
  }

  /**
   * Fetch measurements for public monitors from database
   * NOTE: Public monitors currently only support PM2.5 notifications
   */
  private async fetchPublicMeasurements(
    notifications: NotificationEntity[],
  ): Promise<LatestLocationMeasurementData[]> {
    // Filter out non-PM2.5 notifications for public monitors
    const pm25Notifications = notifications.filter(n => n.parameter === NotificationParameter.PM25);

    if (pm25Notifications.length < notifications.length) {
      const skippedCount = notifications.length - pm25Notifications.length;
      this.logger.warn(
        `Skipping ${skippedCount} public monitor notification(s) - only PM2.5 is supported for public monitors`,
      );
    }

    if (pm25Notifications.length === 0) {
      return [];
    }

    const locationIds = [...new Set(pm25Notifications.map(n => n.location_id))];
    this.logger.debug(`Fetching measurements for ${locationIds.length} public locations`);

    const measurements = await this.locationRepository.retrieveLastPM25ByLocationsList(locationIds);

    // Apply EPA correction for AirGradient sensors to ensure consistency with display values
    measurements.forEach((measurement: LatestLocationMeasurementData) => {
      if (measurement.pm25 && measurement.dataSource === DataSource.AIRGRADIENT) {
        measurement.pm25 = getPMWithEPACorrectionIfNeeded(
          measurement.dataSource as DataSource,
          measurement.pm25,
          measurement.rhum,
        );
      }
    });

    return measurements;
  }

  /**
   * Fetch measurements for owned monitors from external Dashboard API.
   * Temporarily disabled until shared Dashboard client lands.
   */
  private async fetchOwnedMeasurements(
    notifications: NotificationEntity[],
  ): Promise<LatestLocationMeasurementData[]> {
    if (notifications.length === 0) {
      return [];
    }

    this.logger.warn(
      `Owned monitor notifications disabled: skipping ${notifications.length} notification(s) until Dashboard API client integration is restored.`,
    );

    return [];
  }

  /**
   * Get measurement value for a specific parameter
   */
  private getParameterValue(
    measurement: LatestLocationMeasurementData,
    parameter: NotificationParameter,
  ): number | null {
    switch (parameter) {
      case NotificationParameter.PM25:
        return measurement.pm25;
      case NotificationParameter.RCO2:
        return measurement.rco2;
      case NotificationParameter.TVOC_INDEX:
        return measurement.tvoc_index;
      case NotificationParameter.NOX_INDEX:
        return measurement.nox_index;
      case NotificationParameter.ATMP:
        return measurement.atmp;
      case NotificationParameter.RHUM:
        return measurement.rhum;
      default:
        return null;
    }
  }

  /**
   * Build notification jobs from notifications and measurements
   */
  private async buildNotificationJobs(
    notifications: NotificationEntity[],
    measurementMap: Map<number, LatestLocationMeasurementData>,
    now: Date,
  ): Promise<NotificationJob[]> {
    const jobs: NotificationJob[] = [];

    for (const notification of notifications) {
      const measurement = measurementMap.get(notification.location_id);

      // Validate measurement exists
      if (!measurement) {
        this.logger.warn(
          `No measurement for location ${notification.location_id} - skipping notification`,
        );
        continue;
      }

      // Get the value for the specific parameter
      const parameterValue = this.getParameterValue(measurement, notification.parameter);

      // Get Android accent color based on parameter type and value
      const androidAccentColor = getAndroidAccentColor(notification.parameter, parameterValue);

      // Get mascot image URL based on parameter type and value
      const imageUrl = getMascotImageUrl(notification.parameter, parameterValue);

      // Handle scheduled notification with no data
      if (parameterValue === null && notification.alarm_type === NotificationType.SCHEDULED) {
        jobs.push(
          this.buildScheduledNoDataJob(notification, measurement, androidAccentColor, imageUrl),
        );
        continue;
      }

      // Skip threshold notifications if no data
      if (parameterValue === null) {
        this.logger.warn(
          `No ${notification.parameter} data for location ${notification.location_id} - skipping notification`,
        );
        continue;
      }

      // Convert value to display unit
      const convertedValue = this.convertValueForDisplayUnit(
        parameterValue,
        notification.parameter,
        notification.display_unit,
      );

      // Handle scheduled notification with data
      if (notification.alarm_type === NotificationType.SCHEDULED) {
        jobs.push(
          this.buildScheduledJob(
            notification,
            measurement,
            convertedValue,
            androidAccentColor,
            imageUrl,
          ),
        );
        continue;
      }

      // Handle threshold notification
      if (notification.alarm_type === NotificationType.THRESHOLD) {
        const job = await this.buildThresholdJob(
          notification,
          measurement,
          convertedValue,
          androidAccentColor,
          imageUrl,
          now,
        );
        if (job) {
          jobs.push(job);
        }
      }
    }

    return jobs;
  }

  /**
   * Convert measurement value to display unit
   */
  private convertValueForDisplayUnit(
    value: number,
    parameter: NotificationParameter,
    displayUnit: NotificationDisplayUnit,
  ): number {
    // Only PM2.5 has unit conversion (ug/m3 to US AQI)
    if (parameter === NotificationParameter.PM25) {
      if (displayUnit === NotificationDisplayUnit.UG) {
        return value;
      }
      return convertPmToUsAqi(value);
    }

    // Temperature conversion (Celsius to Fahrenheit)
    if (parameter === NotificationParameter.ATMP) {
      if (displayUnit === NotificationDisplayUnit.FAHRENHEIT) {
        return (value * 9) / 5 + 32;
      }
      return value; // Return Celsius as-is
    }

    // All other parameters (CO2, TVOC, NOx, Humidity) - no conversion needed
    return value;
  }

  /**
   * Build scheduled notification job (no data)
   */
  private buildScheduledNoDataJob(
    notification: NotificationEntity,
    measurement: LatestLocationMeasurementData,
    androidAccentColor: string,
    imageUrl: string,
  ): NotificationJob {
    return {
      playerId: notification.player_id,
      locationName: measurement.locationName,
      value: null,
      unitLabel: NOTIFICATION_UNIT_LABELS[notification.display_unit],
      unit: notification.display_unit as NotificationDisplayUnit,
      imageUrl,
      androidAccentColor,
      isScheduledNotificationNoData: true,
      title: {
        en: 'Scheduled Notification: ' + measurement.locationName,
        de: 'Geplante Benachrichtigung: ' + measurement.locationName,
      },
    };
  }

  /**
   * Build scheduled notification job (with data)
   */
  private buildScheduledJob(
    notification: NotificationEntity,
    measurement: LatestLocationMeasurementData,
    convertedValue: number,
    androidAccentColor: string,
    imageUrl: string,
  ): NotificationJob {
    return {
      playerId: notification.player_id,
      locationName: measurement.locationName,
      value: convertedValue,
      unitLabel: NOTIFICATION_UNIT_LABELS[notification.display_unit],
      unit: notification.display_unit as NotificationDisplayUnit,
      imageUrl,
      androidAccentColor,
      title: {
        en: 'Scheduled Notification: ' + measurement.locationName,
        de: 'Geplante Benachrichtigung: ' + measurement.locationName,
      },
    };
  }

  /**
   * Build threshold notification job
   */
  private async buildThresholdJob(
    notification: NotificationEntity,
    measurement: LatestLocationMeasurementData,
    convertedValue: number,
    androidAccentColor: string,
    imageUrl: string,
    now: Date,
  ): Promise<NotificationJob | null> {
    const shouldSend = await this.shouldSendThresholdNotification(
      notification,
      measurement.pm25,
      now,
    );

    if (!shouldSend) {
      this.logger.debug('Threshold notification skipped', {
        notificationId: notification.id,
        reason:
          measurement.pm25 === null || measurement.pm25 === undefined
            ? 'missing_pm_value'
            : notification.threshold === null || notification.threshold === undefined
              ? 'missing_threshold_setting'
              : measurement.pm25 <= notification.threshold
                ? 'below_threshold'
                : 'conditions_not_met',
      });
      return null;
    }

    return {
      playerId: notification.player_id,
      locationName: measurement.locationName,
      value: convertedValue,
      unitLabel: NOTIFICATION_UNIT_LABELS[notification.display_unit],
      unit: notification.display_unit as NotificationDisplayUnit,
      imageUrl,
      androidAccentColor,
    };
  }

  /**
   * Determine if a threshold notification should be sent and update state
   */
  private async shouldSendThresholdNotification(
    notification: NotificationEntity,
    currentValue: number,
    now: Date,
  ): Promise<boolean> {
    const thresholdValue = notification.threshold;

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

  private ownedNotificationRequiresCoreApiUpdate(
    original: NotificationEntity,
    updated: NotificationEntity,
  ): boolean {
    return (
      original.location_id !== updated.location_id ||
      original.place_id !== updated.place_id ||
      original.parameter !== updated.parameter ||
      original.threshold !== updated.threshold ||
      original.threshold_cycle !== updated.threshold_cycle
    );
  }

  private async updateOwnedNotificationOnCoreApi(
    original: NotificationEntity,
    updated: NotificationEntity,
    request?: Request,
  ): Promise<void> {
    validateAuthenticatedRequest(request, 'for owned notifications');

    if (!original.external_reference_id) {
      throw new BadRequestException(
        'Owned notification is missing external reference id; cannot update Core API',
      );
    }

    const placeId = updated.place_id ?? original.place_id;
    if (!placeId) {
      throw new BadRequestException('place_id is required when updating owned notifications');
    }

    const payload: DashboardNotificationPayload = {
      ...DEFAULT_DASHBOARD_NOTIFICATION_PAYLOAD,
      active: updated.active,
      locationId: updated.location_id,
      measure: updated.parameter,
      threshold: updated.threshold,
    };

    await this.coreApiService.patch(
      request,
      `/places/${placeId}/admin/alarms/${original.external_reference_id}`,
      payload,
    );
  }

  private async forwardOwnedNotificationToDashboard(
    notification: NotificationEntity,
    request?: Request,
  ): Promise<number | null> {
    if (notification.alarm_type !== NotificationType.THRESHOLD) {
      this.logger.warn(
        `Owned notification for location ${notification.location_id} with alarm type ${notification.alarm_type} cannot be forwarded to Dashboard API`,
      );
      return null;
    }

    if (notification.threshold === null || notification.threshold === undefined) {
      throw new BadRequestException('Owned notifications require a threshold value');
    }

    validateAuthenticatedRequest(request, 'for owned notifications');

    const payload: DashboardNotificationPayload = {
      ...DEFAULT_DASHBOARD_NOTIFICATION_PAYLOAD,
      locationId: notification.location_id,
      measure: notification.parameter,
      threshold: notification.threshold,
    };

    const placeId = notification.place_id;
    if (!placeId) {
      throw new BadRequestException('place_id is required for owned notifications');
    }

    const response = await this.coreApiService.post<{ id: number }>(
      request,
      `/places/${placeId}/admin/alarms`,
      payload,
    );

    if (!response?.id) {
      throw new BadRequestException('Core API did not return a trigger id for owned notification');
    }

    return response.id;
  }

  private async deleteOwnedNotificationFromCoreApi(
    notification: NotificationEntity,
    request?: Request,
  ): Promise<void> {
    validateAuthenticatedRequest(request, 'for owned notifications');

    if (!notification.external_reference_id) {
      this.logger.warn(
        `Owned notification ${notification.id} missing external_reference_id; skipping Core API delete`,
      );
      return;
    }

    if (!notification.place_id) {
      this.logger.warn(
        `Owned notification ${notification.id} missing place_id; skipping Core API delete`,
      );
      return;
    }

    await this.coreApiService.delete(
      request,
      `/places/${notification.place_id}/admin/alarms/${notification.external_reference_id}`,
    );
  }
}
