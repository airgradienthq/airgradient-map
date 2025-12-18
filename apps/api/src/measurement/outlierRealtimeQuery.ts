import { ApiPropertyOptional } from '@nestjs/swagger';
import { Transform } from 'class-transformer';
import { IsBoolean, IsNumber, IsOptional, Max, Min } from 'class-validator';

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

  @ApiPropertyOptional({
    description:
      'Override absolute delta threshold used when neighborhood mean is below outlierZScoreMinMean',
    example: 30,
  })
  @IsOptional()
  @Transform(({ value }) => (value !== undefined ? Number(value) : undefined))
  @IsNumber()
  @Min(0)
  @Max(500)
  outlierAbsoluteThreshold?: number;

  @ApiPropertyOptional({
    description: 'Override Z-score threshold used when neighborhood mean is high',
    example: 2,
  })
  @IsOptional()
  @Transform(({ value }) => (value !== undefined ? Number(value) : undefined))
  @IsNumber()
  @Min(0.1)
  @Max(10)
  outlierZScoreThreshold?: number;

  @ApiPropertyOptional({
    description:
      'Neighborhood mean value above which Z-score is used instead of absolute delta threshold',
    example: 50,
  })
  @IsOptional()
  @Transform(({ value }) => (value !== undefined ? Number(value) : undefined))
  @IsNumber()
  @Min(0)
  @Max(500)
  outlierZScoreMinMean?: number;

  @ApiPropertyOptional({
    description: 'Exclude already-flagged outliers from neighbor stats (stored flag)',
    example: true,
  })
  @IsOptional()
  @IsBoolean()
  @Transform(({ value }) => {
    if (value === 'true' || value === true) return true;
    if (value === 'false' || value === false) return false;
    return Boolean(value);
  })
  outlierUseStoredOutlierFlagForNeighbors?: boolean;

  @ApiPropertyOptional({
    description: 'Enable the same-value check (constant PM2.5 over a time window)',
    example: true,
  })
  @IsOptional()
  @IsBoolean()
  @Transform(({ value }) => {
    if (value === 'true' || value === true) return true;
    if (value === 'false' || value === false) return false;
    return Boolean(value);
  })
  outlierEnableSameValueCheck?: boolean;

  @ApiPropertyOptional({
    description: 'Time window (hours) for same-value check',
    example: 24,
  })
  @IsOptional()
  @Transform(({ value }) => (value !== undefined ? Number(value) : undefined))
  @IsNumber()
  @Min(1)
  @Max(168)
  outlierSameValueWindowHours?: number;

  @ApiPropertyOptional({
    description: 'Minimum number of points required for same-value check',
    example: 3,
  })
  @IsOptional()
  @Transform(({ value }) => (value !== undefined ? Number(value) : undefined))
  @IsNumber()
  @Min(1)
  @Max(100)
  outlierSameValueMinCount?: number;

  @ApiPropertyOptional({
    description: 'Include PM2.5 = 0 values in same-value check',
    example: false,
  })
  @IsOptional()
  @IsBoolean()
  @Transform(({ value }) => {
    if (value === 'true' || value === true) return true;
    if (value === 'false' || value === false) return false;
    return Boolean(value);
  })
  outlierSameValueIncludeZero?: boolean;

  @ApiPropertyOptional({
    description:
      'Return only measurements flagged as outliers (useful for debugging / finding hidden monitors)',
    example: false,
  })
  @IsOptional()
  @IsBoolean()
  @Transform(({ value }) => {
    if (value === 'true' || value === true) return true;
    if (value === 'false' || value === false) return false;
    return Boolean(value);
  })
  outliersOnly?: boolean;
}
