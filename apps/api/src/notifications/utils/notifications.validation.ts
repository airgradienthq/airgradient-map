import { BadRequestException, NotFoundException } from '@nestjs/common';
import { Request } from 'express';
import { CreateNotificationDto } from '../create-notification.dto';
import { NotificationEntity } from '../notification.entity';
import {
  MonitorType,
  NotificationDisplayUnit,
  NotificationParameter,
  NotificationType,
  PARAMETER_VALID_UNITS,
} from '../notification.model';

export function validateAuthenticatedRequest(
  request: Request | undefined,
  context: string,
): Request {
  if (!request) {
    throw new BadRequestException(`Authenticated request is required ${context}`);
  }

  return request;
}

export function validateNotificationOwnership(
  notification: NotificationEntity | null,
  playerId: string,
  id: string,
): NotificationEntity {
  if (!notification) {
    throw new NotFoundException(`Notification registration ${id} not found`);
  }

  if (notification.player_id !== playerId) {
    throw new BadRequestException('Player ID does not match notification registration');
  }

  return notification;
}

export function validateOwnedMonitorConstraints(notification: NotificationEntity): void {
  if (notification.monitor_type === MonitorType.OWNED) {
    if (notification.alarm_type !== NotificationType.THRESHOLD) {
      throw new BadRequestException('Owned monitors only support threshold notifications');
    }
    if (!notification.place_id) {
      throw new BadRequestException('place_id is required when monitor_type is "owned"');
    }
  }
}

export function validateAlarmTypeFieldConsistency(
  alarmType: NotificationType,
  hasThresholdFields: boolean,
  hasScheduledFields: boolean,
): void {
  if (alarmType === NotificationType.SCHEDULED && hasThresholdFields) {
    throw new BadRequestException(
      'Cannot set threshold fields on a scheduled notification. Create a new notification instead.',
    );
  }

  if (alarmType === NotificationType.THRESHOLD && hasScheduledFields) {
    throw new BadRequestException(
      'Cannot set scheduled fields on a threshold notification. Create a new notification instead.',
    );
  }
}

export function validateNotificationData(
  data: Partial<CreateNotificationDto>,
  threshold: number | undefined,
  display_unit: NotificationDisplayUnit | undefined,
): void {
  if (data.alarm_type === NotificationType.THRESHOLD) {
    if (threshold === undefined || threshold === null) {
      throw new BadRequestException(
        'Threshold notifications require threshold (or threshold_ug_m3 for legacy clients)',
      );
    }

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

    const hasThresholdFields = threshold !== undefined || data.threshold_cycle !== undefined;
    if (hasThresholdFields) {
      throw new BadRequestException(
        'Cannot set threshold fields on a scheduled notification. Use alarm_type: "threshold" instead.',
      );
    }

    if (data.scheduled_timezone) {
      try {
        new Intl.DateTimeFormat('en-US', { timeZone: data.scheduled_timezone });
      } catch (error) {
        throw new BadRequestException(
          `Invalid timezone '${data.scheduled_timezone}'. Please provide a valid IANA timezone (e.g., America/New_York, Europe/London)`,
        );
      }
    }
  }

  if (!data.location_id || data.location_id <= 0) {
    throw new BadRequestException('Valid location_id is required');
  }

  if (!data.parameter) {
    throw new BadRequestException('parameter is required');
  }

  if (display_unit === undefined || display_unit === null) {
    throw new BadRequestException('display_unit (or unit for legacy clients) is required');
  }

  const validUnits = PARAMETER_VALID_UNITS[data.parameter as NotificationParameter];
  if (validUnits && !validUnits.includes(display_unit)) {
    throw new BadRequestException(
      `Invalid display_unit '${display_unit}' for parameter '${data.parameter}'. ` +
        `Valid units for ${data.parameter}: ${validUnits.join(', ')}`,
    );
  }

  if (data.monitor_type === MonitorType.OWNED) {
    if (data.alarm_type && data.alarm_type !== NotificationType.THRESHOLD) {
      throw new BadRequestException('Owned monitors only support threshold notifications');
    }
    if (!data.place_id) {
      throw new BadRequestException('place_id is required when monitor_type is "owned"');
    }
  }
}
