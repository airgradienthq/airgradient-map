import { ApiPropertyOptional } from '@nestjs/swagger';
import { Transform } from 'class-transformer';
import { IsNumber, IsOptional, Max, Min } from 'class-validator';

export default class OutlierRealtimeQuery {
  @ApiPropertyOptional({
    description: 'Override spatial radius used for outlier detection (meters)',
    example: 12000,
  })
  @IsOptional()
  @Transform(({ value }) => (value !== undefined ? Number(value) : undefined))
  @IsNumber()
  @Min(1000)
  @Max(300000)
  outlierRadiusMeters?: number;

  @ApiPropertyOptional({
    description: 'Hours before/after the measurement to include nearby values',
    example: 2,
  })
  @IsOptional()
  @Transform(({ value }) => (value !== undefined ? Number(value) : undefined))
  @IsNumber()
  @Min(0.5)
  @Max(24)
  outlierWindowHours?: number;

  @ApiPropertyOptional({
    description: 'Minimum number of nearby points required to calculate stats',
    example: 3,
  })
  @IsOptional()
  @Transform(({ value }) => (value !== undefined ? Number(value) : undefined))
  @IsNumber()
  @Min(1)
  @Max(20)
  outlierMinNearby?: number;
}
