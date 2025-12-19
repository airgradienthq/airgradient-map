import { ApiProperty } from '@nestjs/swagger';
import { IsEnum, IsNumber, IsOptional, IsString } from 'class-validator';
import { NotificationParameter } from '../notification.model';

export class ExternalNotificationTriggerDto {
  @ApiProperty({
    description: 'External alarm identifier (matches notifications.external_reference_id)',
    example: 1234,
  })
  @IsNumber()
  id: number;

  @ApiProperty({
    description: 'Location identifier associated with the trigger',
    example: 2276,
  })
  @IsNumber()
  locationId: number;

  @ApiProperty({
    description: 'Measurement type for the trigger',
    enum: NotificationParameter,
    example: NotificationParameter.PM25,
  })
  @IsEnum(NotificationParameter)
  measure: NotificationParameter;

  @ApiProperty({
    description: 'Measured value that triggered the alarm',
    example: 54.2,
  })
  @IsNumber()
  value: number;

  @ApiProperty({
    description: 'Optional place identifier (for logging/auditing)',
    example: 42,
    required: false,
  })
  @IsOptional()
  @IsNumber()
  placeId?: number;

  @ApiProperty({
    description: 'Optional location name to use in notifications',
    example: 'Living Room',
    required: false,
  })
  @IsOptional()
  @IsString()
  locationName?: string;
}
