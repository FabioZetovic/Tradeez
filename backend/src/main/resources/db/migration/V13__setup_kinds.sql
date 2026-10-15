-- T10 (DEC-050) — one detector may report several kinds of hit at one place: a gap's lifecycle
-- states (OPEN, TAPPED, CE, FILLED, INVERTED, EXPIRED), an opening gap's NDOG or NWOG, a gap or a
-- void. The kind is part of a hit's identity, so each state of one gap is stored once.
--
-- Null for a detector with one kind. Every row stored so far is an FVG, which has one.

ALTER TABLE detected_setup ADD COLUMN kind TEXT;
ALTER TABLE detected_setup ADD CONSTRAINT ck_detected_setup_kind CHECK (kind IS NULL OR kind ~ '^[A-Z][A-Z_]*$');

ALTER TABLE detected_setup DROP CONSTRAINT uq_detected_setup;
ALTER TABLE detected_setup ADD CONSTRAINT uq_detected_setup UNIQUE NULLS NOT DISTINCT (
    instrument_id, seed, generator_version, timeframe, detector, rule_version, params,
    anchor_time, direction, kind
);
