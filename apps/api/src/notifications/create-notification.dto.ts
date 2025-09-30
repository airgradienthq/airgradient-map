import { ApiProperty } from '@nestjs/swagger';
import {
  IsString,
  IsNumber,
  IsBoolean,
  IsArray,
  IsEnum,
  IsOptional,
  Min,
  Matches,
} from 'class-validator';
import { NotificationType, NotificationPMUnit } from './notification.model';
import { IsValidTimezone } from './validators/timezone.validator';

export class CreateNotificationDto {
  @ApiProperty({
    description: 'OneSignal Player ID obtained from the mobile app after OneSignal initialization',
    example: 'xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx',
  })
  @IsString()
  player_id: string;

  @ApiProperty({
    description: 'Internal user identifier',
    example: 'user-123',
  })
  @IsString()
  user_id: string;

  @ApiProperty({
    description:
      'ID of the location to monitor for air quality. Must be an existing location in the system',
    example: 888,
  })
  @IsNumber()
  @Min(1)
  location_id: number;

  @ApiProperty({
    description:
      'PM2.5 threshold value in μg/m³ for threshold-based notifications (not used for scheduled notifications)',
    required: false,
    example: 50,
  })
  @IsOptional()
  @IsNumber()
  threshold_ug_m3?: number;

  @ApiProperty({
    description:
      'How often to check threshold conditions. Use "once" for single notification per exceedance, or "1h" to "24h" for hourly cycles',
    required: false,
    pattern: '^(once|([1-9]|1[0-9]|2[0-4])h)$',
    example: '6h',
  })
  @IsOptional()
  @IsString()
  @Matches(/^(once|([1-9]|1[0-9]|2[0-4])h)$/, {
    message:
      'threshold_cycle must be "once" or hour format "1h" to "24h" (e.g., "1h", "6h", "13h", "24h")',
  })
  threshold_cycle?: string;

  @ApiProperty({
    description:
      'Days to send scheduled notifications. Leave empty or omit to disable scheduled notifications. If empty array is provided, no notifications will be sent.',
    type: [String],
    required: false,
    enum: ['monday', 'tuesday', 'wednesday', 'thursday', 'friday', 'saturday', 'sunday'],
    example: ['monday', 'tuesday', 'wednesday', 'thursday', 'friday'],
  })
  @IsOptional()
  @IsArray()
  @IsString({ each: true })
  scheduled_days?: string[];

  @ApiProperty({
    description: 'Time to send scheduled notifications (required for scheduled notifications)',
    example: '09:30',
    pattern: '^([01]?[0-9]|2[0-3]):[0-5][0-9]$',
    required: false,
  })
  @IsOptional()
  @IsString()
  @Matches(/^([01]?[0-9]|2[0-3]):[0-5][0-9]$/, {
    message: 'scheduled_time must be in HH:mm format (e.g., 09:30, 23:45)',
  })
  scheduled_time?: string;

  @ApiProperty({
    description:
      'Timezone for scheduled notifications (required for scheduled notifications, IANA timezone format)',
    example: 'America/New_York',
    required: false,
  })
  @IsOptional()
  @IsString()
  @IsValidTimezone()
  scheduled_timezone?: string;

  @ApiProperty({
    description: 'Whether the notification is active and should be processed',
    default: true,
    example: true,
  })
  @IsBoolean()
  @IsOptional()
  active?: boolean;

  @ApiProperty({
    description:
      'Unit for PM values - determines how threshold values are interpreted. Options: "ug" or "us_aqi"',
    enum: NotificationPMUnit,
    enumName: 'NotificationPMUnit',
    example: NotificationPMUnit.UG,
  })
  @IsEnum(NotificationPMUnit)
  unit: NotificationPMUnit;

  @ApiProperty({
    description:
      'Type of notification - threshold-based or time-scheduled. Options: "threshold" or "scheduled". Note: Only one threshold notification per player per location is allowed.',
    enum: NotificationType,
    enumName: 'AlarmType',
    example: NotificationType.THRESHOLD,
  })
  @IsEnum(NotificationType)
  alarm_type: NotificationType;
}
