import { Pool } from 'pg';
import { logger } from '../utils/logger';

export const pool = new Pool({
  host: process.env.DATABASE_HOST || 'postgrex',
  port: parseInt(process.env.DATABASE_PORT || '5432'),
  database: process.env.DATABASE_NAME || 'agmap',
  user: process.env.DATABASE_USER || 'postgres',
  password: process.env.DATABASE_PASSWORD || 'password',
  max: 20,
  idleTimeoutMillis: 30000,
  connectionTimeoutMillis: 2000,
});

// PostgreSQL parameter limit: 65535
// Each record uses 5 parameters (lon, lat, time, u, v)
// Safe batch size: 65535 / 5 = 13107, using 10000 for safety margin
export const WIND_DATA_BATCH_SIZE = 10000;

export async function testConnection(): Promise<void> {
  try {
    const client = await pool.connect();
    logger.info('database', 'Database connection established');
    client.release();
  } catch (error) {
    logger.error('database', 'Database connection failed', {
      error: error instanceof Error ? error.message : 'Unknown error'
    });
    throw error;
  }
}
