-- Chart drawings (task 5, DEC-004).
--
-- DEC-022 — user-scoped from its first migration.
--
-- DEC-024 — anchors store RAW prices as integer ticks, never adjusted prices and never pixels.
-- Pixels are a function of zoom and window size, so a drawing stored in them would slide off its
-- level the moment either changed. Adjusted prices are worse: a later contract roll would silently
-- shift every line the user ever drew.
--
-- Geometry lives in JSONB because each tool has a different anchor count and the set of tools is
-- expected to grow. This is the one genuinely schemaless part of the domain, and exactly the case
-- DEC-001 kept JSONB around for — the alternative is a drawing_anchor table joined on every read
-- for no benefit, since anchors are never queried independently of their drawing.

CREATE TABLE chart_drawing (
    id             UUID        PRIMARY KEY,
    user_id        UUID        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    instrument_id  UUID        NOT NULL REFERENCES instrument (id) ON DELETE CASCADE,

    kind           TEXT        NOT NULL,

    -- [{"time": 1710338400, "priceTicks": 80000}, ...]
    anchors        JSONB       NOT NULL,
    style          JSONB       NOT NULL DEFAULT '{}'::jsonb,

    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT ck_chart_drawing_kind CHECK (
        kind IN ('horizontal-line', 'trend-line', 'rectangle', 'position')
    ),
    -- Anchors must be a non-empty array. A drawing with no geometry is not a drawing, and without
    -- this a bad client could persist rows the renderer would choke on.
    CONSTRAINT ck_chart_drawing_anchors CHECK (
        jsonb_typeof(anchors) = 'array' AND jsonb_array_length(anchors) > 0
    )
);

-- The only query that matters: everything I drew on this instrument.
CREATE INDEX ix_chart_drawing_user_instrument ON chart_drawing (user_id, instrument_id);
