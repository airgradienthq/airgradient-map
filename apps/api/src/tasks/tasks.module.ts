import { Module } from '@nestjs/common';
import { TasksService } from './tasks.service';
import { TasksRepository } from './tasks.repository';
import { HttpModule } from '@nestjs/axios';
import { TasksHttp } from './tasks.http';
import { NotificationCoreModule } from 'src/notifications/notification-core.module';

@Module({
  imports: [HttpModule, NotificationCoreModule],
  providers: [TasksService, TasksRepository, TasksHttp],
})
export class TasksModule {}
