/**
 * GeoJSON type definitions for measurement data
 */

import { Feature, Point, FeatureCollection } from 'geojson';

/**
 * Properties for measurement GeoJSON features
 */
export interface MeasurementGeoJSONProperties {
  locationId: number;
  locationName: string;
  sensorType: string;
  dataSource: string;
  value: number | null;
}

/**
 * Single measurement as a GeoJSON feature
 */
export type MeasurementGeoJSONFeature = Feature<Point, MeasurementGeoJSONProperties>;

/**
 * Collection of measurement features
 */
export type MeasurementFeatureCollection = FeatureCollection<Point, MeasurementGeoJSONProperties>;

/**
 * Cluster properties for supercluster results
 */
export interface ClusterProperties {
  cluster: boolean;
  cluster_id?: number;
  point_count?: number;
  point_count_abbreviated?: number;
  sum?: number;
}

/**
 * Supercluster result can be either a cluster or an individual point
 */
export type SuperclusterResult = Feature<Point, ClusterProperties | MeasurementGeoJSONProperties>;
