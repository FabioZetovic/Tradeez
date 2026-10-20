-- T27 (DEC-072) — a learner's trading costs, set like the risk limit before a session: commission and fees per
-- round trip, the ticks a market fill and a stop fill each slip, and whether a target's limit fills on the touch or only once
-- price trades through it. Null means the app's standard (fill-1.3.0's STANDARD model). Each result keeps the
-- model it was resolved with (fill_model_version), so changing these never rewrites an old trade.

ALTER TABLE practice_settings
    ADD COLUMN fees_cents            BIGINT  CHECK (fees_cents BETWEEN 0 AND 5000),
    ADD COLUMN market_slippage_ticks INTEGER CHECK (market_slippage_ticks BETWEEN 0 AND 8),
    ADD COLUMN stop_slippage_ticks   INTEGER CHECK (stop_slippage_ticks BETWEEN 0 AND 8),
    ADD COLUMN limit_through_ticks   INTEGER CHECK (limit_through_ticks BETWEEN 0 AND 4);
