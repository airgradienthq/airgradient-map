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
    last24HoursPm25Measurements: { measured_at: Date; pm25: number }[],
    newPm25: number,
  ): Promise<boolean> {
    return (
      last24HoursPm25Measurements.length >= 3 &&
      last24HoursPm25Measurements.every(m => m.pm25 === newPm25)
    );
  }

  private async isSpatialZscoreOutlier(dataPoint: {
    locationId: number;
    pm25: number;
    measuredAt: string;
  }): Promise<boolean> {
    const result = await this.outlierRepository.getSpatialZScoreStats(
      dataPoint,
      this.RADIUS_METERS,
      this.MEASURED_AT_INTERVAL_HOURS,
      this.MIN_NEARBY_COUNT,
    );

    if (result.rowCount === 0) {
      return false
    }

    const { mean, stddev } = result.rows[0];
    const zScore = (dataPoint.pm25 - mean) / stddev;

    return Math.abs(zScore) > this.Z_SCORE_THRESHOLD;
  }

  public async calculateIsPm25Outlier(dataPoint: {
    locationId: number;
    pm25: number;
    measuredAt: string;
  }): Promise<boolean> {
    const last24HoursPm25Measurements =
      await this.outlierRepository.getLast24HoursPm25Measurements(dataPoint);

    if (await this.isSameValueFor24Hours(last24HoursPm25Measurements.rows, dataPoint.pm25)) {
      return true;
    }

    if (await this.isSpatialZscoreOutlier(dataPoint)) {
      return true;
    }

    return false;
  }
}
