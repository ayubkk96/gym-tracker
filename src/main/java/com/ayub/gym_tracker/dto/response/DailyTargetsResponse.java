// DailyTargetsResponse.java
package com.ayub.gym_tracker.dto.response;

public record DailyTargetsResponse(
        int calories,
        int proteinG,
        int carbsG,
        int fatG
) {
}