package com.ayub.gym_tracker.service;

import com.ayub.gym_tracker.dto.response.*;
import com.ayub.gym_tracker.repository.WorkoutSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.Optional;

@Service
public class PreviousWorkoutService {
    private final WorkoutSessionRepository sessions;
    private final CurrentUserService currentUser;
    public PreviousWorkoutService(WorkoutSessionRepository sessions, CurrentUserService currentUser) {
        this.sessions = sessions;
        this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    public Optional<PreviousWorkoutResponse> find(String name, LocalDate before) {
        return sessions.findFirstByUserIdAndNameIgnoreCaseAndWorkoutDateBeforeOrderByWorkoutDateDescIdDesc(
                currentUser.getCurrentUser().getId(), name.trim(), before).map(session ->
                new PreviousWorkoutResponse(session.getWorkoutDate(), session.getName(),
                        session.getExercises().stream().map(exercise -> {
                            var sets = exercise.getExerciseSets().stream()
                                    .map(set -> new WorkoutSetResponse(set.getWeightKg(), set.getReps())).toList();
                            return new ExerciseResponse(exercise.getName(), sets,
                                    sets.stream().mapToInt(WorkoutSetResponse::reps).sum(), exercise.getNotes());
                        }).toList()));
    }
}
