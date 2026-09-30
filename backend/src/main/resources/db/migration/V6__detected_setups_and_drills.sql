-- Detected setups and the first drill (task 7).
--
-- DEC-026 — detected_setup is the keystone table: every hit a detector finds, stored once, with
-- everything needed to reproduce it and to explain it. Drills are the first consumer; practice-
-- session selection, stop-placement scoring and review annotations are meant to read it too.
--
-- DEC-034 — a drill built on SIMULATED data is a developer fixture. Enforced below by a CHECK, not
-- only by the absence of grading code: a later feature that starts grading drills cannot quietly
-- start grading the simulated ones.

CREATE TABLE detected_setup (
    id                 UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    instrument_id      UUID        NOT NULL REFERENCES instrument (id),
    data_class         data_class  NOT NULL,
    -- DEC-012. For generated data the market is (seed, generator version); real data has neither.
    seed               BIGINT,
    generator_version  TEXT,
    timeframe          TEXT        NOT NULL,

    -- What found it, under which rule, with which knobs (DEC-026). Two parameter sets are two
    -- different claims, so parameters are part of a hit's identity.
    detector           TEXT        NOT NULL,
    rule_version       TEXT        NOT NULL,
    params             JSONB       NOT NULL,
    methodology_source TEXT        NOT NULL,

    direction          TEXT        NOT NULL,
    -- The pattern's span, and the candle a learner points at (for an FVG, the middle one).
    start_time         TIMESTAMPTZ NOT NULL,
    anchor_time        TIMESTAMPTZ NOT NULL,
    end_time           TIMESTAMPTZ NOT NULL,
    -- INVARIANT 3: integer ticks.
    zone_low_ticks     BIGINT      NOT NULL,
    zone_high_ticks    BIGINT      NOT NULL,

    -- The numbers the rule was decided on. Explanations are rendered from these, never hand-written.
    evidence           JSONB       NOT NULL,
    -- DEC-026: optional human confirmation. Nothing sets it yet; it exists so a reviewed hit can be
    -- told apart from a merely detected one without a migration later.
    human_validated    BOOLEAN     NOT NULL DEFAULT false,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT ck_detected_setup_direction CHECK (direction IN ('BULLISH', 'BEARISH')),
    CONSTRAINT ck_detected_setup_span CHECK (start_time <= anchor_time AND anchor_time <= end_time),
    CONSTRAINT ck_detected_setup_zone CHECK (zone_low_ticks < zone_high_ticks),
    CONSTRAINT ck_detected_setup_reproducible CHECK (
        data_class <> 'SIMULATED' OR (seed IS NOT NULL AND generator_version IS NOT NULL)
    ),
    -- Scanning the same stretch twice must not store the same hit twice. NULLS NOT DISTINCT so that
    -- real data, which has no seed, is deduplicated too.
    CONSTRAINT uq_detected_setup UNIQUE NULLS NOT DISTINCT (
        instrument_id, seed, generator_version, timeframe, detector, rule_version, params,
        anchor_time, direction
    )
);

CREATE INDEX ix_detected_setup_lookup
    ON detected_setup (instrument_id, timeframe, detector, rule_version, anchor_time);


CREATE TABLE drill (
    id                 UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id            UUID        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    kind               TEXT        NOT NULL,
    instrument_id      UUID        NOT NULL REFERENCES instrument (id),
    timeframe          TEXT        NOT NULL,
    data_class         data_class  NOT NULL,
    seed               BIGINT,
    generator_version  TEXT,
    detector           TEXT        NOT NULL,
    rule_version       TEXT        NOT NULL,

    -- The candles shown: [window_from, window_to).
    window_from        TIMESTAMPTZ NOT NULL,
    window_to          TIMESTAMPTZ NOT NULL,

    graded             BOOLEAN     NOT NULL DEFAULT false,

    -- The learner's answer: epoch seconds of the candles they marked. Written once.
    marks              JSONB,
    answered_at        TIMESTAMPTZ,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT ck_drill_kind CHECK (kind IN ('FVG_IDENTIFICATION')),
    CONSTRAINT ck_drill_window CHECK (window_from < window_to),
    CONSTRAINT ck_drill_answer CHECK ((marks IS NULL) = (answered_at IS NULL)),
    -- DEC-034, where it cannot be bypassed.
    CONSTRAINT ck_drill_simulated_never_graded CHECK (NOT graded OR data_class <> 'SIMULATED')
);

CREATE INDEX ix_drill_user ON drill (user_id, created_at DESC);

-- The answer key: which detected setups the drill's window contains.
CREATE TABLE drill_setup (
    drill_id           UUID        NOT NULL REFERENCES drill (id) ON DELETE CASCADE,
    setup_id           UUID        NOT NULL REFERENCES detected_setup (id),
    PRIMARY KEY (drill_id, setup_id)
);


-- An answered drill is a record, not a draft: the answer cannot be changed after the learner has
-- seen the result. Same reasoning as DEC-013 for trade plans.
CREATE FUNCTION forbid_update_after_answer() RETURNS trigger AS $$
BEGIN
    IF OLD.answered_at IS NOT NULL THEN
        RAISE EXCEPTION 'drill % was already answered and is immutable', OLD.id
            USING ERRCODE = 'integrity_constraint_violation';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER drill_answer_immutable
    BEFORE UPDATE ON drill
    FOR EACH ROW EXECUTE FUNCTION forbid_update_after_answer();
