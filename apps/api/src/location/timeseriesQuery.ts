import { ApiProperty } from '@nestjs/swagger';
import { IsString, IsIn } from 'class-validator';
import { BucketSize } from 'src/utils/timeSeriesBucket';

class TimeseriesQuery {
  @ApiProperty({
    default: '2025-08-30T20:01:00.000Z',
    description: 'End date in ISO 8601 format with timezone (eg. 2025-08-30T20:01:00-04:00)',
  })
  @IsString()
  start: string;

  @ApiProperty({
    default: '2025-08-30T20:01:00.000Z',
    description: 'End date in ISO 8601 format with timezone (eg. 2025-08-30T20:01:00-04:00)',
  })
  @IsString()
  end: string;

  @ApiProperty({ enum: BucketSize, required: true })
  @IsString()
  @IsIn(
    [
      BucketSize.FifteenMinutes,
      BucketSize.OneHour,
      BucketSize.EightHours,
      BucketSize.OneDay,
    ],
    { message: 'Invalid measure parameter' },
  )
  bucketSize: string;
}
export default TimeseriesQuery;
