-- T13 (DEC-054) — the drill engine: a drill for each question a rule can answer about a static chart.
--
-- Two modes (ICT_CONCEPTS.md, teaching modes). Identification (A): mark every instance, the answer
-- is the candles marked. Classification (B): one structure is marked, the learner names it, the
-- answer is the name chosen. A drill is answered one way or the other, never both, and once.
--
-- Drills on real data are graded; on simulated data they stay developer fixtures, which the
-- ck_drill_simulated_never_graded constraint from V6 still guarantees (DEC-034).

ALTER TABLE drill DROP CONSTRAINT ck_drill_kind;
ALTER TABLE drill ADD CONSTRAINT ck_drill_kind CHECK (kind IN (
    'FVG_IDENTIFICATION', 'SWING_HIGH_IDENTIFICATION', 'SWING_LOW_IDENTIFICATION',
    'VI_IDENTIFICATION', 'DISPLACEMENT_IDENTIFICATION', 'EQHL_IDENTIFICATION',
    'SWEEP_IDENTIFICATION', 'STRUCTURE_CLASSIFICATION', 'BOS_CHOCH_CLASSIFICATION',
    'SWEEP_RUN_CLASSIFICATION'));

-- Mode B's answer: the name chosen, from the options the drill offered.
ALTER TABLE drill ADD COLUMN choice TEXT;

-- Mode A: candles the rule cannot judge from what the chart shows — a gap whose first candle is
-- before the window, a swing whose confirming candle is after it, a sweep of a level drawn before
-- it. A mark on one counts neither way. Epoch seconds, fixed when the drill is made.
ALTER TABLE drill ADD COLUMN excused JSONB NOT NULL DEFAULT '[]'::jsonb;

ALTER TABLE drill DROP CONSTRAINT ck_drill_answer;
ALTER TABLE drill ADD CONSTRAINT ck_drill_answer CHECK (
    (marks IS NULL OR choice IS NULL)
    AND ((marks IS NOT NULL OR choice IS NOT NULL) = (answered_at IS NOT NULL)));
