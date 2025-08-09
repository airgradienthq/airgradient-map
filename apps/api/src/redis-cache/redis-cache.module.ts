import { Global, Module } from '@nestjs/common';
import Redis from 'ioredis';
import {
  ConfigurableRedisCacheModule,
  CONNECTION_POOL,
  REDIS_CACHE_OPTIONS,
} from './redis-cache.module-definition';
import { RedisCacheService } from './redis-cache.service';
import { RedisCacheOptions } from './redis-cache.options';

@Global()
@Module({
  exports: [RedisCacheService],
  providers: [
    RedisCacheService,
    {
      provide: CONNECTION_POOL,
      inject: [REDIS_CACHE_OPTIONS],
      useFactory: (redisOptions: RedisCacheOptions) => {
        return new Redis({
          host: redisOptions.host,
          port: redisOptions.port,
          db: redisOptions.db,
        });
      },
    },
  ],
})
export default class RedisCacheModule extends ConfigurableRedisCacheModule {}
