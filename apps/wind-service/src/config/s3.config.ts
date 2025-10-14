import { S3Client } from '@aws-sdk/client-s3';

export const s3Client = new S3Client({
  region: process.env.AWS_REGION || 'eu-north-1',
  credentials: {
    accessKeyId: process.env.AWS_ACCESS_KEY_ID!,
    secretAccessKey: process.env.AWS_SECRET_ACCESS_KEY!,
  },
});

export const S3_BUCKET = process.env.S3_BUCKET_NAME || 'airgradient-wind-data';
export const WIND_FILE_KEY = 'wind/current-wind-surface-level-gfs-1.0.json';

export function getPublicUrl(): string {
  const region = process.env.AWS_REGION || 'eu-north-1';
  return `https://${S3_BUCKET}.s3.${region}.amazonaws.com/${WIND_FILE_KEY}`;
}