import { Injectable, Logger } from '@nestjs/common';
import { Cron, CronExpression } from '@nestjs/schedule';
import { promisify } from 'util';
import { exec } from 'child_process';
import * as fs from 'fs/promises';
import * as fsSync from 'fs';
import * as path from 'path';
import * as https from 'https';

const execAsync = promisify(exec);

@Injectable()
export class WindDataService {
  private readonly logger = new Logger(WindDataService.name);
  private readonly dataDir = path.join(process.cwd(), 'public', 'data', 'wind');
  private readonly tempDir = path.join(process.cwd(), 'temp');
  private readonly windFile = path.join(this.dataDir, 'current-wind-surface-level-gfs-1.0.json');

  constructor() {
    this.initializeDirectories();
    this.updateWindData().catch(err => this.logger.error('Initial update failed:', err));
  }

  private async initializeDirectories() {
    try {
      await fs.mkdir(this.dataDir, { recursive: true });
      await fs.mkdir(this.tempDir, { recursive: true });

      const exists = await fs.access(this.windFile).then(() => true).catch(() => false);
      if (!exists) await this.createFallbackData();
    } catch (error) {
      this.logger.error('Directory initialization failed', error);
    }
  }

  @Cron(CronExpression.EVERY_6_HOURS)
  async updateWindDataCron() {
    await this.updateWindData();
  }

  async updateWindData(): Promise<{ success: boolean; message: string }> {
    try {
      const grib2jsonPath = await this.findGrib2Json();
      if (!grib2jsonPath) {
        await this.createFallbackData();
        return { success: true, message: 'Using fallback data (grib2json not available)' };
      }

      const gribFile = await this.downloadGFSData();
      if (!gribFile) {
        await this.createFallbackData();
        return { success: true, message: 'Using fallback data (download failed)' };
      }

      const success = await this.convertGribToJson(grib2jsonPath, gribFile);
      await fs.unlink(gribFile).catch(() => {});

      if (success) {
        this.logger.log('Wind data updated with real GFS data');
        return { success: true, message: 'Wind data updated with REAL GFS data from NOAA NOMADS' };
      }

      await this.createFallbackData();
      return { success: true, message: 'Using fallback data (conversion failed)' };
    } catch (error) {
      this.logger.error('Wind data update failed:', error);
      await this.createFallbackData();
      return { success: false, message: `Error: ${error.message}` };
    }
  }

  private async findGrib2Json(): Promise<string | null> {
    for (const cmd of ['/usr/local/bin/grib2json', 'grib2json', '/usr/local/lib/node_modules/weacast-grib2json/bin/grib2json']) {
      try {
        await execAsync(`${cmd} --help 2>&1`);
        return cmd;
      } catch {}
    }
    return null;
  }

  private async downloadGFSData(): Promise<string | null> {
    const today = new Date();
    const yesterday = new Date(today);
    yesterday.setDate(yesterday.getDate() - 1);

    for (const date of [today, yesterday]) {
      const dateStr = date.toISOString().slice(0, 10).replace(/-/g, '');
      
      for (const cycle of ['18', '12', '06', '00']) {
        const url = `https://nomads.ncep.noaa.gov/cgi-bin/filter_gfs_0p25.pl?` +
          `dir=%2Fgfs.${dateStr}%2F${cycle}%2Fatmos&file=gfs.t${cycle}z.pgrb2.0p25.f000&` +
          `var_UGRD=on&var_VGRD=on&lev_10_m_above_ground=on`;

        const filePath = path.join(this.tempDir, `wind-${dateStr}-${cycle}.grib2`);

        if (await this.downloadFile(url, filePath)) {
          const stats = await fs.stat(filePath);
          if (stats.size > 1000) {
            this.logger.log(`Downloaded ${stats.size} bytes from ${dateStr} ${cycle}z`);
            return filePath;
          }
          await fs.unlink(filePath).catch(() => {});
        }
      }
    }
    return null;
  }

  private downloadFile(url: string, dest: string): Promise<boolean> {
    return new Promise(resolve => {
      const file = fsSync.createWriteStream(dest);

      https.get(url, { timeout: 30000 }, response => {
        if (response.statusCode !== 200) {
          file.close();
          resolve(false);
          return;
        }

        response.pipe(file);
        file.on('finish', () => { file.close(); resolve(true); });
        file.on('error', () => { file.close(); resolve(false); });
      }).on('error', () => { file.close(); resolve(false); });
    });
  }

  private async convertGribToJson(grib2jsonPath: string, gribFile: string): Promise<boolean> {
    try {
      const [outputU, outputV] = ['wind-u.json', 'wind-v.json'].map(f => path.join(this.tempDir, f));

      await execAsync(`${grib2jsonPath} --names --data --fp 2 --fs 103 --fv 10.0 -o ${outputU} ${gribFile}`, { timeout: 60000 });
      await execAsync(`${grib2jsonPath} --names --data --fp 3 --fs 103 --fv 10.0 -o ${outputV} ${gribFile}`, { timeout: 60000 });

      const [uData, vData] = await Promise.all([outputU, outputV].map(f => fs.readFile(f, 'utf8').then(JSON.parse)));
      await fs.writeFile(this.windFile, JSON.stringify([uData[0], vData[0]]));
      await Promise.all([outputU, outputV].map(f => fs.unlink(f).catch(() => {})));

      return true;
    } catch (error) {
      this.logger.error('GRIB conversion failed:', error);
      return false;
    }
  }

  private async createFallbackData() {
    const createComponent = (num: number, name: string) => ({
      header: {
        discipline: 0,
        disciplineName: 'Meteorological products',
        centerName: 'Fallback - No Wind Data Available',
        refTime: new Date().toISOString(),
        parameterNumber: num,
        parameterNumberName: name,
        parameterUnit: 'm.s-1',
        nx: 360,
        ny: 181,
        lo1: 0,
        la1: 90,
        lo2: 359,
        la2: -90,
        dx: 1,
        dy: 1
      },
      data: Array(65160).fill(0)
    });

    await fs.writeFile(this.windFile, JSON.stringify([
      createComponent(2, 'U-component_of_wind'),
      createComponent(3, 'V-component_of_wind')
    ]));
  }

  async getWindDataInfo() {
    try {
      const [stats, data] = await Promise.all([
        fs.stat(this.windFile),
        fs.readFile(this.windFile, 'utf8').then(JSON.parse)
      ]);

      return {
        fileExists: true,
        lastModified: stats.mtime,
        size: stats.size,
        refTime: data[0]?.header?.refTime,
        recordCount: data.length,
        dataPoints: data[0]?.data?.length || 0,
        source: data[0]?.header?.centerName
      };
    } catch (error) {
      return { fileExists: false, error: error.message };
    }
  }

  async getWindData() {
    return fs.readFile(this.windFile, 'utf8').then(JSON.parse);
  }

  async checkGrib2JsonAvailability() {
    const path = await this.findGrib2Json();
    if (!path) return { available: false, error: 'grib2json not found' };

    try {
      const { stdout } = await execAsync(`${path} --help`);
      return { available: true, path, help: stdout };
    } catch (error) {
      return { available: false, error: error.message };
    }
  }
}