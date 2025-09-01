import { Injectable } from '@nestjs/common';

import { AdminBoundariesService } from 'src/admin-boundaries/admin-boundaries.service';

@Injectable()
export class SiteMapService {
  constructor(private readonly adminBoundariesService: AdminBoundariesService) {}

  public async getFullHierarchy() {
    return await this.adminBoundariesService.getAllAdminBoundaries();
  }

  flattenBoundaries(subLevels: any, prefix = ''): string[] {
    let urls: string[] = [];
    for (const key of Object.keys(subLevels)) {
      const path = `${prefix}/${key.toLowerCase()}`;
      urls.push(path);
      if (subLevels[key].subLevels && Object.keys(subLevels[key].subLevels).length > 0) {
        urls.push(...this.flattenBoundaries(subLevels[key].subLevels, path));
      }
    }
    return urls;
  }
}
