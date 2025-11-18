export const OUTLIER_CONFIG = {
  RADIUS_METERS: 10000,
  MEASURED_AT_INTERVAL_HOURS: 2,
  ABSOLUTE_THRESHOLD: 30, // for PM2.5 < 50 µg/m3
  Z_SCORE_THRESHOLD: 2, // cover around 0.9545 of data in normal distribution
  MIN_NEARBY_COUNT: 3, // Including itself
} as const;
