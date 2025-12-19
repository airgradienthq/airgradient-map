import type { Knex } from 'knex';

const TABLE_NAME = 'notifications';
const COLUMN_NAME = 'external_reference_id';

export async function up(knex: Knex): Promise<void> {
  const hasColumn = await knex.schema.hasColumn(TABLE_NAME, COLUMN_NAME);
  if (hasColumn) {
    return;
  }

  await knex.schema.alterTable(TABLE_NAME, table => {
    table.bigInteger(COLUMN_NAME).nullable();
  });

  await knex.raw(
    `CREATE INDEX IF NOT EXISTS idx_${TABLE_NAME}_${COLUMN_NAME} ON ${TABLE_NAME}(${COLUMN_NAME})`,
  );
}

export async function down(knex: Knex): Promise<void> {
  const hasColumn = await knex.schema.hasColumn(TABLE_NAME, COLUMN_NAME);
  if (!hasColumn) {
    return;
  }

  await knex.raw(`DROP INDEX IF EXISTS idx_${TABLE_NAME}_${COLUMN_NAME}`);

  await knex.schema.alterTable(TABLE_NAME, table => {
    table.dropColumn(COLUMN_NAME);
  });
}
