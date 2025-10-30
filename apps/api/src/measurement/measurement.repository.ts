import { Injectable, InternalServerErrorException, Logger } from '@nestjs/common';
import DatabaseService from 'src/database/database.service';
import { MeasurementEntity } from './measurement.entity';
import { MeasureType } from 'src/types';
import { getMeasureValidValueRange } from 'src/utils/measureValueValidation';

@Injectable()
class MeasurementRepository {
  constructor(private readonly databaseService: DatabaseService) {}
  private readonly logger = new Logger(MeasurementRepository.name);

  private buildMeasureQuery(
    excludeOutliers: boolean,
    measure?: MeasureType,
    paramsCount: number = 0,
  ): {
    selectQuery: string;
    whereQuery: string;
    hasValidation: boolean;
    minVal: number | null;
    maxVal: number | null;
  } {
    const query = {
      selectQuery: `m.pm25, m.pm10, m.atmp, m.rhum, m.rco2, m.o3, m.no2`,
      whereQuery: 'AND 1=1',
      hasValidation: false,
      minVal: null,
      maxVal: null,
    };

    if (measure) {
      const { minVal, maxVal, hasValidation } = getMeasureValidValueRange(measure);
      let validationQuery = '';

      if (hasValidation) {
        query.hasValidation = true;
        query.minVal = minVal;
        query.maxVal = maxVal;
        validationQuery = `AND m.${measure} BETWEEN $${paramsCount + 1} AND $${paramsCount + 2}`;
      }

      if (measure === MeasureType.PM25) {
        query.selectQuery = `m.pm25, m.rhum`;
        query.whereQuery = `AND m.pm25 IS NOT NULL ${validationQuery}`;
        query.whereQuery += excludeOutliers ? ' AND m.is_pm25_outlier = false' : '';
      } else {
        query.selectQuery = `m.${measure}`;
        query.whereQuery = `AND m.${measure} IS NOT NULL ${validationQuery}`;
      }
    }
    return query;
  }

  async retrieveLatest(
    offset: number = 0,
    limit: number = 100,
    measure?: MeasureType,
  ): Promise<MeasurementEntity[]> {
    const params = [offset, limit];
    // TODO: Might consider later if we want to exclude outliers here as well
    const { selectQuery, whereQuery, hasValidation, minVal, maxVal } = this.buildMeasureQuery(
      false,
      measure,
      params.length,
    );

    if (hasValidation) {
      params.push(minVal, maxVal);
    }

    const query = `
      SELECT
        m.location_id AS "locationId", 
        l.location_name AS "locationName", 
        ST_X(l.coordinate) AS "longitude",
        ST_Y(l.coordinate) AS "latitude",
        l.sensor_type AS "sensorType",
        m.measured_at AS "measuredAt",
        l.data_source AS "dataSource",
        ${selectQuery}
      FROM location l
      JOIN LATERAL (
        SELECT *
        FROM measurement m
        WHERE m.location_id = l.id
          AND m.measured_at  >= NOW() - INTERVAL '6 hours'
        ORDER BY m.measured_at DESC
        LIMIT 1
      ) m on TRUE
      WHERE
        TRUE
        ${whereQuery}
      ORDER BY l.id
      OFFSET $1 LIMIT $2;`;

    try {
      const result = await this.databaseService.runQuery(query, params);
      return result.rows.map(
        (measurement: Partial<MeasurementEntity>) => new MeasurementEntity(measurement),
      );
    } catch (error) {
      this.logger.error(error);
      throw new InternalServerErrorException({
        message: 'MEAS_001: Failed to retrieve latest measurements',
        operation: 'retrieveLatest',
        parameters: { offset, limit, measure },
        error: error.message,
        code: 'MEAS_001',
      });
    }
  }

  async retrieveLatestByArea(
    xMin: number,
    yMin: number,
    xMax: number,
    yMax: number,
    excludeOutliers: boolean,
    measure?: MeasureType,
  ): Promise<MeasurementEntity[]> {
    const params = [xMin, yMin, xMax, yMax];

    const { selectQuery, whereQuery, hasValidation, minVal, maxVal } = this.buildMeasureQuery(
      excludeOutliers,
      measure,
      params.length,
    );

    if (hasValidation) {
      params.push(minVal, maxVal);
    }

    // Format query
    const query = `
      SELECT
        m.location_id AS "locationId", 
        l.location_name AS "locationName", 
        ST_X(l.coordinate) AS "longitude",
        ST_Y(l.coordinate) AS "latitude",
        l.sensor_type AS "sensorType",
        m.measured_at AS "measuredAt",
        l.data_source AS "dataSource",
        ${selectQuery}
      FROM location l
      JOIN LATERAL (
        SELECT *
        FROM measurement m
        WHERE m.location_id = l.id
          AND m.measured_at  >= NOW() - INTERVAL '6 hours'
        ORDER BY m.measured_at DESC
        LIMIT 1
      ) m on TRUE
      WHERE
        ST_Within(
          l.coordinate,
          ST_MakeEnvelope($1, $2, $3, $4, 4326)
        )
        ${whereQuery}
      ORDER BY l.id;`;

    try {
      // Execute query with query params value
      const result = await this.databaseService.runQuery(query, params);

      // Return rows while map the result first to measurement entity
      return result.rows.map(
        (measurement: Partial<MeasurementEntity>) => new MeasurementEntity(measurement),
      );
    } catch (error) {
      this.logger.error(error);
      throw new InternalServerErrorException({
        message: 'MEAS_002: Failed to retrieve latest measurements by area',
        operation: 'retrieveLatestByArea',
        parameters: { xMin, yMin, xMax, yMax, excludeOutliers, measure },
        error: error.message,
        code: 'MEAS_002',
      });
    }
  }
}

export default MeasurementRepository;
