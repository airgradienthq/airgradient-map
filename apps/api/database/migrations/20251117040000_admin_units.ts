import { Knex } from 'knex';

export async function up(knex: Knex): Promise<void> {
  // Single normalized table for all boundary levels
  await knex.raw(`
    CREATE TABLE admin_units (
      id            BIGSERIAL PRIMARY KEY,
      country_code  VARCHAR(3) NOT NULL,  -- ISO 3166-1 alpha-3 e.g. 'AFG'
      level         SMALLINT   NOT NULL,   -- 0=country, 1=ADM1, 2=ADM2
      parent_id     BIGINT REFERENCES admin_units(id) ON DELETE CASCADE,
      name          TEXT NOT NULL,
      geom          geometry(MULTIPOLYGON, 4326) NOT NULL,
      centroid      geometry(POINT, 4326) GENERATED ALWAYS AS (ST_PointOnSurface(geom)) STORED
    );
  `);

  await knex.raw(`
    ALTER TABLE location 
    ADD COLUMN admin_unit_id INT;
  `);

  await knex.raw(`
    ALTER TABLE location 
    ADD CONSTRAINT fk_location_admin_unit 
      FOREIGN KEY (admin_unit_id) 
      REFERENCES admin_units(id);
  `);

  // Uniqueness rules (practical):
  // - country level (0) names must be unique globally
  // - below level 0, (parent_id, name) should be unique
  await knex.raw(`
    CREATE UNIQUE INDEX admin_units_country_unique
      ON admin_units (name)
      WHERE level = 0;

    CREATE UNIQUE INDEX admin_units_sibling_name_unique
      ON admin_units (parent_id, name)
      WHERE parent_id IS NOT NULL;
  `);

  // Spatial + lookup indexes
  await knex.raw(`
    CREATE INDEX admin_units_gix      ON admin_units USING GIST (geom);
    CREATE INDEX admin_units_centroid ON admin_units USING GIST (centroid);
    CREATE INDEX admin_units_level    ON admin_units (country_code, level);
    CREATE INDEX admin_units_parent   ON admin_units (parent_id);
  `);
}

export async function down(knex: Knex): Promise<void> {
  await knex.raw(`
    DROP INDEX IF EXISTS admin_units_parent;
    DROP INDEX IF EXISTS admin_units_level;
    DROP INDEX IF EXISTS admin_units_centroid;
    DROP INDEX IF EXISTS admin_units_gix;
    DROP INDEX IF EXISTS admin_units_sibling_name_unique;
    DROP INDEX IF EXISTS admin_units_country_unique;
  `);

  await knex.raw(`
    DROP TABLE IF EXISTS admin_units;

    ALTER TABLE location
    DROP CONSTRAINT fk_location_admin_unit;

    ALTER TABLE location
    DROP COLUMN admin_unit_id;
  `);
}
