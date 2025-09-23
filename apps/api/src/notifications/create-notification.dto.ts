import { ApiProperty } from '@nestjs/swagger';
import { IsString, IsNumber, IsBoolean, IsArray, IsEnum, IsOptional, Min, Matches } from 'class-validator';
import { AlarmType, NotificationPMUnit } from './notification.model';

export class CreateNotificationDto {
  @ApiProperty({ description: 'OneSignal Player ID' })
  @IsString()
  player_id: string;

  @ApiProperty({ description: 'User ID who owns this notification' })
  @IsString()
  user_id: string;

  @ApiProperty({ description: 'Location ID to monitor' })
  @IsNumber()
  @Min(1)
  location_id: number;

  @ApiProperty({ description: 'PM2.5 threshold in ug/m3', required: false })
  @IsOptional()
  @IsNumber()
  threshold_ug_m3?: number;

  @ApiProperty({ description: 'Threshold category (e.g., moderate, unhealthy)', required: false })
  @IsOptional()
  @IsString()
  threshold_category?: string;

  @ApiProperty({ description: 'Threshold cycle (e.g., daily, hourly)', required: false })
  @IsOptional()
  @IsString()
  threshold_cycle?: string;

  @ApiProperty({
    description: 'Days to send scheduled notifications',
    type: [String],
    required: false,
    example: ['monday', 'tuesday', 'wednesday', 'thursday', 'friday']
  })
  @IsOptional()
  @IsArray()
  @IsString({ each: true })
  scheduled_days?: string[];

  @ApiProperty({ description: 'Time to send scheduled notifications (HH:mm format)' })
  @IsString()
  @Matches(/^([01]?[0-9]|2[0-3]):[0-5][0-9]$/, {
    message: 'scheduled_time must be in HH:mm format (e.g., 09:30, 23:45)'
  })
  scheduled_time: string;

  @ApiProperty({
    description: 'Timezone for scheduled notifications (IANA timezone format)',
    example: 'America/New_York'
  })
  @IsString()
  @Matches(/^[A-Za-z]+\/[A-Za-z_]+([A-Za-z_\/]*)?$/, {
    message: 'scheduled_timezone must be a valid IANA timezone (e.g., America/New_York, Europe/London)'
  })
  scheduled_timezone: string;

  @ApiProperty({ description: 'Whether notification is active', default: true })
  @IsBoolean()
  @IsOptional()
  active?: boolean;

  @ApiProperty({ description: 'Unit for PM values', enum: NotificationPMUnit })
  @IsEnum(NotificationPMUnit)
  unit: NotificationPMUnit;

  @ApiProperty({ description: 'Type of alarm', enum: AlarmType })
  @IsEnum(AlarmType)
  alarm_type: AlarmType;
}