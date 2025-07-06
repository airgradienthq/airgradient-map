import { ConfigurableModuleBuilder } from '@nestjs/common';
import RedisCacheOptions from './redis-cache.options';

export const CONNECTION_POOL = 'REDIS_CONNECTION';

export const {
  ConfigurableModuleClass: ConfigurableRedisCacheModule,
  MODULE_OPTIONS_TOKEN: REDIS_CACHE_OPTIONS,
} = new ConfigurableModuleBuilder<RedisCacheOptions>().setClassMethodName('forRoot').build();