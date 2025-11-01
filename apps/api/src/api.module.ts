import { Module } from '@nestjs/common';
import { ScheduleModule } from '@nestjs/schedule';
import DatabaseModule from './database/database.module';
import { ConfigModule, ConfigService } from '@nestjs/config';
import { MeasurementModule } from './measurement/measurement.module';
import { LocationModule } from './location/location.module';
import { NotificationModule } from './notifications/notification.module';

@Module({
  imports: [
    ScheduleModule.forRoot(),
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
    MeasurementModule,
    LocationModule,
    NotificationModule,
  ],
  controllers: [],
  providers: [],
})
export class ApiModule {}
