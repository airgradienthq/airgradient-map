import { BadRequestException, Injectable, Logger } from '@nestjs/common';
import { OutlierRepository } from './outlier.repository';
import { ConfigService } from '@nestjs/config';
import { OUTLIER_CONFIG } from 'src/constants/outlier.constants';
import { NearbyPm25Stats } from './nearby-pm25-stats.entity';

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

type ResolvedOutlierParams = {
  radiusMeters: number;
  measuredAtIntervalHours: number;
  sameValueWindowHours: number;
  sameValueMinCount: number;
  sameValueIncludeZero: boolean;
  sameValueTolerance: number;
  sameValueMinValue: number;
  enableSameValueCheck: boolean;
  pm25HardMax: number;
  zScoreMinMean: number;
  absoluteThreshold: number;
  zScoreThreshold: number;
  minNearbyCount: number;
  useStoredOutlierFlagForNeighbors: boolean;
};

type SpatialStatsMapsWithFallback = {
  radiusMeters: number;
  fallbackRadiusMeters: number;
  maxRadiusMeters: number;
  primaryStatsMap: Map<string, NearbyPm25Stats>;
  fallbackStatsMap: Map<string, NearbyPm25Stats> | null;
  maxStatsMap: Map<string, NearbyPm25Stats> | null;
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

  private buildKey(locationReferenceId: number, measuredAt: string): string {
    return `${locationReferenceId}_${measuredAt}`;
  }

  private resolveParams(options?: OutlierCalculationOptions): ResolvedOutlierParams {
    return {
      radiusMeters: options?.radiusMeters ?? this.RADIUS_METERS,
      measuredAtIntervalHours: options?.measuredAtIntervalHours ?? this.MEASURED_AT_INTERVAL_HOURS,
      sameValueWindowHours: options?.sameValueWindowHours ?? this.SAME_VALUE_WINDOW_HOURS,
      sameValueMinCount: options?.sameValueMinCount ?? this.SAME_VALUE_MIN_COUNT,
      sameValueIncludeZero: options?.sameValueIncludeZero ?? this.SAME_VALUE_INCLUDE_ZERO,
      sameValueTolerance: options?.sameValueTolerance ?? this.SAME_VALUE_TOLERANCE,
      sameValueMinValue: options?.sameValueMinValue ?? this.SAME_VALUE_MIN_VALUE,
      enableSameValueCheck: options?.enableSameValueCheck ?? this.ENABLE_SAME_VALUE_CHECK,
      pm25HardMax: options?.pm25HardMax ?? this.PM25_HARD_MAX,
      zScoreMinMean: options?.zScoreMinMean ?? this.Z_SCORE_MIN_MEAN,
      absoluteThreshold: options?.absoluteThreshold ?? this.ABSOLUTE_THRESHOLD,
      zScoreThreshold: options?.zScoreThreshold ?? this.Z_SCORE_THRESHOLD,
      minNearbyCount: options?.minNearbyCount ?? this.MIN_NEARBY_COUNT,
      useStoredOutlierFlagForNeighbors: options?.useStoredOutlierFlagForNeighbors ?? true,
    };
  }

  private isSpatialStatsUsable(stats: NearbyPm25Stats | undefined | null): boolean {
    return Boolean(stats && stats.count > 0 && stats.mean !== null);
  }

  private selectSpatialStatsForKey(
    key: string,
    maps: SpatialStatsMapsWithFallback,
  ): NearbyPm25Stats | null {
    const candidates = [
      maps.primaryStatsMap.get(key),
      maps.fallbackStatsMap?.get(key),
      maps.maxStatsMap?.get(key),
    ];

    return (
      candidates.find(candidate => this.isSpatialStatsUsable(candidate)) ??
      candidates.find(Boolean) ??
      null
    );
  }

  private async getSpatialStatsMapsWithFallback(
    source: string,
    locationReferenceIds: number[],
    measuredAts: string[],
    params: ResolvedOutlierParams,
  ): Promise<SpatialStatsMapsWithFallback> {
    const primaryStatsMap = await this.outlierRepository.getBatchSpatialZScoreStats(
      source,
      locationReferenceIds,
      measuredAts,
      params.radiusMeters,
      params.measuredAtIntervalHours,
      params.useStoredOutlierFlagForNeighbors,
    );

    const maxRadiusMeters = 300000;
    const fallbackRadiusMeters = Math.min(params.radiusMeters * 5, maxRadiusMeters);
    const shouldTryFallback = fallbackRadiusMeters > params.radiusMeters;

    const fallbackReferenceIds: number[] = [];
    const fallbackMeasuredAts: string[] = [];

    if (shouldTryFallback) {
      for (let index = 0; index < locationReferenceIds.length; index++) {
        const key = this.buildKey(locationReferenceIds[index], measuredAts[index]);
        const stats = primaryStatsMap.get(key);
        if (!this.isSpatialStatsUsable(stats)) {
          fallbackReferenceIds.push(locationReferenceIds[index]);
          fallbackMeasuredAts.push(measuredAts[index]);
        }
      }
    }

    const fallbackStatsMap =
      shouldTryFallback && fallbackReferenceIds.length > 0
        ? await this.outlierRepository.getBatchSpatialZScoreStats(
            source,
            fallbackReferenceIds,
            fallbackMeasuredAts,
            fallbackRadiusMeters,
            params.measuredAtIntervalHours,
            params.useStoredOutlierFlagForNeighbors,
          )
        : null;

    const shouldTryMax = maxRadiusMeters > fallbackRadiusMeters;
    const maxReferenceIds: number[] = [];
    const maxMeasuredAts: string[] = [];

    if (shouldTryMax) {
      for (let index = 0; index < locationReferenceIds.length; index++) {
        const key = this.buildKey(locationReferenceIds[index], measuredAts[index]);
        const primaryStats = primaryStatsMap.get(key);
        if (this.isSpatialStatsUsable(primaryStats)) continue;

        const fallbackStats = fallbackStatsMap?.get(key);
        if (this.isSpatialStatsUsable(fallbackStats)) continue;

        maxReferenceIds.push(locationReferenceIds[index]);
        maxMeasuredAts.push(measuredAts[index]);
      }
    }

    const maxStatsMap =
      shouldTryMax && maxReferenceIds.length > 0
        ? await this.outlierRepository.getBatchSpatialZScoreStats(
            source,
            maxReferenceIds,
            maxMeasuredAts,
            maxRadiusMeters,
            params.measuredAtIntervalHours,
            params.useStoredOutlierFlagForNeighbors,
          )
        : null;

    return {
      radiusMeters: params.radiusMeters,
      fallbackRadiusMeters,
      maxRadiusMeters,
      primaryStatsMap,
      fallbackStatsMap,
      maxStatsMap,
    };
  }

  private computeRobustBaseline(stats: NearbyPm25Stats): {
    center: number;
    centerType: 'median' | 'mean';
    scale: number | null;
    scaleType: 'iqr' | 'stddev' | null;
  } {
    const hasRobustQuantiles = stats.p25 !== null && stats.median !== null && stats.p75 !== null;

    const center = stats.median ?? stats.mean ?? 0;
    const centerType: 'median' | 'mean' = stats.median !== null ? 'median' : 'mean';

    const iqr = hasRobustQuantiles ? stats.p75! - stats.p25! : null;
    const iqrScale = iqr !== null && Number.isFinite(iqr) && iqr > 0 ? iqr / 1.349 : null;

    const stddevScale =
      typeof stats.stddev === 'number' && Number.isFinite(stats.stddev) && stats.stddev > 0
        ? stats.stddev
        : null;

    const scale = iqrScale ?? stddevScale;
    const scaleType: 'iqr' | 'stddev' | null =
      iqrScale !== null ? 'iqr' : stddevScale ? 'stddev' : null;

    return { center, centerType, scale, scaleType };
  }

  private computeSparsityFactor(neighborCount: number, minNearbyCount: number): number {
    if (neighborCount <= 0 || neighborCount >= minNearbyCount) return 1;
    return Math.sqrt(minNearbyCount / neighborCount);
  }

  private isSpatialOutlier(
    pm25: number,
    stats: NearbyPm25Stats | null,
    params: ResolvedOutlierParams,
  ): boolean {
    if (!stats || stats.mean === null) return false;

    const neighborCount = stats.count;
    const requiredNeighborCount = 1;
    if (neighborCount < requiredNeighborCount) return false;

    const { center, scale } = this.computeRobustBaseline(stats);
    const sparsityFactor = this.computeSparsityFactor(neighborCount, params.minNearbyCount);

    if (center >= params.zScoreMinMean && scale !== null && scale > 0) {
      const zScore = (pm25 - center) / scale;
      return Math.abs(zScore) > params.zScoreThreshold * sparsityFactor;
    }

    const absDelta = Math.abs(pm25 - center);
    const varianceAwareBaseThreshold =
      scale !== null && Number.isFinite(scale) && scale > 0
        ? Math.max(params.absoluteThreshold, params.zScoreThreshold * scale)
        : params.absoluteThreshold;

    return absDelta > varianceAwareBaseThreshold * sparsityFactor;
  }

  public async calculateBatchIsPm25Outlier(
    dataSource: string | undefined,
    dataPoints: OutlierDataPoint[],
    options?: OutlierCalculationOptions,
  ): Promise<Map<string, boolean>> {
    const params = this.resolveParams(options);

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
      if (params.enableSameValueCheck) {
        this.logger.debug(`Check same value for ${params.sameValueWindowHours} hours`);
        sameValueCheckMap = await this.outlierRepository.getBatchSameValue24hCheck(
          source,
          locationReferenceIds,
          measuredAts,
          pm25Values,
          params.sameValueIncludeZero,
          params.sameValueMinCount,
          params.sameValueWindowHours,
          params.sameValueMinValue,
          params.sameValueTolerance,
        );
        this.logger.debug(`Same value check spend ${Date.now() - before}ms`);
      }

      before = Date.now();

      // Check 2: Fetch spatial stats (with radius fallback when sparse)
      this.logger.debug('Fetch spatial statistics');
      const spatialMaps = await this.getSpatialStatsMapsWithFallback(
        source,
        locationReferenceIds,
        measuredAts,
        params,
      );

      this.logger.debug(`Spatial stats fetch spend ${Date.now() - before}ms`);

      // Combine results for each data point
      for (const dataPoint of points) {
        const { locationReferenceId, pm25, measuredAt } = dataPoint;
        const key = this.buildKey(locationReferenceId, measuredAt);

        if (params.pm25HardMax > 0 && pm25 >= params.pm25HardMax) {
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
        const stats = this.selectSpatialStatsForKey(key, spatialMaps);
        resultsMap.set(key, this.isSpatialOutlier(pm25, stats, params));
      }
    }

    return resultsMap;
  }

  public async explainPm25Outlier(
    dataSource: string | undefined,
    dataPoint: OutlierDataPoint,
    options?: OutlierCalculationOptions,
  ): Promise<Pm25OutlierExplanation> {
    const params = this.resolveParams(options);

    const {
      radiusMeters,
      measuredAtIntervalHours,
      absoluteThreshold,
      zScoreThreshold,
      zScoreMinMean,
      minNearbyCount,
      useStoredOutlierFlagForNeighbors,
      sameValueWindowHours,
      sameValueMinCount,
      sameValueIncludeZero,
      sameValueTolerance,
      sameValueMinValue,
      enableSameValueCheck,
      pm25HardMax,
    } = params;

    const source = dataPoint.dataSource ?? dataSource;
    if (!source) {
      throw new BadRequestException('dataSource is required to explain PM2.5 outlier');
    }

    const key = this.buildKey(dataPoint.locationReferenceId, dataPoint.measuredAt);

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

    const spatialMaps = await this.getSpatialStatsMapsWithFallback(
      source,
      [dataPoint.locationReferenceId],
      [dataPoint.measuredAt],
      params,
    );

    const spatialStats = this.selectSpatialStatsForKey(key, spatialMaps);
    const neighborCount = spatialStats?.count ?? 0;
    const mean = spatialStats?.mean ?? null;
    const stddev = spatialStats?.stddev ?? null;
    const p25 = spatialStats?.p25 ?? null;
    const median = spatialStats?.median ?? null;
    const p75 = spatialStats?.p75 ?? null;

    const requiredNeighborCount = 1;
    const hasMinimalNeighbors = neighborCount >= requiredNeighborCount;
    const sparsityFactor = this.computeSparsityFactor(neighborCount, minNearbyCount);

    let center: number | null = null;
    let centerType: 'median' | 'mean' | null = null;
    let scale: number | null = null;
    let scaleType: 'iqr' | 'stddev' | null = null;

    if (spatialStats && mean !== null) {
      const baseline = this.computeRobustBaseline(spatialStats);
      center = baseline.center;
      centerType = baseline.centerType;
      scale = baseline.scale;
      scaleType = baseline.scaleType;
    }

    let spatialMode: 'zscore' | 'absolute' | null = null;
    let zScore: number | null = null;
    let absoluteDelta: number | null = null;
    let threshold: number | null = null;
    let thresholdType: 'zscore' | 'absolute' | null = null;
    let spatialIsOutlier: boolean | null = null;
    let spatialNote: string | undefined;

    const spatialNoteParts: string[] = [];

    if (spatialMaps.fallbackStatsMap || spatialMaps.maxStatsMap) {
      const tried = [radiusMeters];
      if (spatialMaps.fallbackStatsMap) tried.push(spatialMaps.fallbackRadiusMeters);
      if (spatialMaps.maxStatsMap) tried.push(spatialMaps.maxRadiusMeters);

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
