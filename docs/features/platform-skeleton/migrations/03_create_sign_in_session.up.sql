-- platform-skeleton: one row per browser sign-in, behind the opaque telex_session cookie (ADR-0001).
CREATE TABLE IF NOT EXISTS sign_in_session
(
    id               UUID                     NOT NULL,
    owner_id         UUID                     NOT NULL,
    key_hash         BYTEA                    NOT NULL,
    user_agent_label VARCHAR(100)             NOT NULL,
    device_type      VARCHAR(16)              NOT NULL,
    time_zone        VARCHAR(64)              NOT NULL,
    started_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    last_activity_at TIMESTAMP WITH TIME ZONE NOT NULL,
    ended_at         TIMESTAMP WITH TIME ZONE,
    PRIMARY KEY (id),
    CONSTRAINT sign_in_session_owner_fk FOREIGN KEY (owner_id) REFERENCES owner (id),
    CONSTRAINT sign_in_session_key_hash_len_ck CHECK (octet_length(key_hash) = 32),
    CONSTRAINT sign_in_session_device_type_ck CHECK (device_type IN ('phone', 'tablet', 'computer', 'unknown')),
    CONSTRAINT sign_in_session_activity_ck CHECK (last_activity_at >= started_at),
    CONSTRAINT sign_in_session_ended_ck CHECK (ended_at IS NULL OR ended_at >= started_at)
);

CREATE UNIQUE INDEX IF NOT EXISTS sign_in_session_key_hash_uq ON sign_in_session (key_hash);

CREATE INDEX IF NOT EXISTS sign_in_session_owner_id_idx ON sign_in_session (owner_id);
