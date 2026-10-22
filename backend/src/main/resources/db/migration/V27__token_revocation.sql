-- BUG-077 — "sign out everywhere" and a password change end every session, the access tokens already handed out
-- included. Each access token carries the generation it was issued in; ending every session moves the account to the
-- next one, and a token from an earlier generation is refused, though its own expiry is still ahead. A counter, not
-- a moment: a token's issue time is kept to the second, so a token stolen in the same second as the sign-out would
-- have survived it.

ALTER TABLE app_user ADD COLUMN token_generation INTEGER NOT NULL DEFAULT 0;
