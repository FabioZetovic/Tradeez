-- T5 / DEC-046: several trades in one replay.
--
-- A session used to hold one plan and one result. A replay holds a sequence of them, one open at a
-- time, numbered 1, 2, 3… in the order they were locked. A result belongs to the plan with the same
-- number, and the foreign key says so. Rows that already exist are trade number 1 of their session.
--
-- Adding a column with a constant default rewrites no rows and fires no row triggers, so the
-- immutability triggers from V5 are untouched: no locked plan and no written result is updated here.
ALTER TABLE trade_plan DROP CONSTRAINT trade_plan_session_id_key;
ALTER TABLE trade_plan ADD COLUMN seq INTEGER NOT NULL DEFAULT 1;
ALTER TABLE trade_plan ALTER COLUMN seq DROP DEFAULT;
ALTER TABLE trade_plan
    ADD CONSTRAINT uq_trade_plan_seq UNIQUE (session_id, seq),
    ADD CONSTRAINT ck_trade_plan_seq CHECK (seq >= 1);

ALTER TABLE practice_result DROP CONSTRAINT practice_result_session_id_key;
ALTER TABLE practice_result ADD COLUMN seq INTEGER NOT NULL DEFAULT 1;
ALTER TABLE practice_result ALTER COLUMN seq DROP DEFAULT;
ALTER TABLE practice_result
    ADD CONSTRAINT uq_practice_result_seq UNIQUE (session_id, seq),
    ADD CONSTRAINT fk_practice_result_plan FOREIGN KEY (session_id, seq)
        REFERENCES trade_plan (session_id, seq) ON DELETE CASCADE;
