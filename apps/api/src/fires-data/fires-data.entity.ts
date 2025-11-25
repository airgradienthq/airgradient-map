import { ApiProperty } from '@nestjs/swagger';

/**
 * Individual fire detection record
 */
export class FireRecord {
  @ApiProperty({
    description: 'Latitude of fire detection',
    example: 37.7749,
  })
  latitude: number;

  @ApiProperty({
    description: 'Longitude of fire detection',
    example: -122.4194,
  })
  longitude: number;

  @ApiProperty({
    description: 'Acquisition date (YYYY-MM-DD)',
    example: '2025-11-24',
  })
  acq_date: string;

  @ApiProperty({
    description: 'Acquisition time (HHMM UTC)',
    example: '1430',
  })
  acq_time: string;

  @ApiProperty({
    description: 'Confidence level',
    enum: ['low', 'nominal', 'high'],
    example: 'high',
  })
  confidence: string;

  @ApiProperty({
    description: 'Fire Radiative Power in megawatts',
    example: 45.2,
  })
  frp: number;

  @ApiProperty({
    description: 'Brightness temperature (I-4 channel) in Kelvin',
    example: 350.5,
  })
  bright_ti4: number;

  @ApiProperty({
    description: 'Brightness temperature (I-5 channel) in Kelvin',
    example: 310.2,
  })
  bright_ti5: number;

  @ApiProperty({
    description: 'Satellite code (N=S-NPP, N20=NOAA-20, N21=NOAA-21)',
    example: 'N20',
  })
  satellite: string;

  @ApiProperty({
    description: 'Day or night detection (D=day, N=night)',
    enum: ['D', 'N'],
    example: 'D',
  })
  daynight: string;

  @ApiProperty({
    description: 'Along-scan pixel size in meters',
    example: 375.0,
  })
  scan: number;

  @ApiProperty({
    description: 'Along-track pixel size in meters',
    example: 375.0,
  })
  track: number;

  @ApiProperty({
    description: 'Data version',
    example: '2.0NRT',
  })
  version: string;
}

/**
 * Complete fires data response as GeoJSON FeatureCollection
 */
export class FiresDataEntity {
  @ApiProperty({
    description: 'GeoJSON type',
    example: 'FeatureCollection',
  })
  type: string;

  @ApiProperty({
    description: 'Array of fire features',
    type: [Object],
  })
  features: Array<{
    type: string;
    geometry: {
      type: string;
      coordinates: [number, number];
    };
    properties: FireRecord;
  }>;

  @ApiProperty({
    description: 'Total count of fires in response',
    example: 1234,
  })
  count: number;

  @ApiProperty({
    description: 'Time range of data in response',
    example: { start: '2025-11-22', end: '2025-11-24' },
  })
  timeRange: {
    start: string;
    end: string;
  };

  constructor(partial: Partial<FiresDataEntity>) {
    Object.assign(this, partial);
  }
}
