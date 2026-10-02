-- platform-skeleton: the Owner account (AC-34). One row per canonical email address.
CREATE TABLE IF NOT EXISTS owner
(
    id              UUID                     NOT NULL,
    email           VARCHAR(254)             NOT NULL,
    canonical_email VARCHAR(254)             NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT owner_canonical_email_lowercase_ck CHECK (canonical_email = lower(canonical_email))
);

CREATE UNIQUE INDEX IF NOT EXISTS owner_canonical_email_uq ON owner (canonical_email);
