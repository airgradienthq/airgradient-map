-- Migration: Add state tracking fields for threshold notifications
-- Date: 2025-09-26
-- Purpose: Support threshold notification processing with state tracking

-- Add state tracking columns for threshold notifications
ALTER TABLE notifications
ADD COLUMN was_exceeded BOOLEAN DEFAULT FALSE,
ADD COLUMN last_notified_at TIMESTAMP;

-- Add comments to explain field usage
COMMENT ON COLUMN notifications.was_exceeded IS 'For "once" threshold notifications: tracks if threshold is currently exceeded';
COMMENT ON COLUMN notifications.last_notified_at IS 'For cycle-based threshold notifications: tracks when last notification was sent';

-- Create index for efficient threshold notification queries
CREATE INDEX IF NOT EXISTS idx_notifications_threshold_active
    ON notifications(active, alarm_type)
    WHERE alarm_type = 'threshold' AND active = true;

-- Create index for cycle-based notifications that need checking
CREATE INDEX IF NOT EXISTS idx_notifications_threshold_cycle
    ON notifications(active, alarm_type, last_notified_at)
    WHERE alarm_type = 'threshold'
    AND active = true
    AND threshold_cycle IS NOT NULL
    AND threshold_cycle != 'once';

-- Create index for once notifications that haven't been triggered
CREATE INDEX IF NOT EXISTS idx_notifications_threshold_once
    ON notifications(active, alarm_type, was_exceeded)
    WHERE alarm_type = 'threshold'
    AND active = true
    AND threshold_cycle = 'once'
    AND was_exceeded = false;