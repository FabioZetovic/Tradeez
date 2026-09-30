-- Real NQ from Databento (DEC-040).
--
-- Until now every price in Tradeez was generated (DEC-033). Fabio's decision in session 017: the
-- product needs real historical NQ — ten years back to recent months — and the simulated market
-- leaves the app. This migration adds what real data needs: an instrument that says truthfully
-- where it comes from, somewhere to keep the bars, and a ledger of every download and what it cost.

-- Instruments can be withdrawn from the app without being deleted: rows that bars, drawings,
-- practice sessions and drills refer to stay intact, they are simply no longer offered.
ALTER TABLE instrument ADD COLUMN listed BOOLEAN NOT NULL DEFAULT true;

-- The real contract. IMPORTED_PRIVATE until written permission to display it to others exists
-- (DEC-035, Q-09): usable locally, by one person, and never served by a public deployment.
INSERT INTO instrument (
    symbol, display_name, exchange, currency,
    tick_size, tick_value_cents, point_multiplier_cents,
    exchange_timezone, session_open_local, session_close_local, maintenance_break_minutes,
    data_class
) VALUES (
    'NQ', 'E-mini Nasdaq-100 futures', 'CME', 'USD',
    0.25, 500, 2000,
    'America/Chicago', '17:00', '16:00', 60,
    'IMPORTED_PRIVATE'
);


-- Bars exactly as the exchange printed them.
--
-- INVARIANT 3: integer ticks. DEC-024: raw prices — the continuous series rolls from one contract
-- to the next with the real jump between them, never back-adjusted, so a line drawn on a price
-- stays on that price. contract_id records which contract each bar actually came from.
--
-- One-minute bars are imported for the whole history. One-second bars — what the fill engine walks
-- (DEC-008) — are fetched a trading day at a time, only for the days practice actually uses.
CREATE TABLE market_bar (
    instrument_id  UUID        NOT NULL REFERENCES instrument (id),
    resolution     TEXT        NOT NULL,
    open_time      TIMESTAMPTZ NOT NULL,
    open_ticks     BIGINT      NOT NULL,
    high_ticks     BIGINT      NOT NULL,
    low_ticks      BIGINT      NOT NULL,
    close_ticks    BIGINT      NOT NULL,
    volume         BIGINT      NOT NULL,
    contract_id    BIGINT      NOT NULL,

    PRIMARY KEY (instrument_id, resolution, open_time),
    CONSTRAINT ck_market_bar_resolution CHECK (resolution IN ('1s', '1m')),
    CONSTRAINT ck_market_bar_ohlc CHECK (
        low_ticks <= LEAST(open_ticks, close_ticks) AND high_ticks >= GREATEST(open_ticks, close_ticks)
    ),
    CONSTRAINT ck_market_bar_volume CHECK (volume >= 0)
);


-- Every download: what was asked for, what Databento said it would cost before anything was
-- fetched, and how it ended. The budget caps are enforced against this table, so the free credit
-- is accounted for in the database rather than remembered.
CREATE TABLE market_data_import (
    id                  UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    instrument_id       UUID          NOT NULL REFERENCES instrument (id),
    source              TEXT          NOT NULL,
    dataset             TEXT          NOT NULL,
    symbol              TEXT          NOT NULL,
    resolution          TEXT          NOT NULL,
    range_from          TIMESTAMPTZ   NOT NULL,
    range_to            TIMESTAMPTZ   NOT NULL,
    estimated_cost_usd  NUMERIC(12,4) NOT NULL,
    bars_stored         BIGINT,
    status              TEXT          NOT NULL,
    error               TEXT,
    started_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    finished_at         TIMESTAMPTZ,

    CONSTRAINT ck_import_status CHECK (status IN ('RUNNING', 'DONE', 'FAILED', 'REFUSED')),
    CONSTRAINT ck_import_resolution CHECK (resolution IN ('1s', '1m')),
    CONSTRAINT ck_import_range CHECK (range_from < range_to),
    CONSTRAINT ck_import_cost CHECK (estimated_cost_usd >= 0)
);

CREATE INDEX ix_import_coverage
    ON market_data_import (instrument_id, resolution, status, range_from);
