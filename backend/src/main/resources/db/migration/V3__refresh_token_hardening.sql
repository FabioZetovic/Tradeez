-- Refresh-token hardening (BUG-001, BUG-004).
--
-- DEC-030: migrations are append-only. V1 is never edited, even to add an index it should have had.

-- BUG-004 — PostgreSQL does not index foreign keys automatically. Without this, deleting or
-- updating a token requires a sequential scan of refresh_token to check the self-referencing
-- constraint. Invisible at ten rows, not at ten million.
CREATE INDEX ix_refresh_token_replaced_by ON refresh_token (replaced_by)
    WHERE replaced_by IS NOT NULL;

-- BUG-001 — optimistic locking. Two refresh requests arriving in genuinely parallel transactions
-- could both read the same token as un-rotated and both mint a successor, leaving one orphaned but
-- valid. A version column makes the second commit fail instead, so exactly one rotation wins.
ALTER TABLE refresh_token ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
