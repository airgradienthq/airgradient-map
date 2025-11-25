import { ApiProperty } from '@nestjs/swagger';
import { Type } from 'class-transformer';
import { IsNumber, IsOptional, IsIn } from 'class-validator';

/**
 * Query parameters for fetching fires data within a bounding box
 * Follows the same pattern as wind-data/dto/wind-data-area.query.ts
 */
export class FiresDataAreaQuery {
  @ApiProperty({
    description: 'Minimum longitude (western bound)',
    example: -125,
  })
  @Type(() => Number)
  @IsNumber()
  xmin: number;

  @ApiProperty({
    description: 'Maximum longitude (eastern bound)',
    example: -114,
  })
  @Type(() => Number)
  @IsNumber()
  xmax: number;

  @ApiProperty({
    description: 'Minimum latitude (southern bound)',
    example: 32,
  })
  @Type(() => Number)
  @IsNumber()
  ymin: number;

  @ApiProperty({
    description: 'Maximum latitude (northern bound)',
    example: 42,
  })
  @Type(() => Number)
  @IsNumber()
  ymax: number;

  @ApiProperty({
    description: 'Time range in hours (default: 48)',
    example: 48,
    required: false,
  })
  @Type(() => Number)
  @IsNumber()
  @IsOptional()
  hours?: number = 48;

  @ApiProperty({
    description: 'Filter by confidence level (optional)',
    enum: ['low', 'nominal', 'high'],
    required: false,
  })
  @IsIn(['low', 'nominal', 'high'])
  @IsOptional()
  confidence?: string;
}
