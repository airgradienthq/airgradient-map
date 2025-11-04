import { Module } from '@nestjs/common';
import { NotificationsController } from './notifications.controller';
import { NotificationCoreModule } from './notification-core.module';

@Module({
  imports: [NotificationCoreModule],
  controllers: [NotificationsController],
})
export class NotificationModule {}
