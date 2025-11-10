import { S3Client } from '@aws-sdk/client-s3';

export const s3Client = new S3Client({
  region: process.env.AWS_REGION || 'eu-north-1',
  credentials: {
    accessKeyId: process.env.AWS_ACCESS_KEY_ID!,
    secretAccessKey: process.env.AWS_SECRET_ACCESS_KEY!,
  },
});

export const S3_BUCKET = process.env.S3_BUCKET_NAME || 'airgradient-wind-data';
export const WIND_FOLDER = 'wind/';

/**
 * Generates a timestamped S3 key for historical wind data archival
 * @param timestamp - Optional timestamp to use, defaults to current time
 * @returns S3 key in format: wind/gfs-surface-YYYY-MM-DDTHH-mm-ss.sssZ.json
 */
export function getWindFileKey(timestamp?: Date): string {
  const date = timestamp || new Date();
  const isoString = date.toISOString();
  return `${WIND_FOLDER}gfs-surface-${isoString}.json`;
}

/**
 * Gets the public URL for a specific wind data file in S3
 * @param key - S3 object key
 * @returns Public HTTPS URL for the S3 object
 */
export function getPublicUrl(key: string): string {
  const region = process.env.AWS_REGION || 'eu-north-1';
  return `https://${S3_BUCKET}.s3.${region}.amazonaws.com/${key}`;
}