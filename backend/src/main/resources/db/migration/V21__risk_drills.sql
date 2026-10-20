-- T26 (DEC-070) — risk drills, mode F (DEC-023): the arithmetic of risk against the clock. A question is
-- generated, stored with its answer, and answered once; the answer leaves the server only with the result.

CREATE TABLE risk_drill (
    id           UUID        PRIMARY KEY,
    user_id      UUID        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    kind         TEXT        NOT NULL CHECK (kind IN ('CONTRACTS', 'STREAK', 'RECOVERY')),
    rule_version TEXT        NOT NULL,
    -- The givens, the question in words, its unit, time limit and working (risk-1.0.0).
    question     JSONB       NOT NULL,
    answer       NUMERIC     NOT NULL,
    tolerance    NUMERIC     NOT NULL CHECK (tolerance >= 0),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    given        NUMERIC,
    answered_at  TIMESTAMPTZ,
    correct      BOOLEAN,
    -- Answered once, and all of it at once.
    CONSTRAINT ck_risk_drill_answered CHECK ((given IS NULL) = (answered_at IS NULL)
        AND (given IS NULL) = (correct IS NULL)),
    CONSTRAINT ck_risk_drill_order CHECK (answered_at IS NULL OR answered_at >= created_at)
);

CREATE INDEX ix_risk_drill_user ON risk_drill (user_id, kind, created_at);
