-- The Practice slice (task 6).
--
-- A session is a hidden moment in a market. The learner sees the history up to a cursor, commits to
-- a bracket trade, and the market is revealed forward from there.
--
-- Three decisions shape this schema more than any other:
--
--   DEC-031 — replay state lives HERE, not in a server process. The cursor is a column. A backend
--             restart, a dropped connection or a sleeping free-tier instance cannot lose or corrupt
--             a session, because nothing about it exists anywhere else.
--
--   DEC-013 — the plan is immutable. Enforced below by a trigger, not merely by the absence of an
--             update endpoint. Code can be changed, and an endpoint can be added by someone who never
--             read the decision; a trigger that raises an exception cannot be forgotten.
--
--   INVARIANT 2 — outcomes are written once. The result is computed the moment the plan is locked
--             and is likewise immutable, so a learner cannot retry until they win.

CREATE TABLE practice_session (
    id                 UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id            UUID        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    instrument_id      UUID        NOT NULL REFERENCES instrument (id),
    timeframe          TEXT        NOT NULL,

    -- DEC-038: the seed IS the future of a simulated market. SecureRandom, never the public chart's
    -- seed, and never returned to a client until status = COMPLETE.
    seed               BIGINT      NOT NULL,
    generator_version  TEXT        NOT NULL,

    -- Where the visible history begins, and where the replay stood when the session was created.
    history_from       TIMESTAMPTZ NOT NULL,
    start_time         TIMESTAMPTZ NOT NULL,
    -- DEC-009: nothing at or after this instant may leave the server.
    cursor_time        TIMESTAMPTZ NOT NULL,

    status             TEXT        NOT NULL DEFAULT 'ANALYSING',
    version            BIGINT      NOT NULL DEFAULT 0,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT ck_practice_session_status
        CHECK (status IN ('ANALYSING', 'IN_TRADE', 'COMPLETE')),
    -- The replay only moves forward. A cursor that could move back would let a learner rewind past a
    -- losing trade and try again.
    CONSTRAINT ck_practice_session_cursor CHECK (cursor_time >= start_time),
    CONSTRAINT ck_practice_session_history CHECK (history_from < start_time)
);

CREATE INDEX ix_practice_session_user ON practice_session (user_id, created_at DESC);


CREATE TABLE trade_plan (
    id                 UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    -- One plan per session. The unique constraint is what turns a second submission into a
    -- conflict rather than a quiet replacement.
    session_id         UUID        NOT NULL UNIQUE REFERENCES practice_session (id) ON DELETE CASCADE,

    direction          TEXT        NOT NULL,
    stop_ticks         BIGINT      NOT NULL,
    target_ticks       BIGINT      NOT NULL,
    contracts          INTEGER     NOT NULL,
    -- The last visible close when the plan was made: what the learner was actually looking at.
    reference_ticks    BIGINT      NOT NULL,
    submitted_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    submitted_cursor   TIMESTAMPTZ NOT NULL,

    CONSTRAINT ck_trade_plan_direction CHECK (direction IN ('LONG', 'SHORT')),
    CONSTRAINT ck_trade_plan_contracts CHECK (contracts BETWEEN 1 AND 100),
    -- A bracket whose stop and target are on the wrong sides of the price is not a trade.
    CONSTRAINT ck_trade_plan_sides CHECK (
        (direction = 'LONG'  AND stop_ticks < reference_ticks AND target_ticks > reference_ticks) OR
        (direction = 'SHORT' AND stop_ticks > reference_ticks AND target_ticks < reference_ticks)
    )
);


CREATE TABLE practice_result (
    id                 UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id         UUID        NOT NULL UNIQUE REFERENCES practice_session (id) ON DELETE CASCADE,

    outcome            TEXT        NOT NULL,
    entry_time         TIMESTAMPTZ NOT NULL,
    entry_ticks        BIGINT      NOT NULL,
    -- Secret until the cursor passes it: knowing when a trade ends is knowing how it ends.
    exit_time          TIMESTAMPTZ NOT NULL,
    exit_ticks         BIGINT      NOT NULL,
    pnl_ticks          BIGINT      NOT NULL,
    net_pnl_cents      BIGINT      NOT NULL,
    commission_cents   BIGINT      NOT NULL,
    mae_ticks          BIGINT      NOT NULL,
    mfe_ticks          BIGINT      NOT NULL,
    gap_exit           BOOLEAN     NOT NULL,
    resolution         TEXT        NOT NULL,

    -- DEC-012: everything needed to re-derive this row later, and to explain it if it cannot be.
    engine_version     TEXT        NOT NULL,
    fill_model_version TEXT        NOT NULL,
    generator_version  TEXT        NOT NULL,
    seed               BIGINT      NOT NULL,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT ck_practice_result_outcome
        CHECK (outcome IN ('TARGET', 'STOP', 'AMBIGUOUS', 'TIME_EXIT')),
    CONSTRAINT ck_practice_result_excursions CHECK (mae_ticks >= 0 AND mfe_ticks >= 0)
);


-- DEC-013 and INVARIANT 2, enforced where they cannot be bypassed.
CREATE FUNCTION forbid_update() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION '% rows are immutable once written (DEC-013 / INVARIANT 2)', TG_TABLE_NAME
        USING ERRCODE = 'integrity_constraint_violation';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trade_plan_immutable
    BEFORE UPDATE ON trade_plan
    FOR EACH ROW EXECUTE FUNCTION forbid_update();

CREATE TRIGGER practice_result_immutable
    BEFORE UPDATE ON practice_result
    FOR EACH ROW EXECUTE FUNCTION forbid_update();
