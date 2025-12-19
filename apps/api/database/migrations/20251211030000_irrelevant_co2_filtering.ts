import { Knex } from 'knex';

export async function up(knex: Knex): Promise<void> {
  // Add is_rco2_outlier column in public.measurement table
  await knex.raw('ALTER TABLE public.measurement ADD COLUMN IF NOT EXISTS is_rco2_outlier boolean NOT NULL DEFAULT false;');

  /*
    In PostgreSQL, SELECT m.* in a view does not auto-update when you add new columns to measurement. 
    The * is expanded to an explicit column list at creation time and then stored that way.
    The same sql command with `airgradient-map/apps/api/database/migrations/20251201060000_create_public_views.ts`
  */
  await knex.raw(`
    CREATE OR REPLACE VIEW public.vw_measurement_public AS
      SELECT m.*
      FROM measurement m
      JOIN location l ON m.location_id = l.id
      JOIN data_source d ON l.data_source_id = d.id
      WHERE d.allow_api_access = true;
  `);

}

export async function down(knex: Knex): Promise<void> {
  // Drop is_rco2_outlier column in public.measurement table
  await knex.raw('ALTER TABLE public.measurement DROP COLUMN IF EXISTS is_rco2_outlier');

  /*
    In PostgreSQL, SELECT m.* in a view does not auto-update when you add new columns to measurement. 
    The * is expanded to an explicit column list at creation time and then stored that way.
    The same sql command with `airgradient-map/apps/api/database/migrations/20251201060000_create_public_views.ts`
  */
  await knex.raw(`
    CREATE OR REPLACE VIEW public.vw_measurement_public AS
      SELECT m.*
      FROM measurement m
      JOIN location l ON m.location_id = l.id
      JOIN data_source d ON l.data_source_id = d.id
      WHERE d.allow_api_access = true;
  `);
}
