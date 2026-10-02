-- platform-skeleton: Spring Security WebAuthn tables (ADR-0002), to the framework's Postgres schema
-- (spring-security-web 7.1.1) with three teleX additions: timestamptz instead of timestamp, a FK from
-- credential to user entity, and the indexes for the lookups the JDBC repositories run.
-- user_entities.name holds the OwnerId as text; every passkey query filters on it (AC-97).
CREATE TABLE IF NOT EXISTS user_entities
(
    id           VARCHAR(1000) NOT NULL,
    name         VARCHAR(100)  NOT NULL,
    display_name VARCHAR(200),
    PRIMARY KEY (id)
);

CREATE UNIQUE INDEX IF NOT EXISTS user_entities_name_uq ON user_entities (name);

CREATE TABLE IF NOT EXISTS user_credentials
(
    credential_id                VARCHAR(1000)            NOT NULL,
    user_entity_user_id          VARCHAR(1000)            NOT NULL,
    public_key                   BYTEA                    NOT NULL,
    signature_count              BIGINT,
    uv_initialized               BOOLEAN,
    backup_eligible              BOOLEAN                  NOT NULL,
    authenticator_transports     VARCHAR(1000),
    public_key_credential_type   VARCHAR(100),
    backup_state                 BOOLEAN                  NOT NULL,
    attestation_object           BYTEA,
    attestation_client_data_json BYTEA,
    created                      TIMESTAMP WITH TIME ZONE,
    last_used                    TIMESTAMP WITH TIME ZONE,
    label                        VARCHAR(1000)            NOT NULL,
    PRIMARY KEY (credential_id),
    CONSTRAINT user_credentials_user_entity_fk
        FOREIGN KEY (user_entity_user_id) REFERENCES user_entities (id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS user_credentials_user_entity_user_id_idx ON user_credentials (user_entity_user_id);
