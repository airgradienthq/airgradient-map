import { IsOptional, IsNumber, Min, Max } from 'class-validator';
import { Type } from 'class-transformer';
import { ApiProperty } from '@nestjs/swagger';

export default class AreaQuery {
  @ApiProperty({
    description: 'West longitude (minimum longitude value) - leftmost boundary',
    example: -122.4194,
    type: Number,
    minimum: -180,
    maximum: 180,
  })
  @IsNumber()
  @Min(-180)
  @Max(180)
  @Type(() => Number)
  xmin: number;

  @ApiProperty({
    description: 'South latitude (minimum latitude value) - bottom boundary',
    example: 37.7749,
    type: Number,
    minimum: -90,
    maximum: 90,
  })
  @IsNumber()
  @Min(-90)
  @Max(90)
  @Type(() => Number)
  ymin: number;

  @ApiProperty({
    description: 'East longitude (maximum longitude value) - rightmost boundary',
    example: -122.4094,
    type: Number,
    minimum: -180,
    maximum: 180,
  })
  @IsNumber()
  @Min(-180)
  @Max(180)
  @Type(() => Number)
  xmax: number;

  @ApiProperty({
    description: 'North latitude (maximum latitude value) - top boundary',
    example: 37.7849,
    type: Number,
    minimum: -90,
    maximum: 90,
  })
  @IsNumber()
  @Min(-90)
  @Max(90)
  @Type(() => Number)
  ymax: number;

  @ApiProperty({
    description: 'Map zoom level for clustering (higher values = more detailed clustering)',
    example: 10,
    type: Number,
    minimum: 1,
    maximum: 20,
  })
  @IsOptional()
  @IsNumber()
  @Type(() => Number)
  zoom?: number;
}
