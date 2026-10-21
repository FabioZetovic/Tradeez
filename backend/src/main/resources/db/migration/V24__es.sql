-- T20 (DEC-073) — ES beside NQ, for SMT divergence (ICT-66): two normally correlated markets disagreeing at a
-- swing. The same exchange, hours and quarterly cycle as NQ; Massive's free plan covers it (DEC-042). Imported
-- like NQ and, like NQ, IMPORTED_PRIVATE until written permission to display it to others exists (DEC-035, Q-09).
INSERT INTO instrument (
    symbol, display_name, exchange, currency,
    tick_size, tick_value_cents, point_multiplier_cents,
    exchange_timezone, session_open_local, session_close_local, maintenance_break_minutes,
    data_class
) VALUES (
    'ES', 'E-mini S&P 500 futures', 'CME', 'USD',
    0.25, 1250, 5000,
    'America/Chicago', '17:00', '16:00', 60,
    'IMPORTED_PRIVATE'
);
