// DashboardResponse.java
package com.ayub.gym_tracker.dto.response;

import java.time.LocalDate;
import java.util.List;

public record DashboardResponse(
        LocalDate date,
        Integer day,
        DailyTargetsResponse targets,
        NutritionResponse nutrition,
        List<WorkoutResponse> workouts,
        WeeklySummaryResponse weeklySummary,
        List<RecentHistoryResponse> recentHistory
) {
}
