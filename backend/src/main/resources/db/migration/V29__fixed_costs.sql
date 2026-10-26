-- T41 (DEC-080) — costs are no longer the learner's to set: every new trade pays the standard fees and slippage. The
-- settings keep only the risk limit. Every result already stored keeps the fill model it was resolved with
-- (practice_result.fill_model_version, practice_close.fill_model_version), so no score changes.

ALTER TABLE practice_settings
    DROP COLUMN fees_cents,
    DROP COLUMN market_slippage_ticks,
    DROP COLUMN stop_slippage_ticks,
    DROP COLUMN limit_through_ticks;
