import { BadRequestException, Injectable, Logger } from '@nestjs/common';
import { OutlierRepository } from './outlier.repository';
import { ConfigService } from '@nestjs/config';
import { OUTLIER_CONFIG } from 'src/constants/outlier.constants';

export type OutlierCalculationOptions = {
  radiusMeters?: number;
  measuredAtIntervalHours?: number;
  sameValueWindowHours?: number;
  sameValueMinCount?: number;
  sameValueIncludeZero?: boolean;
  sameValueTolerance?: number;
  sameValueMinValue?: number;
  enableSameValueCheck?: boolean;
  pm25HardMax?: number;
  zScoreMinMean?: number;
  absoluteThreshold?: number;
  zScoreThreshold?: number;
  minNearbyCount?: number;
  useStoredOutlierFlagForNeighbors?: boolean;
};

export type Pm25OutlierExplanation = {
  key: string;
  locationReferenceId: number;
  dataSource: string;
  measuredAt: string;
  pm25: number;
  isOutlier: boolean;
  params: {
    radiusMeters: number;
    measuredAtIntervalHours: number;
    minNearbyCount: number;
    useStoredOutlierFlagForNeighbors: boolean;
    pm25HardMax: number;
    zScoreThreshold: number;
    absoluteThreshold: number;
    zScoreMinMean: number;
    enableSameValueCheck: boolean;
    sameValueWindowHours: number;
    sameValueMinCount: number;
    sameValueIncludeZero: boolean;
    sameValueTolerance: number;
    sameValueMinValue: number;
  };
  checks: {
    hardMax: {
      enabled: boolean;
      max: number;
      isOutlier: boolean;
      note?: string;
    };
    sameValue: {
      enabled: boolean;
      includeZero: boolean;
      tolerance: number;
      minValue: number;
      windowHours: number;
      minCount: number;
      measurementCount: number | null;
      distinctCount: number | null;
      min: number | null;
      max: number | null;
      maxDelta: number | null;
      isOutlier: boolean;
      note?: string;
    };
    spatial: {
      neighborCount: number;
      mean: number | null;
      stddev: number | null;
      p25: number | null;
      median: number | null;
      p75: number | null;
      center: number | null;
      centerType: 'median' | 'mean' | null;
      scale: number | null;
      scaleType: 'iqr' | 'stddev' | null;
      mode: 'zscore' | 'absolute' | null;
      zScore: number | null;
      absoluteDelta: number | null;
      threshold: number | null;
      thresholdType: 'zscore' | 'absolute' | null;
      isOutlier: boolean | null;
      note?: string;
    };
  };
  decision: {
    reason:
      | 'hard_max'
      | 'same_value'
      | 'spatial_zscore'
      | 'spatial_absolute'
      | 'insufficient_neighbors'
      | 'within_threshold';
    message: string;
  };
};

type OutlierDataPoint = {
  locationReferenceId: number;
  pm25: number;
  measuredAt: string;
  dataSource?: string;
};

@Injectable()
export class OutlierService {
  private readonly logger = new Logger(OutlierService.name);

  // Configuration from constants or environment
  private readonly RADIUS_METERS: number;
  private readonly MEASURED_AT_INTERVAL_HOURS: number;
  private readonly SAME_VALUE_WINDOW_HOURS: number;
  private readonly SAME_VALUE_MIN_COUNT: number;
  private readonly SAME_VALUE_INCLUDE_ZERO: boolean;
  private readonly SAME_VALUE_TOLERANCE: number;
  private readonly SAME_VALUE_MIN_VALUE: number;
  private readonly ENABLE_SAME_VALUE_CHECK: boolean;
  private readonly PM25_HARD_MAX: number;
  private readonly Z_SCORE_MIN_MEAN: number;
  private readonly ABSOLUTE_THRESHOLD: number;
  private readonly Z_SCORE_THRESHOLD: number;
  private readonly MIN_NEARBY_COUNT: number;

  constructor(
    private readonly outlierRepository: OutlierRepository,
    private readonly configService: ConfigService,
  ) {
    // Allow environment overrides for production tuning
    this.RADIUS_METERS = this.configService.get<number>(
      'RADIUS_METERS',
      OUTLIER_CONFIG.RADIUS_METERS,
    );
    this.MEASURED_AT_INTERVAL_HOURS = this.configService.get<number>(
      'MEASURED_AT_INTERVAL_HOURS',
      OUTLIER_CONFIG.MEASURED_AT_INTERVAL_HOURS,
    );
    this.SAME_VALUE_WINDOW_HOURS = this.configService.get<number>(
      'SAME_VALUE_WINDOW_HOURS',
      OUTLIER_CONFIG.SAME_VALUE_WINDOW_HOURS,
    );
    this.SAME_VALUE_MIN_COUNT = this.configService.get<number>(
      'SAME_VALUE_MIN_COUNT',
      OUTLIER_CONFIG.SAME_VALUE_MIN_COUNT,
    );
    this.SAME_VALUE_INCLUDE_ZERO = this.configService.get<boolean>(
      'SAME_VALUE_INCLUDE_ZERO',
      OUTLIER_CONFIG.SAME_VALUE_INCLUDE_ZERO,
    );
    this.SAME_VALUE_TOLERANCE = this.configService.get<number>(
      'SAME_VALUE_TOLERANCE',
      OUTLIER_CONFIG.SAME_VALUE_TOLERANCE,
    );
    this.SAME_VALUE_MIN_VALUE = this.configService.get<number>(
      'SAME_VALUE_MIN_VALUE',
      OUTLIER_CONFIG.SAME_VALUE_MIN_VALUE,
    );
    this.ENABLE_SAME_VALUE_CHECK = this.configService.get<boolean>(
      'ENABLE_SAME_VALUE_CHECK',
      OUTLIER_CONFIG.ENABLE_SAME_VALUE_CHECK,
    );
    this.PM25_HARD_MAX = this.configService.get<number>(
      'PM25_HARD_MAX',
      OUTLIER_CONFIG.PM25_HARD_MAX,
    );
    this.Z_SCORE_MIN_MEAN = this.configService.get<number>(
      'Z_SCORE_MIN_MEAN',
      OUTLIER_CONFIG.Z_SCORE_MIN_MEAN,
    );
    this.ABSOLUTE_THRESHOLD = this.configService.get<number>(
      'ABSOLUTE_THRESHOLD',
      OUTLIER_CONFIG.ABSOLUTE_THRESHOLD,
    );
    this.Z_SCORE_THRESHOLD = this.configService.get<number>(
      'Z_SCORE_THRESHOLD',
      OUTLIER_CONFIG.Z_SCORE_THRESHOLD,
    );
    this.MIN_NEARBY_COUNT = this.configService.get<number>(
      'MIN_NEARBY_COUNT',
      OUTLIER_CONFIG.MIN_NEARBY_COUNT,
    );
  }

  public async calculateBatchIsPm25Outlier(
    dataSource: string | undefined,
    dataPoints: OutlierDataPoint[],
    options?: OutlierCalculationOptions,
  ): Promise<Map<string, boolean>> {
    const radiusMeters = options?.radiusMeters ?? this.RADIUS_METERS;
    const measuredAtIntervalHours =
      options?.measuredAtIntervalHours ?? this.MEASURED_AT_INTERVAL_HOURS;
    const sameValueWindowHours = options?.sameValueWindowHours ?? this.SAME_VALUE_WINDOW_HOURS;
    const sameValueMinCount = options?.sameValueMinCount ?? this.SAME_VALUE_MIN_COUNT;
    const sameValueIncludeZero = options?.sameValueIncludeZero ?? this.SAME_VALUE_INCLUDE_ZERO;
    const sameValueTolerance = options?.sameValueTolerance ?? this.SAME_VALUE_TOLERANCE;
    const sameValueMinValue = options?.sameValueMinValue ?? this.SAME_VALUE_MIN_VALUE;
    const enableSameValueCheck = options?.enableSameValueCheck ?? this.ENABLE_SAME_VALUE_CHECK;
    const pm25HardMax = options?.pm25HardMax ?? this.PM25_HARD_MAX;
    const zScoreMinMean = options?.zScoreMinMean ?? this.Z_SCORE_MIN_MEAN;
    const absoluteThreshold = options?.absoluteThreshold ?? this.ABSOLUTE_THRESHOLD;
    const zScoreThreshold = options?.zScoreThreshold ?? this.Z_SCORE_THRESHOLD;
    const minNearbyCount = options?.minNearbyCount ?? this.MIN_NEARBY_COUNT;
    const useStoredOutlierFlagForNeighbors = options?.useStoredOutlierFlagForNeighbors ?? true;

    // Group datapoints by dataSource (map requests can combine sources)
    const pointsBySource = new Map<string, OutlierDataPoint[]>();
    for (const dp of dataPoints) {
      const source = dp.dataSource ?? dataSource;
      if (!source) {
        this.logger.warn(
          `Skipping outlier calculation for reference ${dp.locationReferenceId} because dataSource is missing`,
        );
        continue;
      }

      if (!pointsBySource.has(source)) {
        pointsBySource.set(source, []);
      }
      pointsBySource.get(source)!.push(dp);
    }

    // Extract arrays for batch processing
    const resultsMap = new Map<string, boolean>();
    for (const [source, points] of pointsBySource.entries()) {
      if (points.length === 0) continue;
      const locationReferenceIds = points.map(dp => dp.locationReferenceId);
      const measuredAts = points.map(dp => dp.measuredAt);
      const pm25Values = points.map(dp => dp.pm25);

      let before = Date.now();

      // Check 1: Same value for N hours (computed in database)
      let sameValueCheckMap = new Map<string, boolean>();
      if (enableSameValueCheck) {
        this.logger.debug(`Check same value for ${sameValueWindowHours} hours`);
        sameValueCheckMap = await this.outlierRepository.getBatchSameValue24hCheck(
          source,
          locationReferenceIds,
          measuredAts,
          pm25Values,
          sameValueIncludeZero,
          sameValueMinCount,
          sameValueWindowHours,
          sameValueMinValue,
          sameValueTolerance,
        );
        this.logger.debug(`Same value check spend ${Date.now() - before}ms`);
      }

      before = Date.now();

      // Check 2: Fetch all spatial stats in one query
      this.logger.debug('Fetch spatial statistics');
      const spatialStatsMap = await this.outlierRepository.getBatchSpatialZScoreStats(
        source,
        locationReferenceIds,
        measuredAts,
        radiusMeters,
        measuredAtIntervalHours,
        useStoredOutlierFlagForNeighbors,
      );

      this.logger.debug(`Spatial stats fetch spend ${Date.now() - before}ms`);

      const maxRadiusMeters = 300000;
      const fallbackRadiusMeters = Math.min(radiusMeters * 5, maxRadiusMeters);
      const shouldUseFallbackRadius = fallbackRadiusMeters > radiusMeters;
      const fallbackReferenceIds: number[] = [];
      const fallbackMeasuredAts: string[] = [];

      if (shouldUseFallbackRadius) {
        for (const dp of points) {
          const key = `${dp.locationReferenceId}_${dp.measuredAt}`;
          const stats = spatialStatsMap.get(key);
          if (!stats || stats.count === 0 || stats.mean === null) {
            fallbackReferenceIds.push(dp.locationReferenceId);
            fallbackMeasuredAts.push(dp.measuredAt);
          }
        }
      }

      const fallbackStatsMap =
        shouldUseFallbackRadius && fallbackReferenceIds.length > 0
          ? await this.outlierRepository.getBatchSpatialZScoreStats(
              source,
              fallbackReferenceIds,
              fallbackMeasuredAts,
              fallbackRadiusMeters,
              measuredAtIntervalHours,
              useStoredOutlierFlagForNeighbors,
            )
          : null;

      const shouldUseMaxRadius = maxRadiusMeters > fallbackRadiusMeters;
      const maxRadiusReferenceIds: number[] = [];
      const maxRadiusMeasuredAts: string[] = [];

      if (shouldUseMaxRadius) {
        for (const dp of points) {
          const key = `${dp.locationReferenceId}_${dp.measuredAt}`;
          const primary = spatialStatsMap.get(key);
          const primaryMissing = !primary || primary.count === 0 || primary.mean === null;
          if (!primaryMissing) continue;

          const fallback = fallbackStatsMap?.get(key);
          const fallbackMissing = !fallback || fallback.count === 0 || fallback.mean === null;
          if (!fallbackMissing) continue;

          maxRadiusReferenceIds.push(dp.locationReferenceId);
          maxRadiusMeasuredAts.push(dp.measuredAt);
        }
      }

      const maxRadiusStatsMap =
        shouldUseMaxRadius && maxRadiusReferenceIds.length > 0
          ? await this.outlierRepository.getBatchSpatialZScoreStats(
              source,
              maxRadiusReferenceIds,
              maxRadiusMeasuredAts,
              maxRadiusMeters,
              measuredAtIntervalHours,
              useStoredOutlierFlagForNeighbors,
            )
          : null;

      // Combine results for each data point
      for (const dataPoint of points) {
        const { locationReferenceId, pm25, measuredAt } = dataPoint;
        const key = `${locationReferenceId}_${measuredAt}`;

        if (pm25HardMax > 0 && pm25 >= pm25HardMax) {
          resultsMap.set(key, true);
          continue;
        }

        // Check 1: Same value outlier (already computed in DB)
        const isSameValue = sameValueCheckMap.get(key);
        if (isSameValue === true) {
          resultsMap.set(key, true);
          continue;
        }

        // Check 2: Spatial Z-score outlier
        const primaryStats = spatialStatsMap.get(key);
        const fallbackStats = fallbackStatsMap?.get(key);
        const maxStats = maxRadiusStatsMap?.get(key);

        const stats =
          [primaryStats, fallbackStats, maxStats].find(
            s => !!s && s.count > 0 && s.mean !== null,
          ) ??
          primaryStats ??
          fallbackStats ??
          maxStats ??
          null;

        if (!stats || stats.mean === null) {
          // No data available, hence not outlier
          resultsMap.set(key, false);
          continue;
        }

        const { mean, stddev, count: neighborCount } = stats;

        const requiredNeighborCount = 1;
        if (neighborCount < requiredNeighborCount) {
          resultsMap.set(key, false);
          continue;
        }

        const hasRobustQuantiles =
          stats.p25 !== null && stats.median !== null && stats.p75 !== null;
        const center = stats.median ?? mean;
        const centerType: 'median' | 'mean' = stats.median !== null ? 'median' : 'mean';

        const iqr = hasRobustQuantiles ? stats.p75! - stats.p25! : null;
        const iqrScale = iqr !== null && Number.isFinite(iqr) && iqr > 0 ? iqr / 1.349 : null;
        const stddevScale =
          typeof stddev === 'number' && Number.isFinite(stddev) && stddev > 0 ? stddev : null;
        const scale = iqrScale ?? stddevScale;

        const sparsityFactor =
          neighborCount >= minNearbyCount ? 1 : Math.sqrt(minNearbyCount / neighborCount);

        let isOutlier = false;

        if (center >= zScoreMinMean && scale !== null && scale > 0) {
          const effectiveZScoreThreshold = zScoreThreshold * sparsityFactor;
          const zScore = (pm25 - center) / scale;
          isOutlier = Math.abs(zScore) > effectiveZScoreThreshold;
        } else {
          const absDelta = Math.abs(pm25 - center);
          const varianceAwareBaseThreshold =
            scale !== null && Number.isFinite(scale) && scale > 0
              ? Math.max(absoluteThreshold, zScoreThreshold * scale)
              : absoluteThreshold;
          const effectiveAbsThreshold = varianceAwareBaseThreshold * sparsityFactor;
          isOutlier = absDelta > effectiveAbsThreshold;
        }

        resultsMap.set(key, isOutlier);
      }
    }

    return resultsMap;
  }

  public async explainPm25Outlier(
    dataSource: string | undefined,
    dataPoint: OutlierDataPoint,
    options?: OutlierCalculationOptions,
  ): Promise<Pm25OutlierExplanation> {
    const radiusMeters = options?.radiusMeters ?? this.RADIUS_METERS;
    const measuredAtIntervalHours =
      options?.measuredAtIntervalHours ?? this.MEASURED_AT_INTERVAL_HOURS;
    const absoluteThreshold = options?.absoluteThreshold ?? this.ABSOLUTE_THRESHOLD;
    const zScoreThreshold = options?.zScoreThreshold ?? this.Z_SCORE_THRESHOLD;
    const zScoreMinMean = options?.zScoreMinMean ?? this.Z_SCORE_MIN_MEAN;
    const minNearbyCount = options?.minNearbyCount ?? this.MIN_NEARBY_COUNT;
    const useStoredOutlierFlagForNeighbors = options?.useStoredOutlierFlagForNeighbors ?? true;
    const sameValueWindowHours = options?.sameValueWindowHours ?? this.SAME_VALUE_WINDOW_HOURS;
    const sameValueMinCount = options?.sameValueMinCount ?? this.SAME_VALUE_MIN_COUNT;
    const sameValueIncludeZero = options?.sameValueIncludeZero ?? this.SAME_VALUE_INCLUDE_ZERO;
    const sameValueTolerance = options?.sameValueTolerance ?? this.SAME_VALUE_TOLERANCE;
    const sameValueMinValue = options?.sameValueMinValue ?? this.SAME_VALUE_MIN_VALUE;
    const enableSameValueCheck = options?.enableSameValueCheck ?? this.ENABLE_SAME_VALUE_CHECK;
    const pm25HardMax = options?.pm25HardMax ?? this.PM25_HARD_MAX;

    const source = dataPoint.dataSource ?? dataSource;
    if (!source) {
      throw new BadRequestException('dataSource is required to explain PM2.5 outlier');
    }

    const key = `${dataPoint.locationReferenceId}_${dataPoint.measuredAt}`;

    const sameValueStats = enableSameValueCheck
      ? await this.outlierRepository.getSameValueWindowStats(
          source,
          dataPoint.locationReferenceId,
          dataPoint.measuredAt,
          sameValueWindowHours,
        )
      : null;

    const sameValueIsApplicable =
      enableSameValueCheck &&
      (sameValueIncludeZero || dataPoint.pm25 !== 0) &&
      (dataPoint.pm25 === 0 || dataPoint.pm25 >= sameValueMinValue);

    const sameValueMaxDelta =
      sameValueStats?.min !== null && sameValueStats?.max !== null
        ? sameValueStats.max - sameValueStats.min
        : null;

    const sameValueIsOutlier =
      sameValueIsApplicable &&
      sameValueStats !== null &&
      sameValueStats.count >= sameValueMinCount &&
      sameValueStats.min !== null &&
      sameValueStats.max !== null &&
      sameValueStats.max <= dataPoint.pm25 + sameValueTolerance &&
      sameValueStats.min >= dataPoint.pm25 - sameValueTolerance;

    const spatialStatsMap = await this.outlierRepository.getBatchSpatialZScoreStats(
      source,
      [dataPoint.locationReferenceId],
      [dataPoint.measuredAt],
      radiusMeters,
      measuredAtIntervalHours,
      useStoredOutlierFlagForNeighbors,
    );

    const primarySpatialStats = spatialStatsMap.get(key);
    const maxRadiusMeters = 300000;
    const fallbackRadiusMeters = Math.min(radiusMeters * 5, maxRadiusMeters);
    const shouldTryFallbackRadius =
      fallbackRadiusMeters > radiusMeters &&
      (!primarySpatialStats ||
        primarySpatialStats.count === 0 ||
        primarySpatialStats.mean === null);

    const fallbackSpatialStatsMap = shouldTryFallbackRadius
      ? await this.outlierRepository.getBatchSpatialZScoreStats(
          source,
          [dataPoint.locationReferenceId],
          [dataPoint.measuredAt],
          fallbackRadiusMeters,
          measuredAtIntervalHours,
          useStoredOutlierFlagForNeighbors,
        )
      : null;

    const fallbackSpatialStats = fallbackSpatialStatsMap?.get(key);

    const shouldTryMaxRadius =
      maxRadiusMeters > fallbackRadiusMeters &&
      (!fallbackSpatialStats ||
        fallbackSpatialStats.count === 0 ||
        fallbackSpatialStats.mean === null) &&
      (!primarySpatialStats ||
        primarySpatialStats.count === 0 ||
        primarySpatialStats.mean === null);

    const maxSpatialStatsMap = shouldTryMaxRadius
      ? await this.outlierRepository.getBatchSpatialZScoreStats(
          source,
          [dataPoint.locationReferenceId],
          [dataPoint.measuredAt],
          maxRadiusMeters,
          measuredAtIntervalHours,
          useStoredOutlierFlagForNeighbors,
        )
      : null;

    const maxSpatialStats = maxSpatialStatsMap?.get(key);

    const spatialStats =
      [primarySpatialStats, fallbackSpatialStats, maxSpatialStats].find(
        s => !!s && s.count > 0 && s.mean !== null,
      ) ??
      primarySpatialStats ??
      fallbackSpatialStats ??
      maxSpatialStats ??
      null;
    const neighborCount = spatialStats?.count ?? 0;
    const mean = spatialStats?.mean ?? null;
    const stddev = spatialStats?.stddev ?? null;
    const p25 = spatialStats?.p25 ?? null;
    const median = spatialStats?.median ?? null;
    const p75 = spatialStats?.p75 ?? null;

    const requiredNeighborCount = 1;
    const hasMinimalNeighbors = neighborCount >= requiredNeighborCount;
    const sparsityFactor =
      neighborCount >= minNearbyCount || neighborCount === 0
        ? 1
        : Math.sqrt(minNearbyCount / neighborCount);

    const hasRobustQuantiles = p25 !== null && median !== null && p75 !== null;
    const center = median ?? mean;
    const centerType: 'median' | 'mean' | null =
      center === null ? null : median !== null ? 'median' : 'mean';

    const iqr = hasRobustQuantiles ? p75 - p25 : null;
    const iqrScale = iqr !== null && Number.isFinite(iqr) && iqr > 0 ? iqr / 1.349 : null;
    const stddevScale =
      typeof stddev === 'number' && Number.isFinite(stddev) && stddev > 0 ? stddev : null;
    const scale = iqrScale ?? stddevScale;
    const scaleType: 'iqr' | 'stddev' | null =
      iqrScale !== null ? 'iqr' : stddevScale ? 'stddev' : null;

    let spatialMode: 'zscore' | 'absolute' | null = null;
    let zScore: number | null = null;
    let absoluteDelta: number | null = null;
    let threshold: number | null = null;
    let thresholdType: 'zscore' | 'absolute' | null = null;
    let spatialIsOutlier: boolean | null = null;
    let spatialNote: string | undefined;

    const spatialNoteParts: string[] = [];

    if (fallbackSpatialStatsMap || maxSpatialStatsMap) {
      const tried = [radiusMeters];
      if (fallbackSpatialStatsMap) tried.push(fallbackRadiusMeters);
      if (maxSpatialStatsMap) tried.push(maxRadiusMeters);

      if (tried.length === 2) {
        spatialNoteParts.push(
          `Expanded radius from ${tried[0]}m to ${tried[1]}m to find neighbors`,
        );
      } else if (tried.length === 3) {
        spatialNoteParts.push(
          `Expanded radius from ${tried[0]}m to ${tried[1]}m, then to ${tried[2]}m to find neighbors`,
        );
      }
    }
    if (neighborCount < minNearbyCount && neighborCount > 0) {
      spatialNoteParts.push(
        `Sparse neighborhood; thresholds scaled by ×${sparsityFactor.toFixed(2)}`,
      );
    }
    if (centerType === 'median' && scaleType === 'iqr') {
      spatialNoteParts.push(
        'Baseline uses robust median/IQR to reduce influence of extreme neighbors',
      );
    }

    if (!hasMinimalNeighbors || center === null) {
      spatialNoteParts.push(
        `Insufficient neighbors (need ≥ ${requiredNeighborCount}, target ${minNearbyCount})`,
      );
      spatialNote = spatialNoteParts.join('; ');
    } else if (center >= zScoreMinMean && scale !== null && scale > 0) {
      spatialMode = 'zscore';
      threshold = zScoreThreshold * sparsityFactor;
      thresholdType = 'zscore';

      zScore = (dataPoint.pm25 - center) / scale;

      spatialIsOutlier = Math.abs(zScore) > threshold;
      if (spatialNoteParts.length > 0) {
        spatialNote = spatialNoteParts.join('; ');
      }
    } else {
      spatialMode = 'absolute';
      const varianceAwareBaseThreshold =
        scale !== null && Number.isFinite(scale) && scale > 0
          ? Math.max(absoluteThreshold, zScoreThreshold * scale)
          : absoluteThreshold;
      threshold = varianceAwareBaseThreshold * sparsityFactor;
      thresholdType = 'absolute';
      absoluteDelta = Math.abs(dataPoint.pm25 - center);
      spatialIsOutlier = absoluteDelta > threshold;

      if (scale !== null && Number.isFinite(scale) && scale > 0) {
        const scaled = zScoreThreshold * scale;
        if (scaled > absoluteThreshold) {
          spatialNoteParts.push(
            `Variance-aware threshold uses max(absoluteThreshold=${absoluteThreshold}, zScoreThreshold*${scaleType ?? 'scale'}=${scaled.toFixed(
              2,
            )})`,
          );
        }
      }

      if (spatialNoteParts.length > 0) {
        spatialNote = spatialNoteParts.join('; ');
      }
    }

    const hardMaxEnabled = pm25HardMax > 0;
    const hardMaxIsOutlier = hardMaxEnabled && dataPoint.pm25 >= pm25HardMax;

    const isOutlier = hardMaxIsOutlier || sameValueIsOutlier || spatialIsOutlier === true;

    let decisionReason: Pm25OutlierExplanation['decision']['reason'] = 'within_threshold';
    let decisionMessage = 'Within thresholds';

    if (hardMaxIsOutlier) {
      decisionReason = 'hard_max';
      decisionMessage = 'PM2.5 exceeds hard maximum';
    } else if (sameValueIsOutlier) {
      decisionReason = 'same_value';
      decisionMessage = 'Same-value check triggered';
    } else if (spatialIsOutlier === true && spatialMode === 'zscore') {
      decisionReason = 'spatial_zscore';
      decisionMessage = 'Spatial Z-score exceeded threshold';
    } else if (spatialIsOutlier === true && spatialMode === 'absolute') {
      decisionReason = 'spatial_absolute';
      decisionMessage = 'Spatial absolute delta exceeded threshold';
    } else if (spatialIsOutlier === null) {
      decisionReason = 'insufficient_neighbors';
      decisionMessage = spatialNote ?? 'Spatial check not applicable';
    }

    const sameValueNote = !enableSameValueCheck
      ? 'Same-value check disabled'
      : !sameValueIsApplicable
        ? dataPoint.pm25 === 0 && !sameValueIncludeZero
          ? 'Same-value check skipped (PM2.5 = 0 and include-zero is off)'
          : dataPoint.pm25 !== 0 && dataPoint.pm25 < sameValueMinValue
            ? `Same-value check skipped (PM2.5 < minValue=${sameValueMinValue})`
            : undefined
        : undefined;

    const hardMaxNote =
      pm25HardMax <= 0
        ? 'Hard-max check disabled'
        : hardMaxIsOutlier
          ? `PM2.5 (${dataPoint.pm25}) ≥ hardMax (${pm25HardMax})`
          : undefined;

    return {
      key,
      locationReferenceId: dataPoint.locationReferenceId,
      dataSource: source,
      measuredAt: dataPoint.measuredAt,
      pm25: dataPoint.pm25,
      isOutlier,
      params: {
        radiusMeters,
        measuredAtIntervalHours,
        minNearbyCount,
        useStoredOutlierFlagForNeighbors,
        pm25HardMax,
        zScoreThreshold,
        absoluteThreshold,
        zScoreMinMean,
        enableSameValueCheck,
        sameValueWindowHours,
        sameValueMinCount,
        sameValueIncludeZero,
        sameValueTolerance,
        sameValueMinValue,
      },
      checks: {
        hardMax: {
          enabled: hardMaxEnabled,
          max: pm25HardMax,
          isOutlier: hardMaxIsOutlier,
          note: hardMaxNote,
        },
        sameValue: {
          enabled: enableSameValueCheck,
          includeZero: sameValueIncludeZero,
          tolerance: sameValueTolerance,
          minValue: sameValueMinValue,
          windowHours: sameValueWindowHours,
          minCount: sameValueMinCount,
          measurementCount: sameValueStats?.count ?? null,
          distinctCount: sameValueStats?.distinctCount ?? null,
          min: sameValueStats?.min ?? null,
          max: sameValueStats?.max ?? null,
          maxDelta: sameValueMaxDelta,
          isOutlier: sameValueIsOutlier,
          note: sameValueNote,
        },
        spatial: {
          neighborCount,
          mean,
          stddev,
          p25,
          median,
          p75,
          center,
          centerType,
          scale,
          scaleType,
          mode: spatialMode,
          zScore,
          absoluteDelta,
          threshold,
          thresholdType,
          isOutlier: spatialIsOutlier,
          note: spatialNote,
        },
      },
      decision: {
        reason: decisionReason,
        message: decisionMessage,
      },
    };
  }
}
