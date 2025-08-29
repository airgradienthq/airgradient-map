import { ApiProperty } from '@nestjs/swagger';

export class LocationEntity {
  @ApiProperty()
  locationId: number;

  @ApiProperty()
  locationName: string;

  @ApiProperty()
  latitude: number;

  @ApiProperty()
  longitude: number;

  @ApiProperty()
  ownerId: number;

  @ApiProperty({ description: 'a simple string for comparison' })
  ownerName: string;

  @ApiProperty()
  url: string;

  @ApiProperty({
    description: "differentiate type of the sensor. 'Reference' or 'Small Sensor'",
  })
  sensorType: string;

  @ApiProperty()
  licenses: string[];

  @ApiProperty({ description: 'Entity that provide the sensor data' })
  provider: string;

  @ApiProperty({ description: 'From what platform is the data obtained' })
  dataSource: string;

  @ApiProperty()
  timezone: string;

  constructor(partial: Partial<LocationEntity>) {
    Object.assign(this, partial);
  }
}
