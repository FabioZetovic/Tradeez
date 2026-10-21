-- T26 (DEC-071) — management drills, mode E (DEC-023): a trade open and in profit; hold, stop to breakeven, or
-- half off? Like a prediction (V16), decided when made from stored candles and written once — the question holds
-- the trade and what each choice made, the outcome the choices that paid most — and graded by a real future only.

ALTER TABLE drill DROP CONSTRAINT ck_drill_kind;
ALTER TABLE drill ADD CONSTRAINT ck_drill_kind CHECK (kind IN (
    'FVG_IDENTIFICATION', 'SWING_HIGH_IDENTIFICATION', 'SWING_LOW_IDENTIFICATION',
    'VI_IDENTIFICATION', 'DISPLACEMENT_IDENTIFICATION', 'EQHL_IDENTIFICATION',
    'SWEEP_IDENTIFICATION', 'STRUCTURE_CLASSIFICATION', 'BOS_CHOCH_CLASSIFICATION',
    'SWEEP_RUN_CLASSIFICATION', 'LIQUIDITY_FIRST_PREDICTION', 'ERL_IRL_PREDICTION',
    'OPEN_TRADE_MANAGEMENT'));

ALTER TABLE drill DROP CONSTRAINT ck_drill_prediction;
ALTER TABLE drill ADD CONSTRAINT ck_drill_prediction CHECK (
    (question IS NULL) = (outcome IS NULL)
    AND (outcome IS NULL) = (outcome_at IS NULL)
    AND (outcome IS NULL) = (reveal_to IS NULL)
    AND (outcome_at IS NULL OR (outcome_at >= window_to AND reveal_to > outcome_at))
    AND ((outcome IS NULL) = (kind NOT LIKE '%\_PREDICTION' AND kind <> 'OPEN_TRADE_MANAGEMENT')));
