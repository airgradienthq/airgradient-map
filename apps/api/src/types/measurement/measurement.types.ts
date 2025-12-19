/**
 * Measurement service type definitions
 */

import { MeasurementEntity } from '../../measurement/measurement.entity';
import MeasurementCluster from '../../measurement/measurementCluster.model';
import { PaginatedResult } from '../shared/database.types';

/**
 * Result type for getLastMeasurements service method
 */
export type MeasurementServiceResult = MeasurementEntity[];

/**
 * Paginated measurements result
 */
export type PaginatedMeasurementsResult = PaginatedResult<MeasurementEntity>;

/**
 * Result type for getLastMeasurementsByArea service method
 */
export type MeasurementsByAreaResult = MeasurementEntity[];

/**
 * Result type for getLastMeasurementsByCluster service method
 */
export type MeasurementClusterResult = MeasurementCluster[];

/**
 * Available measurement types for filtering
 * IMPORTANT: Enum Values must match the column name in the database
 */
export enum MeasureType {
  PM25 = 'pm25', // Fine particulate matter (≤2.5µm) in µg/m³
  PM10 = 'pm10', // Coarse particulate matter (≤10µm) in µg/m³
  ATMP = 'atmp', // Ambient temperature in °C
  RHUM = 'rhum', // Relative humidity in %
  RCO2 = 'rco2', // Carbon dioxide in ppm
  O3 = 'o3', // Ozone
  NO2 = 'no2', // Nitrogen dioxide
}

/**
 * Measurement query parameters for area-based searches
 */
export interface MeasurementAreaQuery {
  xMin: number;
  yMin: number;
  xMax: number;
  yMax: number;
  measure?: MeasureType;
}

/**
 * Measurement query parameters for cluster-based searches
 */
export interface MeasurementClusterQuery extends MeasurementAreaQuery {
  zoom: number;
}
