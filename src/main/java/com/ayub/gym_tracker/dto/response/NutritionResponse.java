// NutritionResponse.java
package com.ayub.gym_tracker.dto.response;

import java.math.BigDecimal;

public record NutritionResponse(
        Integer day,
        int calories,
        int proteinG,
        int carbsG,
        int fatG,
        BigDecimal weightKg,
        String notes
) {
}
