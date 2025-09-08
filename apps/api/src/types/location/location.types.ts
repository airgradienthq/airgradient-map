/**
 * Location service type definitions
 */

import { LocationEntity } from '../../location/location.entity';
import { PaginatedResult } from '../shared/database.types';

/**
 * Result type for getLocations service method
 */
export type LocationServiceResult = LocationEntity[];

/**
 * Paginated locations result
 */
export type PaginatedLocationsResult = PaginatedResult<LocationEntity>;

/**
 * Result type for getLocationById service method
 */
export type LocationByIdResult = LocationEntity | null;

/**
 * Location last measures raw database result
 */
export interface LocationMeasuresRaw {
  locationId: number;
  locationName: string;
  pm25?: number;
  pm10?: number;
  atmp?: number;
  rhum?: number;
  rco2?: number;
  o3?: number;
  no2?: number;
  measuredAt: Date;
  sensorType: string;
  dataSource: string;
}

/**
 * Result type for getLocationLastMeasures service method
 */
export type LocationMeasuresResult = LocationMeasuresRaw | null;

/**
 * Result type for getCigarettesSmoked service method
 */
export type CigarettesSmokedResult = Record<string, number>;
