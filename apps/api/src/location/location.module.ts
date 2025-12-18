import { Module } from '@nestjs/common';
import { LocationController } from './location.controller';
import { LocationService } from './location.service';
import LocationRepository from './location.repository';
import { OutlierModule } from 'src/outlier/outlier.module';

@Module({
  imports: [OutlierModule],
  controllers: [LocationController],
  providers: [LocationService, LocationRepository],
  exports: [LocationRepository],
})
export class LocationModule {}
