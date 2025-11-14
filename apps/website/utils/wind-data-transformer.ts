/**
 * Wind data types matching the API response format
 */
export interface WindDataApiResponse {
  header: {
    forecastTime: string;
    bounds: {
      north: number;
      south: number;
      west: number;
      east: number;
    };
    resolution: {
      dx: number;
      dy: number;
    };
    grid: {
      nx: number;
      ny: number;
    };
  };
  data: {
    u: number[];
    v: number[];
  };
}

/**
 * Leaflet-velocity format (grib2json compatible)
 */
export interface LeafletVelocityData {
  header: {
    parameterUnit: string;
    parameterNumber: number;
    parameterNumberName: string;
    dx: number;
    dy: number;
    la1: number;
    la2: number;
    lo1: number;
    lo2: number;
    nx: number;
    ny: number;
    refTime: string;
  };
  data: number[];
}

/**
 * Transforms API wind data format to leaflet-velocity (grib2json) format
 *
 * GRIB2 format specifications:
 * - la1: First latitude (northernmost)
 * - la2: Last latitude (southernmost)
 * - lo1: First longitude (westernmost)
 * - lo2: Last longitude (easternmost)
 * - Data ordering: rows from north to south, columns from west to east
 * - Index formula: index = y * nx + x
 *
 * @param apiData Wind data from API in optimized format
 * @returns Array of 2 objects (U and V components) in grib2json format
 */
export function transformToLeafletVelocityFormat(
  apiData: WindDataApiResponse
): [LeafletVelocityData, LeafletVelocityData] {
  const { header, data } = apiData;

  const baseHeader = {
    discipline: 0,
    parameterCategory: 2,
    parameterCategoryName: 'Momentum',
    parameterUnit: 'm.s-1',
    dx: header.resolution.dx,
    dy: header.resolution.dy,
    la1: header.bounds.north,
    la2: header.bounds.south,
    lo1: header.bounds.west,
    lo2: header.bounds.east,
    nx: header.grid.nx,
    ny: header.grid.ny,
    refTime: header.forecastTime
  };

  const uComponent: LeafletVelocityData = {
    header: {
      ...baseHeader,
      parameterNumber: 2,
      parameterNumberName: 'eastward_wind'
    },
    data: data.u
  };

  const vComponent: LeafletVelocityData = {
    header: {
      ...baseHeader,
      parameterNumber: 3,
      parameterNumberName: 'northward_wind'
    },
    data: data.v
  };

  return [uComponent, vComponent];
}