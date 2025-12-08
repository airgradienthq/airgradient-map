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
    // make sure this location id exist
    await this.locationRepository.isLocationIdExist(id);
    return await this.locationRepository.retrieveLocationById(id);
  }

  async getLocationLastMeasures(id: number): Promise<LocationMeasuresResult> {
    // make sure this location id exist
    await this.locationRepository.isLocationIdExist(id);

    const results = await this.locationRepository.retrieveLastMeasuresByLocationId(id);
    if (results.dataSource === DataSource.AIRGRADIENT) {
      results.pm25 = getEPACorrectedPM(results.pm25, results.rhum);
    }
    return results;
  }

  async getCigarettesSmoked(id: number): Promise<CigarettesSmokedResult> {
    // make sure this location id exist
    await this.locationRepository.isLocationIdExist(id);

    try {
      // Define periods for cigarette calculation
      const periods = ['1d', '7d', '30d', '365d'];

      // Fetch EPA-corrected PM2.5 averages for all periods in a single optimized query
      const result = await this.locationRepository.retrieveEPACorrectedAveragesByLocationId(
        id,
        MeasureType.PM25,
        periods,
      );

      // Map periods to cigarette labels and days
      const periodMapping = {
        '1d': { label: 'last24hours', days: 1 },
        '7d': { label: 'last7days', days: 7 },
        '30d': { label: 'last30days', days: 30 },
        '365d': { label: 'last365days', days: 365 },
      };

      const cigaretteData: Record<string, number | null> = {};

      // Convert PM2.5 averages to cigarettes smoked
      for (const period of periods) {
        const { label, days } = periodMapping[period];
        const avgPM25 = result.averages[period];

        if (avgPM25 === null || avgPM25 === undefined) {
          cigaretteData[label] = null;
        } else {
          // Berkeley Earth conversion: 22 µg/m³ PM2.5 = 1 cigarette per day
          // Formula: (average_daily_PM25 × timeframe_days) / 22
          const cigarettesForTimeframe = (avgPM25 * days) / 22;
          cigaretteData[label] = Math.round(cigarettesForTimeframe * 100) / 100;
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
    // make sure this location id exist
    await this.locationRepository.isLocationIdExist(id);

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
    // make sure this location id exist
    await this.locationRepository.isLocationIdExist(id);

    // Default set to pm25 if not provided
    let measureType = measure == null ? MeasureType.PM25 : measure;

    return await this.locationRepository.retrieveAveragesByLocationId(id, measureType, periods);
  }
}
