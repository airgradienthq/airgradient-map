import { Injectable, InternalServerErrorException, Logger } from '@nestjs/common';
import DatabaseService from 'src/database/database.service';
import Measurement from './measurement.entity';
import { MeasureType } from 'src/utils/measureTypeQuery';
import { getMeasureValidValueRange } from 'src/utils/measureValueValidation';

@Injectable()
class MeasurementRepository {
  constructor(private readonly databaseService: DatabaseService) {}
  private readonly logger = new Logger(MeasurementRepository.name);

  private buildMeasureQuery(
    measure?: string,
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
      whereQuery: '',
      hasValidation: false,
      minVal: null,
      maxVal: null,
    };

    if (measure) {
      const { minVal, maxVal, hasValidation } = getMeasureValidValueRange(measure as MeasureType);
      let validationQuery = '';

      if (hasValidation) {
        query.hasValidation = true;
        query.minVal = minVal;
        query.maxVal = maxVal;
        validationQuery = `AND m.${measure} BETWEEN $${paramsCount + 1} AND $${paramsCount + 2}`;
      }

      query.selectQuery = `m.${measure}`;
      query.whereQuery = `WHERE m.${measure} IS NOT NULL ${validationQuery}`;
    }
    return query;
  }

  async retrieveLatest(
    offset: number = 0,
    limit: number = 100,
    measure?: string,
  ): Promise<Measurement[]> {
    const params = [offset, limit];
    const { selectQuery, whereQuery, hasValidation, minVal, maxVal } = this.buildMeasureQuery(
      measure,
      params.length,
    );

    if (hasValidation) {
      params.push(minVal, maxVal);
    }

    const query = ` 
            WITH latest_measurements AS (
                SELECT 
                    location_id,
                    last(measured_at, measured_at) AS last_measured_at
                FROM 
                    measurement
                WHERE measured_at  >= NOW() - INTERVAL '6 hours'
                GROUP BY
                    location_id
            )
            SELECT 
                lm.location_id AS "locationId", 
                l.location_name AS "locationName", 
                ST_X(l.coordinate) AS longitude,
                ST_Y(l.coordinate) AS latitude,
                l.sensor_type AS "sensorType",
                ${selectQuery},
                lm.last_measured_at AS "measuredAt"
            FROM 
                latest_measurements lm
            JOIN 
                measurement m ON lm.location_id = m.location_id AND lm.last_measured_at = m.measured_at
            JOIN 
                location l ON m.location_id = l.id
            ${whereQuery}
            ORDER BY 
                lm.location_id 
            OFFSET $1 LIMIT $2; 
        `;

    try {
      const result = await this.databaseService.runQuery(query, params);
      return result.rows.map((measurement: Partial<Measurement>) => new Measurement(measurement));
    } catch (error) {
      this.logger.error(error);
      throw new InternalServerErrorException('Error query latest measures');
    }
  }

  async retrieveLatestByArea(
    xMin: number,
    yMin: number,
    xMax: number,
    yMax: number,
    measure?: string,
  ): Promise<Measurement[]> {
    const params = [xMin, yMin, xMax, yMax];

    const { selectQuery, whereQuery, hasValidation, minVal, maxVal } = this.buildMeasureQuery(
      measure,
      params.length,
    );

    if (hasValidation) {
      params.push(minVal, maxVal);
    }

    // Format query
    const query = `
            WITH latest_measurements AS (
                SELECT 
                    l.id AS location_id,
                    LAST(m.measured_at, m.measured_at) AS last_measured_at
                FROM 
                    measurement m
                JOIN location l 
                    ON m.location_id = l.id 
                WHERE 
                    ST_Within(
                        coordinate,
                        ST_MakeEnvelope($1, $2, $3, $4, 3857)
                    )
                AND
                    m.measured_at  >= NOW() - INTERVAL '6 hours'
                GROUP BY 
                    l.id
            )
            SELECT 
                lm.location_id AS "locationId", 
                l.location_name AS "locationName", 
                ST_X(l.coordinate) AS longitude,
                ST_Y(l.coordinate) AS latitude,
                l.sensor_type AS "sensorType",
                ${selectQuery},
                lm.last_measured_at AS "measuredAt"
            FROM 
                latest_measurements lm
            JOIN 
                measurement m ON lm.location_id = m.location_id AND lm.last_measured_at = m.measured_at
            JOIN 
                location l ON m.location_id = l.id
                ${whereQuery};
        `;

    try {
      // Execute query with query params value
      const result = await this.databaseService.runQuery(query, params);

      // Return rows while map the result first to measurement entity
      return result.rows.map((measurement: Partial<Measurement>) => new Measurement(measurement));
    } catch (error) {
      this.logger.error(error);
      throw new InternalServerErrorException('Error query latest measures by area');
    }
  }
}

export default MeasurementRepository;
