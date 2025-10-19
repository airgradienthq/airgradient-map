import { Module } from '@nestjs/common';
import { ScheduleModule } from '@nestjs/schedule';
import DatabaseModule from './database/database.module';
import { TasksModule } from './tasks/tasks.module';
import { ConfigModule, ConfigService } from '@nestjs/config';
import { MeasurementModule } from './measurement/measurement.module';
import { LocationModule } from './location/location.module';
import { ValkeyCacheModule } from './valkey-cache/valkey-cache.module';

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
    ValkeyCacheModule.forRootAsync({
      imports: [ConfigModule],
      inject: [ConfigService],
      useFactory: (configService: ConfigService) => ({
        host: configService.get('VALKEY_HOST'),
        port: +configService.get<number>('VALKEY_PORT'),
        dbId: +configService.get<number>('VALKEY_DB_ID') || 0,
      }),
    }),
  ],
  controllers: [],
  providers: [],
})
export class AppModule {}
