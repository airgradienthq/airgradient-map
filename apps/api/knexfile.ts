import { Knex } from 'knex';

const config: Knex.Config = {
  client: 'postgresql',
  connection: {
    host: process.env.DATABASE_HOST || 'postgrex',
    port: parseInt(process.env.DATABASE_PORT || '5432', 10),
    database: 'agmap',
    user: process.env.DATABASE_USER || 'postgres',
    password: process.env.DATABASE_PASSWORD || 'password',
  },
  migrations: {
    directory: './database/migrations',
    tableName: 'schema_migrations',
    extension: 'ts'
  },
  seeds: {
    directory: './database/seeds',
    extension: 'ts'
  }
};

export default config;
