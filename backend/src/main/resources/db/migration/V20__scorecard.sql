-- T23 (DEC-067) — the scorecard: what a trade did, and how it was made, side by side and never added up
-- into one number (DEC-027).
--
-- A plan may now carry how sure the learner was that it would reach its target before its stop, in whole
-- tens of percent; calibration compares that with how often it did. And it carries the risk limit the
-- learner had set when it was placed — copied, so changing the limit later cannot rewrite whether an old
-- trade kept it. Both written once with the plan (trade_plan_immutable, INVARIANT 10).

ALTER TABLE trade_plan
    ADD COLUMN confidence       INTEGER,
    ADD COLUMN risk_limit_cents BIGINT,
    ADD CONSTRAINT ck_trade_plan_confidence CHECK (confidence BETWEEN 10 AND 90 AND confidence % 10 = 0),
    ADD CONSTRAINT ck_trade_plan_risk_limit CHECK (risk_limit_cents BETWEEN 100 AND 100000000);

-- The learner's own rules, set before a session rather than per trade: a limit chosen trade by trade is
-- not a limit. One row per learner; no row means nothing has been set.
CREATE TABLE practice_settings (
    user_id          UUID        PRIMARY KEY REFERENCES app_user (id) ON DELETE CASCADE,
    -- The most a single trade may risk: the stop's distance times the contracts times the tick value.
    risk_limit_cents BIGINT      CHECK (risk_limit_cents BETWEEN 100 AND 100000000),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
