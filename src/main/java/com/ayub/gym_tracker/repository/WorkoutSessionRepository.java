package com.ayub.gym_tracker.repository;

import com.ayub.gym_tracker.entity.WorkoutSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface WorkoutSessionRepository extends JpaRepository<WorkoutSession, Long> {

    Optional<WorkoutSession>
    findByUserIdAndWorkoutDateAndNameIgnoreCase(
            Long userId,
            LocalDate workoutDate,
            String name
    );

    List<WorkoutSession>
    findByUserIdAndWorkoutDateOrderByIdAsc(
            Long userId,
            LocalDate workoutDate
    );
}