package com.ayub.gym_tracker.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
public class ProgressService {
    public record Point(LocalDate date, BigDecimal value) {}
    public record RecordSet(LocalDate date, BigDecimal weightKg, int reps) {}
    public record Progress(LocalDate from, LocalDate through, List<String> exercises,
                           List<Point> bodyweight, List<Point> heaviestSets, List<Point> bodyweightReps,
                           RecordSet heaviest, RecordSet bestBodyweight) {}
    private final JdbcTemplate jdbc;
    private final CurrentUserService users;
    private static final String SETS = """
            FROM workout_sessions w JOIN exercise_logs e ON e.workout_session_id = w.id
            JOIN exercise_sets s ON s.exercise_log_id = e.id
            WHERE w.user_id = ? AND lower(trim(e.name)) = ? AND w.workout_date <= ?
            """;
    public ProgressService(JdbcTemplate jdbc, CurrentUserService users) { this.jdbc = jdbc; this.users = users; }

    @Transactional(readOnly = true)
    public Progress get(LocalDate through, int days, String exercise) {
        long owner = users.getCurrentUser().getId();
        LocalDate from = through.minusDays(days - 1L);
        String key = exercise.trim().toLowerCase(java.util.Locale.ROOT);
        var names = jdbc.query("""
                SELECT min(trim(e.name)) AS name FROM exercise_logs e
                JOIN workout_sessions w ON w.id = e.workout_session_id
                WHERE w.user_id = ? AND w.workout_date <= ?
                GROUP BY lower(trim(e.name)) ORDER BY lower(trim(e.name))
                """, (rs, i) -> rs.getString("name"), owner, through);
        var weights = jdbc.query("""
                SELECT log_date, weight_kg FROM nutrition_logs
                WHERE user_id = ? AND log_date BETWEEN ? AND ? AND weight_kg IS NOT NULL
                ORDER BY log_date
                """, (rs, i) -> new Point(rs.getDate(1).toLocalDate(), rs.getBigDecimal(2)), owner, from, through);
        var heavy = jdbc.query("SELECT w.workout_date, max(s.weight_kg) " + SETS + """
                 AND w.workout_date >= ? AND s.weight_kg IS NOT NULL
                GROUP BY w.workout_date ORDER BY w.workout_date
                """, (rs, i) -> new Point(rs.getDate(1).toLocalDate(), rs.getBigDecimal(2)), owner, key, through, from);
        var bw = jdbc.query("SELECT w.workout_date, max(s.reps) " + SETS + """
                 AND w.workout_date >= ? AND s.weight_kg IS NULL
                GROUP BY w.workout_date ORDER BY w.workout_date
                """, (rs, i) -> new Point(rs.getDate(1).toLocalDate(), rs.getBigDecimal(2)), owner, key, through, from);
        return new Progress(from, through, names, weights, heavy, bw,
                record(owner, key, through, false), record(owner, key, through, true));
    }

    private RecordSet record(long owner, String key, LocalDate through, boolean bodyweight) {
        String order = bodyweight ? " AND s.weight_kg IS NULL ORDER BY s.reps DESC" :
                " AND s.weight_kg IS NOT NULL ORDER BY s.weight_kg DESC, s.reps DESC";
        var records = jdbc.query("SELECT w.workout_date, s.weight_kg, s.reps " + SETS + order +
                ", w.workout_date ASC, s.id ASC LIMIT 1", (rs, i) -> new RecordSet(
                        rs.getDate(1).toLocalDate(), rs.getBigDecimal(2), rs.getInt(3)), owner, key, through);
        return records.isEmpty() ? null : records.getFirst();
    }
}
