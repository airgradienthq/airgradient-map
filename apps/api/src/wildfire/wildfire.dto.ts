import { IsOptional, IsNumber, IsString, Min, Max } from 'class-validator';
import { Type } from 'class-transformer';
import { ApiPropertyOptional } from '@nestjs/swagger';

export class WildfireQueryDto {
  @ApiPropertyOptional({ description: 'Northern boundary' })
  @IsNumber()
  @Type(() => Number)
  @Min(-90)
  @Max(90)
  north: number;

  @ApiPropertyOptional({ description: 'Southern boundary' })
  @IsNumber()
  @Type(() => Number)
  @Min(-90)
  @Max(90)
  south: number;

  @ApiPropertyOptional({ description: 'Eastern boundary' })
  @IsNumber()
  @Type(() => Number)
  @Min(-180)
  @Max(180)
  east: number;

  @ApiPropertyOptional({ description: 'Western boundary' })
  @IsNumber()
  @Type(() => Number)
  @Min(-180)
  @Max(180)
  west: number;

  @ApiPropertyOptional({ description: 'Historical range in days', default: 1 })
  @IsOptional()
  @IsNumber()
  @Type(() => Number)
  @Min(1)
  @Max(7)
  days?: number = 1;

  @ApiPropertyOptional({ description: 'Data source', default: 'VIIRS_SNPP_NRT' })
  @IsOptional()
  @IsString()
  source?: string = 'VIIRS_SNPP_NRT';
}
