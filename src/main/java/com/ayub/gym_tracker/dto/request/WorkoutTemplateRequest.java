package com.ayub.gym_tracker.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

public record WorkoutTemplateRequest(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 500) String notes,
        @NotNull @Size(min = 1, max = 20) List<@NotNull @Valid TemplateExercise> exercises
) {
    public record TemplateExercise(@NotBlank @Size(max = 100) String name,
                                   @Min(1) @Max(20) int setCount,
                                   @Size(max = 500) String notes) {}
}
