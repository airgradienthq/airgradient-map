import { ApiProperty } from '@nestjs/swagger';

class Timeseries {
  @ApiProperty({
    description: 'Timestamp of respective value in ISO 8601 format with timezone UTC',
  })
  timebucket: Date;

  @ApiProperty({
    description: 'measures value based on measure query provided',
  })
  value: number;

  constructor(timebucket: Date, value: number) {
    this.timebucket = timebucket;
    this.value = value;
  }
}

export default Timeseries;
