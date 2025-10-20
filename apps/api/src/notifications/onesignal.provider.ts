import { Injectable, Logger } from '@nestjs/common';
import { HttpService } from '@nestjs/axios';
import { ConfigService } from '@nestjs/config';
import { firstValueFrom } from 'rxjs';

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
  android_channel_id?: string;
  android_accent_color?: string;
}

@Injectable()
export class OneSignalProvider {
  private readonly logger = new Logger(OneSignalProvider.name);
  private readonly apiUrl = 'https://onesignal.com/api/v1/notifications';
  private readonly appId: string;
  private readonly apiKey: string;
  private readonly androidChannelId: string;

  constructor(
    private readonly httpService: HttpService,
    private readonly configService: ConfigService,
  ) {
    this.appId = this.configService.get<string>('ONESIGNAL_APP_ID');
    this.apiKey = this.configService.get<string>('ONESIGNAL_API_KEY');
    this.androidChannelId = this.configService.get<string>('ONESIGNAL_ANDROID_CHANNEL_ID');

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
    pmValue: number,
    imageUrl?: string,
    unitLabel?: string,
    title?: { en: string; de: string },
    androidAccentColor?: string,
  ): Promise<any> {
    const notification: OneSignalNotification = {
      include_player_ids: playerIds,
      headings: {
        en: title?.en || locationName,
        de: title?.de || locationName,
      },
      contents: {
        en: `Air Quality is now ${pmValue} ${unitLabel}`,
        de: `Die Luftqualität ist derzeit ${pmValue} ${unitLabel}`,
      },
      ios_attachments: imageUrl
        ? {
            id1:
              imageUrl || 'https://www.airgradient.com/images/alert-icons-mascot/aqi-moderate.png',
          }
        : undefined,
      mutable_content: true,
      ios_sound: 'default',
      android_channel_id: this.androidChannelId,
      android_accent_color: androidAccentColor,
    };

    return this.sendNotification(notification);
  }
}
