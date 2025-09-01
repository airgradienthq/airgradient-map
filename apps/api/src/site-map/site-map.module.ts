import { Module } from '@nestjs/common';
import { SitemapController } from './site-map.controller';
import { AdminBoundariesService } from 'src/admin-boundaries/admin-boundaries.service';
import { SiteMapService } from './site-map.service';

@Module({
  controllers: [SitemapController],
  providers: [SiteMapService, AdminBoundariesService],
})
export class SiteMapModule {}
