-- Historical logs use their calendar date; legacy day numbers remain unchanged.
-- Existing positive-value CHECK constraints also permit NULL.
ALTER TABLE nutrition_logs ALTER COLUMN day_number DROP NOT NULL;
ALTER TABLE workout_sessions ALTER COLUMN day_number DROP NOT NULL;
