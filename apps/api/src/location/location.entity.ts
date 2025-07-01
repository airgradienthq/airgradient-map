import { ApiProperty } from '@nestjs/swagger';

class Location {
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

  @ApiProperty({
    description: 'a full owner name that have better presentation',
  })
  ownerNameDisplay: string;

  @ApiProperty()
  description: string;

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

  constructor(partial: Partial<Location>) {
    Object.assign(this, partial);
  }
}

export default Location;
