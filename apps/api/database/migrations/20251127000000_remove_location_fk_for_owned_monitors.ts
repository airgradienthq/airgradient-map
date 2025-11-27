import type { Knex } from 'knex';

/**
 * Remove foreign key constraint on location_id to allow owned monitors
 * Owned monitors (monitor_type='owned') have location_ids that don't exist in our database
 * They are fetched from the external Dashboard API instead
 */
export async function up(knex: Knex): Promise<void> {
  await knex.raw(`
    -- Drop the foreign key constraint on location_id
    ALTER TABLE public.notifications
    DROP CONSTRAINT IF EXISTS fk_location;
  `);

  console.log('✓ Removed fk_location constraint to support owned monitors');
}

export async function down(knex: Knex): Promise<void> {
  await knex.raw(`
    -- Re-add the foreign key constraint
    -- NOTE: This will fail if there are any owned monitor notifications in the table
    ALTER TABLE public.notifications
    ADD CONSTRAINT fk_location
      FOREIGN KEY (location_id)
      REFERENCES location(id)
      ON DELETE CASCADE;
  `);

  console.log('✓ Re-added fk_location constraint');
}
