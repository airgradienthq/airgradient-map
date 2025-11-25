import type { Knex } from 'knex';

export async function up(knex: Knex): Promise<void> {
  // Create fires_data table with optimized settings
  await knex.raw(`
    CREATE TABLE IF NOT EXISTS public.fires_data (
      longitude REAL NOT NULL,
      latitude REAL NOT NULL,
      acq_date DATE NOT NULL,
      acq_time TIME NOT NULL,
      satellite VARCHAR(10) NOT NULL,
      confidence VARCHAR(10) NOT NULL,
      bright_ti4 REAL,
      bright_ti5 REAL,
      frp REAL,
      scan REAL,
      track REAL,
      version VARCHAR(20),
      daynight VARCHAR(1),

      -- Use composite primary key to prevent duplicates
      PRIMARY KEY (longitude, latitude, acq_date, acq_time, satellite)
    ) WITH (
      fillfactor = 100
    );
  `);

  // Create indexes for efficient querying
  // Spatial index for bounding box queries
  await knex.raw(
    'CREATE INDEX IF NOT EXISTS idx_fires_data_location ON public.fires_data(longitude, latitude)',
  );

  // Index on acquisition date for time-based queries (most recent first)
  await knex.raw(
    'CREATE INDEX IF NOT EXISTS idx_fires_data_date_desc ON public.fires_data(acq_date DESC)',
  );

  // Composite index for spatial + temporal queries
  await knex.raw(
    'CREATE INDEX IF NOT EXISTS idx_fires_data_date_location ON public.fires_data(acq_date, longitude, latitude)',
  );

  // Index for confidence filtering
  await knex.raw(
    'CREATE INDEX IF NOT EXISTS idx_fires_data_confidence ON public.fires_data(confidence)',
  );
}

export async function down(knex: Knex): Promise<void> {
  await knex.raw('DROP TABLE IF EXISTS public.fires_data');
}
