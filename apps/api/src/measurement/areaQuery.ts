import { IsOptional, IsNotEmpty, IsNumber, Min, Max } from 'class-validator';
import { Type } from 'class-transformer';
import { ApiProperty } from '@nestjs/swagger';

class AreaQuery {
  @ApiProperty({ description: 'south latitude of the area' })
  @IsNotEmpty()
  @IsNumber()
  @Min(-90)
  @Max(90)
  @Type(() => Number)
  xmin: number;

  @ApiProperty({ description: 'west longitude of the area' })
  @IsNotEmpty()
  @IsNumber()
  @Min(-180)
  @Max(180)
  @Type(() => Number)
  ymin: number;

  @ApiProperty({ description: 'north latitude of the area' })
  @IsNotEmpty()
  @IsNumber()
  @Min(-90)
  @Max(90)
  @Type(() => Number)
  xmax: number;

  @ApiProperty({ description: 'east longitude of the area' })
  @IsNotEmpty()
  @IsNumber()
  @Min(-180)
  @Max(180)
  @Type(() => Number)
  ymax: number;

  @ApiProperty({ description: 'zoom level' })
  @IsOptional()
  @IsNumber()
  @Type(() => Number)
  zoom: number;
}

export default AreaQuery;
