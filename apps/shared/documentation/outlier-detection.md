# PM2.5 Outlier Detection - Current Implementation

This document describes the *current* PM2.5 outlier detection implementation across the API and Website. It is intended as a stable reference for iterating on the algorithm and the debug dashboard.

## Goals

Detect air-quality monitors that are likely reporting *wrong* PM2.5 data (e.g. stuck sensors, permanent zeros, implausible highs) while handling:

- Highly uneven monitor density (dense in US/EU, sparse elsewhere)
- Map clustering (clusters can be contaminated by bad sensors)
- Multiple data sources (neighbors may be from different providers)
- Explainability (users need to understand *why* a location is flagged)

## System Overview

There are two “modes” of outlier handling:

1. **Stored outlier flag** (default production path)
   - Outlier status is computed during ingestion and stored in `measurement.is_pm25_outlier`.
   - API queries can filter stored outliers directly in SQL.

2. **Dynamic (runtime) outlier filtering** (debug/tuning path)
   - The same outlier algorithm is run on the fly for the current viewport using query-param overrides.
   - Used by the dashboard to tune parameters and to visualize “hidden” sensors.

High-level architecture:

```
┌──────────────────────────────┐
│  Ingestion (cron/tasks)       │
│  • Fetch latest measurements  │
│  • Compute is_pm25_outlier    │
│  • INSERT measurement rows    │
└──────────────┬───────────────┘
               │ stored flag
               ▼
┌──────────────────────────────┐
│ PostgreSQL                    │
│ • measurement.is_pm25_outlier │
└──────────────┬───────────────┘
               │
               ▼
┌───────────────────────────────────────────┐
│ API (NestJS)                               │
│ • GET /measurements/current/cluster        │
│   - default: filter by stored flag         │
│   - debug: dynamic filtering w/ overrides  │
│ • GET /locations/:id/outliers/pm25/explain │
│   - explain “why outlier/not”              │
└──────────────┬────────────────────────────┘
               │
               ▼
┌───────────────────────────────────────────┐
│ Website (Nuxt + Leaflet)                   │
│ • Outlier Sensitivity panel (debug)        │
│ • “Show hidden only” + viewport stats      │
│ • Marker click → outlier explanation UI    │
└───────────────────────────────────────────┘
```

## Inputs and Time Assumptions

### “Current” measurements on the map

The map uses “current” measurements defined as:

- For each location, the **latest** `measurement` row
- Restricted to `measured_at >= NOW() - INTERVAL '6 hours'`

This means:

- A sensor that stopped reporting more than ~6 hours ago will not appear on the “current” map.
- Outlier detection for the map runs on this “current” set (plus the spatial/temporal lookups described below).

### Raw vs corrected PM2.5

The outlier algorithm uses **raw** PM2.5 values stored in the database (`measurement.pm25`).

The website may display **EPA-corrected** PM2.5 (depending on data source + RH) for visualization, but the *outlier decision* currently does not use the corrected values.

## Where the Logic Lives

**Core algorithm**

- `apps/api/src/outlier/outlier.service.ts`
  - `calculateBatchIsPm25Outlier(...)` (batch decisions for map filtering and ingestion)
  - `explainPm25Outlier(...)` (single-location explanation payload)
- `apps/api/src/outlier/outlier.repository.ts`
  - Batch “same-value / near-flatline” check
  - Batch spatial neighborhood statistics query

**Dynamic map filtering**

- `apps/api/src/measurement/measurement.service.ts`
  - `filterOutliersWithOverrides(...)`
  - Decides when to use stored-flag SQL filtering vs dynamic filtering
- `apps/api/src/measurement/outlierRealtimeQuery.ts`
  - Validated query DTO for all tunable parameters

**Explanation endpoint**

- `apps/api/src/location/location.controller.ts`
  - `GET /locations/:id/outliers/pm25/explain`
- `apps/api/src/location/location.service.ts`
  - Collects latest PM2.5 + calls `OutlierService.explainPm25Outlier(...)`

**Website debug dashboard**

- `apps/website/components/map/Map.vue`
  - Outlier Sensitivity panel + tooltips
  - Toggle “Show hidden only”
  - Viewport counts (visible/hidden/total)
  - Sends override parameters to the API
- `apps/website/components/dialogs/LocationHistoryDialog.vue`
  - Fetches and displays “why outlier/not” details on marker click

## Data Model Notes

The stored flag is on the measurement table:

- `measurement.is_pm25_outlier` (boolean)

This is used in two distinct ways:

1. **Filtering returned points** (SQL filtering when dynamic overrides are not used)
2. **Optionally filtering neighbors** (when building the spatial baseline, to avoid contaminated baselines)

## Stored Outlier Flag Computation (Ingestion)

During ingestion (cron/tasks), the API computes outliers for incoming “latest measurements” before inserting them:

- Uses `OutlierService.calculateBatchIsPm25Outlier(...)` with **default config**
- Persists the result to `measurement.is_pm25_outlier`

Important default behavior:

- The ingestion path does not pass dynamic options, so `useStoredOutlierFlagForNeighbors` defaults to **true** inside `OutlierService`.
  - This attempts to keep neighbor baselines clean when stored flags already exist.

## Dynamic vs Stored Filtering Behavior

The API endpoint powering the map is:

- `GET /map/api/v1/measurements/current/cluster`

### Stored-flag filtering (default)

If `excludeOutliers=true` and *no* dynamic outlier parameters are provided:

- SQL filters out outliers using `measurement.is_pm25_outlier`
- Clustering happens on the already-filtered set

### Dynamic filtering (when dashboard overrides are provided)

If `excludeOutliers=true` (or `outliersOnly=true`) and any dynamic parameters are provided, the API:

1. Fetches latest measurements for the viewport *without* filtering by stored outlier flags.
2. Runs the outlier algorithm in the application layer for those points.
3. Filters results based on computed outlier status:
   - `excludeOutliers=true` → keep inliers
   - `outliersOnly=true` → keep outliers
4. Runs clustering on the filtered set.

This is what enables parameter tuning without requiring re-ingestion.

## Outlier Algorithm: Decision Flow

The algorithm evaluates each PM2.5 datapoint and returns `isOutlier = true/false`.

It is intentionally structured as a *small set of interpretable checks*:

1. **Hard max check** (optional safety net)
2. **Same-value / near-flatline check** (temporal lookback on the same sensor)
3. **Spatial neighborhood check** (compare against nearby monitors at the same time)

Final decision:

```
isOutlier =
  hardMaxIsOutlier
  OR sameValueIsOutlier
  OR spatialIsOutlier
```

### 1) Hard Max Check (Optional)

Purpose: Catch extreme values in sparse areas where spatial checks may have insufficient neighbors.

- Enabled if `pm25HardMax > 0`
- Triggers if `pm25 >= pm25HardMax`
- This check short-circuits the decision in the batch path.

Default: disabled (`PM25_HARD_MAX = 0`).

### 2) Same-Value / Near-Flatline Check (Temporal)

Purpose: Detect sensors that are “stuck” or near-stuck (including permanent zeros if desired).

Implementation:

- Runs as a batch SQL query via `getBatchSameValue24hCheck(...)` (name kept for historical reasons).
- For each input `(reference_id, measured_at, pm25)`:
  - Look back over `[measured_at - windowHours, measured_at)` (**lookback only**, no future values).
  - Require `COUNT(*) >= sameValueMinCount`.
  - If enabled, flag when all values in the window stay within a tolerance band:
    - `MIN(pm25) >= currentPm25 - tolerance`
    - `MAX(pm25) <= currentPm25 + tolerance`

Gating:

- If `sameValueIncludeZero=false`, the check is skipped for `pm25 == 0`.
- If `sameValueMinValue > 0`, the check is skipped for `0 < pm25 < sameValueMinValue`
  - This helps avoid false positives at very low PM where quantization/rounding can dominate.

Notes:

- `tolerance = 0` means exact same-value behavior (classic “stuck at X”).
- Non-zero tolerance enables “near flatline” detection (e.g., fluctuating within ±1–2 µg/m³).

### 3) Spatial Neighborhood Check

Purpose: Detect sensors whose value deviates strongly from nearby monitors at the same time.

This check consists of:

1. Fetch neighborhood statistics in SQL (batch)
2. Apply robust statistics + sparsity-aware thresholds in the service layer

#### 3.1 Neighborhood query (SQL)

For each `(reference_id, measured_at)` input, the query:

1. Finds nearby locations `l2` within `radiusMeters` using PostGIS:
   - `ST_DWithin(l1.coordinate::geography, l2.coordinate::geography, radiusMeters)`
2. Excludes the sensor itself from its neighborhood:
   - `l2.id <> l1.id`
3. For each neighbor location, picks the *closest-in-time* measurement within ±`measuredAtIntervalHours`:
   - `DISTINCT ON (l2.id)` ordered by absolute time delta
4. Optionally excludes neighbor rows where `measurement.is_pm25_outlier` is stored as true:
   - `COALESCE(m.is_pm25_outlier, false) = false`
5. Computes summary stats over the resulting neighbor PM2.5 values:
   - `mean`, `stddev_samp`, `count`
   - robust quantiles: `p25`, `median`, `p75` using `percentile_cont`

Cross-data-source behavior:

- The query uses the `dataSource` only to uniquely identify the *target* (`l1`) by `reference_id`.
- The neighborhood (`l2`) is not constrained to a specific data source (neighbors can be from any source).

#### 3.2 Radius expansion (sparse neighborhoods)

Sparse regions may have no neighbors at the initial radius. To reduce false negatives:

- Primary radius: `radiusMeters`
- Fallback radius: `min(radiusMeters * 5, 300_000)`
- Final fallback radius: `300_000` meters (300 km)

The algorithm uses the first radius that yields usable stats (`count > 0` and `mean != null`).

#### 3.3 Robust baseline + sparsity-aware thresholds

Definitions:

- `neighborCount`: number of distinct neighbor locations with a usable measurement
- `center`:
  - `median` if quantiles are available
  - otherwise `mean`
- `scale`:
  - robust scale from IQR if available: `IQR / 1.349`
    - where `IQR = p75 - p25`
  - otherwise fallback to `stddev` (if > 0)

Sparsity factor (more conservative with fewer neighbors):

```
sparsityFactor =
  neighborCount >= minNearbyCount ? 1 : sqrt(minNearbyCount / neighborCount)
```

Mode selection:

1. **Z-score mode** (for higher-PM neighborhoods):
   - If `center >= zScoreMinMean` and `scale > 0`
   - Compute `z = (pm25 - center) / scale`
   - Threshold: `zScoreThreshold * sparsityFactor`
   - Outlier if `|z| > threshold`

2. **Absolute-delta mode** (for lower-PM neighborhoods):
   - Else compute `absDelta = |pm25 - center|`
   - Use a variance-aware threshold to avoid false positives in high-variance neighborhoods:
     - `base = max(absoluteThreshold, zScoreThreshold * scale)` (when `scale` is available)
     - otherwise `base = absoluteThreshold`
   - Threshold: `base * sparsityFactor`
   - Outlier if `absDelta > threshold`

Minimum neighbor requirement:

- The spatial check requires at least `1` neighbor to produce a verdict.
- In the batch path, “no neighbors” results in `false` (not outlier).
- In the explanation path, “no neighbors” returns a structured `insufficient_neighbors` decision reason.

## Parameters

Parameters can come from:

1. Environment/constant defaults (`OUTLIER_CONFIG`)
2. API query overrides (`OutlierRealtimeQuery`)
3. Website debug panel sliders (persisted via localStorage)

### Environment/config defaults

The API loads defaults from `OUTLIER_CONFIG` and allows overriding them via environment variables through Nest `ConfigService`:

| Env var | Default constant | Notes |
|---|---|---|
| `RADIUS_METERS` | `RADIUS_METERS` | Spatial radius for baseline stats. |
| `MEASURED_AT_INTERVAL_HOURS` | `MEASURED_AT_INTERVAL_HOURS` | ± hours to match neighbor measurements. |
| `MIN_NEARBY_COUNT` | `MIN_NEARBY_COUNT` | Target neighbor count (excluding self). |
| `Z_SCORE_THRESHOLD` | `Z_SCORE_THRESHOLD` | Sensitivity in Z-score mode. |
| `ABSOLUTE_THRESHOLD` | `ABSOLUTE_THRESHOLD` | Sensitivity in absolute mode. |
| `Z_SCORE_MIN_MEAN` | `Z_SCORE_MIN_MEAN` | Switch point between Z-score vs absolute modes. |
| `ENABLE_SAME_VALUE_CHECK` | `ENABLE_SAME_VALUE_CHECK` | Enables the temporal flatline check. |
| `SAME_VALUE_WINDOW_HOURS` | `SAME_VALUE_WINDOW_HOURS` | Lookback window size. |
| `SAME_VALUE_MIN_COUNT` | `SAME_VALUE_MIN_COUNT` | Minimum measurements in the lookback window. |
| `SAME_VALUE_INCLUDE_ZERO` | `SAME_VALUE_INCLUDE_ZERO` | Whether to allow `pm25=0` flatlines to be flagged. |
| `SAME_VALUE_TOLERANCE` | `SAME_VALUE_TOLERANCE` | ± band for near-flatline detection. |
| `SAME_VALUE_MIN_VALUE` | `SAME_VALUE_MIN_VALUE` | Skip flatline detection below this PM (except zero). |
| `PM25_HARD_MAX` | `PM25_HARD_MAX` | Optional “always outlier above X” cutoff. |

### Core parameters (spatial)

| Parameter | API query name | Meaning |
|---|---|---|
| Radius (meters) | `outlierRadiusMeters` | Neighbor search radius for spatial baseline. |
| Time window (hours) | `outlierWindowHours` | ± hours around `measured_at` to match neighbor measurements. |
| Min nearby (target) | `outlierMinNearby` | Target neighbor count; fewer neighbors increases thresholds. |
| Z-score threshold | `outlierZScoreThreshold` | Sensitivity in Z-score mode. |
| Absolute threshold (µg/m³) | `outlierAbsoluteThreshold` | Sensitivity in absolute mode at low PM. |
| Z-score min mean (µg/m³) | `outlierZScoreMinMean` | Switch point between Z-score vs absolute modes. |
| Filter outlier neighbors | `outlierUseStoredOutlierFlagForNeighbors` | Exclude neighbors using stored outlier flags (may reduce neighbor count). |

### Temporal parameters (flatline)

| Parameter | API query name | Meaning |
|---|---|---|
| Enable flatline check | `outlierEnableSameValueCheck` | Enables the temporal “same-value / near-flatline” check. |
| Flatline window (hours) | `outlierSameValueWindowHours` | Lookback window size. |
| Flatline min count | `outlierSameValueMinCount` | Minimum measurements needed within the lookback window. |
| Flatline tolerance (µg/m³) | `outlierSameValueTolerance` | Allowed ± drift from current PM. 0 = exact. |
| Flatline min value (µg/m³) | `outlierSameValueMinValue` | Skip flatline check for low PM (except `pm25=0` when include-zero applies). |
| Include zeros | `outlierSameValueIncludeZero` | Allows `pm25=0` flatlines to be flagged. |

### Safety parameter

| Parameter | API query name | Meaning |
|---|---|---|
| Hard max (µg/m³) | `outlierPm25HardMax` | If >0, `pm25 >= hardMax` is always outlier. Default off. |

### Debug-only query parameter

| Parameter | API query name | Meaning |
|---|---|---|
| Outliers only | `outliersOnly` | Returns only outliers (used for “Show hidden only”). |

## Explainability: “Why was this labeled outlier?”

Endpoint:

- `GET /map/api/v1/locations/:id/outliers/pm25/explain`

Behavior:

- Uses the latest PM2.5 measurement for that location.
- Accepts the same override query parameters as the map endpoint.
- Returns a structured object including:
  - Input fields (pm25, measuredAt, dataSource, stored flag)
  - Parameters used for this evaluation
  - Check details:
    - Hard max check status
    - Same-value window stats (count, min, max, maxDelta)
    - Spatial baseline stats (mean/stddev, p25/median/p75, thresholds, computed z-score/delta)
  - Notes (e.g. radius expansion, sparsity scaling, robust baseline usage)
  - Final decision reason/message

UI:

- In debug mode (`?debug=true`), clicking a marker opens a dialog that fetches and renders this explanation.

## Debug Dashboard (Website) Usage

The “Outlier Sensitivity” panel is available on the map when:

- PM measure is selected (PM2.5 or PM AQI), and
- outlier filtering is enabled (`excludeOutliers=true`)

Additional debug-only controls (viewport stats, “Show hidden only”, etc.) are enabled when the URL includes:

- `?debug=true`

Key behaviors:

- **Show hidden only** toggles `outliersOnly=true` on the map endpoint to return only sensors flagged as outliers by the *current parameters*.
- **Visible / Hidden / Total monitors** are computed for the current viewport:
  - Visible = monitors returned by the filtered request
  - Total = monitors returned by an unfiltered request (cached by viewport bounds)
  - Hidden = Total - Visible
- Clicking a marker fetches `/locations/:id/outliers/pm25/explain` with the current slider values so you can see *exactly* why it was (not) flagged.

Persistence:

- Slider values are persisted client-side via localStorage keys (through `useStorage(...)` in `Map.vue`).

### Current dashboard defaults (debug panel)

These are the current default slider/toggle values used by the website when there is no existing value in localStorage:

| Control | Default |
|---|---|
| Show hidden only | `true` |
| Radius | `10 km` |
| Time window | `±2 h` |
| Min nearby | `5` |
| Z-score threshold | `3.8` |
| Absolute threshold | `14 µg/m³` |
| Z-score min mean | `60 µg/m³` |
| Filter outlier neighbors | `false` |
| Same-value check | `true` |
| Same-value window | `48 h` |
| Same-value min count | `24` |
| Same-value tolerance | `±0.0 µg/m³` |
| Same-value min value | `9 µg/m³` |
| Same-value include zero | `true` |
| Hard max | `940 µg/m³` |

Note:

- These defaults only affect the debug/tuning UI and are passed as **query overrides** to the API when `?debug=true` is enabled.
- Existing localStorage values take precedence (so changing defaults does not retroactively update users’ stored slider positions).

## Performance Considerations

- The spatial query uses a LATERAL subquery per input point. This is efficient vs N queries, but still costly when the viewport contains many sensors.
- Quantiles are computed via `percentile_cont(...)`. This improves robustness but increases DB work per datapoint.
- Radius expansion can trigger additional spatial queries for points with missing neighborhood stats.

Practical guidance:

- Keep the initial radius and time window as small as possible while maintaining adequate neighbor coverage.
- Use “Filter outlier neighbors” carefully: it can improve robustness but may reduce neighborCount (and cause more radius expansion).

## Known Limitations (Current State)

- No explicit **diurnal-pattern** check yet (e.g., comparing daily cycles to neighbors or to the sensor’s own history).
- No explicit “erratic behavior” detector (e.g., rapid spikes) beyond spatial deviation; real events can look like “outliers”.
- Decisions are based on **raw PM2.5** values (not EPA-corrected values used for display in some places).
- Spatial checks can fail in regions where *all* nearby sensors are biased similarly (shared failure modes or real regional events).

## Suggested Next Improvements (Future Work)

These are not implemented yet, but this document is intended to support them:

1. **Diurnal similarity score**
   - Compare last 24–72h curve to neighborhood median curve
   - Correlation / DTW / phase-aligned similarity
2. **Rate-of-change / spike detector**
   - Flag implausible jumps vs recent history and vs neighbors
3. **Drift detector**
   - Gradual divergence from neighbors over days (CUSUM / trend residuals)
4. **Cluster-robust aggregation**
   - For clusters, compute robust representative values (median-of-sensors) and ignore flagged sensors
5. **Multi-signal validation**
   - Cross-check PM2.5 patterns with RH/temperature (sensor-specific failure modes)

---

## Appendix: Pseudocode

```text
for each point:
  if pm25HardMax > 0 and pm25 >= pm25HardMax:
    outlier
  else if enableSameValueCheck and applicable(point) and windowIsFlat(point, tolerance):
    outlier
  else:
    stats = neighborhoodStats(point, radius) or neighborhoodStats(point, radius*5) or neighborhoodStats(point, 300km)
    if stats.count < 1:
      inlier (batch) / insufficient_neighbors (explain)
    else:
      center = median if available else mean
      scale = IQR/1.349 if available else stddev
      sparsityFactor = 1 if count >= minNearby else sqrt(minNearby/count)

      if center >= zScoreMinMean and scale > 0:
        z = (pm25-center)/scale
        outlier if abs(z) > zScoreThreshold*sparsityFactor
      else:
        base = absoluteThreshold if scale missing else max(absoluteThreshold, zScoreThreshold*scale)
        outlier if abs(pm25-center) > base*sparsityFactor
```
