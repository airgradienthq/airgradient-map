import { Module } from '@nestjs/common';
import { HttpModule } from '@nestjs/axios';
import { ConfigModule } from '@nestjs/config';
import { NotificationsService } from './notifications.service';
import { NotificationsController } from './notifications.controller';
import { OneSignalProvider } from './onesignal.provider';
import { NotificationBatchProcessor } from './notification-batch.processor';
import { NotificationsRepository } from './notifications.repository';
import { LocationModule } from 'src/location/location.module';

@Module({
  imports: [HttpModule, ConfigModule, LocationModule],
  controllers: [NotificationsController],
  providers: [
    NotificationsService,
    OneSignalProvider,
    NotificationBatchProcessor,
    NotificationsRepository,
  ],
  exports: [NotificationsService],
})
export class NotificationModule {}