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
                    PongYaeng: {
                      centerCoordinate: { latitude: 18.88, longitude: 98.84 },
                      subLevels: {},
                    },
                  },
                },
                HangDong: {
                  centerCoordinate: { latitude: 18.687, longitude: 98.935 },
                  subLevels: {
                    BanWaen: {
                      centerCoordinate: { latitude: 18.7, longitude: 98.93 },
                      subLevels: {},
                    },
                    NongKaew: {
                      centerCoordinate: { latitude: 18.68, longitude: 98.92 },
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
                PathumWan: {
                  centerCoordinate: { latitude: 13.746, longitude: 100.532 },
                  subLevels: {
                    Lumphini: {
                      centerCoordinate: { latitude: 13.73, longitude: 100.541 },
                      subLevels: {},
                    },
                    RongMueang: {
                      centerCoordinate: { latitude: 13.746, longitude: 100.516 },
                      subLevels: {},
                    },
                  },
                },
              },
            },
          },
        },
        Vietnam: {
          centerCoordinate: { latitude: 14.0583, longitude: 108.2772 },
          subLevels: {
            Hanoi: {
              centerCoordinate: { latitude: 21.0278, longitude: 105.8342 },
              subLevels: {
                HoanKiem: {
                  centerCoordinate: { latitude: 21.0285, longitude: 105.8542 },
                  subLevels: {},
                },
                TayHo: {
                  centerCoordinate: { latitude: 21.081, longitude: 105.818 },
                  subLevels: {},
                },
              },
            },
            HoChiMinh: {
              centerCoordinate: { latitude: 10.7769, longitude: 106.7009 },
              subLevels: {
                District1: {
                  centerCoordinate: { latitude: 10.7758, longitude: 106.7004 },
                  subLevels: {},
                },
                District3: {
                  centerCoordinate: { latitude: 10.787, longitude: 106.685 },
                  subLevels: {},
                },
              },
            },
          },
        },
        Japan: {
          centerCoordinate: { latitude: 36.2048, longitude: 138.2529 },
          subLevels: {
            Tokyo: {
              centerCoordinate: { latitude: 35.6895, longitude: 139.6917 },
              subLevels: {
                Shinjuku: {
                  centerCoordinate: { latitude: 35.6938, longitude: 139.7034 },
                  subLevels: {},
                },
                Shibuya: {
                  centerCoordinate: { latitude: 35.6595, longitude: 139.7005 },
                  subLevels: {},
                },
              },
            },
            Osaka: {
              centerCoordinate: { latitude: 34.6937, longitude: 135.5023 },
              subLevels: {
                Namba: {
                  centerCoordinate: { latitude: 34.6664, longitude: 135.501 },
                  subLevels: {},
                },
                Umeda: {
                  centerCoordinate: { latitude: 34.7025, longitude: 135.4959 },
                  subLevels: {},
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

  async getAllAdminBoundaries(): Promise<object> {
    return this.adminBoundaries['World'].subLevels;
  }
}
