import { IsNumber, Min } from 'class-validator';
import { Transform } from 'class-transformer';
import { ApiProperty } from '@nestjs/swagger';

class FindOneParams {
  @ApiProperty()
  @IsNumber()
  @Min(1, { message: 'Location ID must be a positive integer' })
  @Transform(({ value }) => Number(value))
  id: number;
}

export default FindOneParams;
