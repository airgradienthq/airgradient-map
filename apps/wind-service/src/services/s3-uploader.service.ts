import { PutObjectCommand } from '@aws-sdk/client-s3';
import { s3Client, S3_BUCKET, WIND_FILE_KEY, getPublicUrl } from '../config/s3.config';

export class S3UploaderService {
  async uploadWindData(windData: any): Promise<string> {
    console.log('Uploading wind data to S3...');
    const jsonString = JSON.stringify(windData);

    try {
      await s3Client.send(
        new PutObjectCommand({
          Bucket: S3_BUCKET,
          Key: WIND_FILE_KEY,
          Body: jsonString,
          ContentType: 'application/json',
          CacheControl: 'no-cache, must-revalidate',
          ACL: 'public-read',
          Metadata: {
            'last-updated': new Date().toISOString(),
            'data-source': 'NOAA GFS',
          },
        })
      );

      const publicUrl = getPublicUrl();
      return publicUrl;
    } catch (error) {
      console.error('Upload failed:', error);
      throw error;
    }
  }

  async uploadFallbackData(): Promise<string> {
    const fallbackData = this.createFallbackData();
    return this.uploadWindData(fallbackData);
  }

  private createFallbackData() {
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
        dy: 1,
      },
      data: Array(65160).fill(0),
    });

    return [
      createComponent(2, 'U-component_of_wind'),
      createComponent(3, 'V-component_of_wind'),
    ];
  }
}