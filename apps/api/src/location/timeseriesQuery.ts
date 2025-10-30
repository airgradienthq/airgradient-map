import { ApiProperty } from '@nestjs/swagger';
import { IsEnum, IsISO8601 } from 'class-validator';
import { BucketSize } from 'src/utils/timeSeriesBucket';

class TimeseriesQuery {
  @ApiProperty({
    default: '2025-08-30T20:01:00.000Z',
    description: 'Start date in ISO 8601 format with timezone (eg. 2025-08-30T20:01:00-04:00)',
  })
  @IsISO8601()
  start: string;

  @ApiProperty({
    default: '2025-08-30T20:01:00.000Z',
    description: 'End date in ISO 8601 format with timezone (eg. 2025-08-30T20:01:00-04:00)',
  })
  @IsISO8601()
  end: string;

  @ApiProperty({
    enum: BucketSize,
    required: true,
    description: 'Size of bucket for data aggregation. For 1w, weeks always start on Mondays',
  })
  @IsEnum(BucketSize, { message: 'Invalid bucket size parameter' })
  bucketSize: BucketSize;
}
export default TimeseriesQuery;
