-- telegram-link: one random per-Owner key, stored only sealed under TELEX_MASTER_KEY (ADR-0003).
-- Sealed form: 12-byte GCM nonce || 32-byte ciphertext || 16-byte tag, AAD = the owner id.
-- Startup opens any one row to check the master key; no rows = an installation that never stored a key.
CREATE TABLE IF NOT EXISTS owner_key
(
    owner_id   UUID                     NOT NULL,
    sealed_key BYTEA                    NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (owner_id),
    CONSTRAINT owner_key_owner_fk FOREIGN KEY (owner_id) REFERENCES owner (id),
    CONSTRAINT owner_key_sealed_key_len_ck CHECK (octet_length(sealed_key) = 60)
);
