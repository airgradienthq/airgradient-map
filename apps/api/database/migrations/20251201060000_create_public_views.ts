import { Knex } from 'knex';

export async function up(knex: Knex): Promise<void> {
  // 1. location
  await knex.raw(`
    DO $$
    BEGIN
        IF NOT EXISTS (
            SELECT 1
            FROM   pg_views
            WHERE  schemaname = 'public'
            AND    viewname   = 'vw_location_public'
        ) THEN
            CREATE VIEW public.vw_location_public AS
                SELECT l.*
                FROM location l
                JOIN data_source d ON l.data_source_id = d.id
                WHERE d.allow_api_access = true;
        END IF;
    END
    $$;
  `);

  // 2. measurement
  await knex.raw(`
    DO $$
    BEGIN
        IF NOT EXISTS (
            SELECT 1
            FROM   pg_views
            WHERE  schemaname = 'public'
            AND    viewname   = 'vw_measurement_public'
        ) THEN
            CREATE VIEW public.vw_measurement_public AS
              SELECT m.*
              FROM measurement m
              JOIN location l ON m.location_id = l.id
              JOIN data_source d ON l.data_source_id = d.id
              WHERE d.allow_api_access = true;
        END IF;
    END
    $$;
  `);
}

export async function down(knex: Knex): Promise<void> {
  // 1. location
  await knex.raw(`DROP VIEW IF EXISTS public.vw_location_public CASCADE;`);

  // 2. measurement
  await knex.raw(`DROP VIEW IF EXISTS public.vw_measurement_public CASCADE;`);  
}
