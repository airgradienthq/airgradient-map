import { Injectable, Logger } from '@nestjs/common';
import { HttpService } from '@nestjs/axios';
import { ConfigService } from '@nestjs/config';
import { firstValueFrom } from 'rxjs';
import { WildfireQueryDto } from './wildfire.dto';

interface FireData {
  latitude: number;
  longitude: number;
  brightness: number;
  confidence: number;
  frp: number;
  acq_date: string;
  acq_time: string;
  satellite: string;
  instrument: string;
}

type FireIntensity = 'low' | 'medium' | 'high' | 'extreme';

@Injectable()
export class WildfireService {
  private readonly logger = new Logger(WildfireService.name);
  private readonly FIRMS_BASE_URL = 'https://firms.modaps.eosdis.nasa.gov/api/area/csv';
  private readonly API_KEY: string;

  constructor(
    private readonly httpService: HttpService,
    private readonly configService: ConfigService,
  ) {
    this.API_KEY =
      this.configService.get<string>('NASA_FIRMS_API_KEY') || '7bce1276dbdac07ecc6d91d21ec00574';
    this.logger.log(`Using NASA FIRMS API key: ${this.API_KEY.substring(0, 8)}...`);
  }

  async getWildfiresGeoJSON(params: WildfireQueryDto) {
    if (!this.API_KEY) {
      throw new Error('NASA FIRMS API key not configured');
    }

    try {
      let { north, south, east, west } = params;

      this.logger.log(
        `Original coordinates: north=${north}, south=${south}, east=${east}, west=${west}`,
      );

      north = Math.min(85, Math.max(-85, north));
      south = Math.min(85, Math.max(-85, south));

      while (east > 180) east -= 360;
      while (east < -180) east += 360;
      while (west > 180) west -= 360;
      while (west < -180) west += 360;

      this.logger.log(
        `Normalized coordinates: north=${north}, south=${south}, east=${east}, west=${west}`,
      );

      const sources = ['VIIRS_SNPP_NRT', 'MODIS_NRT', 'VIIRS_NOAA20_NRT'];

      for (const source of sources) {
        try {
          this.logger.log(`Trying source: ${source}`);

          let allFires: FireData[] = [];

          if (west > east) {
            this.logger.log('Bounds cross date line, making two requests');
            const fires1 = await this.fetchFIRMSData(north, south, 180, west, params.days, source);
            const fires2 = await this.fetchFIRMSData(north, south, east, -180, params.days, source);
            allFires = [...fires1, ...fires2];
          } else {
            allFires = await this.fetchFIRMSData(north, south, east, west, params.days, source);
          }

          if (allFires.length > 0) {
            this.logger.log(`Successfully found ${allFires.length} fires using ${source}`);
            return this.convertToGeoJSON(allFires);
          }

          this.logger.log(`Source ${source} returned no fires`);
        } catch (error) {
          this.logger.warn(`Source ${source} failed: ${error.message}`);
          continue;
        }
      }

      this.logger.log('No fires found from any data source');
      return this.convertToGeoJSON([]);
    } catch (error) {
      this.logger.error('Failed to fetch FIRMS data:', error.message);
      throw new Error('Failed to fetch wildfire data');
    }
  }

  private async fetchFIRMSData(
    north: number,
    south: number,
    east: number,
    west: number,
    days: number,
    source: string,
  ): Promise<FireData[]> {
    const url = `${this.FIRMS_BASE_URL}/${this.API_KEY}/${source}/${west},${south},${east},${north}/${days}`;

    this.logger.log(`Fetching fire data: ${url}`);

    const response = await firstValueFrom(
      this.httpService.get<string>(url, {
        timeout: 15000,
        headers: {
          'User-Agent': 'AirGradient-Map/1.0',
        },
      }),
    );

    this.logger.log(`FIRMS API response status: ${response.status}`);
    this.logger.log(`FIRMS API response length: ${response.data?.length || 0} characters`);

    if (response.data) {
      const lines = response.data.split('\n');
      this.logger.log(`Response has ${lines.length} lines`);
      if (lines.length > 0) {
        this.logger.log(`First line: ${lines[0]}`);
      }
      if (lines.length > 1) {
        this.logger.log(`Second line: ${lines[1]}`);
      }
    }

    return this.parseCSVData(response.data);
  }

  private parseCSVData(csvData: string): FireData[] {
    if (!csvData || csvData.trim().length === 0) {
      this.logger.warn('Empty CSV data received');
      return [];
    }

    const lines = csvData.trim().split('\n');
    if (lines.length < 1) {
      this.logger.warn('No lines in CSV data');
      return [];
    }

    const header = lines[0].toLowerCase();
    if (!header.includes('latitude') || !header.includes('longitude')) {
      this.logger.warn(`Invalid CSV header: ${lines[0]}`);
      return [];
    }

    if (lines.length < 2) {
      this.logger.log('CSV has header but no data rows - no fires detected');
      return [];
    }

    const fires: FireData[] = [];

    for (let i = 1; i < lines.length; i++) {
      const values = lines[i].split(',');
      if (values.length < 13) {
        this.logger.warn(`Line ${i} has insufficient columns (${values.length}): ${lines[i]}`);
        continue;
      }

      try {
        const fire: FireData = {
          latitude: parseFloat(values[0]),
          longitude: parseFloat(values[1]),
          brightness: parseFloat(values[2]) || 0,
          confidence: parseInt(values[9]) || 0,
          frp: parseFloat(values[12]) || 0,
          acq_date: values[5],
          acq_time: values[6],
          satellite: values[7],
          instrument: values[8],
        };

        if (
          !isNaN(fire.latitude) &&
          !isNaN(fire.longitude) &&
          fire.latitude !== 0 &&
          fire.longitude !== 0
        ) {
          fires.push(fire);
        } else {
          this.logger.warn(
            `Invalid coordinates in line ${i}: lat=${fire.latitude}, lng=${fire.longitude}`,
          );
        }
      } catch (error) {
        this.logger.warn(`Error parsing line ${i}: ${error.message}`);
      }
    }

    this.logger.log(
      `Successfully parsed ${fires.length} valid fire records from ${lines.length - 1} data lines`,
    );
    return fires;
  }

  private convertToGeoJSON(fires: FireData[]) {
    const geoJson = {
      type: 'FeatureCollection',
      features: fires.map(fire => ({
        type: 'Feature',
        geometry: {
          type: 'Point',
          coordinates: [fire.longitude, fire.latitude],
        },
        properties: {
          brightness: fire.brightness,
          confidence: fire.confidence,
          frp: fire.frp,
          intensity: this.calculateFireIntensity(fire.frp, fire.confidence),
          acq_date: fire.acq_date,
          acq_time: fire.acq_time,
          satellite: fire.satellite,
          instrument: fire.instrument,
        },
      })),
    };

    this.logger.log(`Created GeoJSON with ${geoJson.features.length} fire features`);
    return geoJson;
  }

  private calculateFireIntensity(frp: number, confidence: number): FireIntensity {
    if (!frp || confidence < 50) return 'low';
    if (frp < 10) return 'low';
    if (frp < 50) return 'medium';
    if (frp < 200) return 'high';
    return 'extreme';
  }
}
