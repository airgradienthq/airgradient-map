import { Injectable, Logger } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { HttpService } from '@nestjs/axios';
import { AxiosError, AxiosRequestConfig } from 'axios';
import { firstValueFrom } from 'rxjs';
import { catchError } from 'rxjs/operators';
import {
  BatchMeasurementsRequestDto,
  BatchMeasurementsResponseDto,
} from './dto/batch-measurements.dto';

/**
 * HTTP client for Dashboard API - used to fetch measurements for owned monitors
 */
@Injectable()
export class DashboardApiClient {
  private readonly logger = new Logger(DashboardApiClient.name);
  private readonly apiUrl: string;
  private readonly apiToken: string;

  constructor(
    private readonly httpService: HttpService,
    private readonly configService: ConfigService,
  ) {
    this.apiUrl = this.configService.get<string>('DASHBOARD_API_URL') || '';
    this.apiToken = this.configService.get<string>('DASHBOARD_API_TOKEN') || '';

    if (!this.apiUrl || !this.apiToken) {
      this.logger.warn(
        'Dashboard API configuration missing. Owned monitor notifications will not work.',
      );
    }
  }

  /**
   * Generate mock data for local testing
   * TODO: Remove this method when external API is ready
   */
  private generateMockData(
    request: BatchMeasurementsRequestDto,
  ): BatchMeasurementsResponseDto {
    this.logger.warn('🔧 MOCK MODE: Returning mock data for Dashboard API');

    const data = request.locations.flatMap((place) =>
      place.location_ids.map((locationId, index) => ({
        place_id: place.place_id,
        location_id: locationId,
        location_name: ['Living Room', 'Bedroom', 'Kitchen', 'Office', 'Basement'][
          index % 5
        ],
        timestamp: new Date().toISOString(),
        measurements: {
          pm25: Math.random() * 50 + 5, // 5-55 µg/m³
          rco2: Math.random() * 2000 + 400, // 400-2400 ppm
          tvoc_index: Math.random() > 0.3 ? Math.random() * 300 + 50 : null, // 50-350 or null
          nox_index: Math.random() > 0.3 ? Math.random() * 200 + 20 : null, // 20-220 or null
          atmp: Math.random() * 10 + 18, // 18-28°C
          rhum: Math.random() * 40 + 30, // 30-70%
        },
      })),
    );

    return {
      success: true,
      data,
      errors: [],
    };
  }

  /**
   * Fetch batch measurements for multiple locations
   */
  async fetchBatchMeasurements(
    request: BatchMeasurementsRequestDto,
  ): Promise<BatchMeasurementsResponseDto> {



    // TODO: Remove this mock mode when external API is ready
      // return this.generateMockData(request);
      // TODO: Remove this mock mode when external API is ready
   

    const url = `${this.apiUrl}/api/v1/measurements/batch`;
    const requestConfig: AxiosRequestConfig = {
      headers: {
        'Content-Type': 'application/json',
        Accept: 'application/json',
        Authorization: `Bearer ${this.apiToken}`,
      },
    };

    this.logger.debug(
      `Fetching batch measurements for ${request.locations.reduce((sum, loc) => sum + loc.location_ids.length, 0)} locations across ${request.locations.length} places`,
    );

    try {
      const { data } = await firstValueFrom(
        this.httpService.post<BatchMeasurementsResponseDto>(url, request, requestConfig).pipe(
          catchError((error: AxiosError) => {
            if (error.response) {
              // Non-200 response from server
              this.logger.error(
                `Dashboard API request failed: ${error.response.status} - ${JSON.stringify(error.response.data)}`,
              );
              // Return a failed response instead of throwing
              return [
                {
                  data: {
                    success: false,
                    data: [],
                    errors: [],
                    message: `API request failed: ${error.response.status}`,
                  },
                },
              ];
            } else {
              // No response or network error
              this.logger.error(`Dashboard API network error: ${error.message}`);
              return [
                {
                  data: {
                    success: false,
                    data: [],
                    errors: [],
                    message: 'Network error or no response received',
                  },
                },
              ];
            }
          }),
        ),
      );

      if (data.success) {
        this.logger.debug(
          `Successfully fetched ${data.data.length} measurements, ${data.errors.length} errors`,
        );
      } else {
        this.logger.warn(`Dashboard API returned failure: ${data.message}`);
      }

      return data;
    } catch (error) {
      this.logger.error(`Unexpected error fetching batch measurements: ${error.message}`);
      return {
        success: false,
        data: [],
        errors: [],
        message: error.message,
      };
    }
  }
}
