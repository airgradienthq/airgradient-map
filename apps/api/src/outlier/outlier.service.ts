import { Injectable, Logger } from '@nestjs/common';
import { OutlierRepository } from './outlier.repository';
import { PM25DataPointEntity } from './pm25-data-point.entity';

@Injectable()
export class OutlierService {
  private readonly logger = new Logger(OutlierService.name);

  constructor(private readonly outlierRepository: OutlierRepository) {}

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

    return false;
  }
}
