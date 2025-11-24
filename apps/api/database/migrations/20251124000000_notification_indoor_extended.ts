
// Changes:
// 1. Rename threshold_ug_m3 -> threshold (generic name for multi-parameter support)
// 2. Rename unit -> display_unit (more descriptive naming)
// 3. Add parameter column (pm25, rco2, tvoc, nox_index, atmp, rhum)
//    - Default to 'pm25' for existing records
// 4. Add monitor_type column (owned, public) - default 'public'
// 5. Add place_id column (nullable, required when monitor_type is 'owned')
//

import type { Knex } from 'knex';

export async function up(knex: Knex): Promise<void> {
  // 1. Rename threshold_ug_m3 to threshold
  await knex.raw(`
    ALTER TABLE public.notifications
    RENAME COLUMN threshold_ug_m3 TO threshold
  `);

  // 2. Rename unit to display_unit
  await knex.raw(`
    ALTER TABLE public.notifications
    RENAME COLUMN unit TO display_unit
  `);

  // 3. Add parameter column with default 'pm25' for existing records (IF NOT EXISTS)
  await knex.raw(`
    DO $$
    BEGIN
      IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'notifications' AND column_name = 'parameter') THEN
        ALTER TABLE public.notifications ADD COLUMN parameter VARCHAR(50) NOT NULL DEFAULT 'pm25';
      END IF;
    END $$;
  `);

  // Add check constraint for valid parameter values (IF NOT EXISTS)
  await knex.raw(`
    DO $$
    BEGIN
      IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'valid_parameter_check') THEN
        ALTER TABLE public.notifications ADD CONSTRAINT valid_parameter_check CHECK (parameter IN ('pm25', 'rco2', 'tvoc', 'nox_index', 'atmp', 'rhum'));
      END IF;
    END $$;
  `);

  // Update column comments
  await knex.raw(`
    COMMENT ON COLUMN public.notifications.threshold IS 'Threshold value for threshold-based notifications (unit depends on parameter type)'
  `);

  await knex.raw(`
    COMMENT ON COLUMN public.notifications.display_unit IS 'Display unit for notification values (ug, us_aqi, ppm, index, celsius, fahrenheit, percent)'
  `);

  await knex.raw(`
    COMMENT ON COLUMN public.notifications.parameter IS 'Parameter to monitor: pm25, rco2, tvoc, nox_index, atmp, rhum'
  `);

  // Create index for parameter-based queries
  await knex.raw(`
    CREATE INDEX IF NOT EXISTS idx_notifications_parameter
    ON public.notifications (parameter)
  `);

  // 4. Add monitor_type column with default 'public' (IF NOT EXISTS)
  await knex.raw(`
    DO $$
    BEGIN
      IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'notifications' AND column_name = 'monitor_type') THEN
        ALTER TABLE public.notifications ADD COLUMN monitor_type VARCHAR(20) NOT NULL DEFAULT 'public';
      END IF;
    END $$;
  `);

  // Add check constraint for valid monitor_type values (IF NOT EXISTS)
  await knex.raw(`
    DO $$
    BEGIN
      IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'valid_monitor_type_check') THEN
        ALTER TABLE public.notifications ADD CONSTRAINT valid_monitor_type_check CHECK (monitor_type IN ('owned', 'public'));
      END IF;
    END $$;
  `);

  // 5. Add place_id column (nullable, IF NOT EXISTS)
  await knex.raw(`
    DO $$
    BEGIN
      IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'notifications' AND column_name = 'place_id') THEN
        ALTER TABLE public.notifications ADD COLUMN place_id INTEGER NULL;
      END IF;
    END $$;
  `);

  // Add column comments
  await knex.raw(`
    COMMENT ON COLUMN public.notifications.monitor_type IS 'Type of monitor: owned (user''s own device) or public (community monitor)'
  `);

  await knex.raw(`
    COMMENT ON COLUMN public.notifications.place_id IS 'Place ID for owned monitors (required when monitor_type is owned)'
  `);

  // Create index for monitor_type queries
  await knex.raw(`
    CREATE INDEX IF NOT EXISTS idx_notifications_monitor_type
    ON public.notifications (monitor_type)
  `);
}

export async function down(knex: Knex): Promise<void> {
  // Drop monitor_type index
  await knex.raw('DROP INDEX IF EXISTS public.idx_notifications_monitor_type');

  // Drop place_id column
  await knex.raw(`
    ALTER TABLE public.notifications
    DROP COLUMN IF EXISTS place_id
  `);

  // Drop monitor_type constraint
  await knex.raw(`
    ALTER TABLE public.notifications
    DROP CONSTRAINT IF EXISTS valid_monitor_type_check
  `);

  // Drop monitor_type column
  await knex.raw(`
    ALTER TABLE public.notifications
    DROP COLUMN IF EXISTS monitor_type
  `);

  // Drop parameter index
  await knex.raw('DROP INDEX IF EXISTS public.idx_notifications_parameter');

  // Drop check constraint
  await knex.raw(`
    ALTER TABLE public.notifications
    DROP CONSTRAINT IF EXISTS valid_parameter_check
  `);

  // Drop parameter column
  await knex.raw(`
    ALTER TABLE public.notifications
    DROP COLUMN IF EXISTS parameter
  `);

  // Rename columns back to original names (IF EXISTS)
  await knex.raw(`
    DO $$
    BEGIN
      IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'notifications' AND column_name = 'threshold') THEN
        ALTER TABLE public.notifications RENAME COLUMN threshold TO threshold_ug_m3;
      END IF;
    END $$;
  `);

  await knex.raw(`
    DO $$
    BEGIN
      IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'notifications' AND column_name = 'display_unit') THEN
        ALTER TABLE public.notifications RENAME COLUMN display_unit TO unit;
      END IF;
    END $$;
  `);
}
