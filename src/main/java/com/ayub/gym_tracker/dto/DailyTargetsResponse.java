// DailyTargetsResponse.java
package com.ayub.gym_tracker.dto;

public record DailyTargetsResponse(
        int calories,
        int proteinG,
        int carbsG,
        int fatG
) {
}