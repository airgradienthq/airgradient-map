import type { Knex } from 'knex';

export async function up(knex: Knex): Promise<void> {
  // Create notifications table
  await knex.raw(`
    CREATE TABLE IF NOT EXISTS public.notifications (
      id SERIAL PRIMARY KEY,
      player_id VARCHAR(255) NOT NULL,
      user_id VARCHAR(255) NOT NULL,
      alarm_type VARCHAR(50) NOT NULL CHECK (alarm_type IN ('threshold', 'scheduled')),
      location_id INTEGER NOT NULL,
      threshold_ug_m3 NUMERIC,
      threshold_cycle VARCHAR(50),
      scheduled_days TEXT[],
      scheduled_time VARCHAR(10),
      scheduled_timezone VARCHAR(50),
      active BOOLEAN DEFAULT true,
      unit VARCHAR(20) NOT NULL,
      created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
      updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

      -- Foreign key constraints
      CONSTRAINT fk_location
        FOREIGN KEY (location_id)
        REFERENCES location(id)
        ON DELETE CASCADE,

      -- Time format validation constraint
      CONSTRAINT valid_time_format_check
        CHECK (
          scheduled_time IS NULL OR
          scheduled_time ~ '^([01]\\?[0-9]|2[0-3]):[0-5][0-9]$'
        )
    );
  `);

  // Create trigger function for updated_at
  await knex.raw(`
    CREATE OR REPLACE FUNCTION update_updated_at_column()
    RETURNS TRIGGER AS $$
    BEGIN
        NEW.updated_at = CURRENT_TIMESTAMP;
        RETURN NEW;
    END;
    $$ language 'plpgsql';
  `);

  // Create trigger
  await knex.raw(`
    CREATE TRIGGER update_notifications_updated_at 
    BEFORE UPDATE ON public.notifications 
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column()
  `);

  // Create indexes
  await knex.raw(
    'CREATE INDEX IF NOT EXISTS idx_notifications_player_id ON public.notifications(player_id)',
  );

  await knex.raw(
    'CREATE INDEX IF NOT EXISTS idx_notifications_location_id ON public.notifications(location_id)',
  );

  await knex.raw(`
    CREATE INDEX IF NOT EXISTS idx_notifications_scheduled 
    ON public.notifications (active, scheduled_time)
    WHERE alarm_type = 'scheduled'
  `);
}

export async function down(knex: Knex): Promise<void> {
  await knex.raw('DROP TRIGGER IF EXISTS update_notifications_updated_at ON public.notifications');
  await knex.raw('DROP FUNCTION IF EXISTS update_updated_at_column()');
  await knex.raw('DROP TABLE IF EXISTS public.notifications');
}
