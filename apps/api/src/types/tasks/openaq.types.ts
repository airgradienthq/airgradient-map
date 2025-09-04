/**
 * OpenAQ API response type definitions
 */

/**
 * Attribution information from OpenAQ API
 */
export interface Attribution {
  name: string;
  url?: string;
}

/**
 * Country information from OpenAQ API
 */
export interface Country {
  id: number;
  code: string;
  name: string;
}

/**
 * Instrument information from OpenAQ API
 */
export interface Instrument {
  id: number;
  name: string;
  manufacturer?: string;
  model?: string;
  serialNumber?: string;
}

/**
 * OpenAQ latest measurement data structure for database insertion
 */
export interface OpenAQLatestData {
  locationId: number;
  pm25: number;
  measuredAt: string;
}

/**
 * Full OpenAQ API measurement response
 */
export interface OpenAQMeasurementResponse {
  locationId: number;
  location: string;
  parameter: string;
  value: number;
  date: {
    utc: string;
    local: string;
  };
  unit: string;
  coordinates: {
    latitude: number;
    longitude: number;
  };
  country: string;
  city?: string;
  isMobile?: boolean;
  isAnalysis?: boolean;
  entity?: string;
  sensorType?: string;
}

/**
 * OpenAQ API response metadata
 */
export interface OpenAQMeta {
  name: string;
  website: string;
  page: number;
  limit: number;
  found: number | string;
}

/**
 * Full OpenAQ API response structure
 */
export interface OpenAQResponse<T> {
  meta: OpenAQMeta;
  results: T[];
}
