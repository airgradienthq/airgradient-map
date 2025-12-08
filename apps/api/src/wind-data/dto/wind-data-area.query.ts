import { ApiProperty } from '@nestjs/swagger';
import { Type } from 'class-transformer';
import { IsNumber } from 'class-validator';

/**
 * Query parameters for fetching wind data within a bounding box
 * Follows the same pattern as measurement/areaQuery.ts
 */
export class WindDataAreaQuery {
  @ApiProperty({
    description: 'Minimum longitude (western bound)',
    example: -10,
  })
  @Type(() => Number)
  @IsNumber()
  xmin: number;

  @ApiProperty({
    description: 'Maximum longitude (eastern bound)',
    example: 40,
  })
  @Type(() => Number)
  @IsNumber()
  xmax: number;

  @ApiProperty({
    description: 'Minimum latitude (southern bound)',
    example: 35,
  })
  @Type(() => Number)
  @IsNumber()
  ymin: number;

  @ApiProperty({
    description: 'Maximum latitude (northern bound)',
    example: 70,
  })
  @Type(() => Number)
  @IsNumber()
  ymax: number;
}
