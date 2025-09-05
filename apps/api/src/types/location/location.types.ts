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

/**
 * PM2.5 time periods for averaging
 */
export enum PM25Period {
  HOURS_6 = '6h',
  HOURS_24 = '24h',
  DAYS_7 = '7d',
  DAYS_30 = '30d',
  DAYS_90 = '90d',
}

/**
 * Configuration for PM2.5 periods
 */
export const PM25PeriodConfig = {
  [PM25Period.HOURS_6]: {
    interval: '6 hours',
    label: 'pm25_6h',
    order: 1,
  },
  [PM25Period.HOURS_24]: {
    interval: '24 hours',
    label: 'pm25_24h',
    order: 2,
  },
  [PM25Period.DAYS_7]: {
    interval: '7 days',
    label: 'pm25_7d',
    order: 3,
  },
  [PM25Period.DAYS_30]: {
    interval: '30 days',
    label: 'pm25_30d',
    order: 4,
  },
  [PM25Period.DAYS_90]: {
    interval: '90 days',
    label: 'pm25_90d',
    order: 5,
  },
} as const;

/**
 * Result type for PM2.5 averages
 */
export interface PM25AveragesResult {
  locationId: number;
  averages: Record<PM25Period, number | null>;
}
