import { Module } from '@nestjs/common';
import { AdminBoundariesService } from './admin-boundaries.service';
import { AdminBoundariesController } from './admin-boundaries.controller';

@Module({
  controllers: [AdminBoundariesController],
  providers: [AdminBoundariesService],
})
export class AdminBoundariesModule {}
