-- telegram-link: the chat list of a Linked Account, main and archived (AC-116, AC-121). E04 extends these rows.
-- The composite FK keeps a chat on its account's Owner (AC-03) and deletes it with the account in the unlink
-- transaction (ADR-0002). folder_ids are TDLib chat folder ids; chat_order is TDLib's position order in its list.
CREATE TABLE IF NOT EXISTS channel
(
    id                UUID         NOT NULL,
    owner_id          UUID         NOT NULL,
    linked_account_id UUID         NOT NULL,
    telegram_chat_id  BIGINT       NOT NULL,
    type              VARCHAR(16)  NOT NULL,
    title             VARCHAR(255) NOT NULL,
    folder_ids        INTEGER[]    NOT NULL,
    archived          BOOLEAN      NOT NULL,
    unread_count      INTEGER      NOT NULL,
    chat_order        BIGINT       NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT channel_linked_account_fk
        FOREIGN KEY (linked_account_id, owner_id) REFERENCES linked_account (id, owner_id) ON DELETE CASCADE,
    CONSTRAINT channel_type_ck CHECK (type IN ('private', 'secret', 'basic_group', 'supergroup', 'channel')),
    CONSTRAINT channel_unread_count_ck CHECK (unread_count >= 0)
);

CREATE UNIQUE INDEX IF NOT EXISTS channel_linked_account_telegram_chat_uq
    ON channel (linked_account_id, telegram_chat_id);
