-- T4 / DEC-025: practice on real NQ history.
--
-- A real session has no seed and no generator — the market is what it was. Its provenance is where
-- the bars came from (DEC-012: every result must be re-derivable and explicable), so both tables gain
-- data_source, and a session must carry a seed or a source: never neither.
ALTER TABLE practice_session ALTER COLUMN seed DROP NOT NULL;
ALTER TABLE practice_session ALTER COLUMN generator_version DROP NOT NULL;
ALTER TABLE practice_session ADD COLUMN data_source TEXT;
ALTER TABLE practice_session
    ADD CONSTRAINT ck_practice_session_provenance CHECK (seed IS NOT NULL OR data_source IS NOT NULL);

-- DEC-025: a real session's date is hidden until the result. Every time sent to the client is shown on
-- a fictional week with the same weekday and New York wall-clock time; this is how many days the real
-- date lies after that fictional one. Secret: never sent to a client before COMPLETE.
ALTER TABLE practice_session ADD COLUMN clock_day_shift INTEGER;

ALTER TABLE practice_result ALTER COLUMN seed DROP NOT NULL;
ALTER TABLE practice_result ALTER COLUMN generator_version DROP NOT NULL;
ALTER TABLE practice_result ADD COLUMN data_source TEXT;
ALTER TABLE practice_result
    ADD CONSTRAINT ck_practice_result_provenance CHECK (seed IS NOT NULL OR data_source IS NOT NULL);
