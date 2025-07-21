import { IsOptional, IsNumber, Min, Max } from 'class-validator';
import { Type } from 'class-transformer';

export default class AreaQuery {
  @IsNumber()
  @Min(-180)
  @Max(180)
  @Type(() => Number)
  xmin: number;

  @IsNumber()
  @Min(-90)
  @Max(90)
  @Type(() => Number)
  ymin: number;

  @IsNumber()
  @Min(-180)
  @Max(180)
  @Type(() => Number)
  xmax: number;

  @IsNumber()
  @Min(-90)
  @Max(90)
  @Type(() => Number)
  ymax: number;

  @IsOptional()
  @IsNumber()
  @Type(() => Number)
  zoom?: number;
}
