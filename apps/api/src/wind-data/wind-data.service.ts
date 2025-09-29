import { Injectable, Logger } from '@nestjs/common';
import { Cron, CronExpression } from '@nestjs/schedule';
import { promisify } from 'util';
import { exec } from 'child_process';
import * as fs from 'fs/promises';
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
  }

  private async initializeDirectories() {
    try {
      await fs.mkdir(this.dataDir, { recursive: true });
      await fs.mkdir(this.tempDir, { recursive: true });
      
      const fileExists = await fs
        .access(this.windFile)
        .then(() => true)
        .catch(() => false);

      if (!fileExists) {
        this.logger.log('Wind data file does not exist, creating fallback');
        await this.createFallbackData();
      } else {
        this.logger.log('Wind data file exists');
      }
    } catch (error) {
      this.logger.error('Failed to initialize directories', error);
    }
  }

  @Cron(CronExpression.EVERY_6_HOURS)
  async updateWindDataCron() {
    this.logger.log('Running scheduled wind data update');
    await this.updateWindData();
  }

  async updateWindData(): Promise<{ success: boolean; message: string }> {
    this.logger.log('Starting wind data update...');

    try {
      // Check if grib2json is available
      const grib2jsonPath = await this.findGrib2Json();
      if (!grib2jsonPath) {
        this.logger.error('grib2json not found, using fallback data');
        await this.createFallbackData();
        return { success: true, message: 'Using fallback data (grib2json not available)' };
      }

      this.logger.log(`Found grib2json at: ${grib2jsonPath}`);

      // Try to download and process real GFS data
      const gribFile = await this.downloadGFSData();
      
      if (!gribFile) {
        this.logger.warn('Failed to download GFS data, using fallback');
        await this.createFallbackData();
        return { success: true, message: 'Using fallback data (download failed)' };
      }

      this.logger.log(`Downloaded GRIB file: ${gribFile}`);

      // Convert GRIB to JSON
      const success = await this.convertGribToJson(grib2jsonPath, gribFile);
      
      // Clean up temp file
      try {
        await fs.unlink(gribFile);
      } catch (e) {
        this.logger.warn('Failed to delete temp GRIB file', e);
      }

      if (success) {
        this.logger.log('Successfully updated wind data with real GFS data');
        return { success: true, message: 'Wind data updated with REAL GFS data from NOAA NOMADS' };
      } else {
        this.logger.error('Failed to convert GRIB to JSON, using fallback');
        await this.createFallbackData();
        return { success: true, message: 'Using fallback data (conversion failed)' };
      }
    } catch (error) {
      this.logger.error('Error updating wind data:', error);
      await this.createFallbackData();
      return { success: false, message: `Error: ${error.message}` };
    }
  }

  private async findGrib2Json(): Promise<string | null> {
    const commands = [
      '/usr/local/bin/grib2json',
      'grib2json',
      '/usr/local/lib/node_modules/weacast-grib2json/bin/grib2json',
    ];

    for (const cmd of commands) {
      try {
        await execAsync(`${cmd} --help 2>&1`);
        return cmd;
      } catch {
        continue;
      }
    }

    return null;
  }

  private async downloadGFSData(): Promise<string | null> {
    const now = new Date();
    
    // Try different forecast cycles (most recent first)
    const cycles = ['18', '12', '06', '00'];
    const dateStr = now.toISOString().slice(0, 10).replace(/-/g, '');
    
    // Also try yesterday's data
    const yesterday = new Date(now);
    yesterday.setDate(yesterday.getDate() - 1);
    const yesterdayStr = yesterday.toISOString().slice(0, 10).replace(/-/g, '');
    
    const dates = [dateStr, yesterdayStr];

    for (const date of dates) {
      for (const cycle of cycles) {
        const url = `https://nomads.ncep.noaa.gov/cgi-bin/filter_gfs_0p25.pl?` +
          `dir=%2Fgfs.${date}%2F${cycle}%2Fatmos&` +
          `file=gfs.t${cycle}z.pgrb2.0p25.f000&` +
          `var_UGRD=on&var_VGRD=on&` +
          `lev_10_m_above_ground=on`;

        this.logger.log(`Trying to download: ${date} cycle ${cycle}z`);
        
        const filePath = path.join(this.tempDir, `wind-${date}-${cycle}.grib2`);
        
        try {
          const downloaded = await this.downloadFile(url, filePath);
          if (downloaded) {
            // Check file size
            const stats = await fs.stat(filePath);
            if (stats.size > 1000) {
              this.logger.log(`Successfully downloaded ${stats.size} bytes`);
              return filePath;
            } else {
              this.logger.warn(`File too small (${stats.size} bytes), trying next option`);
              await fs.unlink(filePath).catch(() => {});
            }
          }
        } catch (error) {
          this.logger.warn(`Failed to download ${date} ${cycle}z:`, error.message);
        }
      }
    }

    return null;
  }

  private downloadFile(url: string, dest: string): Promise<boolean> {
    return new Promise((resolve) => {
      const file = require('fs').createWriteStream(dest);
      
      https.get(url, { timeout: 30000 }, (response) => {
        if (response.statusCode !== 200) {
          this.logger.error(`HTTP ${response.statusCode} for ${url}`);
          file.close();
          fs.unlink(dest).catch(() => {});
          resolve(false);
          return;
        }

        response.pipe(file);

        file.on('finish', () => {
          file.close();
          resolve(true);
        });

        file.on('error', (err) => {
          this.logger.error('File write error:', err);
          file.close();
          fs.unlink(dest).catch(() => {});
          resolve(false);
        });
      }).on('error', (err) => {
        this.logger.error('Download error:', err);
        file.close();
        fs.unlink(dest).catch(() => {});
        resolve(false);
      });
    });
  }

  private async convertGribToJson(grib2jsonPath: string, gribFile: string): Promise<boolean> {
    try {
      const outputU = path.join(this.tempDir, 'wind-u.json');
      const outputV = path.join(this.tempDir, 'wind-v.json');

      // Convert U component
      const cmdU = `${grib2jsonPath} --names --data --fp 2 --fs 103 --fv 10.0 --output ${outputU} ${gribFile}`;
      this.logger.log('Converting U component...');
      await execAsync(cmdU, { timeout: 60000 });

      // Convert V component  
      const cmdV = `${grib2jsonPath} --names --data --fp 3 --fs 103 --fv 10.0 --output ${outputV} ${gribFile}`;
      this.logger.log('Converting V component...');
      await execAsync(cmdV, { timeout: 60000 });

      // Read and combine
      const uData = JSON.parse(await fs.readFile(outputU, 'utf8'));
      const vData = JSON.parse(await fs.readFile(outputV, 'utf8'));

      // Write combined file
      const combined = [uData[0], vData[0]];
      await fs.writeFile(this.windFile, JSON.stringify(combined));

      // Clean up temp files
      await fs.unlink(outputU).catch(() => {});
      await fs.unlink(outputV).catch(() => {});

      this.logger.log('Successfully converted and saved wind data');
      return true;
    } catch (error) {
      this.logger.error('Error converting GRIB to JSON:', error);
      return false;
    }
  }

  private async createFallbackData() {
    this.logger.log('Creating fallback wind data');
    
    const fallback = [
      {
        header: {
          discipline: 0,
          disciplineName: 'Meteorological products',
          centerName: 'Fallback - No Wind Data Available',
          refTime: new Date().toISOString(),
          parameterNumber: 2,
          parameterNumberName: 'U-component_of_wind',
          parameterUnit: 'm.s-1',
          nx: 360,
          ny: 181,
          lo1: 0,
          la1: 90,
          lo2: 359,
          la2: -90,
          dx: 1,
          dy: 1,
        },
        data: Array(360 * 181).fill(0),
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
          nx: 360,
          ny: 181,
          lo1: 0,
          la1: 90,
          lo2: 359,
          la2: -90,
          dx: 1,
          dy: 1,
        },
        data: Array(360 * 181).fill(0),
      },
    ];

    await fs.writeFile(this.windFile, JSON.stringify(fallback));
  }

  async getWindDataInfo() {
    try {
      const stats = await fs.stat(this.windFile);
      const content = await fs.readFile(this.windFile, 'utf8');
      const data = JSON.parse(content);

      return {
        fileExists: true,
        lastModified: stats.mtime,
        size: stats.size,
        refTime: data[0]?.header?.refTime,
        recordCount: data.length,
        dataPoints: data[0]?.data?.length || 0,
        format: 'REAL-GFS-nullschool-compatible',
        source: data[0]?.header?.centerName,
      };
    } catch (error) {
      return {
        fileExists: false,
        error: error.message,
      };
    }
  }

  async getWindData() {
    try {
      const content = await fs.readFile(this.windFile, 'utf8');
      return JSON.parse(content);
    } catch (error) {
      this.logger.error('Error reading wind data:', error);
      throw error;
    }
  }

  async checkGrib2JsonAvailability() {
    const path = await this.findGrib2Json();
    if (path) {
      try {
        const { stdout } = await execAsync(`${path} --help`);
        return { available: true, path, help: stdout };
      } catch (error) {
        return { available: false, error: error.message };
      }
    }
    return { available: false, error: 'grib2json not found' };
  }
}