import { PutObjectCommand } from '@aws-sdk/client-s3';
import { gzipSync } from 'zlib';
import { s3Client, S3_BUCKET, getWindFileKey, getPublicUrl } from '../config/s3.config';
import { s3Logger } from '../utils/logger';

export interface UploadResult {
  success: boolean;
  url?: string;
  key?: string;
  timestamp?: Date;
  error?: string;
}

export class S3UploaderService {
  /**
   * Uploads wind data to S3 as timestamped historical file only
   * Frontend will use API to get current data from database
   * S3 is used for historical data archival
   *
   * @param windData - The wind data to upload
   * @param timestamp - Optional timestamp for the data (defaults to current time)
   * @returns Upload result with URL and key
   */
  async uploadWindData(windData: any, timestamp?: Date): Promise<UploadResult> {
    const uploadTimestamp = timestamp || new Date();
    const { compressedBody, uncompressedSize } = this.preparePayload(windData);

    try {
      const historicalKey = getWindFileKey(uploadTimestamp);

      s3Logger.info('Uploading historical wind data to S3', {
        key: historicalKey,
        uncompressedBytes: uncompressedSize,
        timestamp: uploadTimestamp.toISOString()
      });

      await s3Client.send(
        new PutObjectCommand({
          Bucket: S3_BUCKET,
          Key: historicalKey,
          Body: compressedBody,
          ContentType: 'application/json',
          ContentEncoding: 'gzip',
          CacheControl: 'public, max-age=31536000, immutable', // Historical data never changes
          Metadata: {
            'data-timestamp': uploadTimestamp.toISOString(),
            'data-source': 'NOAA GFS',
            'uncompressed-bytes': uncompressedSize.toString(),
          },
        })
      );

      const url = getPublicUrl(historicalKey);
      s3Logger.info('Historical wind data uploaded successfully', { url });

      return {
        success: true,
        url,
        key: historicalKey,
        timestamp: uploadTimestamp
      };
    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : 'Unknown error';
      s3Logger.error('S3 upload failed', { error: errorMessage });

      return {
        success: false,
        error: errorMessage,
        timestamp: uploadTimestamp
      };
    }
  }

  private preparePayload(data: any) {
    const jsonString = JSON.stringify(data);
    const compressedBody = gzipSync(jsonString);

    return {
      compressedBody,
      uncompressedSize: Buffer.byteLength(jsonString),
    };
  }
}