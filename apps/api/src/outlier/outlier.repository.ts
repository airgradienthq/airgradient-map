import { Injectable, Logger, InternalServerErrorException } from '@nestjs/common';
import DatabaseService from 'src/database/database.service';
import { PM25DataPointEntity } from './pm25-data-point.entity';

@Injectable()
export class OutlierRepository {
  private readonly logger = new Logger(OutlierRepository.name);

  constructor(private readonly databaseService: DatabaseService) {}

  public async getLast24HoursPm25Measurements(
    locationReferenceId: number,
    measuredAt: string,
  ): Promise<PM25DataPointEntity[]> {
    try {
      const query = `
        SELECT measured_at, pm25
        FROM public.measurement m
        JOIN public.location l ON m.location_id = l.id
        WHERE l.reference_id = $1
          AND m.measured_at >= ($2::timestamp - INTERVAL '24 HOURS');
      `;
      const result = await this.databaseService.runQuery(query, [locationReferenceId, measuredAt]);
      return result.rows.map(
        (r: any) =>
          new PM25DataPointEntity({
            measuredAt: new Date(r.measured_at),
            pm25: Number(r.pm25),
          }),
      );
    } catch (error) {
      this.logger.error(error);
      throw new InternalServerErrorException({
        message: 'OUT_001: Failed to retrieve last 24 Hours pm25 by id',
        operation: 'getLast24HoursPm25Measurements',
        parameters: { locationReferenceId, measuredAt },
        error: error.message,
        code: 'OUT_001',
      });
    }
  }
}
