/**
 * Constants for NASA FIRMS fire data processing
 */

/**
 * FIRMS API Configuration
 */
export const FIRMS_API = {
  BASE_URL: 'https://firms.modaps.eosdis.nasa.gov/api/area/csv',
  DEFAULT_DATA_SOURCE: 'VIIRS_NOAA20_NRT',
  DEFAULT_DAY_RANGE: 1,
  REQUEST_TIMEOUT: 30000, // 30 seconds
} as const;

/**
 * Database Configuration
 */
export const FIRES_DATABASE = {
  BATCH_SIZE: 1000,       // Records per batch insert
} as const;

/**
 * Query Defaults
 */
export const FIRES_QUERY = {
  DEFAULT_HOURS: 48,      // Default time range for queries
  SPATIAL_BUFFER: 0.5,    // Buffer around bbox (degrees)
} as const;
