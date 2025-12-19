import { Module } from '@nestjs/common';
import { HttpModule } from '@nestjs/axios';
import { ConfigModule } from '@nestjs/config';
import { NotificationsService } from './notifications.service';
import { NotificationsWebhookController } from './notifications-webhook.controller';
import { OneSignalProvider } from './onesignal.provider';
import { NotificationBatchProcessor } from './notification-batch.processor';
import { NotificationsRepository } from './notifications.repository';
import { LocationModule } from 'src/location/location.module';
import { CoreApiService } from 'src/utils/core-api.service';

@Module({
  imports: [ConfigModule, LocationModule, HttpModule],
  controllers: [NotificationsWebhookController],
  providers: [
    NotificationsService,
    OneSignalProvider,
    NotificationBatchProcessor,
    NotificationsRepository,
    CoreApiService,
  ],
  exports: [NotificationsService],
})
export class NotificationCoreModule {}
