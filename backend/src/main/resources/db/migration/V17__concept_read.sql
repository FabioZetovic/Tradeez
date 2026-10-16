-- T16 (DEC-059) — the learning state. Everything about a learner's progress is computed from records
-- that already exist (graded drills, written once) and these: which lesson cards they have read.
--
-- A read is the learner saying so ("Mark as read"), not the card having been opened — the Learn tab
-- opens one by itself. Written once: reading a card again changes nothing.

CREATE TABLE concept_read (
    user_id     UUID        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    concept_id  TEXT        NOT NULL,
    read_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, concept_id),
    -- The ids of ICT_CONCEPTS.md's master table (F-1, ICT-20). Which ones exist is the registry's to say.
    CONSTRAINT ck_concept_read_id CHECK (concept_id ~ '^(F-[0-9]+|ICT-[0-9]{2})$')
);

CREATE TRIGGER concept_read_immutable
    BEFORE UPDATE ON concept_read
    FOR EACH ROW EXECUTE FUNCTION forbid_update();
