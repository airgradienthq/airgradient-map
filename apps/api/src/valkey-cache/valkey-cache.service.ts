import { Inject, Injectable, Logger } from '@nestjs/common';
import { CONNECTION_POOL } from './valkey-cache.module-definition';
import { GlideClient } from '@valkey/valkey-glide';

@Injectable()
export class ValkeyCacheService {
  private readonly logger = new Logger(ValkeyCacheService.name);
  constructor(@Inject(CONNECTION_POOL) private readonly client: GlideClient) {}

  async get(key: string): Promise<string | null> {
    try {
      const result = await this.client.get(key);
      this.logger.debug(`GET ${key} => ${result ? 'hit' : 'miss'}`);
      return result.toString();
    } catch (error) {
      this.logger.error(`GET ${key} - ${error.message}`, error.stack);
      throw error;
    }
  }
}
