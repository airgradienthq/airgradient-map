import { Module } from '@nestjs/common';
import { MeasurementController } from './measurement.controller';
import { MeasurementService } from './measurement.service';
import MeasurementRepository from './measurement.repository';
import { OutlierModule } from 'src/outlier/outlier.module';

@Module({
  imports: [OutlierModule],
  controllers: [MeasurementController],
  providers: [MeasurementService, MeasurementRepository],
})
export class MeasurementModule {}
