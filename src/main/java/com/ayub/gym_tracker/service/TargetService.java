package com.ayub.gym_tracker.service;

import com.ayub.gym_tracker.dto.request.UpdateTargetsRequest;
import com.ayub.gym_tracker.entity.DailyTarget;
import com.ayub.gym_tracker.repository.DailyTargetRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;

@Service
public class TargetService {
    private final CurrentUserService currentUser;
    private final DailyTargetRepository targets;
    private final EntityManager entityManager;

    public TargetService(CurrentUserService currentUser, DailyTargetRepository targets, EntityManager entityManager) {
        this.currentUser = currentUser;
        this.targets = targets;
        this.entityManager = entityManager;
    }

    @Transactional
    public void save(UpdateTargetsRequest request) {
        var user = currentUser.getCurrentUser();
        // Serialize updates for this account, including creation of a new effective date.
        entityManager.lock(user, LockModeType.PESSIMISTIC_WRITE);
        var existing = targets.findFirstByUserIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                user.getId(), request.effectiveFrom());
        var protein = BigDecimal.valueOf(request.proteinG());
        var carbs = BigDecimal.valueOf(request.carbsG());
        var fat = BigDecimal.valueOf(request.fatG());
        if (existing.isPresent() && existing.get().getEffectiveFrom().equals(request.effectiveFrom())) {
            existing.get().updateValues(request.calories(), protein, carbs, fat);
        } else {
            targets.save(new DailyTarget(user, request.calories(), protein, carbs, fat, request.effectiveFrom()));
        }
    }
}
