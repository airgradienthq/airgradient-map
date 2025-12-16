import { Injectable, Logger } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { HttpService } from '@nestjs/axios';
import { AxiosError } from 'axios';
import { firstValueFrom } from 'rxjs';

export interface DashboardNotificationPayload {
  active: boolean;
  alarmType: string;
  description: string;
  locationGroupId: number | null;
  locationGroupName: string | null;
  locationId: number;
  measure: string;
  operator: string;
  threshold: number;
  title: string;
  triggerDelay: number;
  triggerOnlyWhenOpen: boolean;
  triggerType: string;
}

@Injectable()
export class DashboardApiClient {
  private readonly logger = new Logger(DashboardApiClient.name);
  private readonly apiUrl: string;
  private readonly apiToken: string;

  constructor(
    private readonly httpService: HttpService,
    configService: ConfigService,
  ) {
    this.apiUrl = configService.get<string>('DASHBOARD_API_URL') || '';
    this.apiToken = configService.get<string>('DASHBOARD_API_TOKEN') || '';

    if (!this.apiUrl || !this.apiToken) {
      this.logger.warn(
        'Dashboard API configuration missing. Owned notification registration calls will fail.',
      );
    }
  }

  async registerNotificationTrigger(payload: DashboardNotificationPayload): Promise<void> {
    if (!this.apiUrl || !this.apiToken) {
      throw new Error('Dashboard API configuration is missing');
    }

    const url = `${this.apiUrl}/api/v1/notifications/triggers`;

    try {
      await firstValueFrom(
        this.httpService.post(url, payload, {
          headers: {
            'Content-Type': 'application/json',
            Accept: 'application/json',
            Authorization: `Bearer ${this.apiToken}`,
          },
        }),
      );
      this.logger.log(
        `Registered owned notification trigger for location ${payload.locationId} (${payload.measure})`,
      );
    } catch (error) {
      const axiosError = error as AxiosError;
      const status = axiosError.response?.status;
      const data = axiosError.response?.data;
      this.logger.error(
        `Dashboard API registration failed: ${status ?? 'unknown'} - ${JSON.stringify(data)}`,
      );
      throw new Error('Dashboard API registration failed');
    }
  }
}
