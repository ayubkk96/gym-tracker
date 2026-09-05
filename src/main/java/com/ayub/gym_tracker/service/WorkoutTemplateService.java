package com.ayub.gym_tracker.service;

import com.ayub.gym_tracker.dto.request.WorkoutTemplateRequest;
import com.ayub.gym_tracker.dto.request.WorkoutTemplateRequest.TemplateExercise;
import com.ayub.gym_tracker.dto.response.WorkoutTemplateResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
public class WorkoutTemplateService {
    private final JdbcTemplate jdbc;
    private final CurrentUserService currentUser;
    public WorkoutTemplateService(JdbcTemplate jdbc, CurrentUserService currentUser) {
        this.jdbc = jdbc;
        this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    public List<WorkoutTemplateResponse> list() {
        Long owner = currentUser.getCurrentUser().getId();
        return jdbc.query("SELECT id, name, notes FROM workout_templates WHERE user_id = ? ORDER BY lower(name), id",
                (rs, row) -> new WorkoutTemplateResponse(rs.getLong("id"), rs.getString("name"),
                        rs.getString("notes"), exercises(rs.getLong("id"))), owner);
    }

    private List<TemplateExercise> exercises(long id) {
        return jdbc.query("SELECT name, set_count, notes FROM workout_template_exercises WHERE template_id = ? ORDER BY exercise_order",
                (rs, row) -> new TemplateExercise(rs.getString("name"), rs.getInt("set_count"), rs.getString("notes")), id);
    }

    @Transactional
    public WorkoutTemplateResponse save(WorkoutTemplateRequest request) {
        String name = request.name().trim();
        if (name.equalsIgnoreCase("Rest")) throw new IllegalArgumentException("Rest days do not need an exercise template.");
        Long owner = currentUser.getCurrentUser().getId();
        // Upsert locks this owner's template; concurrent saves cannot interleave its children.
        Long id = jdbc.queryForObject("""
                INSERT INTO workout_templates(user_id, name, notes) VALUES (?, ?, ?)
                ON CONFLICT (user_id, lower(name)) DO UPDATE SET name = EXCLUDED.name, notes = EXCLUDED.notes
                RETURNING id
                """, Long.class, owner, name, clean(request.notes()));
        jdbc.update("DELETE FROM workout_template_exercises WHERE template_id = ?", id);
        for (int index = 0; index < request.exercises().size(); index++) {
            TemplateExercise exercise = request.exercises().get(index);
            jdbc.update("INSERT INTO workout_template_exercises(template_id, exercise_order, name, notes, set_count) VALUES (?, ?, ?, ?, ?)",
                    id, index, exercise.name().trim(), clean(exercise.notes()), exercise.setCount());
        }
        return new WorkoutTemplateResponse(id, name, clean(request.notes()), exercises(id));
    }

    @Transactional
    public boolean delete(long id) {
        return jdbc.update("DELETE FROM workout_templates WHERE id = ? AND user_id = ?",
                id, currentUser.getCurrentUser().getId()) == 1;
    }

    private String clean(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
