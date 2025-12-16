import { Module } from '@nestjs/common';
import { HttpModule } from '@nestjs/axios';
import { ConfigModule } from '@nestjs/config';
import { NotificationsService } from './notifications.service';
import { OneSignalProvider } from './onesignal.provider';
import { NotificationBatchProcessor } from './notification-batch.processor';
import { NotificationsRepository } from './notifications.repository';
import { LocationModule } from 'src/location/location.module';
import { DashboardApiClient } from './dashboard-api.client';

@Module({
  imports: [ConfigModule, LocationModule, HttpModule],
  providers: [
    NotificationsService,
    OneSignalProvider,
    NotificationBatchProcessor,
    NotificationsRepository,
    DashboardApiClient,
  ],
  exports: [NotificationsService],
})
export class NotificationCoreModule {}
