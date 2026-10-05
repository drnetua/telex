-- telegram-link: one row per Linked Account (ADR-0002). One Owner per Telegram account, matched by the
-- Telegram user id, not the phone (AC-04, AC-108). The phone is stored masked: country code + last two digits.
-- tdlib_key_sealed = the session's TDLib database key sealed with the Owner's key, AAD = the account id (ADR-0003).
-- Only a Session lost account may lack a Telegram session (after a master-key reset, sad §7).
CREATE TABLE IF NOT EXISTS linked_account
(
    id                     UUID                     NOT NULL,
    owner_id               UUID                     NOT NULL,
    telegram_user_id       BIGINT                   NOT NULL,
    telegram_session_id    UUID,
    tdlib_key_sealed       BYTEA,
    display_name           VARCHAR(255)             NOT NULL,
    phone_country_code     VARCHAR(3)               NOT NULL,
    phone_last_digits      CHAR(2)                  NOT NULL,
    state                  VARCHAR(16)              NOT NULL,
    chats_total            INTEGER,
    chat_sync_completed_at TIMESTAMP WITH TIME ZONE,
    created_at             TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT linked_account_id_owner_uq UNIQUE (id, owner_id),
    CONSTRAINT linked_account_owner_fk FOREIGN KEY (owner_id) REFERENCES owner (id),
    CONSTRAINT linked_account_state_ck CHECK (state IN ('connected', 'reconnecting', 'session_lost')),
    CONSTRAINT linked_account_session_pair_ck CHECK (num_nonnulls(telegram_session_id, tdlib_key_sealed) IN (0, 2)),
    CONSTRAINT linked_account_session_required_ck CHECK (state = 'session_lost' OR telegram_session_id IS NOT NULL),
    CONSTRAINT linked_account_tdlib_key_len_ck CHECK (tdlib_key_sealed IS NULL OR octet_length(tdlib_key_sealed) = 60),
    CONSTRAINT linked_account_phone_country_code_ck CHECK (phone_country_code ~ '^[0-9]{1,3}$'),
    CONSTRAINT linked_account_phone_last_digits_ck CHECK (phone_last_digits ~ '^[0-9]{2}$'),
    CONSTRAINT linked_account_chats_total_ck CHECK (chats_total IS NULL OR chats_total >= 0),
    CONSTRAINT linked_account_sync_completed_ck CHECK (chat_sync_completed_at IS NULL OR chats_total IS NOT NULL)
);

CREATE UNIQUE INDEX IF NOT EXISTS linked_account_telegram_user_id_uq ON linked_account (telegram_user_id);

CREATE UNIQUE INDEX IF NOT EXISTS linked_account_telegram_session_id_uq ON linked_account (telegram_session_id);

CREATE INDEX IF NOT EXISTS linked_account_owner_id_idx ON linked_account (owner_id);
