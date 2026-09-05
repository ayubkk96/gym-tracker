package com.ayub.gym_tracker.service;

import com.ayub.gym_tracker.dto.response.DailyTargetsResponse;
import com.ayub.gym_tracker.dto.response.DashboardResponse;
import com.ayub.gym_tracker.dto.response.NutritionResponse;
import com.ayub.gym_tracker.dto.response.RecentHistoryResponse;
import com.ayub.gym_tracker.dto.response.WeeklySummaryResponse;
import com.ayub.gym_tracker.dto.response.WorkoutResponse;
import com.ayub.gym_tracker.entity.AppUser;
import com.ayub.gym_tracker.entity.NutritionLog;
import com.ayub.gym_tracker.entity.WorkoutSession;
import com.ayub.gym_tracker.repository.NutritionLogRepository;
import com.ayub.gym_tracker.repository.WorkoutSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class DashboardService {

    private static final int SUMMARY_DAYS = 7;

    private final NutritionService nutritionService;
    private final WorkoutService workoutService;
    private final NutritionLogRepository nutritionLogRepository;
    private final WorkoutSessionRepository workoutSessionRepository;
    private final CurrentUserService currentUserService;

    public DashboardService(
            NutritionService nutritionService,
            WorkoutService workoutService,
            NutritionLogRepository nutritionLogRepository,
            WorkoutSessionRepository workoutSessionRepository,
            CurrentUserService currentUserService
    ) {
        this.nutritionService = nutritionService;
        this.workoutService = workoutService;
        this.nutritionLogRepository = nutritionLogRepository;
        this.workoutSessionRepository = workoutSessionRepository;
        this.currentUserService = currentUserService;
    }

    public DashboardResponse getDashboard(
            LocalDate date
    ) {
        DailyTargetsResponse targets =
                nutritionService.getDailyTargets(date);

        NutritionResponse nutrition =
                nutritionService.getNutritionByDate(date);

        List<WorkoutResponse> workouts =
                workoutService.getWorkoutsByDate(date);

        AppUser user = currentUserService.getCurrentUser();
        LocalDate fromDate = date.minusDays(SUMMARY_DAYS - 1L);

        List<NutritionLog> weeklyNutrition =
                nutritionLogRepository
                        .findByUserIdAndLogDateBetweenOrderByLogDateDesc(
                                user.getId(),
                                fromDate,
                                date
                        );

        List<WorkoutSession> weeklyWorkouts =
                workoutSessionRepository
                        .findByUserIdAndWorkoutDateBetweenOrderByWorkoutDateDescIdAsc(
                                user.getId(),
                                fromDate,
                                date
                        );

        WeeklySummaryResponse weeklySummary =
                createWeeklySummary(
                        fromDate,
                        date,
                        weeklyNutrition,
                        weeklyWorkouts
                );

        List<RecentHistoryResponse> recentHistory =
                createRecentHistory(
                        weeklyNutrition,
                        weeklyWorkouts
                );

        Integer day = findDay(nutrition, workouts);

        return new DashboardResponse(
                date,
                day,
                targets,
                nutrition,
                workouts,
                weeklySummary,
                recentHistory
        );
    }

    private WeeklySummaryResponse createWeeklySummary(
            LocalDate fromDate,
            LocalDate toDate,
            List<NutritionLog> nutritionLogs,
            List<WorkoutSession> workoutSessions
    ) {
        int trainingSessions = Math.toIntExact(
                workoutSessions.stream()
                        .filter(session -> !"Rest".equalsIgnoreCase(
                                session.getName()
                        ))
                        .count()
        );

        return new WeeklySummaryResponse(
                fromDate,
                toDate,
                roundedAverage(
                        nutritionLogs,
                        log -> log.getCalories().doubleValue()
                ),
                roundedAverage(
                        nutritionLogs,
                        log -> log.getProteinG().doubleValue()
                ),
                roundedAverage(
                        nutritionLogs,
                        log -> log.getCarbsG().doubleValue()
                ),
                roundedAverage(
                        nutritionLogs,
                        log -> log.getFatG().doubleValue()
                ),
                nutritionLogs.size(),
                trainingSessions
        );
    }

    private Integer roundedAverage(
            List<NutritionLog> logs,
            ToDoubleFunction<NutritionLog> value
    ) {
        if (logs.isEmpty()) {
            return null;
        }

        return (int) Math.round(
                logs.stream()
                        .mapToDouble(value)
                        .average()
                        .orElseThrow()
        );
    }

    private List<RecentHistoryResponse> createRecentHistory(
            List<NutritionLog> nutritionLogs,
            List<WorkoutSession> workoutSessions
    ) {
        Map<LocalDate, NutritionLog> nutritionByDate =
                nutritionLogs.stream()
                        .collect(Collectors.toMap(
                                NutritionLog::getLogDate,
                                Function.identity()
                        ));

        Map<LocalDate, List<String>> workoutsByDate =
                workoutSessions.stream()
                        .collect(Collectors.groupingBy(
                                WorkoutSession::getWorkoutDate,
                                Collectors.mapping(
                                        WorkoutSession::getName,
                                        Collectors.toList()
                                )
                        ));

        Set<LocalDate> dates =
                new TreeSet<>(Comparator.reverseOrder());

        dates.addAll(nutritionByDate.keySet());
        dates.addAll(workoutsByDate.keySet());

        return dates.stream()
                .map(date -> {
                    NutritionLog nutrition =
                            nutritionByDate.get(date);

                    return new RecentHistoryResponse(
                            date,
                            nutrition == null
                                    ? null
                                    : nutrition.getCalories(),
                            nutrition == null
                                    ? null
                                    : nutrition.getProteinG()
                                            .intValueExact(),
                            nutrition == null
                                    ? null
                                    : nutrition.getWeightKg(),
                            workoutsByDate.getOrDefault(
                                    date,
                                    List.of()
                            )
                    );
                })
                .toList();
    }

    private Integer findDay(
            NutritionResponse nutrition,
            List<WorkoutResponse> workouts
    ) {
        if (nutrition != null) {
            return nutrition.day();
        }

        if (!workouts.isEmpty()) {
            return workouts.get(0).day();
        }

        return null;
    }
}
