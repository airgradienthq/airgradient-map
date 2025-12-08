//
// Purpose: Support threshold notification processing with state tracking
//

import type { Knex } from 'knex';

export async function up(knex: Knex): Promise<void> {
  // Add state tracking columns for threshold notifications
  await knex.raw(`
    ALTER TABLE public.notifications
    ADD COLUMN was_exceeded boolean DEFAULT false,
    ADD COLUMN last_notified_at timestamp NULL
  `);

  // Add column comments
  await knex.raw(`
    COMMENT ON COLUMN public.notifications.was_exceeded IS 'For "once" threshold notifications: tracks if threshold is currently exceeded'
  `);

  await knex.raw(`
    COMMENT ON COLUMN public.notifications.last_notified_at IS 'For cycle-based threshold notifications: tracks when last notification was sent'
  `);

  // Create index for efficient threshold notification queries
  await knex.raw(`
    CREATE INDEX IF NOT EXISTS idx_notifications_threshold_active
    ON public.notifications (active, alarm_type)
    WHERE alarm_type = 'threshold' AND active = true
  `);

  // Create index for cycle-based notifications that need checking
  await knex.raw(`
    CREATE INDEX IF NOT EXISTS idx_notifications_threshold_cycle
    ON public.notifications (active, alarm_type, last_notified_at)
    WHERE alarm_type = 'threshold'
    AND active = true
    AND threshold_cycle IS NOT NULL
    AND threshold_cycle != 'once'
  `);

  // Create index for once notifications that haven't been triggered
  await knex.raw(`
    CREATE INDEX IF NOT EXISTS idx_notifications_threshold_once
    ON public.notifications (active, alarm_type, was_exceeded)
    WHERE alarm_type = 'threshold'
    AND active = true
    AND threshold_cycle = 'once'
    AND was_exceeded = false
  `);
}

export async function down(knex: Knex): Promise<void> {
  // Drop indexes
  await knex.raw('DROP INDEX IF EXISTS public.idx_notifications_threshold_once');
  await knex.raw('DROP INDEX IF EXISTS public.idx_notifications_threshold_cycle');
  await knex.raw('DROP INDEX IF EXISTS public.idx_notifications_threshold_active');

  // Drop columns (comments are automatically removed)
  await knex.raw(`
    ALTER TABLE public.notifications
    DROP COLUMN IF EXISTS last_notified_at,
    DROP COLUMN IF EXISTS was_exceeded
  `);
}
