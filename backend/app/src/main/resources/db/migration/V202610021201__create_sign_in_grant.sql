-- platform-skeleton: one row per sign-in email; hashes only, redeemed by one conditional UPDATE (ADR-0003).
CREATE TABLE IF NOT EXISTS sign_in_grant
(
    id              UUID                     NOT NULL,
    email           VARCHAR(254)             NOT NULL,
    canonical_email VARCHAR(254)             NOT NULL,
    link_token_hash BYTEA                    NOT NULL,
    code_hash       BYTEA                    NOT NULL,
    wrong_attempts  SMALLINT                 NOT NULL DEFAULT 0,
    issued_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    used_at         TIMESTAMP WITH TIME ZONE,
    superseded_at   TIMESTAMP WITH TIME ZONE,
    PRIMARY KEY (id),
    CONSTRAINT sign_in_grant_canonical_email_lowercase_ck CHECK (canonical_email = lower(canonical_email)),
    CONSTRAINT sign_in_grant_link_token_hash_len_ck CHECK (octet_length(link_token_hash) = 32),
    CONSTRAINT sign_in_grant_code_hash_len_ck CHECK (octet_length(code_hash) = 32),
    CONSTRAINT sign_in_grant_wrong_attempts_ck CHECK (wrong_attempts BETWEEN 0 AND 5),
    CONSTRAINT sign_in_grant_expiry_ck CHECK (expires_at > issued_at),
    CONSTRAINT sign_in_grant_used_or_superseded_ck CHECK (num_nonnulls(used_at, superseded_at) <= 1)
);

CREATE UNIQUE INDEX IF NOT EXISTS sign_in_grant_link_token_hash_uq ON sign_in_grant (link_token_hash);

CREATE INDEX IF NOT EXISTS sign_in_grant_live_by_canonical_email_idx
    ON sign_in_grant (canonical_email)
    WHERE used_at IS NULL AND superseded_at IS NULL;
