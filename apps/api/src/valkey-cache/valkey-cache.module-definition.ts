import { ConfigurableModuleBuilder } from '@nestjs/common';
import { ValkeyCacheOptions } from './valkey-cache.options';

export const CONNECTION_POOL = 'VALKEY_CONNECTION';

export const {
  ConfigurableModuleClass: ConfigurableValkeyCacheModule,
  MODULE_OPTIONS_TOKEN: VALKEY_CACHE_OPTIONS,
} = new ConfigurableModuleBuilder<ValkeyCacheOptions>().setClassMethodName('forRoot').build();
