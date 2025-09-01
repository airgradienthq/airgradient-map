import { Module } from '@nestjs/common';
import { ScheduleModule } from '@nestjs/schedule';
import DatabaseModule from './database/database.module';
import { TasksModule } from './tasks/tasks.module';
import { ConfigModule, ConfigService } from '@nestjs/config';
import { MeasurementModule } from './measurement/measurement.module';
import { LocationModule } from './location/location.module';
import { AdminBoundariesModule } from './admin-boundaries/admin-boundaries.module';
import { SiteMapModule } from './site-map/site-map.module';

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
    MeasurementModule,
    LocationModule,
    AdminBoundariesModule,
    SiteMapModule,
  ],
  controllers: [],
  providers: [],
})
export class AppModule {}
