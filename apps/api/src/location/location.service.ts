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
    const timeframes = [
      { label: 'last24hours', days: 1 },
      { label: 'last7days', days: 7 },
      { label: 'last30days', days: 30 },
      { label: 'last365days', days: 365 },
    ];

    try {
      const now = new Date();
      const cigaretteData: Record<string, number | null> = {};

      for (const timeframe of timeframes) {
        const start = new Date(Date.now() - timeframe.days * 24 * 60 * 60 * 1000).toISOString();
        const end = now.toISOString();

        const rows = await this.locationRepository.retrieveLocationMeasuresHistory(
          id,
          start,
          end,
          BucketSize.OneDay,
          true,
          MeasureType.PM25,
        );

        let results = rows.map((row: any) => ({
          timebucket: row.timebucket,
          value:
            row.dataSource === DataSource.AIRGRADIENT
              ? getEPACorrectedPM(row.pm25, row.rhum)
              : row.pm25,
        }));

        results = results.filter(result => result.pm25 !== null);

        if (results.length === 0) {
          cigaretteData[timeframe.label] = null;
        } else {
          const sum = results.reduce((acc, result) => acc + parseFloat(result.value), 0);
          cigaretteData[timeframe.label] = Math.round((sum / 22) * 100) / 100;
        }
      }
      return cigaretteData;
    } catch (error) {
      this.logger.error(error);
      throw new InternalServerErrorException('Error query retrieve cigarettes smoked');
    }
  }

  async getLocationMeasuresHistory(
    id: number,
    start: string,
    end: string,
    bucketSize: BucketSize,
    excludeOutliers: boolean,
    measure?: MeasureType,
  ) {
    // Default set to pm25 if not provided
    let measureType = measure == null ? MeasureType.PM25 : measure;

    // Declare and set placeholder
    let startTime: DateTime;
    let endTime: DateTime;
    try {
      this.logger.debug(`Time range before processed: ${start} -- ${end}`);
      startTime = roundToBucket(start, bucketSize);
      endTime = DateTime.fromISO(end, { setZone: true });

      // Ensure the conversion was successful before proceeding.
      if (!endTime.isValid) {
        throw new Error('Invalid ISO end date string provided.');
      }
    } catch (error) {
      this.logger.error(error);
      throw new InternalServerErrorException({
        message: `LOC_007: Failed round range timestamp`,
        operation: 'getLocationMeasuresHistory',
        parameters: { id, start, end, bucketSize, excludeOutliers, measure },
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
      excludeOutliers,
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
