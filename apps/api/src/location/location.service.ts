import { Injectable, Logger, InternalServerErrorException } from '@nestjs/common';
import LocationRepository from './location.repository';
import { getPMWithEPACorrectionIfNeeded } from 'src/utils/getEpaCorrectedPM';
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

  async getLocations(
    hasFullAccess: boolean,
    page = 1,
    pagesize = 100,
  ): Promise<LocationServiceResult> {
    const offset = pagesize * (page - 1); // Calculate the offset for query
    return await this.locationRepository.retrieveLocations(hasFullAccess, offset, pagesize);
  }

  async getLocationById(id: number, hasFullAccess: boolean): Promise<LocationByIdResult> {
    // make sure this location id exist
    await this.locationRepository.isLocationIdExist(id, hasFullAccess);
    return await this.locationRepository.retrieveLocationById(id, hasFullAccess);
  }

  async getLocationLastMeasures(
    id: number,
    hasFullAccess: boolean,
  ): Promise<LocationMeasuresResult> {
    // make sure this location id exist
    await this.locationRepository.isLocationIdExist(id, hasFullAccess);

    const results = await this.locationRepository.retrieveLastMeasuresByLocationId(
      id,
      hasFullAccess,
    );
    results.pm25 = getPMWithEPACorrectionIfNeeded(
      results.dataSource as DataSource,
      results.pm25,
      results.rhum,
    );
    return results;
  }

  async getCigarettesSmoked(id: number, hasFullAccess: boolean): Promise<CigarettesSmokedResult> {
    // make sure this location id exist
    await this.locationRepository.isLocationIdExist(id, hasFullAccess);

    const timeframes = [
      { label: 'last24hours', days: 1 },
      { label: 'last7days', days: 7 },
      { label: 'last30days', days: 30 },
      { label: 'last365days', days: 365 },
    ];

    try {
      // Fetch all daily averages for the longest timeframe (365 days) in a single query
      const now = new Date();
      const maxDays = Math.max(...timeframes.map(tf => tf.days));
      const startDate = new Date(Date.now() - maxDays * 24 * 60 * 60 * 1000).toISOString();
      const endDate = now.toISOString();

      // Round start time to bucket boundary for consistency with other time-series queries
      const startTime = roundToBucket(startDate, BucketSize.OneDay);
      const endTime = DateTime.fromISO(endDate, { setZone: true });

      // Convert to UTC
      const start = startTime.toUTC().toISO({ includeOffset: false });
      const end = endTime.toUTC().toISO({ includeOffset: false });

      const dailyAverages = await this.locationRepository.retrieveDailyAveragesForCigarettes(
        id,
        start,
        end,
        hasFullAccess,
      );

      // Apply EPA correction to each daily average
      const correctedDailyValues = dailyAverages.map(day => ({
        timebucket: day.timebucket,
        value: getPMWithEPACorrectionIfNeeded(day.dataSource as DataSource, day.pm25, day.rhum),
      }));

      // Calculate cigarettes for each timeframe
      const cigaretteData: Record<string, number | null> = {};

      for (const timeframe of timeframes) {
        const cutoffTime = Date.now() - timeframe.days * 24 * 60 * 60 * 1000;

        // Filter daily values that fall within this timeframe
        const relevantDays = correctedDailyValues.filter(day => {
          const dayTime = new Date(day.timebucket).getTime();
          return dayTime >= cutoffTime && day.value !== null && day.value !== undefined;
        });

        if (relevantDays.length === 0) {
          cigaretteData[timeframe.label] = null;
        } else {
          // Calculate average daily PM2.5 from available data
          const sum = relevantDays.reduce((acc, day) => acc + day.value, 0);
          const averageDailyPM25 = sum / relevantDays.length;

          // Apply average to full timeframe and convert to cigarettes
          // Missing days are assumed to have the same average pollution as days with data
          // This projects the average exposure to the entire timeframe period
          // Berkeley Earth conversion: 22 µg/m³ PM2.5 = 1 cigarette per day
          // Formula: (average_daily_PM25 × timeframe_days) / 22
          const cigarettesForTimeframe = (averageDailyPM25 * timeframe.days) / 22;

          cigaretteData[timeframe.label] = Math.round(cigarettesForTimeframe * 100) / 100;
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
    hasFullAccess: boolean,
    measure?: MeasureType,
  ) {
    // make sure this location id exist
    await this.locationRepository.isLocationIdExist(id, hasFullAccess);

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
      hasFullAccess,
    );

    if (measureType === MeasureType.PM25) {
      return results.map((row: any) => ({
        timebucket: row.timebucket,
        value: getPMWithEPACorrectionIfNeeded(row.dataSource as DataSource, row.pm25, row.rhum),
      }));
    }

    return results;
  }

  async getLocationAverages(
    id: number,
    measure: MeasureType,
    hasFullAccess: boolean,
    periods?: string[],
  ): Promise<MeasurementAveragesResult> {
    // make sure this location id exist
    await this.locationRepository.isLocationIdExist(id, hasFullAccess);

    // Default set to pm25 if not provided
    let measureType = measure == null ? MeasureType.PM25 : measure;

    return await this.locationRepository.retrieveAveragesByLocationId(
      id,
      measureType,
      hasFullAccess,
      periods,
    );
  }
}
