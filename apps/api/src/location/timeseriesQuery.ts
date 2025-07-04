import { ApiProperty } from '@nestjs/swagger';
import { IsString, Matches } from 'class-validator';

class TimeseriesQuery {
  @ApiProperty({
    default: '2025-02-01 00:00',
    description: 'Start date in format "YYYY-MM-DD" or "YYYY-MM-DD HH:MM"',
  })
  @IsString()
  @Matches(/^\d{4}-\d{2}-\d{2}( \d{2}:\d{2})?$/, {
    message: 'Date must be in format YYYY-MM-DD or YYYY-MM-DD HH:MM',
  })
  start: string;

  @ApiProperty({
    default: '2025-02-07 00:00',
    description: 'End date in format "YYYY-MM-DD" or "YYYY-MM-DD HH:MM"',
  })
  @IsString()
  @Matches(/^\d{4}-\d{2}-\d{2}( \d{2}:\d{2})?$/, {
    message: 'Date must be in format YYYY-MM-DD or YYYY-MM-DD HH:MM',
  })
  end: string;

  @ApiProperty({
    default: '1 D',
    description: 'Bucket size in ISO 8601 duration format (e.g., "5 M", "1 H", "1 D")',
  })
  @IsString()
  bucketSize: string;
}
export default TimeseriesQuery;
