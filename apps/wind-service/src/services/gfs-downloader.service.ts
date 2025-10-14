import * as https from 'https';
import * as fs from 'fs';
import * as path from 'path';

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

  async downloadGFSData(): Promise<string | null> {
    const today = new Date();
    const yesterday = new Date(today);
    yesterday.setDate(yesterday.getDate() - 1);

    for (const date of [today, yesterday]) {
      const dateStr = date.toISOString().slice(0, 10).replace(/-/g, '');

      for (const cycle of ['18', '12', '06', '00']) {
        const url =
          `https://nomads.ncep.noaa.gov/cgi-bin/filter_gfs_0p25.pl?` +
          `dir=%2Fgfs.${dateStr}%2F${cycle}%2Fatmos&file=gfs.t${cycle}z.pgrb2.0p25.f000&` +
          `var_UGRD=on&var_VGRD=on&lev_10_m_above_ground=on`;

        const filePath = path.join(this.tempDir, `wind-${dateStr}-${cycle}.grib2`);

        console.log(`[GFS] Trying ${dateStr} ${cycle}z...`);

        if (await this.downloadFile(url, filePath)) {
          const stats = fs.statSync(filePath);
          if (stats.size > 1000) {
            console.log(`Downloaded ${stats.size} bytes from ${dateStr} ${cycle}z`);
            return filePath;
          }
          fs.unlinkSync(filePath);
        }
      }
    }

    console.log('All download attempts failed');
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
          file.on('error', () => {
            file.close();
            if (fs.existsSync(dest)) fs.unlinkSync(dest);
            resolve(false);
          });
        })
        .on('error', () => {
          file.close();
          if (fs.existsSync(dest)) fs.unlinkSync(dest);
          resolve(false);
        });
    });
  }
}