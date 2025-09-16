import { Injectable, Logger, InternalServerErrorException } from '@nestjs/common';
import LocationRepository from './location.repository';
import { getEPACorrectedPM } from 'src/utils/getEpaCorrectedPM';
import { BucketSize, roundToBucket } from 'src/utils/timeSeriesBucket';
import { DateTime } from 'luxon';
import {
  LocationServiceResult,
  LocationByIdResult,
  LocationMeasuresResult,
  CigarettesSmokedResult,
  MeasurementAveragesResult,
} from '../types/location/location.types';
import { MeasureType } from 'src/types';
import { DataSource } from 'src/types/shared/data-source';

@Injectable()
export class LocationService {
  constructor(private readonly locationRepository: LocationRepository) {}
  private readonly logger = new Logger(LocationService.name);

  async getLocations(page = 1, pagesize = 100): Promise<LocationServiceResult> {
    const offset = pagesize * (page - 1); // Calculate the offset for query
    return await this.locationRepository.retrieveLocations(offset, pagesize);
  }

  async getLocationById(id: number): Promise<LocationByIdResult> {
    return await this.locationRepository.retrieveLocationById(id);
  }

  async getLocationLastMeasures(id: number): Promise<LocationMeasuresResult> {
    const results = await this.locationRepository.retrieveLastMeasuresByLocationId(id);
    if (results.dataSource === DataSource.AIRGRADIENT) {
      results.pm25 = getEPACorrectedPM(results.pm25, results.rhum);
    }
    return results;
  }

  async getCigarettesSmoked(id: number): Promise<CigarettesSmokedResult> {
    return await this.locationRepository.retrieveCigarettesSmokedByLocationId(id);
  }

  async getLocationMeasuresHistory(
    id: number,
    start: string,
    end: string,
    bucketSize: string,
    measure?: MeasureType,
  ) {
    // Default set to pm25 if not provided
    let measureType = measure == null ? MeasureType.PM25 : measure;

    // Declare and set placeholder
    let startTime: DateTime;
    let endTime: DateTime;
    try {
      this.logger.debug(`Time range before processed: ${start} -- ${end}`);
      startTime = roundToBucket(start, bucketSize as BucketSize);
      endTime = roundToBucket(end, bucketSize as BucketSize);
    } catch (error) {
      this.logger.error(error);
      throw new InternalServerErrorException({
        message: `LOC_007: Failed round range timestamp`,
        operation: 'getLocationMeasuresHistory',
        parameters: { id, start, end, bucketSize, measure },
        error: error.message,
        code: 'LOC_007',
      });
    }

    // Need to make sure timestamp in UTC since DB not automatically shift it to UTC
    startTime = startTime.toUTC();
    endTime = endTime.toUTC();

    const results = await this.locationRepository.retrieveLocationMeasuresHistory(
      id,
      startTime.toISO({ includeOffset: false }),
      endTime.toISO({ includeOffset: false }),
      bucketSize,
      measureType,
    );

    if (measureType === MeasureType.PM25) {
      return results.map((row: any) => ({
        timebucket: row.timebucket,
        value:
          row.dataSource === DataSource.AIRGRADIENT
            ? getEPACorrectedPM(row.pm25, row.rhum)
            : row.pm25,
      }));
    }

    return results;
  }

  async getLocationAverages(
    id: number,
    measure: MeasureType,
    periods?: string[],
  ): Promise<MeasurementAveragesResult> {
    // Default set to pm25 if not provided
    let measureType = measure == null ? MeasureType.PM25 : measure;

    return await this.locationRepository.retrieveAveragesByLocationId(id, measureType, periods);
  }
}
