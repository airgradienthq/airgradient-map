import { Module } from '@nestjs/common';
import { OutlierRepository } from './outlier.repository';
import { OutlierService } from './outlier.service';

@Module({
  providers: [OutlierService, OutlierRepository],
  exports: [OutlierService],
})
export class OutlierModule {}
