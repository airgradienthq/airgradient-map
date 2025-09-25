import { ApiProperty } from '@nestjs/swagger';
import {
  IsString,
  IsNumber,
  IsBoolean,
  IsArray,
  IsEnum,
  IsOptional,
  Matches,
} from 'class-validator';
import { NotificationPMUnit } from './notification.model';

export class UpdateNotificationDto {
  @ApiProperty({ description: 'PM2.5 threshold in ug/m3', required: false })
  @IsOptional()
  @IsNumber()
  threshold_ug_m3?: number;

  @ApiProperty({ description: 'Threshold cycle', required: false })
  @IsOptional()
  @IsString()
  threshold_cycle?: string;

  @ApiProperty({
    description:
      'Days to send scheduled notifications. If empty array is provided, no notifications will be sent.',
    type: [String],
    enum: ['monday', 'tuesday', 'wednesday', 'thursday', 'friday', 'saturday', 'sunday'],
    required: false,
  })
  @IsOptional()
  @IsArray()
  @IsString({ each: true })
  scheduled_days?: string[];

  @ApiProperty({
    description: 'Time to send scheduled notifications (HH:mm format)',
    required: false,
  })
  @IsOptional()
  @IsString()
  @Matches(/^([01]?[0-9]|2[0-3]):[0-5][0-9]$/, {
    message: 'scheduled_time must be in HH:mm format (e.g., 09:30, 23:45)',
  })
  scheduled_time?: string;

  @ApiProperty({
    description: 'Timezone for scheduled notifications (IANA timezone format)',
    example: 'America/New_York',
    required: false,
  })
  @IsOptional()
  @IsString()
  @Matches(/^[A-Za-z]+\/[A-Za-z_]+([A-Za-z_/]*)?$/, {
    message:
      'scheduled_timezone must be a valid IANA timezone (e.g., America/New_York, Europe/London)',
  })
  scheduled_timezone?: string;

  @ApiProperty({ description: 'Whether notification is active', required: false })
  @IsOptional()
  @IsBoolean()
  active?: boolean;

  @ApiProperty({ description: 'Unit for PM values', enum: NotificationPMUnit, required: false })
  @IsOptional()
  @IsEnum(NotificationPMUnit)
  unit?: NotificationPMUnit;
}
