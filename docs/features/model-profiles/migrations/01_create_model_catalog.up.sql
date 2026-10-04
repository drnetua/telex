-- model-profiles (llm): the last known Model Catalog, kept across restarts and provider outages (AC-212).
-- A refresh replaces every entry and stamps the state row in one transaction; only slot-fit models are stored.
CREATE TABLE IF NOT EXISTS model_catalog_entry
(
    model_id              VARCHAR(200)   NOT NULL,
    name                  VARCHAR(200)   NOT NULL,
    provider              VARCHAR(100)   NOT NULL,
    takes_text            BOOLEAN        NOT NULL,
    takes_images          BOOLEAN        NOT NULL,
    produces_text         BOOLEAN        NOT NULL,
    produces_images       BOOLEAN        NOT NULL,
    input_price_per_mtok  NUMERIC(14, 6),
    output_price_per_mtok NUMERIC(14, 6),
    price_per_image       NUMERIC(14, 6),
    context_length        INTEGER,
    PRIMARY KEY (model_id),
    CONSTRAINT model_catalog_entry_fits_a_slot_ck
        CHECK ((takes_text AND produces_text) OR (takes_images AND produces_text) OR produces_images),
    CONSTRAINT model_catalog_entry_input_price_ck CHECK (input_price_per_mtok >= 0),
    CONSTRAINT model_catalog_entry_output_price_ck CHECK (output_price_per_mtok >= 0),
    CONSTRAINT model_catalog_entry_image_price_ck CHECK (price_per_image >= 0),
    CONSTRAINT model_catalog_entry_context_length_ck CHECK (context_length > 0)
);

-- One row (id = 1), written on the first refresh attempt. "Couldn't be updated" = last_failed_at > last_refreshed_at.
CREATE TABLE IF NOT EXISTS model_catalog_state
(
    id                SMALLINT                 NOT NULL,
    last_refreshed_at TIMESTAMP WITH TIME ZONE,
    last_failed_at    TIMESTAMP WITH TIME ZONE,
    PRIMARY KEY (id),
    CONSTRAINT model_catalog_state_single_row_ck CHECK (id = 1),
    CONSTRAINT model_catalog_state_attempted_ck CHECK (num_nonnulls(last_refreshed_at, last_failed_at) >= 1)
);
