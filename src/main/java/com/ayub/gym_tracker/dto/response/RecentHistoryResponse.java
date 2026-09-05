package com.ayub.gym_tracker.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record RecentHistoryResponse(
        LocalDate date,
        Integer calories,
        Integer proteinG,
        BigDecimal weightKg,
        List<String> workouts
) {
}
