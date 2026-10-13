-- DEC-040 / T3: real NQ history is imported, so the simulated markets leave the chart.
--
-- Withdrawn, not deleted. `listed` only controls the public instrument list: DEMO-NQ and DEMO-MNQ
-- still resolve by symbol, because Practice and Drills keep using them as labelled fixtures until
-- practice on real data (T4, which needs the date hidden — DEC-025) and graded drills (T13) exist.
-- The generator also stays in the test suite, where injected ground truth is exactly what a test needs.
UPDATE instrument SET listed = false WHERE data_class = 'SIMULATED';
