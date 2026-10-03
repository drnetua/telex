-- model-profiles (agents): the Owner's default profile (AC-51, AC-220). No row means Balanced (feature ADR-0005).
-- ProfileRef is two columns, exactly one set. The composite FK keeps a custom default inside the Owner's own
-- profiles (AC-222) and drops the row when that profile is deleted, so the default falls back to Balanced.
CREATE TABLE IF NOT EXISTS default_model_profile
(
    owner_id           UUID                     NOT NULL,
    system_profile_key VARCHAR(16),
    custom_profile_id  UUID,
    chosen_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (owner_id),
    CONSTRAINT default_model_profile_owner_fk FOREIGN KEY (owner_id) REFERENCES owner (id),
    CONSTRAINT default_model_profile_custom_profile_fk
        FOREIGN KEY (owner_id, custom_profile_id) REFERENCES model_profile (owner_id, id) ON DELETE CASCADE,
    CONSTRAINT default_model_profile_ref_ck CHECK (num_nonnulls(system_profile_key, custom_profile_id) = 1),
    CONSTRAINT default_model_profile_system_key_ck CHECK (system_profile_key IN ('fast', 'balanced', 'careful'))
);
