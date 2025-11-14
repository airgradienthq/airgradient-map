import type { Knex } from 'knex';

export async function up(knex: Knex): Promise<void> {
  // Create wind_data table with optimized settings
  await knex.raw(`
    CREATE TABLE IF NOT EXISTS public.wind_data (
      longitude REAL NOT NULL,
      latitude REAL NOT NULL,
      forecast_time TIMESTAMP NOT NULL,
      u_component REAL NOT NULL,
      v_component REAL NOT NULL,

      -- Use composite primary key instead of id field
      PRIMARY KEY (longitude, latitude, forecast_time)
    ) WITH (
      fillfactor = 100
    );
  `);

  // Create indexes for efficient querying
  await knex.raw(
    'CREATE INDEX IF NOT EXISTS idx_wind_data_forecast_time ON public.wind_data(forecast_time)',
  );

  await knex.raw(
    'CREATE INDEX IF NOT EXISTS idx_wind_data_location ON public.wind_data(longitude, latitude)',
  );
}

export async function down(knex: Knex): Promise<void> {
  await knex.raw('DROP TABLE IF EXISTS public.wind_data');
}