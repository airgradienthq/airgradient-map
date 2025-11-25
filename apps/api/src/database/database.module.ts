import { Global, Module } from '@nestjs/common';
import { Pool } from 'pg';
import {
  ConfigurableDatabaseModule,
  CONNECTION_POOL,
  DATABASE_OPTIONS,
} from './database.module-definition';
import DatabaseService from './database.service';
import DatabaseOptions from './databaseOptions';

@Global()
@Module({
  exports: [DatabaseService],
  providers: [
    DatabaseService,
    {
      provide: CONNECTION_POOL,
      inject: [DATABASE_OPTIONS],
      useFactory: (databaseOptions: DatabaseOptions) => {
        return new Pool({
          host: databaseOptions.host,
          port: databaseOptions.port,
          user: databaseOptions.user,
          password: databaseOptions.password,
          database: databaseOptions.database,
          max: databaseOptions.max,
          idleTimeoutMillis: databaseOptions.idleTimeoutMillis,
          connectionTimeoutMillis: databaseOptions.connectionTimeoutMillis,
        });
      },
    },
  ],
})
export default class DatabaseModule extends ConfigurableDatabaseModule {}
