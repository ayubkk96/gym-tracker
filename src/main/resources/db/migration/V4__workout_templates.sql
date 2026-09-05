CREATE TABLE workout_templates (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    notes VARCHAR(500)
);
CREATE UNIQUE INDEX workout_template_user_name_idx ON workout_templates(user_id, lower(name));

CREATE TABLE workout_template_exercises (
    id BIGSERIAL PRIMARY KEY,
    template_id BIGINT NOT NULL REFERENCES workout_templates(id) ON DELETE CASCADE,
    exercise_order INTEGER NOT NULL,
    name VARCHAR(100) NOT NULL,
    notes VARCHAR(500),
    set_count INTEGER NOT NULL CHECK (set_count BETWEEN 1 AND 20),
    UNIQUE(template_id, exercise_order)
);

CREATE INDEX workout_previous_session_idx ON workout_sessions(user_id, lower(name), workout_date DESC);
