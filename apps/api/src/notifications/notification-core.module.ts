import { Module } from '@nestjs/common';
import { HttpModule } from '@nestjs/axios';
import { ConfigModule } from '@nestjs/config';
import { NotificationsService } from './notifications.service';
import { OneSignalProvider } from './onesignal.provider';
import { NotificationBatchProcessor } from './notification-batch.processor';
import { NotificationsRepository } from './notifications.repository';
import { LocationModule } from 'src/location/location.module';
import { DashboardApiService } from 'src/utils/dashboard-api.service';

@Module({
  imports: [HttpModule, ConfigModule, LocationModule],
  providers: [
    NotificationsService,
    OneSignalProvider,
    NotificationBatchProcessor,
    NotificationsRepository,
    DashboardApiService,
  ],
  exports: [NotificationsService, DashboardApiService],
})
export class NotificationCoreModule {}
