import { Module } from '@nestjs/common';
import { FiresDataController } from './fires-data.controller';
import { FiresDataService } from './fires-data.service';
import { FiresDataRepository } from './fires-data.repository';

@Module({
  controllers: [FiresDataController],
  providers: [FiresDataService, FiresDataRepository],
  exports: [FiresDataService, FiresDataRepository],
})
export class FiresDataModule {}
