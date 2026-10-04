-- model-profiles (agents): one content-free record per profile slot call, with its attempts (AC-229, feature ADR-0004).
-- Append-only in code. No FK on owner_id or the profile reference: the record outlives a deleted profile.
CREATE TABLE IF NOT EXISTS model_call
(
    id                   UUID                     NOT NULL,
    owner_id             UUID                     NOT NULL,
    system_profile_key   VARCHAR(16),
    custom_profile_id    UUID,
    slot                 VARCHAR(8)               NOT NULL,
    outcome              VARCHAR(24)              NOT NULL,
    answered_by_model_id VARCHAR(200),
    fallback             BOOLEAN                  NOT NULL,
    started_at           TIMESTAMP WITH TIME ZONE NOT NULL,
    finished_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT model_call_profile_ref_ck CHECK (num_nonnulls(system_profile_key, custom_profile_id) = 1),
    CONSTRAINT model_call_system_key_ck CHECK (system_profile_key IN ('fast', 'balanced', 'careful')),
    CONSTRAINT model_call_slot_ck CHECK (slot IN ('text', 'vision', 'image')),
    CONSTRAINT model_call_outcome_ck
        CHECK (outcome IN ('answered', 'no-model-available', 'no-model-answered', 'ai-not-configured')),
    CONSTRAINT model_call_answered_by_ck CHECK ((outcome = 'answered') = (answered_by_model_id IS NOT NULL)),
    CONSTRAINT model_call_finished_ck CHECK (finished_at >= started_at)
);

CREATE TABLE IF NOT EXISTS model_call_attempt
(
    model_call_id UUID         NOT NULL,
    position      SMALLINT     NOT NULL,
    model_id      VARCHAR(200) NOT NULL,
    outcome       VARCHAR(24)  NOT NULL,
    PRIMARY KEY (model_call_id, position),
    CONSTRAINT model_call_attempt_call_fk
        FOREIGN KEY (model_call_id) REFERENCES model_call (id) ON DELETE CASCADE,
    CONSTRAINT model_call_attempt_position_ck CHECK (position BETWEEN 1 AND 3),
    CONSTRAINT model_call_attempt_outcome_ck CHECK (outcome IN ('answered', 'missing', 'unavailable', 'provider-error',
                                                                'rate-limited', 'timeout', 'too-large',
                                                                'content-refused', 'invalid-request'))
);
