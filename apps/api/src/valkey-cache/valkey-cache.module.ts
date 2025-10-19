import { Global, Module } from '@nestjs/common';
import { GlideClient } from '@valkey/valkey-glide';
import {
  ConfigurableValkeyCacheModule,
  CONNECTION_POOL,
  VALKEY_CACHE_OPTIONS,
} from './valkey-cache.module-definition';
import { ValkeyCacheOptions } from './valkey-cache.options';
import { ValkeyCacheService } from './valkey-cache.service';

@Global()
@Module({
  exports: [ValkeyCacheService],
  providers: [
    ValkeyCacheService,
    {
      provide: CONNECTION_POOL,
      inject: [VALKEY_CACHE_OPTIONS],
      useFactory: async (options: ValkeyCacheOptions) => {
        const client = await GlideClient.createClient({
          addresses: [{ host: options.host, port: options.port }],
          databaseId: options.dbId,
        });
        return client;
      },
    },
  ],
})
export class ValkeyCacheModule extends ConfigurableValkeyCacheModule {}
