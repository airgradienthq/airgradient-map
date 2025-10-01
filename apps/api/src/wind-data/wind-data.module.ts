import { Module } from '@nestjs/common';
import { WindDataService } from './wind-data.service';
import { WindDataController } from './wind-data.controller';

@Module({
  controllers: [WindDataController],
  providers: [WindDataService],
  exports: [WindDataService]
})
export class WindDataModule {}