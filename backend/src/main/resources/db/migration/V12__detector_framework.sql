-- T9 / DEC-049: detected_setup for every kind of detector, and the moment each hit became knowable.

-- The shape of a hit: a ZONE between two prices (a gap, a block), a LEVEL at one price (a session
-- high), or a POINT at one price and time (a swing). Every hit so far is an FVG, a zone.
ALTER TABLE detected_setup ADD COLUMN shape TEXT NOT NULL DEFAULT 'ZONE';
ALTER TABLE detected_setup ALTER COLUMN shape DROP DEFAULT;
ALTER TABLE detected_setup ADD CONSTRAINT ck_detected_setup_shape
    CHECK (shape IN ('ZONE', 'LEVEL', 'POINT'));

ALTER TABLE detected_setup DROP CONSTRAINT ck_detected_setup_zone;
ALTER TABLE detected_setup ADD CONSTRAINT ck_detected_setup_zone CHECK (
    (shape = 'ZONE' AND zone_low_ticks < zone_high_ticks)
    OR (shape <> 'ZONE' AND zone_low_ticks = zone_high_ticks)
);

-- Levels and swing points have no direction of their own.
ALTER TABLE detected_setup DROP CONSTRAINT ck_detected_setup_direction;
ALTER TABLE detected_setup ADD CONSTRAINT ck_detected_setup_direction
    CHECK (direction IN ('BULLISH', 'BEARISH', 'NEUTRAL'));

-- When the hit could first be known: the close of the last candle its rule looks at. A replay shows a
-- hit only once its cursor has reached this (DEC-009). Existing hits are FVGs, confirmed when their
-- third candle — end_time — closes.
ALTER TABLE detected_setup ADD COLUMN confirmed_at TIMESTAMPTZ;
UPDATE detected_setup SET confirmed_at = end_time + CASE timeframe
    WHEN '1s' THEN interval '1 second'
    WHEN '1m' THEN interval '1 minute'
    WHEN '3m' THEN interval '3 minutes'
    WHEN '5m' THEN interval '5 minutes'
    WHEN '15m' THEN interval '15 minutes'
    WHEN '30m' THEN interval '30 minutes'
    WHEN '1h' THEN interval '1 hour'
    WHEN '4h' THEN interval '4 hours'
    WHEN '1d' THEN interval '1 day'
END;
ALTER TABLE detected_setup ALTER COLUMN confirmed_at SET NOT NULL;
ALTER TABLE detected_setup ADD CONSTRAINT ck_detected_setup_confirmed CHECK (confirmed_at > end_time);

CREATE INDEX ix_detected_setup_confirmed
    ON detected_setup (instrument_id, timeframe, detector, rule_version, confirmed_at);
