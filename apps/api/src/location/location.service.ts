import { Injectable } from '@nestjs/common';
import LocationRepository from './location.repository';
import { getEPACorrectedPM } from 'src/utils/getEpaCorrectedPM';

@Injectable()
export class LocationService {
  constructor(private readonly locationRepository: LocationRepository) {}

  async getLocations(page = 1, pagesize = 100) {
    const offset = pagesize * (page - 1); // Calculate the offset for query
    return await this.locationRepository.retrieveLocations(offset, pagesize);
  }

  async getLocationById(id: number) {
    return await this.locationRepository.retrieveLocationById(id);
  }

  async getLocationLastMeasures(id: number) {
    const results = await this.locationRepository.retrieveLastMeasuresByLocationId(id);
    results.pm25 = getEPACorrectedPM(results.pm25, results.rhum);
    return results;
  }

  async getCigarettesSmoked(id: number) {
    return await this.locationRepository.retrieveCigarettesSmokedByLocationId(id);
  }

  async getLocationMeasuresHistory(
    id: number,
    start: string,
    end: string,
    bucketSize: string,
    measure?: string,
  ) {
    // Default set to pm25 if not provided
    let measureType = measure == null ? 'pm25' : measure;
    const results = await this.locationRepository.retrieveLocationMeasuresHistory(
      id,
      start,
      end,
      bucketSize,
      measureType,
    );

    if (measureType === 'pm25') {
      return results.map(row => ({
        timebucket: row.timebucket,
        value: getEPACorrectedPM(row.pm25, row.rhum),
      }));
    }

    return results;
  }

  async getDailyAverages(id: number, start: string, end: string, measure?: string) {
    let measureType = measure == null ? 'pm25' : measure;
    return await this.locationRepository.retrieveLocationDailyAverages(id, start, end, measureType);
  }
}
