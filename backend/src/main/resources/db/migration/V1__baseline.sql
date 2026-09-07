-- Tradeez baseline schema (Phase 0)
--
-- Conventions enforced from here on, per docs/DECISIONS.md:
--   INVARIANT 4  — every timestamp is TIMESTAMPTZ and stored in UTC.
--   INVARIANT 3  — prices are integer ticks; never float/double. (Arrives with the bars table.)
--   DEC-030      — schema changes only ever arrive as a new Flyway migration. Never edit this file
--                  after it has been applied anywhere.
--   DEC-022      — every user-owned table carries user_id from its first migration, because
--                  retrofitting ownership later is expensive and error-prone.

-- ---------------------------------------------------------------------------
-- Identity
-- ---------------------------------------------------------------------------

CREATE TABLE app_user (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    email           TEXT         NOT NULL,
    password_hash   TEXT         NOT NULL,
    display_name    TEXT         NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    settings        JSONB        NOT NULL DEFAULT '{}'::jsonb
);

-- Case-insensitive uniqueness: nobody registers twice with different capitalisation.
CREATE UNIQUE INDEX ux_app_user_email_lower ON app_user (lower(email));

-- ---------------------------------------------------------------------------
-- Refresh tokens (DEC-032: rotation + server-side revocation)
--
-- Only a hash of the token is stored. A database leak must not yield usable tokens.
-- Rotation is modelled by replaced_by: issuing a new token marks the old one replaced, so a
-- replayed old token is detectable as theft rather than merely rejected.
-- ---------------------------------------------------------------------------

CREATE TABLE refresh_token (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID         NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    token_hash   TEXT         NOT NULL UNIQUE,
    issued_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    expires_at   TIMESTAMPTZ  NOT NULL,
    revoked_at   TIMESTAMPTZ,
    replaced_by  UUID         REFERENCES refresh_token (id),
    user_agent   TEXT,

    CONSTRAINT ck_refresh_token_expiry CHECK (expires_at > issued_at)
);

CREATE INDEX ix_refresh_token_user ON refresh_token (user_id);
CREATE INDEX ix_refresh_token_active ON refresh_token (user_id, expires_at)
    WHERE revoked_at IS NULL;
