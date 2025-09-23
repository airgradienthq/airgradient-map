import { Knex } from 'knex';

export async function up(knex: Knex): Promise<void> {
  // Enable extensions
  await knex.raw('CREATE EXTENSION IF NOT EXISTS postgis');
  await knex.raw('CREATE EXTENSION IF NOT EXISTS timeseries CASCADE');

  // Create ENUM type
  await knex.raw(`
    CREATE TYPE public."sensor_type_enum" AS ENUM (
      'Small Sensor',
      'Reference'
    )
  `);

  // Create owner table
  await knex.raw(`
    CREATE TABLE public."owner" (
      id serial4 NOT NULL,
      owner_name varchar(255) NOT NULL,
      url varchar(255) NULL,
      created_at timestamp DEFAULT now() NOT NULL,
      reference_id int4 NULL,
      CONSTRAINT owner_pk PRIMARY KEY (id),
      CONSTRAINT owner_reference_id_unique UNIQUE (reference_id)
    )
  `);

  // Create location table
  await knex.raw(`
    CREATE TABLE public."location" (
      id serial4 NOT NULL,
      owner_id int4 NOT NULL,
      reference_id int4 NOT NULL,
      sensor_type public."sensor_type_enum" NOT NULL,
      location_name varchar NULL,
      timezone varchar NOT NULL,
      coordinate public.geometry(point, 3857) NOT NULL,
      created_at timestamp DEFAULT now() NOT NULL,
      deteted_at timestamp NULL,
      licenses varchar[] NULL,
      data_source varchar DEFAULT 'AirGradient'::character varying NOT NULL,
      provider varchar NULL,
      CONSTRAINT location_pk PRIMARY KEY (id),
      CONSTRAINT unique_reference_id_data_source UNIQUE (reference_id, data_source),
      CONSTRAINT owner_fk FOREIGN KEY (owner_id) REFERENCES public."owner"(id)
    )
  `);

  await knex.raw('CREATE INDEX data_source_idx ON public.location USING btree (data_source)');

  // Create measurement table
  await knex.raw(`
    CREATE TABLE public.measurement (
      location_id int4 NOT NULL,
      pm25 float8 NULL,
      pm10 float8 NULL,
      atmp float8 NULL,
      rhum float8 NULL,
      rco2 int4 NULL,
      o3 int4 NULL,
      no2 int4 NULL,
      measured_at timestamp DEFAULT now() NOT NULL,
      created_at timestamp DEFAULT now() NOT NULL,
      CONSTRAINT unique_location_id_measured_at UNIQUE (location_id, measured_at),
      CONSTRAINT location_fk FOREIGN KEY (location_id) REFERENCES public."location"(id)
    ) PARTITION BY RANGE (measured_at)
  `);

  await knex.raw(
    'CREATE INDEX measurement_measured_at_idx ON ONLY public.measurement USING btree (measured_at DESC)',
  );

  // Enable timeseries
  await knex.raw(`
    SELECT enable_ts_table(
      'measurement',
      partition_duration := '1 week'
    )
  `);
}

export async function down(knex: Knex): Promise<void> {
  await knex.raw('DROP TABLE IF EXISTS public.measurement');
  await knex.raw('DROP TABLE IF EXISTS public."location"');
  await knex.raw('DROP TABLE IF EXISTS public."owner"');
  await knex.raw('DROP TYPE IF EXISTS public."sensor_type_enum"');
}
