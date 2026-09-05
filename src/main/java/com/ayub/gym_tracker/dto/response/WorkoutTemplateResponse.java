package com.ayub.gym_tracker.dto.response;

import com.ayub.gym_tracker.dto.request.WorkoutTemplateRequest.TemplateExercise;
import java.util.List;

public record WorkoutTemplateResponse(long id, String name, String notes, List<TemplateExercise> exercises) {}
