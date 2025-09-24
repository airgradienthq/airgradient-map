import { Injectable, Logger } from '@nestjs/common';
import { HttpService } from '@nestjs/axios';
import { ConfigService } from '@nestjs/config';
import { firstValueFrom } from 'rxjs';
import { NotificationPMUnit } from './notification.model';

export interface OneSignalNotification {
  app_id?: string;
  include_player_ids: string[];
  headings: {
    en: string;
    de?: string;
  };
  contents: {
    en: string;
    de?: string;
  };
  ios_attachments?: {
    id1?: string;
  };
  mutable_content?: boolean;
  ios_sound?: string;
}

@Injectable()
export class OneSignalProvider {
  private readonly logger = new Logger(OneSignalProvider.name);
  private readonly apiUrl = 'https://onesignal.com/api/v1/notifications';
  private readonly appId: string;
  private readonly apiKey: string;

  constructor(
    private readonly httpService: HttpService,
    private readonly configService: ConfigService,
  ) {
    this.appId = this.configService.get<string>('ONESIGNAL_APP_ID');
    this.apiKey = this.configService.get<string>('ONESIGNAL_API_KEY');

    if (!this.appId || !this.apiKey) {
      throw new Error(
        'OneSignal configuration missing: ONESIGNAL_APP_ID and ONESIGNAL_API_KEY must be set in environment variables',
      );
    }
  }

  private async sendNotification(notification: OneSignalNotification): Promise<any> {
    const payload = {
      app_id: this.appId,
      ...notification,
    };

    try {
      const response = await firstValueFrom(
        this.httpService.post(this.apiUrl, payload, {
          headers: {
            'Content-Type': 'application/json',
            Authorization: `Basic ${this.apiKey}`,
          },
        }),
      );

      this.logger.debug('OneSignal notification sent successfully', {
        message: payload.contents,
        heading: payload.headings,
        image: payload.ios_attachments.id1,
        notificationId: response.data.id,
        statusCode: response.status,
      });

      return response.data;
    } catch (error) {
      // Log error without exposing sensitive data
      this.logger.error('Failed to send OneSignal notification', {
        statusCode: error.response?.status,
        message: error.message,
      });
      throw error;
    }
  }

  public async sendAirQualityNotification(
    playerIds: string[],
    locationName: string,
    airQuality: number,
    unit: NotificationPMUnit = NotificationPMUnit.UG,
    imageUrl?: string,
  ): Promise<any> {
    const notification: OneSignalNotification = {
      include_player_ids: playerIds,
      headings: {
        en: locationName,
        de: locationName,
      },
      contents: {
        en: `Air Quality is now ${airQuality} ${unit}`,
        de: `Die Luftqualität ist derzeit ${airQuality} ${unit}`,
      },
      ios_attachments: imageUrl
        ? {
            id1:
              imageUrl || 'https://www.airgradient.com/images/alert-icons-mascot/aqi-moderate.png',
          }
        : undefined,
      mutable_content: true,
      ios_sound: 'default',
    };

    return this.sendNotification(notification);
  }
}
