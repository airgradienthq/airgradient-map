import { Knex } from 'knex';

export async function up(knex: Knex): Promise<void> {
  // 1. Create new lookup table
  await knex.raw(`
    CREATE TABLE IF NOT EXISTS public.data_source (
        id SERIAL PRIMARY KEY,
        name VARCHAR UNIQUE,
        allow_api_access BOOLEAN,
        url VARCHAR
    );
  `);
  
  // 2. Seed data_source Only if the old column still exists
  await knex.raw(`
    DO $$
    BEGIN
      IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name   = 'location'
          AND column_name  = 'data_source'
      ) THEN
        INSERT INTO public.data_source (name, allow_api_access, url)
        SELECT DISTINCT
            l.data_source AS name,
            TRUE          AS allow_api_access,
            CASE l.data_source
              WHEN 'AirGradient'  THEN 'https://www.airgradient.com'
              WHEN 'OpenAQ'       THEN 'https://openaq.org'
              ELSE NULL
            END AS url
        FROM public."location" AS l
        WHERE l.data_source IS NOT NULL
        ON CONFLICT (name) DO NOTHING;
      END IF;
    END
    $$;
  `);

  // 3. Add new FK column to location if it doesn't exist
  await knex.raw(`
    ALTER TABLE public."location"
      ADD COLUMN IF NOT EXISTS data_source_id INT;
  `);

  // 4. Backfill data_source_id based on existing data_source values Only if old data_source column still exists, and only for rows where data_source_id is NULL
  await knex.raw(`
    DO $$
    BEGIN
      IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name   = 'location'
          AND column_name  = 'data_source'
      ) THEN
        UPDATE public."location" AS l
        SET data_source_id = ds.id
        FROM public.data_source AS ds
        WHERE ds.name = l.data_source
          AND l.data_source_id IS NULL;
      END IF;
    END
    $$;
  `);
  
  // 5. Make new column NOT NULL
  await knex.raw(`
    ALTER TABLE public."location"
      ALTER COLUMN data_source_id SET NOT NULL;
  `);

  // 6. Add FK constraint only if it doesn't already exist
  await knex.raw(`
    DO $$
    BEGIN
      IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'location_data_source_fk'
      ) THEN
        ALTER TABLE public."location"
        ADD CONSTRAINT location_data_source_fk
          FOREIGN KEY (data_source_id) REFERENCES public.data_source(id);
      END IF;
    END
    $$;
  `);

  // 7. Replace old unique constraint (reference_id, data_source) Drop old one if it exists, add new one only if missing
  await knex.raw(`
    ALTER TABLE public."location"
      DROP CONSTRAINT IF EXISTS unique_reference_id_data_source;

    DO $$
    BEGIN
      IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'unique_reference_id_data_source_id'
      ) THEN
        ALTER TABLE public."location"
        ADD CONSTRAINT unique_reference_id_data_source_id
          UNIQUE (reference_id, data_source_id);
      END IF;
    END
    $$;
  `);

  // 8. Drop old data_source column if it still exists
  await knex.raw(`
    ALTER TABLE public."location"
      DROP COLUMN IF EXISTS data_source;
  `);

  // 9. Add index
  await knex.raw(`
    CREATE INDEX IF NOT EXISTS location_data_source_id_idx
      ON public."location" USING btree (data_source_id);
  `);
}

export async function down(knex: Knex): Promise<void> {
  // 1. Recreate old data_source column on location (nullable)
  await knex.raw(`
    ALTER TABLE public."location"
      ADD COLUMN IF NOT EXISTS data_source VARCHAR;
  `);

  // 2. Backfill data_source from data_source_id / data_source.name
  await knex.raw(`
    DO $$
    BEGIN
      IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name   = 'location'
          AND column_name  = 'data_source_id'
      ) AND EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = 'public'
          AND table_name   = 'data_source'
      ) THEN
        UPDATE public."location" AS l
        SET data_source = ds.name
        FROM public.data_source AS ds
        WHERE l.data_source_id = ds.id
          AND l.data_source IS NULL;
      END IF;
    END
    $$;
  `);

  // 3. Drop FK constraint if it exists
  await knex.raw(`
    ALTER TABLE public."location"
      DROP CONSTRAINT IF EXISTS location_data_source_fk;
  `);

  // 4. Drop new unique constraint and restore the old one
  await knex.raw(`
    ALTER TABLE public."location"
      DROP CONSTRAINT IF EXISTS unique_reference_id_data_source_id;

    DO $$
    BEGIN
      IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'unique_reference_id_data_source'
      ) THEN
        ALTER TABLE public."location"
        ADD CONSTRAINT unique_reference_id_data_source
          UNIQUE (reference_id, data_source);
      END IF;
    END
    $$;
  `);

  // 5. Drop index on data_source_id if it exists
  await knex.raw(`
    DROP INDEX IF EXISTS location_data_source_id_idx;
  `);

  // 6. Drop data_source_id column
  await knex.raw(`
    ALTER TABLE public."location"
      DROP COLUMN IF EXISTS data_source_id;
  `);

  // 7. Drop lookup table
  await knex.raw(`
    DROP TABLE IF EXISTS public.data_source;
  `);
}
