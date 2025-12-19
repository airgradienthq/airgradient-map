import { ApiProperty } from '@nestjs/swagger';
import { NotificationType, NotificationDisplayUnit, NotificationParameter, MonitorType } from './notification.model';

export class NotificationEntity {
  @ApiProperty()
  id: number;

  @ApiProperty()
  player_id: string;

  @ApiProperty()
  created_at: Date;

  @ApiProperty()
  updated_at: Date;

  @ApiProperty()
  user_id: string;

  @ApiProperty({
    enum: NotificationType,
    enumName: 'AlarmType',
    description: 'Type of notification: threshold-based or scheduled',
  })
  alarm_type: NotificationType;

  @ApiProperty({
    enum: NotificationParameter,
    enumName: 'NotificationParameter',
    description: 'Parameter being monitored (pm25, rco2, tvoc, nox_index, atmp, rhum)',
  })
  parameter: NotificationParameter;

  @ApiProperty()
  location_id: number;

  @ApiProperty({ description: 'Threshold value for threshold-based notifications' })
  threshold: number | null;

  @ApiProperty()
  threshold_cycle: string | null;

  @ApiProperty({
    description:
      'Days on which scheduled notifications are sent. Empty array means no notifications will be sent.',
    type: [String],
    enum: ['monday', 'tuesday', 'wednesday', 'thursday', 'friday', 'saturday', 'sunday'],
  })
  scheduled_days: string[];

  @ApiProperty()
  scheduled_time: string;

  @ApiProperty()
  scheduled_timezone: string;

  @ApiProperty()
  active: boolean;

  @ApiProperty({
    enum: NotificationDisplayUnit,
    enumName: 'NotificationDisplayUnit',
    description: 'Display unit for notification values',
  })
  display_unit: NotificationDisplayUnit;

  @ApiProperty({
    enum: MonitorType,
    enumName: 'MonitorType',
    description: 'Type of monitor: owned (user device) or public (community monitor)',
  })
  monitor_type: MonitorType;

  @ApiProperty({
    description: 'Place ID for owned monitors (required when monitor_type is owned)',
    nullable: true,
  })
  place_id: number | null;

  @ApiProperty()
  was_exceeded: boolean;

  @ApiProperty()
  last_notified_at: Date;

  @ApiProperty({
    description: 'External trigger identifier from Core API for owned notifications',
    nullable: true,
  })
  external_reference_id: number | null;

  constructor(partial: Partial<NotificationEntity>) {
    Object.assign(this, partial);
  }
}
