package com.ayub.gym_tracker.repository;

import com.ayub.gym_tracker.entity.DailyTarget;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface DailyTargetRepository
        extends JpaRepository<DailyTarget, Long> {

    Optional<DailyTarget>
    findFirstByUserIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
            Long userId,
            LocalDate date
    );

    Optional<DailyTarget>
    findFirstByUserIdOrderByEffectiveFromAsc(Long userId);
}