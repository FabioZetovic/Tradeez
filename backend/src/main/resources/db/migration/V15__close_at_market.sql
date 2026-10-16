-- DEC-057 — a running trade can be closed at market, as in any real platform.
--
-- The planned outcome stays exactly as it was written at the lock (practice_result, immutable since
-- V5): what would have happened is part of the record. A close made before that outcome is a second,
-- separate fact — when the learner left, at what price, and what that made — written once and never
-- changed, like everything else a learner commits to.

CREATE TABLE practice_close (
    id                 UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id         UUID        NOT NULL REFERENCES practice_session (id) ON DELETE CASCADE,
    seq                INT         NOT NULL,
    exit_time          TIMESTAMPTZ NOT NULL,
    exit_ticks         BIGINT      NOT NULL,
    pnl_ticks          BIGINT      NOT NULL,
    net_pnl_cents      BIGINT      NOT NULL,
    commission_cents   BIGINT      NOT NULL,
    mae_ticks          BIGINT      NOT NULL,
    mfe_ticks          BIGINT      NOT NULL,
    resolution         TEXT        NOT NULL,
    engine_version     TEXT        NOT NULL,
    fill_model_version TEXT        NOT NULL,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),

    -- One close per trade, and only of a trade that exists.
    CONSTRAINT uq_practice_close UNIQUE (session_id, seq),
    CONSTRAINT fk_practice_close_plan FOREIGN KEY (session_id, seq)
        REFERENCES trade_plan (session_id, seq) ON DELETE CASCADE,
    CONSTRAINT ck_practice_close_excursions CHECK (mae_ticks >= 0 AND mfe_ticks >= 0)
);

CREATE TRIGGER practice_close_immutable
    BEFORE UPDATE ON practice_close
    FOR EACH ROW EXECUTE FUNCTION forbid_update();
