-- 0002_add_fcm.sql
-- Add fcm_token for push notifications

ALTER TABLE devices ADD COLUMN IF NOT EXISTS fcm_token TEXT;
