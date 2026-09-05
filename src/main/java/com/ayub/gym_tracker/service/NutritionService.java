package com.ayub.gym_tracker.service;

import com.ayub.gym_tracker.dto.response.DailyTargetsResponse;
import com.ayub.gym_tracker.dto.request.NutritionRequest;
import com.ayub.gym_tracker.dto.response.NutritionResponse;
import com.ayub.gym_tracker.dto.result.NutritionSaveResult;
import com.ayub.gym_tracker.entity.AppUser;
import com.ayub.gym_tracker.entity.DailyTarget;
import com.ayub.gym_tracker.entity.NutritionLog;
import com.ayub.gym_tracker.repository.DailyTargetRepository;
import com.ayub.gym_tracker.repository.NutritionLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@Service
@Transactional(readOnly = true)
public class NutritionService {

    private final NutritionLogRepository nutritionLogRepository;
    private final DailyTargetRepository dailyTargetRepository;
    private final CurrentUserService currentUserService;

    public NutritionService(
            NutritionLogRepository nutritionLogRepository,
            DailyTargetRepository dailyTargetRepository,
            CurrentUserService currentUserService
    ) {
        this.nutritionLogRepository = nutritionLogRepository;
        this.dailyTargetRepository = dailyTargetRepository;
        this.currentUserService = currentUserService;
    }

    @Transactional
    public NutritionSaveResult saveNutrition(
            NutritionRequest request
    ) {
        AppUser user = currentUserService.getCurrentUser();

        NutritionLog existingLog = nutritionLogRepository
                .findByUserIdAndLogDate(
                        user.getId(),
                        request.date()
                )
                .orElse(null);

        if (existingLog != null) {
            existingLog.updateNutrition(
                    request.calories(),
                    request.proteinG(),
                    request.carbsG(),
                    request.fatG(),
                    request.weightKg(),
                    cleanNotes(request.notes())
            );

            NutritionLog savedLog =
                    nutritionLogRepository.save(existingLog);

            return new NutritionSaveResult(
                    savedLog.getId(),
                    false
            );
        }

        LocalDate startDate = dailyTargetRepository
                .findFirstByUserIdOrderByEffectiveFromAsc(user.getId())
                .orElseThrow(() -> new IllegalStateException(
                        "No targets found for user: " + user.getEmail()
                ))
                .getEffectiveFrom();

        long difference = ChronoUnit.DAYS.between(
                startDate,
                request.date()
        );

        Integer dayNumber = difference < 0 ? null : Math.toIntExact(difference) + 1;

        NutritionLog nutritionLog = new NutritionLog(
                user,
                request.date(),
                dayNumber,
                request.calories(),
                request.proteinG(),
                request.carbsG(),
                request.fatG(),
                request.weightKg(),
                cleanNotes(request.notes())
        );

        NutritionLog savedLog =
                nutritionLogRepository.save(nutritionLog);

        return new NutritionSaveResult(
                savedLog.getId(),
                true
        );
    }

    public DailyTargetsResponse getDailyTargets(
            LocalDate date
    ) {
        AppUser user = currentUserService.getCurrentUser();

        DailyTarget targets = dailyTargetRepository
                .findFirstByUserIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                        user.getId(),
                        date
                )
                .or(() -> dailyTargetRepository.findFirstByUserIdOrderByEffectiveFromAsc(user.getId()))
                .orElseThrow(() -> new IllegalStateException(
                        "No targets found for date: " + date
                ));

        return new DailyTargetsResponse(
                targets.getCalories(),
                targets.getProteinG().intValueExact(),
                targets.getCarbsG().intValueExact(),
                targets.getFatG().intValueExact()
        );
    }

    public NutritionResponse getNutritionByDate(
            LocalDate date
    ) {
        AppUser user = currentUserService.getCurrentUser();

        return nutritionLogRepository
                .findByUserIdAndLogDate(user.getId(), date)
                .map(this::toResponse)
                .orElse(null);
    }

    private NutritionResponse toResponse(
            NutritionLog nutritionLog
    ) {
        return new NutritionResponse(
                nutritionLog.getDayNumber(),
                nutritionLog.getCalories(),
                nutritionLog.getProteinG().intValueExact(),
                nutritionLog.getCarbsG().intValueExact(),
                nutritionLog.getFatG().intValueExact(),
                nutritionLog.getWeightKg(),
                nutritionLog.getNotes()
        );
    }

    private String cleanNotes(String notes) {
        if (notes == null || notes.isBlank()) {
            return null;
        }

        return notes.trim();
    }
}
