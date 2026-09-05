package com.ayub.gym_tracker.dto.response;

import java.time.LocalDate;
import java.util.List;

public record PreviousWorkoutResponse(LocalDate date, String name, List<ExerciseResponse> exercises) {}
