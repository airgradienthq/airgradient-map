/**
 * DTOs for external batch measurements API integration
 */

/**
 * Request to fetch measurements for multiple locations grouped by place
 */
export interface BatchMeasurementsRequestDto {
  locations: PlaceLocationRequest[];
}

export interface PlaceLocationRequest {
  place_id: number;
  location_ids: number[];
}

/**
 * Response from batch measurements API
 */
export interface BatchMeasurementsResponseDto {
  success: boolean;
  data: LocationMeasurementDto[];
  errors: MeasurementErrorDto[];
  message?: string; // Present when success is false
}

export interface LocationMeasurementDto {
  place_id: number;
  location_id: number;
  location_name: string;
  timestamp: string; // ISO 8601 format
  measurements: MeasurementValuesDto;
}

export interface MeasurementValuesDto {
  pm25: number | null;
  rco2: number | null;
  tvoc_index: number | null;
  nox_index: number | null;
  atmp: number | null;
  rhum: number | null;
}

export interface MeasurementErrorDto {
  place_id: number;
  location_id: number;
  error: string;
}
