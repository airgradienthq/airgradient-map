import { ApiProperty } from '@nestjs/swagger';
import { IsString } from 'class-validator';

class TimeseriesQuery {
  // TODO format with timezone
  @ApiProperty({
    default: '2025-02-01 00:00',
    description: 'Start date in format "YYYY-MM-DD" or "YYYY-MM-DD HH:MM"',
  })
  @IsString()
  start: string;

  // TODO format with timezone and explain if timezone not included, might inconsistent
  @ApiProperty({
    default: '2025-02-07 00:00',
    description: 'End date in format "YYYY-MM-DD" or "YYYY-MM-DD HH:MM"',
  })
  @IsString()
  end: string;

  // TODO: follow format of bucketSize enum and validate
  @ApiProperty({
    default: '1 D',
    description: 'Bucket size in ISO 8601 duration format (e.g., "5 M", "1 H", "1 D")',
  })
  @IsString()
  bucketSize: string;
}
export default TimeseriesQuery;
