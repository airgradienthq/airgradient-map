import { Knex } from 'knex';

export async function up(knex: Knex): Promise<void> {
  // Remove NOT NULL constraint from owner_name
  await knex.raw('ALTER TABLE owner ALTER COLUMN owner_name DROP NOT NULL');

  // Set empty or 'unknown' values to NULL
  await knex.raw(`
    UPDATE owner
    SET owner_name = NULL
    WHERE owner_name = '' OR owner_name = 'unknown'
  `);
}

export async function down(knex: Knex): Promise<void> {
  // Revert NULL values back to 'unknown' (or empty string)
  await knex.raw(`
    UPDATE owner
    SET owner_name = 'unknown'
    WHERE owner_name IS NULL
  `);

  // Add back NOT NULL constraint
  await knex.raw('ALTER TABLE owner ALTER COLUMN owner_name SET NOT NULL');
}
