import { Injectable, NotFoundException } from '@nestjs/common';
import { GetAdminBoundaryParamsDto } from './dto/get-admin-boundary-params.dto';
import { GetAdminBoundaryResponseDto } from './dto/get-admin-boundary-response.dto';

@Injectable()
export class AdminBoundariesService {
  // TODO: Use data in database instead
  // Mock dataset
  private readonly adminBoundaries = {
    World: {
      centerCoordinate: { latitude: 0, longitude: 0 },
      subLevels: {
        Thailand: {
          centerCoordinate: { latitude: 13.7563, longitude: 100.5018 },
          subLevels: {
            ChiangMai: {
              centerCoordinate: { latitude: 18.7883, longitude: 98.9853 },
              subLevels: {
                MaeRim: {
                  centerCoordinate: { latitude: 18.9233, longitude: 98.9395 },
                  subLevels: {
                    RimTai: {
                      centerCoordinate: { latitude: 18.95, longitude: 98.93 },
                      subLevels: {},
                    },
                    MaeSa: {
                      centerCoordinate: { latitude: 18.92, longitude: 98.91 },
                      subLevels: {},
                    },
                  },
                },
              },
            },
            Bangkok: {
              centerCoordinate: { latitude: 13.7563, longitude: 100.5018 },
              subLevels: {
                PhraNakhon: {
                  centerCoordinate: { latitude: 13.7563, longitude: 100.5018 },
                  subLevels: {
                    BowonNiwet: {
                      centerCoordinate: { latitude: 13.758, longitude: 100.502 },
                      subLevels: {},
                    },
                    TalatYot: {
                      centerCoordinate: { latitude: 13.757, longitude: 100.503 },
                      subLevels: {},
                    },
                  },
                },
              },
            },
          },
        },
      },
    },
  };

  async getAdminBoundaries(
    getAdminBoundaryParamsDto: GetAdminBoundaryParamsDto,
  ): Promise<GetAdminBoundaryResponseDto> {
    const { country, level1, level2, level3 } = getAdminBoundaryParamsDto;
    const path = [country, level1, level2, level3].filter(Boolean);

    // Start at the new top level, e.g., "World"
    let node: any = this.adminBoundaries['World'];
    if (!node) throw new NotFoundException(`Top-level node 'World' not found`);

    for (const p of path) {
      // Traverse sublevels
      if (!node.subLevels || !node.subLevels[p]) {
        throw new NotFoundException(`Path not found: ${path.join('/')}`);
      }
      node = node.subLevels[p];
    }

    return {
      centerCoordinate: node.centerCoordinate,
      subLevels: Object.keys(node.subLevels ?? {}),
    };
  }
}
