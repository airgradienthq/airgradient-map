import { Knex } from 'knex';

export async function up(knex: Knex): Promise<void> {
  // Add is_pm25_outlier column in public.measurement table
  await knex.raw('ALTER TABLE public.measurement ADD COLUMN is_pm25_outlier boolean NOT NULL DEFAULT false');
}

export async function down(knex: Knex): Promise<void> {
  // Drop is_pm25_outlier column in public.measurement table
  await knex.raw('ALTER TABLE public.measurement DROP COLUMN IF EXISTS is_pm25_outlier');
}
