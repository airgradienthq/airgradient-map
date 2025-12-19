import { Module } from '@nestjs/common';
import { WindDataController } from './wind-data.controller';
import { WindDataService } from './wind-data.service';
import { WindDataRepository } from './wind-data.repository';

@Module({
  controllers: [WindDataController],
  providers: [WindDataService, WindDataRepository],
  exports: [WindDataRepository],
})
export class WindDataModule {}
