import { Module } from '@nestjs/common';
import { TasksService } from './tasks.service';
import { TasksRepository } from './tasks.repository';
import { HttpModule } from '@nestjs/axios';
import { TasksHttp } from './tasks.http';
import { NotificationCoreModule } from 'src/notifications/notification-core.module';
import { OutlierModule } from 'src/outlier/outlier.module';
import { FiresDataModule } from 'src/fires-data/fires-data.module';

@Module({
  imports: [HttpModule, NotificationCoreModule, OutlierModule, FiresDataModule],
  providers: [TasksService, TasksRepository, TasksHttp],
})
export class TasksModule {}
