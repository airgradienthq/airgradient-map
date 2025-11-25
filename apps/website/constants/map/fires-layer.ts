/**
 * Fire confidence color scale
 * Colors based on confidence levels from NASA FIRMS data
 */
export const FIRE_CONFIDENCE_COLORS = {
  low: '#FFA726',      // Orange
  nominal: '#FF7043',  // Deep orange
  high: '#D32F2F',     // Red
} as const;

/**
 * Fire size scale based on FRP (Fire Radiative Power in megawatts)
 * Larger FRP = larger/more intense fire = larger marker
 */
export const FIRE_SIZE_SCALE = {
  min: 6,              // Minimum marker radius (px)
  max: 20,             // Maximum marker radius (px)
  frpThresholds: {
    small: 10,         // FRP < 10 MW
    medium: 50,        // FRP 10-50 MW
    large: 100,        // FRP 50-100 MW
    extreme: 100,      // FRP > 100 MW
  }
} as const;

/**
 * Query defaults for fires data
 */
export const FIRES_QUERY_DEFAULTS = {
  hours: 48,           // Match backend default (48 hours)
  timeout: 30000,      // API request timeout (30 seconds)
} as const;

/**
 * Calculate marker size based on Fire Radiative Power
 * @param frp Fire Radiative Power in megawatts
 * @returns radius in pixels
 */
export function calculateFireMarkerSize(frp: number): number {
  const { min, max, frpThresholds } = FIRE_SIZE_SCALE;

  if (frp < frpThresholds.small) {
    return min;
  } else if (frp < frpThresholds.medium) {
    // Linear interpolation between min and mid
    const mid = (min + max) / 2;
    const ratio = (frp - frpThresholds.small) / (frpThresholds.medium - frpThresholds.small);
    return min + (mid - min) * ratio;
  } else if (frp < frpThresholds.large) {
    // Linear interpolation between mid and max
    const mid = (min + max) / 2;
    const ratio = (frp - frpThresholds.medium) / (frpThresholds.large - frpThresholds.medium);
    return mid + (max - mid) * ratio;
  } else {
    return max;
  }
}
