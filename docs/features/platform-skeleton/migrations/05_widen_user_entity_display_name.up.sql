-- display_name holds the Owner's email, which may be up to 254 characters (owner.email).
ALTER TABLE user_entities
    ALTER COLUMN display_name TYPE VARCHAR(254);
