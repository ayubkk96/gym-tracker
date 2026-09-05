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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DashboardServiceTest {

    private static final long USER_ID = 42L;
    private static final LocalDate SELECTED_DATE =
            LocalDate.of(2026, 9, 5);
    private static final LocalDate FROM_DATE =
            SELECTED_DATE.minusDays(6);

    private NutritionService nutritionService;
    private WorkoutService workoutService;
    private NutritionLogRepository nutritionLogRepository;
    private WorkoutSessionRepository workoutSessionRepository;
    private CurrentUserService currentUserService;
    private DashboardService dashboardService;
    private AppUser user;

    @BeforeEach
    void setUp() {
        nutritionService = mock(NutritionService.class);
        workoutService = mock(WorkoutService.class);
        nutritionLogRepository = mock(NutritionLogRepository.class);
        workoutSessionRepository = mock(WorkoutSessionRepository.class);
        currentUserService = mock(CurrentUserService.class);
        user = mock(AppUser.class);

        when(user.getId()).thenReturn(USER_ID);
        when(currentUserService.getCurrentUser()).thenReturn(user);

        dashboardService = new DashboardService(
                nutritionService,
                workoutService,
                nutritionLogRepository,
                workoutSessionRepository,
                currentUserService
        );
    }

    @Test
    void createsWeeklyAveragesAndRecentHistory() {
        DailyTargetsResponse targets =
                new DailyTargetsResponse(2450, 180, 275, 75);
        NutritionResponse selectedNutrition =
                new NutritionResponse(
                        17,
                        2200,
                        190,
                        200,
                        60,
                        new BigDecimal("92.5"),
                        null
                );
        WorkoutResponse selectedWorkout =
                new WorkoutResponse(
                        "Chest",
                        17,
                        List.of(),
                        null
                );

        when(nutritionService.getDailyTargets(SELECTED_DATE))
                .thenReturn(targets);
        when(nutritionService.getNutritionByDate(SELECTED_DATE))
                .thenReturn(selectedNutrition);
        when(workoutService.getWorkoutsByDate(SELECTED_DATE))
                .thenReturn(List.of(selectedWorkout));

        NutritionLog latestNutrition = nutritionLog(
                SELECTED_DATE,
                2200,
                "190",
                "200",
                "60",
                "92.5"
        );
        NutritionLog previousNutrition = nutritionLog(
                SELECTED_DATE.minusDays(1),
                2000,
                "170",
                "180",
                "80",
                null
        );

        WorkoutSession chest = workout(
                SELECTED_DATE,
                "Chest"
        );
        WorkoutSession rest = workout(
                SELECTED_DATE.minusDays(1),
                "Rest"
        );
        WorkoutSession back = workout(
                SELECTED_DATE.minusDays(2),
                "Back"
        );

        when(nutritionLogRepository
                .findByUserIdAndLogDateBetweenOrderByLogDateDesc(
                        USER_ID,
                        FROM_DATE,
                        SELECTED_DATE
                ))
                .thenReturn(List.of(
                        latestNutrition,
                        previousNutrition
                ));
        when(workoutSessionRepository
                .findByUserIdAndWorkoutDateBetweenOrderByWorkoutDateDescIdAsc(
                        USER_ID,
                        FROM_DATE,
                        SELECTED_DATE
                ))
                .thenReturn(List.of(chest, rest, back));

        DashboardResponse dashboard =
                dashboardService.getDashboard(SELECTED_DATE);

        assertEquals(SELECTED_DATE, dashboard.date());
        assertEquals(17, dashboard.day());
        assertEquals(targets, dashboard.targets());
        assertEquals(selectedNutrition, dashboard.nutrition());
        assertEquals(List.of(selectedWorkout), dashboard.workouts());

        WeeklySummaryResponse summary = dashboard.weeklySummary();
        assertEquals(FROM_DATE, summary.fromDate());
        assertEquals(SELECTED_DATE, summary.toDate());
        assertEquals(2100, summary.averageCalories());
        assertEquals(180, summary.averageProteinG());
        assertEquals(190, summary.averageCarbsG());
        assertEquals(70, summary.averageFatG());
        assertEquals(2, summary.nutritionDaysLogged());
        assertEquals(2, summary.trainingSessions());

        List<RecentHistoryResponse> history =
                dashboard.recentHistory();
        assertEquals(3, history.size());

        RecentHistoryResponse latest = history.getFirst();
        assertEquals(SELECTED_DATE, latest.date());
        assertEquals(2200, latest.calories());
        assertEquals(190, latest.proteinG());
        assertEquals(new BigDecimal("92.5"), latest.weightKg());
        assertEquals(List.of("Chest"), latest.workouts());

        RecentHistoryResponse restDay = history.get(1);
        assertEquals(SELECTED_DATE.minusDays(1), restDay.date());
        assertEquals(List.of("Rest"), restDay.workouts());

        RecentHistoryResponse workoutOnly = history.get(2);
        assertEquals(SELECTED_DATE.minusDays(2), workoutOnly.date());
        assertNull(workoutOnly.calories());
        assertNull(workoutOnly.proteinG());
        assertNull(workoutOnly.weightKg());
        assertEquals(List.of("Back"), workoutOnly.workouts());

        verify(nutritionLogRepository)
                .findByUserIdAndLogDateBetweenOrderByLogDateDesc(
                        USER_ID,
                        FROM_DATE,
                        SELECTED_DATE
                );
        verify(workoutSessionRepository)
                .findByUserIdAndWorkoutDateBetweenOrderByWorkoutDateDescIdAsc(
                        USER_ID,
                        FROM_DATE,
                        SELECTED_DATE
                );
    }

    @Test
    void returnsEmptyWeeklyValuesWhenNothingWasLogged() {
        DailyTargetsResponse targets =
                new DailyTargetsResponse(2450, 180, 275, 75);

        when(nutritionService.getDailyTargets(SELECTED_DATE))
                .thenReturn(targets);
        when(nutritionService.getNutritionByDate(SELECTED_DATE))
                .thenReturn(null);
        when(workoutService.getWorkoutsByDate(SELECTED_DATE))
                .thenReturn(List.of());
        when(nutritionLogRepository
                .findByUserIdAndLogDateBetweenOrderByLogDateDesc(
                        USER_ID,
                        FROM_DATE,
                        SELECTED_DATE
                ))
                .thenReturn(List.of());
        when(workoutSessionRepository
                .findByUserIdAndWorkoutDateBetweenOrderByWorkoutDateDescIdAsc(
                        USER_ID,
                        FROM_DATE,
                        SELECTED_DATE
                ))
                .thenReturn(List.of());

        DashboardResponse dashboard =
                dashboardService.getDashboard(SELECTED_DATE);

        WeeklySummaryResponse summary = dashboard.weeklySummary();
        assertNull(summary.averageCalories());
        assertNull(summary.averageProteinG());
        assertNull(summary.averageCarbsG());
        assertNull(summary.averageFatG());
        assertEquals(0, summary.nutritionDaysLogged());
        assertEquals(0, summary.trainingSessions());
        assertEquals(List.of(), dashboard.recentHistory());
        assertNull(dashboard.day());
    }

    private NutritionLog nutritionLog(
            LocalDate date,
            int calories,
            String protein,
            String carbs,
            String fat,
            String weight
    ) {
        return new NutritionLog(
                user,
                date,
                1,
                calories,
                new BigDecimal(protein),
                new BigDecimal(carbs),
                new BigDecimal(fat),
                weight == null ? null : new BigDecimal(weight),
                null
        );
    }

    private WorkoutSession workout(
            LocalDate date,
            String name
    ) {
        return new WorkoutSession(
                user,
                date,
                1,
                name,
                null
        );
    }
}
