import { Module } from '@nestjs/common';
import { ConfigModule, ConfigService } from '@nestjs/config';
import { ScheduleModule } from '@nestjs/schedule';

import DatabaseModule from './database/database.module';
import RedisCacheModule from './redis-cache/redis-cache.module';
import { TasksModule } from './tasks/tasks.module';
import { MeasurementModule } from './measurement/measurement.module';
import { LocationModule } from './location/location.module';

@Module({
  imports: [
    ScheduleModule.forRoot(),
    TasksModule,
    ConfigModule.forRoot({
      isGlobal: true,
      envFilePath: [`.env.${process.env.NODE_ENV}`],
    }),
    DatabaseModule.forRootAsync({
      imports: [ConfigModule],
      inject: [ConfigService],
      useFactory: (configService: ConfigService) => ({
        host: configService.get('DATABASE_HOST'),
        port: configService.get('DATABASE_PORT'),
        user: configService.get('DATABASE_USER'),
        password: configService.get('DATABASE_PASSWORD'),
        database: configService.get('DATABASE_NAME'),
      }),
    }),
    RedisCacheModule.forRootAsync({
      imports: [ConfigModule],
      inject: [ConfigService],
      useFactory: (configService: ConfigService) => ({
        host: configService.get('REDIS_HOST'),
        port: +configService.get<number>('REDIS_PORT'),
        db: +configService.get<number>('REDIS_DB') || 0,
      }),
    }),
    MeasurementModule,
    LocationModule,
  ],
  controllers: [],
  providers: [],
})
export class AppModule {}
