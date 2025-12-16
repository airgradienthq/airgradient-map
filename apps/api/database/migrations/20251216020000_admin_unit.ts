import { Knex } from 'knex';

export async function up(knex: Knex): Promise<void> {
  await knex.raw(`
    CREATE TABLE IF NOT EXISTS public.admin_unit (
      id BIGSERIAL PRIMARY KEY,

      -- ISO 3166-1 alpha-3 e.g. 'AFG'
      country_code VARCHAR(3) NOT NULL,

      -- Raw OSM admin_level (e.g., 2, 4, 6, 8...)
      osm_admin_level SMALLINT NOT NULL CHECK (osm_admin_level > 0),

      -- Self-referencing parent
      parent_id BIGINT REFERENCES admin_unit(id) ON DELETE CASCADE,

      name TEXT NOT NULL,

      -- Boundary geometry
      geom geometry(MULTIPOLYGON, 4326) NOT NULL,

      -- Stable representative point for labeling/queries
      centroid geometry(POINT, 4326)
        GENERATED ALWAYS AS (ST_PointOnSurface(geom)) STORED,

      -- Optional: track source object for de-dup / re-import safety
      source_osm_id BIGINT,

      CONSTRAINT country_code_upper_chk CHECK (country_code = upper(country_code)),
      CONSTRAINT admin_units_country_source_uniq UNIQUE (country_code, source_osm_id)
    );
  `);

  await knex.raw(`
    ALTER TABLE location
    ADD COLUMN admin_unit_id BIGINT;
  `);

  await knex.raw(`
    ALTER TABLE location
    ADD CONSTRAINT fk_location_admin_unit
      FOREIGN KEY (admin_unit_id)
      REFERENCES admin_unit(id);
  `);

  // Uniqueness rules (practical):
  // - country polygons (osm_admin_level=2) names unique globally
  // - below that, (parent_id, name) unique
  await knex.raw(`
    CREATE UNIQUE INDEX IF NOT EXISTS admin_units_country_unique
      ON admin_unit (name)
      WHERE osm_admin_level = 2;

    CREATE UNIQUE INDEX IF NOT EXISTS admin_units_sibling_name_unique
      ON admin_unit (parent_id, name)
      WHERE parent_id IS NOT NULL;
  `);

  // Spatial + lookup indexes
  await knex.raw(`
    CREATE INDEX IF NOT EXISTS admin_units_gix      ON admin_unit USING GIST (geom);
    CREATE INDEX IF NOT EXISTS admin_units_centroid ON admin_unit USING GIST (centroid);
    CREATE INDEX IF NOT EXISTS admin_units_level    ON admin_unit (country_code, osm_admin_level);
    CREATE INDEX IF NOT EXISTS admin_units_parent   ON admin_unit (parent_id);
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
    ALTER TABLE location
    DROP CONSTRAINT IF EXISTS fk_location_admin_unit;

    ALTER TABLE location
    DROP COLUMN IF EXISTS admin_unit_id;

    DROP TABLE IF EXISTS public.admin_unit;
  `);
}
