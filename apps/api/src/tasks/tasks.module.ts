import { Module } from '@nestjs/common';
import { TasksService } from './tasks.service';
import { TasksRepository } from './tasks.repository';
import { HttpModule } from '@nestjs/axios';
import { TasksHttp } from './tasks.http';

@Module({
  imports: [HttpModule],
  providers: [TasksService, TasksRepository, TasksHttp],
})
export class TasksModule {}
