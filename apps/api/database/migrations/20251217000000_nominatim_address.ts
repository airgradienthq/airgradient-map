import { Knex } from 'knex';

export async function up(knex: Knex): Promise<void> {
  await knex.raw(`
    CREATE TABLE IF NOT EXISTS public.nominatim_address (
        id SERIAL PRIMARY KEY,
        coordinate geometry(Point, 4326) NOT NULL,
        street VARCHAR,
        village VARCHAR,
        town VARCHAR,
        city VARCHAR,
        district VARCHAR,
        county VARCHAR,
        state VARCHAR,
        country VARCHAR,
        country_code VARCHAR(10),
        label TEXT
    );
  `);

  await knex.raw(`
    CREATE UNIQUE INDEX IF NOT EXISTS nominatim_address_coordinate_unique_idx
      ON public.nominatim_address ((ST_X(coordinate)), (ST_Y(coordinate)));
  `);

  await knex.raw(`
    CREATE INDEX IF NOT EXISTS nominatim_address_coordinate_geography_idx
      ON public.nominatim_address
      USING gist ((coordinate::geography));
  `);
}

export async function down(knex: Knex): Promise<void> {
  await knex.raw(`
    DROP INDEX IF EXISTS nominatim_address_coordinate_unique_idx;
  `);

  await knex.raw(`
    DROP INDEX IF EXISTS nominatim_address_coordinate_geography_idx;
  `);

  await knex.raw(`
    DROP TABLE IF EXISTS public.nominatim_address;
  `);
}
