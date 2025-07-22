import { ApiProperty } from '@nestjs/swagger';
import { IsString } from 'class-validator';

class TimeseriesQuery {
  @ApiProperty({
    default: '2025-02-01 00:00',
    description: 'Start date in format "YYYY-MM-DD" or "YYYY-MM-DD HH:MM"',
  })
  @IsString()
  start: string;

  @ApiProperty({
    default: '2025-02-07 00:00',
    description: 'End date in format "YYYY-MM-DD" or "YYYY-MM-DD HH:MM"',
  })
  @IsString()
  end: string;

  @ApiProperty({
    default: '1 D',
    description: 'Bucket size in ISO 8601 duration format (e.g., "5 M", "1 H", "1 D")',
  })
  @IsString()
  bucketSize: string;
}
export default TimeseriesQuery;
