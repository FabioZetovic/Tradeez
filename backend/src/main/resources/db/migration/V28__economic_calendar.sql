-- T40 (DEC-079) — the US economic releases that move NQ, from the agencies' own schedules: when each is published,
-- past and future. The dates come from FRED's release calendar (BLS, BEA, the Census Bureau, the Department of
-- Labor) and, for the FOMC, from the Federal Reserve's published meeting calendar. Forex Factory's calendar may not
-- be republished (its terms, read 2026-10-23), so Tradeez keeps its own.

-- One publication of one release, on its New York date, at the moment it comes out.
CREATE TABLE economic_release (
    release_key  VARCHAR(16) NOT NULL,
    release_date DATE        NOT NULL,
    at           TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (release_key, release_date)
);

CREATE INDEX economic_release_at ON economic_release (at);

-- When each release's dates were last brought up to date successfully: how fresh the schedule is, said on screen.
CREATE TABLE economic_release_check (
    release_key VARCHAR(16) PRIMARY KEY,
    checked_at  TIMESTAMPTZ NOT NULL
);
