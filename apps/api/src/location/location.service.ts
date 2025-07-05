import { Injectable } from '@nestjs/common';
import LocationRepository from './location.repository';

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
    return await this.locationRepository.retrieveLastMeasuresByLocationId(id);
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
    return await this.locationRepository.retrieveLocationMeasuresHistory(
      id,
      start,
      end,
      bucketSize,
      measureType,
    );
  }
}
