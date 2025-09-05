import { ApiProperty } from '@nestjs/swagger';
import { PM25Period } from 'src/types';

class AveragesData {
  @ApiProperty({
    description: 'Average measurement value for last 6 hours',
    example: 12.4,
    nullable: true,
  })
  [PM25Period.HOURS_6]: number | null;

  @ApiProperty({
    description: 'Average measurement value for last 24 hours',
    example: 14.2,
    nullable: true,
  })
  [PM25Period.HOURS_24]: number | null;

  @ApiProperty({
    description: 'Average measurement value for last 7 days',
    example: 16.8,
    nullable: true,
  })
  [PM25Period.DAYS_7]: number | null;

  @ApiProperty({
    description: 'Average measurement value for last 30 days',
    example: 18.3,
    nullable: true,
  })
  [PM25Period.DAYS_30]: number | null;

  @ApiProperty({
    description: 'Average measurement value for last 90 days',
    example: 20.1,
    nullable: true,
  })
  [PM25Period.DAYS_90]: number | null;
}

export class PM25AveragesDto {
  @ApiProperty({
    description: 'Location identifier',
    example: 13226,
  })
  locationId: number;

  @ApiProperty({
    description: 'Average measurement values for different time periods',
    type: AveragesData,
  })
  averages: AveragesData;

  constructor(data: { locationId: number; averages: Record<PM25Period, number | null> }) {
    this.locationId = data.locationId;
    this.averages = data.averages;
  }
}
