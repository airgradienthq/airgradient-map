import { IsNumber, IsOptional } from 'class-validator';
import { Type } from 'class-transformer';
import { ApiProperty } from '@nestjs/swagger';

export default class ClusterQuery {
  @ApiProperty({
    description: 'Minimum number of location to form a cluster',
    example: 2,
    required: false,
    type: Number,
  })
  @IsOptional()
  @IsNumber()
  @Type(() => Number)
  minPoints?: number;

  @ApiProperty({
    description: 'Cluster radius, in pixels',
    example: 80,
    required: false,
    type: Number,
  })
  @IsOptional()
  @IsNumber()
  @Type(() => Number)
  radius?: number;

  @ApiProperty({
    description: 'Maximum zoom level at which clusters are generated',
    example: 8,
    required: false,
    type: Number,
  })
  @IsOptional()
  @IsNumber()
  @Type(() => Number)
  maxZoom?: number;
}
