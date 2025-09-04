/**
 * Measurement cluster type definitions
 */

import { SuperclusterResult } from '../shared/geojson.types';

/**
 * Input type for MeasurementCluster constructor
 */
export type ClusterInput = SuperclusterResult;

/**
 * Processed cluster data for API response
 */
export interface ProcessedCluster {
  id?: number;
  locationId?: number;
  locationName?: string;
  latitude: number;
  longitude: number;
  count?: number;
  value?: number | null;
  sensorType?: string;
  dataSource?: string;
  isCluster: boolean;
}