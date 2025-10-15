import { Injectable, Logger } from '@nestjs/common';
import { OutlierRepository } from './outlier.repository';

@Injectable()
export class OutlierService {
  private readonly logger = new Logger(OutlierService.name);

  constructor(private readonly outlierRepository: OutlierRepository) {}

  private async isSameValueFor24Hours(
    last24HoursPm25Measurements: { measured_at: Date; pm25: number }[],
    newPm25: number,
  ): Promise<boolean> {
    return (
      last24HoursPm25Measurements.length >= 3 &&
      last24HoursPm25Measurements.every(m => m.pm25 === newPm25)
    );
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

    return false;
  }
}
