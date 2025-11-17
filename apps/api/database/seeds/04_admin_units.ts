import { Knex } from 'knex';
import * as path from 'path';
import { exec } from "child_process";
import { promisify } from "util";

import config from '../../knexfile';

const execAsync = promisify(exec);

export async function seed(knex: Knex): Promise<void> {
  const admin0Shp = path.join(__dirname, '../data/ne_10m_admin_0_countries/ne_10m_admin_0_countries.shp');
  const admin1Shp = path.join(__dirname, '../data/ne_10m_admin_1_states_provinces/ne_10m_admin_1_states_provinces.shp');
  const admin2Shp = path.join(__dirname, '../data/ne_10m_admin_2_counties/ne_10m_admin_2_counties.shp');

  // Clear existing data in order to prevent foreign key constrain
  await knex('admin_units').del();

  const conn = config.connection as Knex.PgConnectionConfig;
  const OGR_CONN = `host=${conn.host} port=${conn.port} dbname=${conn.database} user=${conn.user} password=${conn.password}`;

  await execAsync(
    `ogr2ogr -f "PostgreSQL" "PG:${OGR_CONN}" "${admin0Shp}" ` +
      `-nln ne_admin0_raw -nlt MULTIPOLYGON -lco GEOMETRY_NAME=geom -overwrite`
  );

  await execAsync(
    `ogr2ogr -f "PostgreSQL" "PG:${OGR_CONN}" "${admin1Shp}" ` +
      `-nln ne_admin1_raw -nlt MULTIPOLYGON -lco GEOMETRY_NAME=geom -overwrite`
  );

  await execAsync(
    `ogr2ogr -f "PostgreSQL" "PG:${OGR_CONN}" "${admin2Shp}" ` +
      `-nln ne_admin2_raw -nlt MULTIPOLYGON -lco GEOMETRY_NAME=geom -overwrite`
  );

  await knex.raw(`
    INSERT INTO admin_units (country_code, level, parent_id, name, geom)
    SELECT
      iso_a3 AS country_code,
      0 AS level,
      NULL::bigint AS parent_id,
      admin AS name,
      ST_Multi(geom)::geometry(MULTIPOLYGON, 4326) AS geom
    FROM ne_admin0_raw
    WHERE iso_a3 != '-99';
  `);

  await knex.raw(`
    INSERT INTO admin_units (country_code, level, parent_id, name, geom)
    SELECT
      admin_0.country_code AS country_code,
      1 AS level,
      admin_0.id::bigint AS parent_id,
      admin_1.name AS name,
      ST_Multi(admin_1.geom)::geometry(MULTIPOLYGON, 4326) AS geom
    FROM ne_admin1_raw admin_1
    JOIN admin_units admin_0 ON admin_0.name = admin_1.admin
    WHERE admin_1.name is not null
    ON CONFLICT (parent_id, name)
      WHERE parent_id IS NOT NULL
    DO NOTHING;
  `);

  await knex.raw(`
    WITH admin_1 AS (
      SELECT id, country_code, ne_admin1_raw.code_local
      FROM admin_units
      JOIN ne_admin1_raw ON ne_admin1_raw.geom = admin_units.geom
      WHERE level = 1 AND country_code = 'USA'
    )
    INSERT INTO admin_units (country_code, level, parent_id, name, geom)
    SELECT
      admin_1.country_code AS country_code,
      2 AS level,
      admin_1.id::bigint AS parent_id,
      admin_2.name AS name,
      ST_Multi(admin_2.geom)::geometry(MULTIPOLYGON, 4326) AS geom
    FROM ne_admin2_raw admin_2
    JOIN admin_1 ON REPLACE(admin_1.code_local, 'US', '') = REPLACE(admin_2.iso_3166_2, 'US-', '')
    ON CONFLICT (parent_id, name)
      WHERE parent_id IS NOT NULL
    DO NOTHING;
  `);

  await knex.raw(`DROP TABLE IF EXISTS ne_admin2_raw`);
  await knex.raw(`DROP TABLE IF EXISTS ne_admin1_raw`);
  await knex.raw(`DROP TABLE IF EXISTS ne_admin0_raw`);

  await knex.raw(`
    UPDATE location AS l
    SET admin_unit_id = x.admin_unit_id
    FROM (
        SELECT DISTINCT ON (l.id)
              l.id AS location_id,
              a.id AS admin_unit_id,
              a.level
        FROM location AS l
        JOIN admin_units AS a
          ON ST_Contains(a.geom, l.coordinate)
        -- pick the row with the highest level per location
        ORDER BY l.id, a.level DESC
    ) AS x
    WHERE l.id = x.location_id;
  `);
  
  console.log(`Seeded admin_units and location.admin_unit_id`);
}
