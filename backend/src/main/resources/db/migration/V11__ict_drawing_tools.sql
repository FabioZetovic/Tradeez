-- T8 / DEC-048: three ICT drawing tools — Fibonacci with ICT's levels, a zone with its consequent
-- encroachment, and a text label — and drawings checked for the shape their kind needs.
ALTER TABLE chart_drawing DROP CONSTRAINT ck_chart_drawing_kind;
ALTER TABLE chart_drawing ADD CONSTRAINT ck_chart_drawing_kind CHECK (
    kind IN ('horizontal-line', 'trend-line', 'rectangle', 'position', 'fibonacci', 'zone', 'label')
);

-- Each kind has a fixed number of anchors. A drawing with any other number cannot be drawn, and one
-- stored that way would break the chart it belongs to every time it loaded.
ALTER TABLE chart_drawing ADD CONSTRAINT ck_chart_drawing_anchor_count CHECK (
    jsonb_array_length(anchors) = CASE kind
        WHEN 'horizontal-line' THEN 1
        WHEN 'label' THEN 1
        WHEN 'position' THEN 3
        ELSE 2
    END
);

-- A label's words, or a zone's name. Short, because it sits on a chart. Labels must have some;
-- only labels and zones may.
ALTER TABLE chart_drawing ADD COLUMN text TEXT;
ALTER TABLE chart_drawing ADD CONSTRAINT ck_chart_drawing_text CHECK (
    (text IS NULL OR char_length(text) BETWEEN 1 AND 80)
    AND (kind <> 'label' OR text IS NOT NULL)
    AND (text IS NULL OR kind IN ('label', 'zone'))
);
