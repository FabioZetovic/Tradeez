-- T57 (2026-10-31), from the security audit.

-- BUG-126. An access token lives 15 minutes and is checked by its signature alone, so signing out could not end it:
-- a copied cookie still worked after "Sign out". Each token now carries an id (jti); signing out writes it here until
-- the token would have expired anyway, and the filter refuses a token found here. Rows past expires_at are deleted.
CREATE TABLE revoked_access_token (
    jti        UUID        PRIMARY KEY,
    expires_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX ix_revoked_access_token_expires_at ON revoked_access_token (expires_at);

-- BUG-130. The full user-agent string of every sign-in was kept with no use (nothing reads it). From now on only a
-- coarse "Chrome on Windows" is stored; the full strings already stored are dropped.
UPDATE refresh_token SET user_agent = NULL WHERE user_agent IS NOT NULL;
