-- Instrument registry (DEC-033, DATA_STRATEGY.md).
--
-- The architecture is instrument-agnostic on purpose: NQ is the first product, not a hardcoded
-- assumption. Everything that differs between contracts — tick size, what a tick is worth, session
-- hours — lives in this table rather than in code.
--
-- INVARIANT 3 — prices are integer TICKS everywhere, never floating point. A price of 20000.25 on a
-- 0.25 tick grid is stored as 80001. Converting to a displayable number is a presentation concern
-- and happens at the edge. tick_value_cents makes P&L exact integer arithmetic:
--     pnl_cents = (exit_ticks - entry_ticks) * tick_value_cents
--
-- DEC-033 / DEC-034 / DEC-035 — data_class is a first-class property, not a README footnote.
-- SIMULATED data may drive detectors but must never back a graded drill.

CREATE TYPE data_class AS ENUM (
    'SIMULATED',           -- generated. Never teachable material (DEC-034).
    'IMPORTED_PRIVATE',    -- real data imported locally under personal-use terms. Never deployed.
    'LICENSED_DEPLOYABLE'  -- real data with written permission for our use (DEC-020, DEC-035).
);

CREATE TABLE instrument (
    id                    UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    symbol                TEXT        NOT NULL UNIQUE,
    display_name          TEXT        NOT NULL,
    exchange              TEXT        NOT NULL,
    currency              TEXT        NOT NULL DEFAULT 'USD',

    -- The tick grid. tick_size is the only non-integer here and exists purely so a tick count can
    -- be rendered as a human-readable price.
    tick_size             NUMERIC(19, 9) NOT NULL,
    tick_value_cents      BIGINT      NOT NULL,
    point_multiplier_cents BIGINT     NOT NULL,

    -- Session definition, in the exchange's own timezone. A futures trading day crosses midnight,
    -- which is why open and close are stored separately from the date.
    exchange_timezone     TEXT        NOT NULL,
    session_open_local    TIME        NOT NULL,
    session_close_local   TIME        NOT NULL,
    maintenance_break_minutes INTEGER NOT NULL DEFAULT 0,

    data_class            data_class  NOT NULL,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT ck_instrument_tick_size_positive CHECK (tick_size > 0),
    CONSTRAINT ck_instrument_tick_value_positive CHECK (tick_value_cents > 0)
);

CREATE INDEX ix_instrument_data_class ON instrument (data_class);

-- Seed: the two development instruments.
--
-- These are NOT NQ and NOT MNQ. They are simulated products that deliberately carry the *real*
-- contract economics — $20 per point, a 0.25 tick worth $5 — so that position sizing, tick
-- rounding and P&L are exercised correctly from the first line of code. Naming them DEMO-NQ rather
-- than NQ is the whole point: a CFD feed relabelled as futures was rejected in DEC-020 for exactly
-- this reason, and inventing data and calling it NQ would be the same mistake.
--
-- Exchange is 'DEMO', never 'CME'. Nothing here came from an exchange.

INSERT INTO instrument (
    symbol, display_name, exchange, currency,
    tick_size, tick_value_cents, point_multiplier_cents,
    exchange_timezone, session_open_local, session_close_local, maintenance_break_minutes,
    data_class
) VALUES
(
    'DEMO-NQ', 'Demo E-mini Nasdaq-100 (simulated)', 'DEMO', 'USD',
    0.25, 500, 2000,
    'America/Chicago', '17:00', '16:00', 60,
    'SIMULATED'
),
(
    'DEMO-MNQ', 'Demo Micro E-mini Nasdaq-100 (simulated)', 'DEMO', 'USD',
    0.25, 50, 200,
    'America/Chicago', '17:00', '16:00', 60,
    'SIMULATED'
);
