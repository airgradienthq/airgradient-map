import { ApiProperty } from '@nestjs/swagger';

export class MeasurementAveragesDto {
  @ApiProperty({
    description: 'Location identifier',
    example: 13226,
  })
  locationId: number;

  @ApiProperty({
    description: 'Average measurement values for requested time periods. Keys are period strings (e.g., "6h", "24h", "7d", "13d"), values are the averages or null if insufficient data.',
    example: {
      "6h": 12.4,
      "24h": 14.2,
      "7d": 16.8,
      "13d": 18.1,
      "30d": 18.3
    },
    additionalProperties: {
      type: 'number',
      nullable: true
    }
  })
  averages: Record<string, number | null>;

  constructor(data: { locationId: number; averages: Record<string, number | null> }) {
    this.locationId = data.locationId;
    this.averages = data.averages;
  }
}
