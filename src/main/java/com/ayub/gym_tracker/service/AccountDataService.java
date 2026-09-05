package com.ayub.gym_tracker.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;

@Service
public class AccountDataService {
    private final JdbcTemplate jdbc;
    private final CurrentUserService users;
    private final PasswordEncoder passwords;
    public AccountDataService(JdbcTemplate jdbc, CurrentUserService users, PasswordEncoder passwords) {
        this.jdbc = jdbc; this.users = users; this.passwords = passwords;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Map<String, Object> export() {
        long id = users.getCurrentUser().getId();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("schemaVersion", 1);
        data.put("exportedAt", Instant.now());
        data.put("account", rows("SELECT id, email, display_name, created_at FROM app_users WHERE id = ?", id).getFirst());
        data.put("targets", rows("SELECT id, effective_from, calories, protein_g, carbs_g, fat_g FROM daily_targets WHERE user_id = ? ORDER BY effective_from, id", id));
        data.put("nutrition", rows("SELECT id, log_date, day_number, calories, protein_g, carbs_g, fat_g, weight_kg, notes, created_at, updated_at FROM nutrition_logs WHERE user_id = ? ORDER BY log_date, id", id));
        data.put("workouts", rows("SELECT id, workout_date, day_number, name, notes, created_at FROM workout_sessions WHERE user_id = ? ORDER BY workout_date, id", id));
        data.put("exercises", rows("SELECT e.id, e.workout_session_id, e.exercise_order, e.name, e.notes FROM exercise_logs e JOIN workout_sessions w ON w.id = e.workout_session_id WHERE w.user_id = ? ORDER BY e.workout_session_id, e.exercise_order", id));
        data.put("sets", rows("SELECT s.id, s.exercise_log_id, s.set_number, s.weight_kg, s.reps FROM exercise_sets s JOIN exercise_logs e ON e.id = s.exercise_log_id JOIN workout_sessions w ON w.id = e.workout_session_id WHERE w.user_id = ? ORDER BY s.exercise_log_id, s.set_number", id));
        data.put("templates", rows("SELECT id, name, notes FROM workout_templates WHERE user_id = ? ORDER BY id", id));
        data.put("templateExercises", rows("SELECT e.id, e.template_id, e.exercise_order, e.name, e.notes, e.set_count FROM workout_template_exercises e JOIN workout_templates t ON t.id = e.template_id WHERE t.user_id = ? ORDER BY e.template_id, e.exercise_order", id));
        return data;
    }

    private List<Map<String, Object>> rows(String sql, long id) {
        return jdbc.query(sql, (rs, row) -> {
            Map<String, Object> values = new LinkedHashMap<>();
            for (int column = 1; column <= rs.getMetaData().getColumnCount(); column++) {
                Object value = rs.getObject(column);
                if (value instanceof java.sql.Date date) value = date.toLocalDate();
                else if (value instanceof java.sql.Timestamp timestamp) value = timestamp.toInstant();
                values.put(rs.getMetaData().getColumnLabel(column), value);
            }
            return values;
        }, id);
    }

    @Transactional
    public boolean delete(String password) {
        long id = users.getCurrentUser().getId();
        // Serialize with password changes and delete only after checking the current hash.
        var hashes = jdbc.queryForList("SELECT password_hash FROM app_users WHERE id = ? FOR UPDATE", String.class, id);
        if (hashes.isEmpty() || hashes.getFirst() == null || !passwords.matches(password, hashes.getFirst())) return false;
        return jdbc.update("DELETE FROM app_users WHERE id = ?", id) == 1;
    }
}
