-- T45 (DEC-082) — mode J, judgement drills: a question about the chart as it stands, answered by a rule when the drill
-- is made. The answer is stored as the outcome, decided at the chart's end, and nothing after it is ever revealed:
-- outcome_at = reveal_to = window_to. Also the daily-bias prediction (ICT-61), answered by what price did, like the
-- other predictions. The kinds the rest of T45 adds are listed here at once.

ALTER TABLE drill DROP CONSTRAINT ck_drill_kind;
ALTER TABLE drill ADD CONSTRAINT ck_drill_kind CHECK (kind IN (
    'FVG_IDENTIFICATION', 'SWING_HIGH_IDENTIFICATION', 'SWING_LOW_IDENTIFICATION',
    'VI_IDENTIFICATION', 'DISPLACEMENT_IDENTIFICATION', 'EQHL_IDENTIFICATION',
    'SWEEP_IDENTIFICATION', 'STRUCTURE_CLASSIFICATION', 'BOS_CHOCH_CLASSIFICATION',
    'SWEEP_RUN_CLASSIFICATION', 'LIQUIDITY_FIRST_PREDICTION', 'ERL_IRL_PREDICTION',
    'OPEN_TRADE_MANAGEMENT', 'SMT_IDENTIFICATION',
    'IFVG_IDENTIFICATION', 'ORDER_BLOCK_IDENTIFICATION', 'BREAKER_IDENTIFICATION', 'UNICORN_IDENTIFICATION',
    'NEAREST_LIQUIDITY_IDENTIFICATION',
    'PREMIUM_DISCOUNT_JUDGEMENT', 'OTE_JUDGEMENT', 'PO3_JUDGEMENT', 'STOP_PLACEMENT_JUDGEMENT',
    'SILVER_BULLET_JUDGEMENT', 'MODEL_2022_JUDGEMENT', 'DAILY_BIAS_PREDICTION'));

ALTER TABLE drill DROP CONSTRAINT ck_drill_prediction;
ALTER TABLE drill ADD CONSTRAINT ck_drill_prediction CHECK (
    (question IS NULL) = (outcome IS NULL)
    AND (outcome IS NULL) = (outcome_at IS NULL)
    AND (outcome IS NULL) = (reveal_to IS NULL)
    AND ((outcome IS NULL) = (kind NOT LIKE '%\_PREDICTION' AND kind <> 'OPEN_TRADE_MANAGEMENT'
        AND kind NOT LIKE '%\_JUDGEMENT'))
    AND (outcome_at IS NULL
        OR (kind LIKE '%\_JUDGEMENT' AND outcome_at = window_to AND reveal_to = window_to)
        OR (kind NOT LIKE '%\_JUDGEMENT' AND outcome_at >= window_to AND reveal_to > outcome_at)));
