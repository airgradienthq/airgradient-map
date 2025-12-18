import { Knex } from 'knex';

export async function up(knex: Knex): Promise<void> {
  /** Create partial index optimized for RCO2 queries
  * This index covers the WHERE clause pattern used in /locations/:id/measures/history:
  *   location_id for filtering by location
  *   measured_at DESC for time range queries
  * Partial index only includes rows where is_rco2_outlier = false AND rco2 IS NOT NULL
  * The same idea as in `20251209000000_add_measurement_cigarettes_index.ts`
  */
  await knex.raw(`
    CREATE INDEX IF NOT EXISTS measurement_location_time_rco2_idx
    ON public.measurement (location_id, measured_at DESC)
    WHERE is_rco2_outlier = false AND rco2 IS NOT NULL
  `);
}

export async function down(knex: Knex): Promise<void> {
  await knex.raw('DROP INDEX IF EXISTS measurement_location_time_rco2_idx');
}
