import { Knex } from 'knex';

export async function up(knex: Knex): Promise<void> {
  // 0. Check current SRID / type of the `coordinate` column
  const result = await knex.raw(`
    SELECT type, srid
    FROM geometry_columns
    WHERE f_table_name = 'location'
      AND f_geometry_column = 'coordinate';
  `);

  const geomInfo = result.rows[0];
  if (!geomInfo) {
    console.warn("⚠️ No geometry column 'coordinate' found in 'location' — skipping migration.");
    return;
  }

  // Only continue if it's geometry(Point, 3857)
  if (geomInfo.type !== 'POINT' || geomInfo.srid !== 3857) {
    console.log(`ℹ️ Skipping migration — current type is ${geomInfo.type}(${geomInfo.srid}).`);
    return;
  }

  console.log("Detected geometry(Point, 3857) — proceeding with migration.");

  // 1. Backup the table (optional but recommended)
  await knex.raw(`CREATE TABLE location_backup AS TABLE location`);

  // 2. Convert the rows whose value is 3857 to 4326
  await knex.raw(`
    UPDATE location
    SET coordinate = ST_SetSRID(ST_Transform(coordinate, 4326), 3857)
    WHERE abs(ST_X(coordinate)) >= 200 AND abs(ST_Y(coordinate)) >= 200;
  `);

  // 3. Alter column type to geometry(Point, 4326)
  await knex.raw(`
    ALTER TABLE location
    ALTER COLUMN coordinate TYPE geometry(Point, 4326)
    USING ST_SetSRID(coordinate, 4326);
  `);

  // 4. Create geography-based index
  await knex.raw(`
    CREATE INDEX idx_location_coordinate_geog
    ON public.location
    USING gist ((coordinate::geography));
  `);
}

export async function down(knex: Knex): Promise<void> {
  // Check if backup table exists
  const backupExists = await knex.schema.hasTable('location_backup');

  if (!backupExists) {
    console.log("Skipping down migration — 'location_backup' does not exist.");
    return;
  }

  console.log("🔁 Restoring from 'location_backup'...");

  // Revert using the backup
  await knex.raw(`DROP TABLE IF EXISTS location`);
  await knex.raw(`ALTER TABLE location_backup RENAME TO location`);
  await knex.raw(`DROP INDEX idx_location_coordinate_geog;`);
}
