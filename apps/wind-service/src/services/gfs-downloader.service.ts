import * as https from 'https';
import * as fs from 'fs';
import * as path from 'path';
import { gfsLogger } from '../utils/logger';

export class GFSDownloaderService {
  private readonly tempDir = path.join(process.cwd(), 'temp');

  constructor() {
    this.ensureTempDir();
  }

  private ensureTempDir() {
    if (!fs.existsSync(this.tempDir)) {
      fs.mkdirSync(this.tempDir, { recursive: true });
    }
  }

  /**
   * Downloads GFS wind data from NOAA at 1° resolution
   * Tries latest available cycles in order: 18z, 12z, 06z, 00z
   * GFS data is typically available 3-5 hours after model run time
   *
   */
  async downloadGFSData(): Promise<string | null> {
    const today = new Date();
    const yesterday = new Date(today);
    yesterday.setDate(yesterday.getDate() - 1);

    for (const date of [today, yesterday]) {
      const dateStr = date.toISOString().slice(0, 10).replace(/-/g, '');

      for (const cycle of ['18', '12', '06', '00']) {
        const url =
          `https://nomads.ncep.noaa.gov/cgi-bin/filter_gfs_1p00.pl?` +
          `dir=%2Fgfs.${dateStr}%2F${cycle}%2Fatmos&file=gfs.t${cycle}z.pgrb2.1p00.f000&` +
          `var_UGRD=on&var_VGRD=on&lev_10_m_above_ground=on`;

        const filePath = path.join(this.tempDir, `wind-${dateStr}-${cycle}.grib2`);

        if (await this.downloadFile(url, filePath)) {
          const stats = fs.statSync(filePath);
          if (stats.size > 1000) {
            gfsLogger.info('GFS download successful', {
              date: dateStr,
              cycle: `${cycle}z`,
              fileSize: `${(stats.size / 1024).toFixed(1)} KB`
            });
            return filePath;
          } else {
            fs.unlinkSync(filePath);
          }
        }
      }
    }

    gfsLogger.error('All GFS download attempts failed');
    return null;
  }

  private downloadFile(url: string, dest: string): Promise<boolean> {
    return new Promise(resolve => {
      const file = fs.createWriteStream(dest);

      https
        .get(url, { timeout: 30000 }, response => {
          if (response.statusCode !== 200) {
            file.close();
            if (fs.existsSync(dest)) fs.unlinkSync(dest);
            resolve(false);
            return;
          }

          response.pipe(file);
          file.on('finish', () => {
            file.close();
            resolve(true);
          });
          file.on('error', (error) => {
            gfsLogger.error('File write error', { error: error.message });
            file.close();
            if (fs.existsSync(dest)) fs.unlinkSync(dest);
            resolve(false);
          });
        })
        .on('error', (error) => {
          gfsLogger.error('Network error during download', { error: error.message });
          file.close();
          if (fs.existsSync(dest)) fs.unlinkSync(dest);
          resolve(false);
        });
    });
  }
}