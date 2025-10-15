import { Injectable, Logger } from '@nestjs/common';
import DatabaseService from 'src/database/database.service';

@Injectable()
export class OutlierRepository {
  private readonly logger = new Logger(OutlierRepository.name);

  constructor(private readonly databaseService: DatabaseService) {}

  public async getLast24HoursPm25Measurements(dataPoint: {
    locationId: number;
    pm25: number;
    measuredAt: string;
  }) {
    try {
      return await this.databaseService.runQuery(
        `SELECT measured_at, pm25 
          FROM public.measurement
          WHERE location_id = '${dataPoint.locationId}' 
          AND measured_at >= (TIMESTAMP '${dataPoint.measuredAt}' - INTERVAL '24 HOURS');`,
      );
    } catch (error) {
      this.logger.error('Failed to', { error: error.message });
      throw error;
    }
  }
}
