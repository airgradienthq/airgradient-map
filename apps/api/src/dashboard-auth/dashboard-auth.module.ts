import { Module } from '@nestjs/common';
import { HttpModule } from '@nestjs/axios';
import { DashboardAuthController } from './dashboard-auth.controller';
import { DashboardApiService } from 'src/utils/dashboard-api.service';

@Module({
  imports: [HttpModule],
  controllers: [DashboardAuthController],
  providers: [DashboardApiService],
})
export class DashboardAuthModule {}
