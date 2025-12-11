import { Module } from '@nestjs/common';
import { ScheduleModule } from '@nestjs/schedule';
import DatabaseModule from './database/database.module';
import { ConfigModule, ConfigService } from '@nestjs/config';
import { MeasurementModule } from './measurement/measurement.module';
import { LocationModule } from './location/location.module';
import { WindDataModule } from './wind-data/wind-data.module';
import { NotificationModule } from './notifications/notification.module';
import { DashboardAuthModule } from './dashboard-auth/dashboard-auth.module';
import { APP_GUARD } from '@nestjs/core';
import { SoftAuthGuard } from './auth/guards/soft-auth.guard';

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
    WindDataModule,
    NotificationModule,
    DashboardAuthModule,
  ],
  controllers: [],
  providers: [
    {
      provide: APP_GUARD,
      useClass: SoftAuthGuard,
    },
  ],
})
export class ApiModule {}
