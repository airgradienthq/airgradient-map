import { Injectable, Logger } from '@nestjs/common';
import { OutlierRepository } from './outlier.repository';
import { PM25DataPointEntity } from './pm25-data-point.entity';
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

  private async isSameValueFor24Hours(
    last24HoursPm25Measurements: PM25DataPointEntity[],
    newPm25: number,
  ): Promise<boolean> {
    return (
      newPm25 !== 0 && // It's not an outlier if the weather condition is good (0) for a long time
      last24HoursPm25Measurements.length >= 3 &&
      last24HoursPm25Measurements.every(m => m.pm25 === newPm25)
    );
  }

  private async isSpatialZscoreOutlier(
    locationReferenceId: number,
    pm25: number,
    measuredAt: string,
  ): Promise<boolean> {
    const result = await this.outlierRepository.getSpatialZScoreStats(
      locationReferenceId,
      measuredAt,
      this.RADIUS_METERS,
      this.MEASURED_AT_INTERVAL_HOURS,
      this.MIN_NEARBY_COUNT,
    );

    const { mean, stddev } = result;

    if (mean === null || stddev === null) {
      return false;
    }

    if (mean >= 50) {
      const zScore = (pm25 - mean) / stddev;
      return Math.abs(zScore) > this.Z_SCORE_THRESHOLD;
    } else {
      return Math.abs(pm25 - mean) > this.ABSOLUTE_THRESHOLD;
    }
  }

  public async calculateIsPm25Outlier(
    locationReferenceId: number,
    pm25: number,
    measuredAt: string,
  ): Promise<boolean> {
    const last24HoursPm25Measurements = await this.outlierRepository.getLast24HoursPm25Measurements(
      locationReferenceId,
      measuredAt,
    );

    if (await this.isSameValueFor24Hours(last24HoursPm25Measurements, pm25)) {
      return true;
    }

    if (await this.isSpatialZscoreOutlier(locationReferenceId, pm25, measuredAt)) {
      return true;
    }

    return false;
  }

  public async calculateBatchIsPm25Outlier(
    dataSource: string,
    dataPoints: Array<{ locationReferenceId: number; pm25: number; measuredAt: string }>,
  ): Promise<Map<string, boolean>> {
    this.logger.debug('Starting batch pm25 outlier calculation');
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
    this.logger.debug(`24 hours check spend ${Date.now() - before}`);

    before = Date.now();

    // Check 2: Spatial outlier check (computed in database)
    this.logger.debug('Perform spatial outlier check');
    const spatialOutlierCheckMap = await this.outlierRepository.getBatchSpatialOutlierCheck(
      dataSource,
      locationReferenceIds,
      measuredAts,
      pm25Values,
      this.RADIUS_METERS,
      this.MEASURED_AT_INTERVAL_HOURS,
      this.MIN_NEARBY_COUNT,
      this.Z_SCORE_THRESHOLD,
      this.ABSOLUTE_THRESHOLD,
    );
    this.logger.debug(`Spatial outlier check spend ${Date.now() - before}`);

    // Combine results for each data point
    const resultsMap = new Map<string, boolean>();

    for (const dataPoint of dataPoints) {
      const { locationReferenceId, measuredAt } = dataPoint;
      const key = `${locationReferenceId}_${measuredAt}`;

      // Check 1: Same value for 24 hours (already computed in DB)
      const isSameValue = sameValueCheckMap.get(key);
      if (isSameValue === true) {
        resultsMap.set(key, true);
        continue;
      }

      // Check 2: Spatial outlier (already computed in DB)
      const isSpatialOutlier = spatialOutlierCheckMap.get(key) ?? false;
      resultsMap.set(key, isSpatialOutlier);
    }

    return resultsMap;
  }
}
