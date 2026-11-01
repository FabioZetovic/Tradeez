-- T56 (BUG-119): an email address is shown to belong to the person before the account can be used.

-- When the owner opened the link sent to the address; null until then, and such an account cannot sign in. Accounts that
-- already exist were made before this rule and are kept usable: they count as verified from when they were made.
ALTER TABLE app_user ADD COLUMN email_verified_at TIMESTAMPTZ;
UPDATE app_user SET email_verified_at = created_at;

-- A single-use link sent by email: VERIFY proves an address at sign-up, RESET sets a new password. Only the token's
-- SHA-256 is kept, as for refresh tokens: the database alone cannot be used to open anyone's link.
CREATE TABLE email_token (
    id         UUID        PRIMARY KEY,
    user_id    UUID        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    purpose    TEXT        NOT NULL,
    token_hash TEXT        NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at    TIMESTAMPTZ,
    -- A sign-up's link carries the password chosen at that sign-up (its BCrypt hash): opening the link sets it. So
    -- whoever signed up first with someone else's address cannot keep a password on the account its owner confirms.
    password_hash TEXT,
    CONSTRAINT ck_email_token_purpose CHECK (purpose IN ('VERIFY', 'RESET')),
    CONSTRAINT ck_email_token_expiry CHECK (expires_at > created_at)
);

CREATE INDEX ix_email_token_user ON email_token (user_id);
