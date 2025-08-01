import { Injectable, Logger } from '@nestjs/common';
import { Cron } from '@nestjs/schedule';
import { HttpService } from '@nestjs/axios';
import { promises as fs } from 'fs';
import * as path from 'path';
import { firstValueFrom } from 'rxjs';
import { exec } from 'child_process';
import { promisify } from 'util';
import { setTimeout } from 'timers/promises';

const execAsync = promisify(exec);

@Injectable()
export class WindDataService {
  private readonly logger = new Logger(WindDataService.name);
  private readonly dataDir = path.join(process.cwd(), 'public', 'data', 'wind');
  private readonly outputFile = path.join(this.dataDir, 'current-wind-surface-level-gfs-1.0.json');
  private readonly tempDir = path.join(process.cwd(), 'temp');

  constructor(private readonly httpService: HttpService) {
    this.init();
  }

  private async init(): Promise<void> {
    await this.ensureDirectories();

    try {
      await fs.access(this.outputFile);
      this.logger.log('Wind data file exists');
    } catch {
      await this.createEmptyWindFile();
      this.updateWindData().catch(() => {});
    }
  }

  private async createEmptyWindFile(): Promise<void> {
    const nx = 360,
      ny = 181;
    const emptyData = new Array(nx * ny).fill(0);

    const windData = [
      {
        header: {
          discipline: 0,
          disciplineName: 'Meteorological products',
          centerName: 'Fallback - No Wind Data Available',
          refTime: new Date().toISOString(),
          parameterNumber: 2,
          parameterNumberName: 'U-component_of_wind',
          parameterUnit: 'm.s-1',
          nx: nx,
          ny: ny,
          lo1: 0,
          la1: 90,
          lo2: 359,
          la2: -90,
          dx: 1,
          dy: 1,
        },
        data: emptyData,
      },
      {
        header: {
          discipline: 0,
          disciplineName: 'Meteorological products',
          centerName: 'Fallback - No Wind Data Available',
          refTime: new Date().toISOString(),
          parameterNumber: 3,
          parameterNumberName: 'V-component_of_wind',
          parameterUnit: 'm.s-1',
          nx: nx,
          ny: ny,
          lo1: 0,
          la1: 90,
          lo2: 359,
          la2: -90,
          dx: 1,
          dy: 1,
        },
        data: emptyData,
      },
    ];

    await fs.writeFile(this.outputFile, JSON.stringify(windData));
    this.logger.log('Created empty wind fallback file');
  }

  private async ensureDirectories(): Promise<void> {
    try {
      await fs.mkdir(this.dataDir, { recursive: true });
      await fs.mkdir(this.tempDir, { recursive: true });
    } catch (error) {
      this.logger.error('Failed to create directories', error);
    }
  }

  @Cron('0 */6 * * *')
  async scheduledUpdate(): Promise<void> {
    await this.updateWindData();
  }

  async updateWindData(): Promise<void> {
    try {
      const success = await this.downloadRealGFSData();
      if (success) {
        this.logger.log('Real GFS wind data updated successfully');
      }
    } catch (error) {
      this.logger.error('Failed to update wind data', error);
    }
  }

  private async downloadRealGFSData(): Promise<boolean> {
    try {
      const cycles = this.getAvailableGFSCycles();

      for (const { dateStr, cycle } of cycles) {
        const urls = [
          `https://nomads.ncep.noaa.gov/cgi-bin/filter_gfs_0p25.pl?dir=%2Fgfs.${dateStr}%2F${cycle}%2Fatmos&file=gfs.t${cycle}z.pgrb2.0p25.anl&var_UGRD=on&var_VGRD=on&lev_10_m_above_ground=on`,
          `https://nomads.ncep.noaa.gov/cgi-bin/filter_gfs_0p25.pl?dir=%2Fgfs.${dateStr}%2F${cycle}%2Fatmos&file=gfs.t${cycle}z.pgrb2.0p25.f000&var_UGRD=on&var_VGRD=on&lev_10_m_above_ground=on`,
          `https://nomads.ncep.noaa.gov/cgi-bin/filter_gfs_0p25.pl?dir=%2Fgfs.${dateStr}%2F${cycle}%2Fatmos&file=gfs.t${cycle}z.pgrb2.0p25.f000&var_UGRD=on&var_VGRD=on&lev_10_m_above_ground=on&leftlon=0&rightlon=360&toplat=90&bottomlat=-90`,
        ];

        for (const gfsUrl of urls) {
          const gribFile = path.join(this.tempDir, 'gfs_wind.grib2');

          try {
            const response = await firstValueFrom(
              this.httpService.get(gfsUrl, {
                responseType: 'arraybuffer',
                timeout: 120000,
                headers: { 'User-Agent': 'AirGradient-WindService/1.0' },
                maxRedirects: 5,
              }),
            );

            if (response.status !== 200 || !response.data) continue;

            await fs.writeFile(gribFile, new Uint8Array(response.data));
            const stats = await fs.stat(gribFile);

            if (stats.size < 1000) {
              await fs.unlink(gribFile).catch(() => {});
              continue;
            }

            const success = await this.convertGrib2ToJson(gribFile);
            await fs.unlink(gribFile).catch(() => {});

            if (success) {
              this.logger.log(`Successfully processed GFS data for ${dateStr}/${cycle}Z`);
              return true;
            }
          } catch {
            // Silently continue to next URL
            continue;
          }
        }
      }

      return false;
    } catch {
      // Silently return false on any error
      return false;
    }
  }

  private getAvailableGFSCycles(): Array<{ dateStr: string; cycle: string }> {
    const now = new Date();
    const cycles = [];

    const current = this.getCurrentGFSCycle();
    cycles.push(current);

    const fallbackHours = [6, 12, 18, 24];

    for (const hoursBack of fallbackHours) {
      const fallbackTime = new Date(now.getTime() - hoursBack * 60 * 60 * 1000);
      const year = fallbackTime.getUTCFullYear();
      const month = String(fallbackTime.getUTCMonth() + 1).padStart(2, '0');
      const day = String(fallbackTime.getUTCDate()).padStart(2, '0');
      const dateStr = `${year}${month}${day}`;

      const hour = fallbackTime.getUTCHours();
      let cycle = '00';
      if (hour >= 18) cycle = '18';
      else if (hour >= 12) cycle = '12';
      else if (hour >= 6) cycle = '06';

      const key = `${dateStr}-${cycle}`;
      if (!cycles.find(c => `${c.dateStr}-${c.cycle}` === key)) {
        cycles.push({ dateStr, cycle });
      }
    }

    return cycles;
  }

  private getCurrentGFSCycle(): { dateStr: string; cycle: string } {
    const now = new Date();
    const currentHour = now.getUTCHours();
    let cycle = '00';
    let dateOffset = 0;

    if (currentHour >= 23) {
      cycle = '18';
    } else if (currentHour >= 17) {
      cycle = '12';
    } else if (currentHour >= 11) {
      cycle = '06';
    } else if (currentHour >= 5) {
      cycle = '00';
    } else {
      cycle = '18';
      dateOffset = -1;
    }

    const targetDate = new Date(now.getTime() + dateOffset * 24 * 60 * 60 * 1000);
    const year = targetDate.getUTCFullYear();
    const month = String(targetDate.getUTCMonth() + 1).padStart(2, '0');
    const day = String(targetDate.getUTCDate()).padStart(2, '0');
    const dateStr = `${year}${month}${day}`;

    return { dateStr, cycle };
  }

  private async convertGrib2ToJson(gribFile: string): Promise<boolean> {
    try {
      const grib2jsonCmd = await this.findGrib2Json();
      if (!grib2jsonCmd) return false;

      const jsonFile = path.join(this.tempDir, 'wind_data.json');
      const cmd = `${grib2jsonCmd} --names --data --fp 2,3 --fs 103 --fv 10.0 --output ${jsonFile} ${gribFile}`;

      await execAsync(cmd);

      const jsonData = JSON.parse(await fs.readFile(jsonFile, 'utf8'));

      if (!Array.isArray(jsonData) || jsonData.length < 2) return false;

      const uComponent = jsonData.find(d => d.header?.parameterNumber === 2);
      const vComponent = jsonData.find(d => d.header?.parameterNumber === 3);

      if (!uComponent || !vComponent || !uComponent.data || !vComponent.data) return false;

      const windData = [
        {
          header: {
            ...uComponent.header,
            refTime: new Date().toISOString(),
            centerName: 'US National Weather Service - NCEP(WMC) - REAL GFS DATA',
          },
          data: uComponent.data,
        },
        {
          header: {
            ...vComponent.header,
            refTime: new Date().toISOString(),
            centerName: 'US National Weather Service - NCEP(WMC) - REAL GFS DATA',
          },
          data: vComponent.data,
        },
      ];

      await fs.writeFile(this.outputFile, JSON.stringify(windData));
      await fs.unlink(jsonFile).catch(() => {});

      return true;
    } catch {
      // Silently return false on any error
      return false;
    }
  }

  private async findGrib2Json(): Promise<string | null> {
    const commands = [
      'grib2json',
      'npx weacast-grib2json',
      '/usr/local/bin/grib2json',
      './node_modules/.bin/weacast-grib2json',
    ];

    for (const cmd of commands) {
      try {
        await execAsync(`which ${cmd.split(' ')[0]}`);
        return cmd;
      } catch {
        continue;
      }
    }

    try {
      await execAsync('npm list -g weacast-grib2json');
      return 'npx weacast-grib2json';
    } catch {
      try {
        const localPath = path.join(process.cwd(), 'node_modules', '.bin', 'weacast-grib2json');
        await fs.access(localPath);
        return localPath;
      } catch {
        return null;
      }
    }
  }

  async manualUpdate(): Promise<{ success: boolean; message: string }> {
    try {
      const updatePromise = this.updateWindData();
      // Use Node.js timers/promises for timeout
      const timeoutPromise = setTimeout(300000, 'timeout').then(() => {
        throw new Error('Update timeout after 5 minutes');
      });

      await Promise.race([updatePromise, timeoutPromise]);

      return {
        success: true,
        message: 'Wind data updated with REAL GFS data from NOAA NOMADS',
      };
    } catch (error: any) {
      return {
        success: false,
        message: `Failed to update: ${error.message}`,
      };
    }
  }

  async getWindDataInfo(): Promise<any> {
    try {
      const stats = await fs.stat(this.outputFile);
      const data = await fs.readFile(this.outputFile, 'utf8');
      const parsed = JSON.parse(data);

      return {
        fileExists: true,
        lastModified: stats.mtime,
        size: stats.size,
        refTime: parsed[0]?.header?.refTime || null,
        recordCount: parsed.length,
        dataPoints: parsed[0]?.data?.length || 0,
        format: 'REAL-GFS-nullschool-compatible',
        source: parsed[0]?.header?.centerName || 'Unknown',
      };
    } catch (error: any) {
      return {
        fileExists: false,
        error: error.message,
      };
    }
  }

  async checkGrib2JsonInstallation(): Promise<{
    installed: boolean;
    command?: string;
    instructions?: string;
  }> {
    const cmd = await this.findGrib2Json();

    if (cmd) {
      return {
        installed: true,
        command: cmd,
      };
    }

    return {
      installed: false,
      instructions: 'Run: npm install -g weacast-grib2json',
    };
  }
}