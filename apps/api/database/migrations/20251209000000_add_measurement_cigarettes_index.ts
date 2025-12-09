import { Knex } from 'knex';

export async function up(knex: Knex): Promise<void> {
  // Create partial index optimized for cigarettes calculation queries
  // This index covers the WHERE clause pattern used in cigarette calculations:
  // - location_id for filtering by location
  // - measured_at DESC for time range queries
  // - Partial index only includes rows where is_pm25_outlier = false AND pm25 IS NOT NULL
  await knex.raw(`
    CREATE INDEX IF NOT EXISTS measurement_location_time_pm25_idx
    ON public.measurement (location_id, measured_at DESC)
    WHERE is_pm25_outlier = false AND pm25 IS NOT NULL
  `);
}

export async function down(knex: Knex): Promise<void> {
  // Drop the index if it exists
  await knex.raw(`
    DROP INDEX IF EXISTS measurement_location_time_pm25_idx
  `);
}
