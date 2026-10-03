-- app-shell: the Owner's theme and timezone, saved on the account (ADR-0005, AC-179…AC-186).
-- theme: light, dark or system; existing Owners get system.
-- time_zone: NULL until the first save from the device (AC-183), never cleared after it (AC-186, enforced in identity).
-- time_zone_is_fallback: the first save had to use UTC; drives the "pick your own" hint, cleared when a zone is picked.
-- Constant defaults make the NOT NULL additions metadata-only on Postgres 17, so no backfill step is needed.
ALTER TABLE owner
    ADD COLUMN IF NOT EXISTS theme                 VARCHAR(6)  NOT NULL DEFAULT 'system'
        CONSTRAINT owner_theme_ck CHECK (theme IN ('light', 'dark', 'system')),
    ADD COLUMN IF NOT EXISTS time_zone             VARCHAR(64),
    ADD COLUMN IF NOT EXISTS time_zone_is_fallback BOOLEAN     NOT NULL DEFAULT false;

ALTER TABLE owner
    DROP CONSTRAINT IF EXISTS owner_time_zone_fallback_ck;

ALTER TABLE owner
    ADD CONSTRAINT owner_time_zone_fallback_ck CHECK (NOT time_zone_is_fallback OR time_zone = 'UTC');
