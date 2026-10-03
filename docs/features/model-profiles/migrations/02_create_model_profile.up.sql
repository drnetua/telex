-- model-profiles (agents): an Owner's custom Model Profiles and their Fallback Chains (AC-213…AC-218, AC-222).
-- System profiles are not stored (feature ADR-0005). Model ids are the provider's strings, deliberately without
-- a FK to the catalog: a model that leaves the catalog stays in the chain (AC-221, AC-10).
CREATE TABLE IF NOT EXISTS model_profile
(
    id         UUID                     NOT NULL,
    owner_id   UUID                     NOT NULL,
    name       VARCHAR(40)              NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT model_profile_owner_fk FOREIGN KEY (owner_id) REFERENCES owner (id),
    CONSTRAINT model_profile_owner_id_id_uq UNIQUE (owner_id, id),
    CONSTRAINT model_profile_name_trimmed_ck CHECK (char_length(name) >= 1 AND name = btrim(name))
);

CREATE UNIQUE INDEX IF NOT EXISTS model_profile_owner_id_lower_name_uq ON model_profile (owner_id, lower(name));

CREATE TABLE IF NOT EXISTS model_profile_slot_model
(
    model_profile_id UUID         NOT NULL,
    slot             VARCHAR(8)   NOT NULL,
    position         SMALLINT     NOT NULL,
    model_id         VARCHAR(200) NOT NULL,
    PRIMARY KEY (model_profile_id, slot, position),
    CONSTRAINT model_profile_slot_model_profile_fk
        FOREIGN KEY (model_profile_id) REFERENCES model_profile (id) ON DELETE CASCADE,
    CONSTRAINT model_profile_slot_model_once_uq UNIQUE (model_profile_id, slot, model_id),
    CONSTRAINT model_profile_slot_model_slot_ck CHECK (slot IN ('text', 'vision', 'image')),
    CONSTRAINT model_profile_slot_model_position_ck CHECK (position BETWEEN 1 AND 3)
);
