import { IsEnum, IsOptional } from 'class-validator';
import { Transform } from 'class-transformer';
import { ApiProperty } from '@nestjs/swagger';
import { MeasureType } from '../types';

class MeasureTypeQuery {
  @ApiProperty({
    enum: MeasureType,
    required: false,
    description: 'Type of measurement to filter by',
  })
  @IsOptional()
  @IsEnum(MeasureType, { message: 'Invalid measure parameter' })
  @Transform(({ value }) => value?.toLowerCase())
  measure?: MeasureType;
}

export default MeasureTypeQuery;
