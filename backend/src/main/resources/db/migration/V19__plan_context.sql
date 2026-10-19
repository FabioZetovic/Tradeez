-- T22 (DEC-066) — what a learner meant by a trade, written with it: the ICT model they chose, the bias
-- they held and the liquidity they expected price to reach; and what the app saw at that moment — the
-- killzone, the daily-bias-1.0.0 suggestion, and the chosen model's checklist checked against the rules
-- confirmed by the cursor. All optional, all written once with the plan (trade_plan_immutable, INVARIANT 10).

ALTER TABLE trade_plan
    ADD COLUMN model          TEXT,
    ADD COLUMN bias           TEXT,
    ADD COLUMN dol_ticks      BIGINT,
    ADD COLUMN killzone       TEXT,
    ADD COLUMN suggested_bias TEXT,
    -- The checklist as checked: the model's rule version and each item, met or not, in words.
    ADD COLUMN checklist      JSONB,
    ADD CONSTRAINT ck_trade_plan_model CHECK (model IN ('MODEL_2022', 'SILVER_BULLET', 'OTE', 'UNICORN',
        'TURTLE_SOUP', 'BREAKER', 'SESSION_REVERSAL', 'OPENING_RANGE_GAP')),
    ADD CONSTRAINT ck_trade_plan_bias CHECK (bias IN ('BULLISH', 'BEARISH')),
    ADD CONSTRAINT ck_trade_plan_suggested_bias CHECK (suggested_bias IN ('BULLISH', 'BEARISH')),
    ADD CONSTRAINT ck_trade_plan_killzone CHECK (killzone IN ('ASIA', 'LONDON', 'NEW_YORK_AM', 'NEW_YORK_PM')),
    ADD CONSTRAINT ck_trade_plan_dol CHECK (dol_ticks > 0),
    -- A checklist belongs to a model.
    ADD CONSTRAINT ck_trade_plan_checklist CHECK (checklist IS NULL OR model IS NOT NULL);
