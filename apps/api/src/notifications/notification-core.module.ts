import { Module } from '@nestjs/common';
import { HttpModule } from '@nestjs/axios';
import { ConfigModule } from '@nestjs/config';
import { NotificationsService } from './notifications.service';
import { OneSignalProvider } from './onesignal.provider';
import { NotificationBatchProcessor } from './notification-batch.processor';
import { NotificationsRepository } from './notifications.repository';
import { DashboardApiClient } from './dashboard-api.client';
import { LocationModule } from 'src/location/location.module';

@Module({
  imports: [HttpModule, ConfigModule, LocationModule],
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
