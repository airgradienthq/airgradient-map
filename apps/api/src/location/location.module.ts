import { Module } from '@nestjs/common';
import { LocationController } from './location.controller';
import { LocationService } from './location.service';
import LocationRepository from './location.repository';

@Module({
  controllers: [LocationController],
  providers: [LocationService, LocationRepository],
  exports: [LocationRepository],
})
export class LocationModule {}
