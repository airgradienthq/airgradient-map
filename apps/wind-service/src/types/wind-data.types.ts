/**
 * Wind data types for database operations
 */

export interface WindDataRecord {
  longitude: number;
  latitude: number;
  forecast_time: Date;
  u_component: number;
  v_component: number;
}

export interface WindDataInsertResult {
  recordCount: number;
  success: boolean;
  error?: string;
}
