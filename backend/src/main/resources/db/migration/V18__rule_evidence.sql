-- T21 (DEC-065) — evidence: what each rule found over the whole imported history of a market, counted.
--
-- One row per market, timeframe and rule version: how many hits, how often, and — for a rule that follows
-- what happens to its zones — how those lives ended. A cache of a pure computation over the stored bars,
-- not a record of anything a user did: recomputed when the data grows or a rule's version changes, and
-- safe to delete. Never for a simulated market (INVARIANT 8b): nothing is taught from invented data.

CREATE TABLE rule_evidence (
    instrument_id     UUID        NOT NULL REFERENCES instrument (id) ON DELETE CASCADE,
    timeframe         TEXT        NOT NULL,
    detector          TEXT        NOT NULL,
    rule_version      TEXT        NOT NULL,
    -- evidence-1.0.0: how the counting is done. Bumped when it changes, as a rule's version is.
    evidence_version  TEXT        NOT NULL,
    data_from         TIMESTAMPTZ NOT NULL,
    data_to           TIMESTAMPTZ NOT NULL,
    candles           INTEGER     NOT NULL,
    -- The counts, as EvidenceCalculator returns them: hits, lives and how they ended, or the share of
    -- periods (days, weeks, windows) with each kind.
    body              JSONB       NOT NULL,
    computed_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (instrument_id, timeframe, detector, rule_version, evidence_version),
    CONSTRAINT ck_rule_evidence_range CHECK (data_from <= data_to),
    CONSTRAINT ck_rule_evidence_candles CHECK (candles >= 0)
);
