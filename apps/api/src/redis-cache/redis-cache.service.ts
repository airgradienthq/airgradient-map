import { Inject, Injectable, Logger } from '@nestjs/common';
import Redis from 'ioredis';
import { CONNECTION_POOL } from './redis-cache.module-definition';

@Injectable()
export class RedisCacheService {
  private readonly logger = new Logger(RedisCacheService.name);

  constructor(@Inject(CONNECTION_POOL) private readonly client: Redis) {}

  async get(key: string): Promise<string | null> {
    try {
      const result = await this.client.get(key);
      this.logger.log(`Redis command: GET ${key} => ${result ? 'hit' : 'miss'}`);
      return result;
    } catch (error) {
      this.logger.error(`Redis command failed: GET ${key} - ${error.message}`, error.stack);
      throw error;
    }
  }

  async set(key: string, value: string, ttlSeconds?: number): Promise<'OK' | null> {
    try {
      let result: 'OK';
      if (ttlSeconds) {
        result = await this.client.set(key, value, 'EX', ttlSeconds);
        this.logger.log(`Redis command: SET ${key} EX ${ttlSeconds}`);
      } else {
        result = await this.client.set(key, value);
        this.logger.log(`Redis command: SET ${key}`);
      }
      return result;
    } catch (error) {
      this.logger.error(`Redis command failed: SET ${key} - ${error.message}`, error.stack);
      throw error;
    }
  }

  async del(key: string): Promise<number> {
    try {
      const result = await this.client.del(key);
      this.logger.log(`Redis command: DEL ${key} => ${result}`);
      return result;
    } catch (error) {
      this.logger.error(`Redis command failed: DEL ${key} - ${error.message}`, error.stack);
      throw error;
    }
  }

  async flushdb(): Promise<string | null> {
    try {
      const result = await this.client.flushdb();
      this.logger.log(`Redis command: FLUSHDB => ${result}`);
      return result;
    } catch (error) {
      this.logger.error(`Redis command failed: FLUSHDB - ${error.message}`, error.stack);
      throw error;
    }
  }
}
