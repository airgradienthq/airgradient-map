/**
 * NASA FIRMS Fire Data Models
 * Type definitions for wildfire detection data from VIIRS satellites
 */

/**
 * Confidence levels for fire detection
 */
export enum FireConfidence {
  LOW = 'low',
  NOMINAL = 'nominal',
  HIGH = 'high',
}

/**
 * Day/Night detection flag
 */
export enum FireDayNight {
  DAY = 'D',
  NIGHT = 'N',
}

/**
 * Satellite codes
 */
export enum FireSatellite {
  SNPP = 'N',       // Suomi NPP
  NOAA20 = 'N20',   // NOAA-20
  NOAA21 = 'N21',   // NOAA-21
}

/**
 * Raw fire record from FIRMS API CSV
 * Used during data sync process
 */
export interface FirmsFireRecord {
  latitude: number;
  longitude: number;
  bright_ti4: number;     // I-4 channel brightness temperature (Kelvin)
  bright_ti5: number;     // I-5 channel brightness temperature (Kelvin)
  scan: number;           // Along-scan pixel size (meters)
  track: number;          // Along-track pixel size (meters)
  acq_date: string;       // Acquisition date (YYYY-MM-DD)
  acq_time: string;       // Acquisition time (HHMM UTC)
  satellite: string;      // Satellite code
  confidence: string;     // Confidence level
  version: string;        // Data version (e.g., "2.0NRT")
  frp: number;            // Fire Radiative Power (megawatts)
  daynight: string;       // Day/Night flag
}

/**
 * Fire record from database
 * Used when querying and returning data
 */
export interface FireRecord {
  latitude: number;
  longitude: number;
  acq_date: string;
  acq_time: string;
  confidence: string;
  frp: number;
  bright_ti4: number;
  bright_ti5: number;
  satellite: string;
  daynight: string;
  scan: number;
  track: number;
  version: string;
}

/**
 * GeoJSON Feature for a single fire detection
 */
export interface FireFeature {
  type: 'Feature';
  geometry: {
    type: 'Point';
    coordinates: [number, number]; // [longitude, latitude]
  };
  properties: FireRecord;
}
