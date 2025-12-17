import { Module } from '@nestjs/common';
import { AqiStandardsController } from './aqi-standards.controller';
import { AqiStandardsService } from './aqi-standards.service';

@Module({
  controllers: [AqiStandardsController],
  providers: [AqiStandardsService],
})
export class AqiStandardsModule {}
