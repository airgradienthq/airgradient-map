import { Controller, Get, Res } from '@nestjs/common';
import { Response } from 'express';

import { SiteMapService } from './site-map.service';

@Controller('map/api/v1')
export class SitemapController {
  constructor(private readonly siteMapService: SiteMapService) {}

  @Get('sitemap.xml')
  async getSitemap(@Res() res: Response) {
    const boundaries = await this.siteMapService.getFullHierarchy();
    const urls = this.siteMapService.flattenBoundaries(boundaries);

    const sitemap = `<?xml version="1.0" encoding="UTF-8"?>
      <urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
        ${urls.map(u => `<url><loc>https://map-int.airgradient.com${u}</loc></url>`).join('')}
      </urlset>`;

    res.header('Content-Type', 'application/xml');
    res.send(sitemap);
  }
}
