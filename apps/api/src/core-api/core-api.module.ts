import { Module } from '@nestjs/common';
import { HttpModule } from '@nestjs/axios';
import { CoreApiController } from './core-api.controller';
import { CoreApiService } from 'src/utils/core-api.service';

@Module({
  imports: [HttpModule],
  controllers: [CoreApiController],
  providers: [CoreApiService],
})
export class CoreApiModule {}
