import { Knex } from 'knex';
import * as path from 'path';
import { exec } from "child_process";
import { promisify } from "util";

const execAsync = promisify(exec);

export async function seed(knex: Knex): Promise<void> {
  const adminPbf = path.join(__dirname, '../admin-unit/admin-australia-oceania-251215.osm.pbf');
  // const roadsPbf = path.join(__dirname, '../admin-unit/road-australia-oceania-251215.osm.pbf');

  // Use the same connection knex is actually using *right now*
  const conn = knex.client.config.connection as Knex.PgConnectionConfig;
  const OGR_CONN = `host=${conn.host} port=${conn.port} dbname=${conn.database} user=${conn.user} password=${conn.password}`;

  await execAsync(
    `ogr2ogr -f "PostgreSQL" "PG:${OGR_CONN}" "${adminPbf}" \
        multipolygons \
        -nln public.osm_admin_raw \
        -nlt PROMOTE_TO_MULTI \
        -lco GEOMETRY_NAME=geom \
        -overwrite`
  );

  await knex.transaction(async (trx) => {
    // 0) Stage raw admin features
    await trx.raw(`
      DROP TABLE IF EXISTS osm_admin_stage;
      CREATE TEMP TABLE osm_admin_stage AS
      SELECT
        NULLIF(osm_id, '')::BIGINT AS osm_id,
        NULLIF(btrim(name), '')    AS name,
        NULLIF(admin_level,'')::INT AS admin_level,
        boundary,
        geom,
        CASE
          WHEN other_tags IS NULL THEN NULL
          ELSE (regexp_match(other_tags, 'ISO3166-1:alpha3"\\?=>"\\?([A-Z]{3})"\\?'))[1]
        END AS iso3
      FROM public.osm_admin_raw
      WHERE boundary = 'administrative'
        AND admin_level IS NOT NULL;
    `);

    // 1) Insert countries first (OSM admin_level=2)
    await trx.raw(`
      INSERT INTO public.admin_unit (country_code, osm_admin_level, parent_id, name, geom, source_osm_id)
      SELECT
        s.iso3,
        2,
        NULL,
        COALESCE(s.name, s.iso3),
        ST_Multi(s.geom)::geometry(MULTIPOLYGON,4326),
        s.osm_id
      FROM osm_admin_stage s
      WHERE s.admin_level = 2
        AND s.iso3 IS NOT NULL
      ON CONFLICT (country_code, source_osm_id) DO NOTHING;
    `);

    // 2) Assign each staged feature to a country using the inserted country polygons
    await trx.raw(`
      DROP TABLE IF EXISTS osm_admin_with_country;
      CREATE TEMP TABLE osm_admin_with_country AS
      SELECT
        s.*,
        c.country_code
      FROM osm_admin_stage s
      JOIN LATERAL (
        SELECT country_code, geom
        FROM public.admin_unit
        WHERE osm_admin_level = 2
          AND ST_Covers(geom, ST_PointOnSurface(s.geom))
        ORDER BY ST_Area(geom) ASC
        LIMIT 1
      ) c ON TRUE;
    `);

    // 3) Final admin features (trim names; drop empty)
    await trx.raw(`
      DROP TABLE IF EXISTS osm_admin_final;
      CREATE TEMP TABLE osm_admin_final AS
      SELECT
        w.osm_id,
        NULLIF(btrim(w.name), '') AS name,
        w.admin_level,
        w.geom,
        w.country_code
      FROM osm_admin_with_country w
      WHERE NULLIF(btrim(w.name), '') IS NOT NULL;
    `);

    // 4) Insert subnationals level-by-level (by admin_level) so parents exist
    // Dedupe by (country_code, parent_id, name, admin_level), union geometries for siblings.
    await trx.raw(`
      DO $$
      DECLARE
        k INT;
      BEGIN
        FOR k IN
          SELECT DISTINCT admin_level
          FROM osm_admin_final
          WHERE admin_level > 2
          ORDER BY admin_level
        LOOP

          WITH lvl AS (
            SELECT
              f.*,
              ST_PointOnSurface(f.geom) AS pt
            FROM osm_admin_final f
            WHERE f.admin_level = k
          ),
          candidates AS (
            SELECT
              l.country_code,
              l.admin_level,
              p.id AS parent_id,
              l.name,
              ST_Multi(l.geom)::geometry(MULTIPOLYGON,4326) AS geom,
              l.osm_id
            FROM lvl l
            LEFT JOIN LATERAL (
              SELECT a.id
              FROM public.admin_unit a
              WHERE a.country_code = l.country_code
                AND a.osm_admin_level < l.admin_level
                AND ST_Covers(a.geom, l.pt)
              ORDER BY a.osm_admin_level DESC, ST_Area(a.geom) ASC
              LIMIT 1
            ) p ON TRUE
          ),
          dedup AS (
            SELECT
              country_code,
              admin_level,
              parent_id,
              name,
              ST_Multi(
                ST_UnaryUnion(ST_Collect(geom))
              )::geometry(MULTIPOLYGON,4326) AS geom,
              (ARRAY_AGG(osm_id ORDER BY ST_Area(geom) DESC))[1] AS osm_id
            FROM candidates
            GROUP BY country_code, admin_level, parent_id, name
          )
          INSERT INTO public.admin_unit (country_code, osm_admin_level, parent_id, name, geom, source_osm_id)
          SELECT
            country_code,
            admin_level,
            parent_id,
            name,
            geom,
            osm_id
          FROM dedup
          ON CONFLICT (country_code, source_osm_id) DO NOTHING;

        END LOOP;
      END $$;
    `);

    // 5) Attach the deepest admin unit per location (highest osm_admin_level)
    await trx.raw(`
      UPDATE public.location AS l
      SET admin_unit_id = x.admin_unit_id
      FROM (
          SELECT DISTINCT ON (l.id)
                l.id AS location_id,
                a.id AS admin_unit_id,
                a.osm_admin_level
          FROM public.location AS l
          JOIN public.admin_unit AS a
            ON ST_Contains(a.geom, l.coordinate)
          ORDER BY l.id, a.osm_admin_level DESC
      ) AS x
      WHERE l.id = x.location_id;
    `);
  });

  console.log(`Seeded admin_unit and location.admin_unit_id`);
}
