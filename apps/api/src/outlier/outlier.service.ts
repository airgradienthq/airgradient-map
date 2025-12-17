import { Injectable, Logger } from '@nestjs/common';
import { OutlierRepository } from './outlier.repository';
import { ConfigService } from '@nestjs/config';
import { OUTLIER_CONFIG } from 'src/constants/outlier.constants';

export type OutlierCalculationOptions = {
  radiusMeters?: number;
  measuredAtIntervalHours?: number;
  absoluteThreshold?: number;
  zScoreThreshold?: number;
  minNearbyCount?: number;
  useStoredOutlierFlagForNeighbors?: boolean;
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
    const absoluteThreshold = options?.absoluteThreshold ?? this.ABSOLUTE_THRESHOLD;
    const zScoreThreshold = options?.zScoreThreshold ?? this.Z_SCORE_THRESHOLD;
    const minNearbyCount = options?.minNearbyCount ?? this.MIN_NEARBY_COUNT;
    const useStoredOutlierFlagForNeighbors =
      options?.useStoredOutlierFlagForNeighbors ?? true;

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

      // Check 1: Same value for 24 hours (computed in database)
      this.logger.debug('Check same value for 24 hours');
      const sameValueCheckMap = await this.outlierRepository.getBatchSameValue24hCheck(
        source,
        locationReferenceIds,
        measuredAts,
        pm25Values,
      );
      this.logger.debug(`24 hours check spend ${Date.now() - before}ms`);

      before = Date.now();

      // Check 2: Fetch all spatial stats in one query
      this.logger.debug('Fetch spatial statistics');
      const spatialStatsMap = await this.outlierRepository.getBatchSpatialZScoreStats(
        source,
        locationReferenceIds,
        measuredAts,
        radiusMeters,
        measuredAtIntervalHours,
        minNearbyCount,
        useStoredOutlierFlagForNeighbors,
      );

      this.logger.debug(`Spatial stats fetch spend ${Date.now() - before}ms`);

      // Combine results for each data point
      for (const dataPoint of points) {
        const { locationReferenceId, pm25, measuredAt } = dataPoint;
        const key = `${locationReferenceId}_${measuredAt}`;

        // Check 1: Same value for 24 hours (already computed in DB)
        const isSameValue = sameValueCheckMap.get(key);
        if (isSameValue === true) {
          resultsMap.set(key, true);
          continue;
        }

        // Check 2: Spatial Z-score outlier
        const stats = spatialStatsMap.get(key);
        if (!stats || stats.mean === null || stats.stddev === null) {
          // No data available, hence not outlier
          resultsMap.set(key, false);
          continue;
        }

        const { mean, stddev } = stats;
        let isOutlier = false;

        if (mean >= 50) {
          const zScore = (pm25 - mean) / stddev;
          isOutlier = Math.abs(zScore) > zScoreThreshold;
        } else {
          isOutlier = Math.abs(pm25 - mean) > absoluteThreshold;
        }

        resultsMap.set(key, isOutlier);
      }
    }

    return resultsMap;
  }
}
