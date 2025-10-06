-- Migration: Add is_pm25_outlier flag for irrelevant pm2.5 data filtering
-- Date: 2025-09-29
-- Purpose: Filter out an irrelevant pm2.5 data point

-- Add visibility control column
ALTER TABLE public.measurement
    ADD COLUMN is_pm25_outlier boolean NOT NULL DEFAULT false;