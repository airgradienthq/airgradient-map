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
import { AlarmType, NotificationPMUnit } from './notification.model';

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
    description: 'AQI category threshold for notifications (not used for scheduled notifications)',
    required: false,
    enum: ['good', 'moderate', 'unhealthy_sensitive', 'unhealthy', 'very_unhealthy', 'hazardous'],
    example: 'moderate',
  })
  @IsOptional()
  @IsString()
  threshold_category?: string;

  @ApiProperty({
    description: 'How often to check threshold conditions (not used for scheduled notifications)',
    required: false,
    enum: ['once', '1h', '6h', '24h'],
    example: '6h',
  })
  @IsOptional()
  @IsEnum(['once', '1h', '6h', '24h'])
  threshold_cycle?: string;

  @ApiProperty({
    description: 'Days to send scheduled notifications (required for scheduled notifications)',
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
  })
  @IsString()
  @Matches(/^([01]?[0-9]|2[0-3]):[0-5][0-9]$/, {
    message: 'scheduled_time must be in HH:mm format (e.g., 09:30, 23:45)',
  })
  scheduled_time: string;

  @ApiProperty({
    description:
      'Timezone for scheduled notifications (required for scheduled notifications, IANA timezone format)',
    example: 'America/New_York',
    pattern: '^[A-Za-z]+/[A-Za-z_]+([A-Za-z_/]*)?$',
  })
  @IsString()
  @Matches(/^[A-Za-z]+\/[A-Za-z_]+([A-Za-z_/]*)?$/, {
    message:
      'scheduled_timezone must be a valid IANA timezone (e.g., America/New_York, Europe/London)',
  })
  scheduled_timezone: string;

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
      'Type of notification - threshold-based or time-scheduled. Options: "threshold" or "scheduled"',
    enum: AlarmType,
    enumName: 'AlarmType',
    example: AlarmType.THRESHOLD,
  })
  @IsEnum(AlarmType)
  alarm_type: AlarmType;
}
