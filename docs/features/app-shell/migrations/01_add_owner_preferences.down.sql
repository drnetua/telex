-- Reverts 01_add_owner_preferences.up.sql. Dropping the columns drops their constraints.
ALTER TABLE owner
    DROP COLUMN IF EXISTS time_zone_is_fallback,
    DROP COLUMN IF EXISTS time_zone,
    DROP COLUMN IF EXISTS theme;
