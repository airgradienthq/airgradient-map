import { ApiProperty } from '@nestjs/swagger';
import { Type } from 'class-transformer';
import { IsNumber, Min, Max, IsOptional } from 'class-validator';

/**
 * Query parameters for fetching wind data within a bounding box
 * Follows the same pattern as measurement/areaQuery.ts
 */
export class WindDataAreaQuery {
  @ApiProperty({
    description: 'Minimum longitude (western bound)',
    example: -10,
    minimum: -180,
    maximum: 180
  })
  @Type(() => Number)
  @IsNumber()
  @Min(-180)
  @Max(180)
  xmin: number;

  @ApiProperty({
    description: 'Maximum longitude (eastern bound)',
    example: 40,
    minimum: -180,
    maximum: 180
  })
  @Type(() => Number)
  @IsNumber()
  @Min(-180)
  @Max(180)
  xmax: number;

  @ApiProperty({
    description: 'Minimum latitude (southern bound)',
    example: 35,
    minimum: -90,
    maximum: 90
  })
  @Type(() => Number)
  @IsNumber()
  @Min(-90)
  @Max(90)
  ymin: number;

  @ApiProperty({
    description: 'Maximum latitude (northern bound)',
    example: 70,
    minimum: -90,
    maximum: 90
  })
  @Type(() => Number)
  @IsNumber()
  @Min(-90)
  @Max(90)
  ymax: number;

  @ApiProperty({
    description: 'Grid resolution in degrees (optional, for future downsampling)',
    example: 0.25,
    minimum: 0.25,
    maximum: 5.0,
    required: false
  })
  @Type(() => Number)
  @IsNumber()
  @IsOptional()
  @Min(0.25)
  @Max(5.0)
  resolution?: number;
}
