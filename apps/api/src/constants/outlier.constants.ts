/*
╒═══════╤══════════════════════════╕
│     z │   Within ±z = P(|Z| ≤ z) │
╞═══════╪══════════════════════════╡
│   0.5 │                  38.29%  │
│     1 │                  68.27%  │
│  1.28 │                  79.95%  │
│ 1.645 │                  90.00%  │
│  1.96 │                  95.00%  │
│     2 │                  95.45%  │
│  2.33 │                  98.02%  │
│ 2.576 │                  99.00%  │
│     3 │                  99.73%  │
╘═══════╧══════════════════════════╛
*/

export const OUTLIER_CONFIG = {
  // Share
  MEASURED_AT_INTERVAL_HOURS: 2,
  ABSOLUTE_THRESHOLD: 30, // for PM2.5 < 50 µg/m3

  // First circle
  RADIUS_METERS: 10000,
  Z_SCORE_THRESHOLD: 3,
  MIN_NEARBY_COUNT: 3, // Including itself

  // Second circle
  MAX_DYNAMIC_RADIUS_METERS_SECOND_CIRCLE: 500000,
  Z_SCORE_THRESHOLD_SECOND_CIRCLE: 2,
  MIN_NEARBY_COUNT_SECOND_CIRCLE: 10, // Including itself
} as const;
