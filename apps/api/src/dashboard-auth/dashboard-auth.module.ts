import { Module } from '@nestjs/common';
import { DashboardAuthController } from './dashboard-auth.controller';
import { DashboardApiService } from 'src/utils/dashboard-api.service';

@Module({
  controllers: [DashboardAuthController],
  providers: [DashboardApiService],
})
export class DashboardAuthModule {}
