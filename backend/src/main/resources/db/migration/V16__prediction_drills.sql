-- T15 (DEC-058) — prediction drills (mode C): a question about what price does after the chart ends,
-- graded by what real price did.
--
-- The question (its targets, drawn on the chart) and the outcome are decided when the drill is made,
-- from stored candles, and written once — the outcome never reaches the client before the answer.
-- A prediction needs a real future: on simulated data there is none to be graded by (DEC-034,
-- INVARIANT 8b), and the database refuses one.

ALTER TABLE drill DROP CONSTRAINT ck_drill_kind;
ALTER TABLE drill ADD CONSTRAINT ck_drill_kind CHECK (kind IN (
    'FVG_IDENTIFICATION', 'SWING_HIGH_IDENTIFICATION', 'SWING_LOW_IDENTIFICATION',
    'VI_IDENTIFICATION', 'DISPLACEMENT_IDENTIFICATION', 'EQHL_IDENTIFICATION',
    'SWEEP_IDENTIFICATION', 'STRUCTURE_CLASSIFICATION', 'BOS_CHOCH_CLASSIFICATION',
    'SWEEP_RUN_CLASSIFICATION', 'LIQUIDITY_FIRST_PREDICTION', 'ERL_IRL_PREDICTION'));

-- The targets and what the chart says about them (distances, obstacles), versioned (predict-1.0.0).
ALTER TABLE drill ADD COLUMN question JSONB;
-- Which target price reached first, the candle that decided it, and how far the answer shows.
ALTER TABLE drill ADD COLUMN outcome TEXT;
ALTER TABLE drill ADD COLUMN outcome_at TIMESTAMPTZ;
ALTER TABLE drill ADD COLUMN reveal_to TIMESTAMPTZ;

ALTER TABLE drill ADD CONSTRAINT ck_drill_prediction CHECK (
    (question IS NULL) = (outcome IS NULL)
    AND (outcome IS NULL) = (outcome_at IS NULL)
    AND (outcome IS NULL) = (reveal_to IS NULL)
    AND (outcome_at IS NULL OR (outcome_at >= window_to AND reveal_to > outcome_at))
    AND ((outcome IS NULL) = (kind NOT LIKE '%\_PREDICTION')));

-- DEC-034: a prediction is graded by a real future, so it exists only on real data.
ALTER TABLE drill ADD CONSTRAINT ck_drill_prediction_real CHECK (outcome IS NULL OR data_class <> 'SIMULATED');
