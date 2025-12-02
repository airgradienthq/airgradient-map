import { Injectable, Logger } from '@nestjs/common';
import { OutlierRepository } from './outlier.repository';
import { ConfigService } from '@nestjs/config';
import { OUTLIER_CONFIG } from 'src/constants/outlier.constants';

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
    dataSource: string,
    dataPoints: Array<{ locationReferenceId: number; pm25: number; measuredAt: string }>,
  ): Promise<Map<string, boolean>> {
    // Extract arrays for batch processing
    const locationReferenceIds = dataPoints.map(dp => dp.locationReferenceId);
    const measuredAts = dataPoints.map(dp => dp.measuredAt);
    const pm25Values = dataPoints.map(dp => dp.pm25);

    let before = Date.now();

    // Check 1: Same value for 24 hours (computed in database)
    this.logger.debug('Check same value for 24 hours');
    const sameValueCheckMap = await this.outlierRepository.getBatchSameValue24hCheck(
      dataSource,
      locationReferenceIds,
      measuredAts,
      pm25Values,
    );
    this.logger.debug(`24 hours check spend ${Date.now() - before}ms`);

    before = Date.now();

    // Check 2: Fetch all spatial stats in one query
    this.logger.debug('Fetch spatial statistics');
    const spatialStatsMap = await this.outlierRepository.getBatchSpatialZScoreStats(
      dataSource,
      locationReferenceIds,
      measuredAts,
      this.RADIUS_METERS,
      this.MEASURED_AT_INTERVAL_HOURS,
      this.MIN_NEARBY_COUNT,
    );

    this.logger.debug(`Spatial stats fetch spend ${Date.now() - before}ms`);

    // Combine results for each data point
    const resultsMap = new Map<string, boolean>();

    for (const dataPoint of dataPoints) {
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
        isOutlier = Math.abs(zScore) > this.Z_SCORE_THRESHOLD;
      } else {
        isOutlier = Math.abs(pm25 - mean) > this.ABSOLUTE_THRESHOLD;
      }

      resultsMap.set(key, isOutlier);
    }

    return resultsMap;
  }
}
