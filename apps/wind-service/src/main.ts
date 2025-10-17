import * as cron from 'node-cron';
import { GFSDownloaderService } from './services/gfs-downloader.service';
import { GribConverterService } from './services/grib-converter.service';
import { S3UploaderService } from './services/s3-uploader.service';
 /**
   * Processes wind data from NOAA GFS
   * Runs immediately on startup + every 6 hours (00:00, 06:00, 12:00, 18:00 UTC)
   * Aligns with GFS model update schedule
   */
class WindService {
  private downloader = new GFSDownloaderService();
  private converter = new GribConverterService();
  private uploader = new S3UploaderService();

  async processWindData(): Promise<void> {
    console.log('Wind Data Update Started');
    try {
      // Step 1: Find grib2json
      const grib2jsonPath = await this.converter.findGrib2Json();
      if (!grib2jsonPath) {
        await this.uploader.uploadFallbackData();
        return;
      }

      // Step 2: Download GFS data
      const gribFile = await this.downloader.downloadGFSData();
      if (!gribFile) {
        console.log('GFS download failed, using fallback data');
        await this.uploader.uploadFallbackData();
        return;
      }

      // Step 3: Convert GRIB to JSON
      const windData = await this.converter.convertGribToJson(grib2jsonPath, gribFile);
      if (!windData) {
        console.log('Conversion failed, using fallback data');
        await this.uploader.uploadFallbackData();
        return;
      }

      // Step 4: Upload to S3
      await this.uploader.uploadWindData(windData);
      console.log('Wind data update completed successfully');
    } catch (error) {
      console.error('Wind data update failed:', error);
      await this.uploader.uploadFallbackData();
    }

    console.log('Wind Data Update Finished');
  }

  start(): void {
    const runSafe = async () => {
      try {
        await this.processWindData();
      } catch (error) {
        console.error('Unhandled error in processWindData:', error);
        try {
          await this.uploader.uploadFallbackData();
        } catch (uploaderErr) {
          console.error('Failed to upload fallback data:', uploaderErr);
        }
      }
    };

    // Run immediately on startup
    runSafe();

    // Schedule every 6 hours
    cron.schedule('0 */6 * * *', () => {
      runSafe();
    });

    console.log('Service started and running...\n');
  }
}

// Start the service
const service = new WindService();
service.start();