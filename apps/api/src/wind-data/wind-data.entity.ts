import { ApiProperty } from '@nestjs/swagger';

/**
 * Wind data grid header containing metadata about the wind forecast
 */
export class WindDataHeader {
  @ApiProperty({
    description: 'Forecast reference time (ISO 8601)',
    example: '2025-11-10T06:00:00.000Z',
  })
  forecastTime: string;

  @ApiProperty({
    description: 'Geographic bounds of the wind data grid',
    example: { north: 70, south: 35, west: -10, east: 40 },
  })
  bounds: {
    north: number;
    south: number;
    west: number;
    east: number;
  };

  @ApiProperty({
    description: 'Grid resolution in degrees',
    example: { dx: 0.25, dy: 0.25 },
  })
  resolution: {
    dx: number;
    dy: number;
  };

  @ApiProperty({
    description: 'Grid dimensions (number of points)',
    example: { nx: 200, ny: 140 },
  })
  grid: {
    nx: number;
    ny: number;
  };
}

/**
 * Wind data grid containing U and V components
 */
export class WindDataGrid {
  @ApiProperty({
    description: 'U-component (eastward wind) values in m/s',
    type: [Number],
    example: [1.5, 2.3, 1.8],
  })
  u: number[];

  @ApiProperty({
    description: 'V-component (northward wind) values in m/s',
    type: [Number],
    example: [-0.5, 1.2, 0.8],
  })
  v: number[];
}

/**
 * Complete wind data response in optimized grid format
 */
export class WindDataEntity {
  @ApiProperty({
    description: 'Wind data grid metadata',
    type: WindDataHeader,
  })
  header: WindDataHeader;

  @ApiProperty({
    description: 'Wind data grid values',
    type: WindDataGrid,
  })
  data: WindDataGrid;

  constructor(partial: Partial<WindDataEntity>) {
    Object.assign(this, partial);
  }
}
