package com.ayub.gym_tracker.dto.response;

import java.time.LocalDate;

public record WeeklySummaryResponse(
        LocalDate fromDate,
        LocalDate toDate,
        Integer averageCalories,
        Integer averageProteinG,
        Integer averageCarbsG,
        Integer averageFatG,
        int nutritionDaysLogged,
        int trainingSessions
) {
}
