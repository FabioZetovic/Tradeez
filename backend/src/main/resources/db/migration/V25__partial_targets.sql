-- T28 (DEC-074) — part of a position taken off before the target. The plan says where, for how many contracts,
-- and whether the stop on the rest then moves to the entry; written once, like the rest of the plan (DEC-013).
-- A result, and a close at market, keep what the partial did: when, at what price, for how many contracts, and
-- whether it filled at an open beyond its level. All null when there was none, or it was never reached.

ALTER TABLE trade_plan
    ADD COLUMN partial_ticks           BIGINT,
    ADD COLUMN partial_contracts       INTEGER,
    ADD COLUMN breakeven_after_partial BOOLEAN NOT NULL DEFAULT FALSE,
    -- Some of the contracts, not none and not all, between the price and the target; the breakeven move only
    -- with a partial to follow.
    ADD CONSTRAINT ck_trade_plan_partial CHECK (
        (partial_ticks IS NULL AND partial_contracts IS NULL AND NOT breakeven_after_partial) OR
        (partial_ticks IS NOT NULL AND partial_contracts BETWEEN 1 AND contracts - 1 AND (
            (direction = 'LONG'  AND partial_ticks > reference_ticks AND partial_ticks < target_ticks) OR
            (direction = 'SHORT' AND partial_ticks < reference_ticks AND partial_ticks > target_ticks)))
    );

ALTER TABLE practice_result
    ADD COLUMN partial_time       TIMESTAMPTZ,
    ADD COLUMN partial_exit_ticks BIGINT,
    ADD COLUMN partial_pnl_ticks  BIGINT,
    ADD COLUMN partial_contracts  INTEGER,
    ADD COLUMN partial_gap        BOOLEAN,
    ADD CONSTRAINT ck_practice_result_partial CHECK (
        (partial_time IS NULL AND partial_exit_ticks IS NULL AND partial_pnl_ticks IS NULL
            AND partial_contracts IS NULL AND partial_gap IS NULL) OR
        (partial_time IS NOT NULL AND partial_exit_ticks IS NOT NULL AND partial_pnl_ticks IS NOT NULL
            AND partial_contracts >= 1 AND partial_gap IS NOT NULL)
    );

ALTER TABLE practice_close
    ADD COLUMN partial_time       TIMESTAMPTZ,
    ADD COLUMN partial_exit_ticks BIGINT,
    ADD COLUMN partial_pnl_ticks  BIGINT,
    ADD COLUMN partial_contracts  INTEGER,
    ADD COLUMN partial_gap        BOOLEAN,
    ADD CONSTRAINT ck_practice_close_partial CHECK (
        (partial_time IS NULL AND partial_exit_ticks IS NULL AND partial_pnl_ticks IS NULL
            AND partial_contracts IS NULL AND partial_gap IS NULL) OR
        (partial_time IS NOT NULL AND partial_exit_ticks IS NOT NULL AND partial_pnl_ticks IS NOT NULL
            AND partial_contracts >= 1 AND partial_gap IS NOT NULL)
    );
