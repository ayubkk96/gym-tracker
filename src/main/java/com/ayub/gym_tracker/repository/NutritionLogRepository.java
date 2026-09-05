package com.ayub.gym_tracker.repository;

import com.ayub.gym_tracker.entity.NutritionLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface NutritionLogRepository
        extends JpaRepository<NutritionLog, Long> {

    Optional<NutritionLog> findByUserIdAndLogDate(
            Long userId,
            LocalDate logDate
    );

    List<NutritionLog>
    findByUserIdAndLogDateBetweenOrderByLogDateDesc(
            Long userId,
            LocalDate fromDate,
            LocalDate toDate
    );
}
