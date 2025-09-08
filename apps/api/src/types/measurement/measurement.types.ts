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
 */
export type MeasureType = 'pm25' | 'pm10' | 'atmp' | 'rhum' | 'rco2' | 'o3' | 'no2';

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
