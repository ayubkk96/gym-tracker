package com.ayub.gym_tracker.controller;

import com.ayub.gym_tracker.dto.request.WorkoutRequest;
import com.ayub.gym_tracker.dto.result.WorkoutSaveResult;
import com.ayub.gym_tracker.service.WorkoutService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class WorkoutController {

    private final WorkoutService workoutService;

    public WorkoutController(WorkoutService workoutService) {
        this.workoutService = workoutService;
    }

    @PostMapping("/api/workouts")
    public ResponseEntity<Map<String, Object>> saveWorkout(
            @Valid @RequestBody WorkoutRequest request
    ) {
        WorkoutSaveResult result =
                workoutService.saveWorkout(request);

        Map<String, Object> response = Map.of(
                "saved", true,
                "action", result.created() ? "created" : "updated",
                "id", result.id(),
                "date", request.date(),
                "workout", request.workout(),
                "exercisesSaved", result.exercisesSaved()
        );

        HttpStatus status = result.created()
                ? HttpStatus.CREATED
                : HttpStatus.OK;

        return ResponseEntity.status(status).body(response);
    }

    @org.springframework.web.bind.annotation.PutMapping("/api/workouts")
    public ResponseEntity<Map<String, Object>> replaceWorkout(
            @org.springframework.web.bind.annotation.RequestParam String originalName,
            @org.springframework.web.bind.annotation.RequestParam java.time.LocalDate originalDate,
            @Valid @RequestBody WorkoutRequest request) {
        WorkoutSaveResult result = workoutService.replaceWorkout(originalName, originalDate, request);
        return ResponseEntity.ok(Map.of("saved", true, "action", "updated", "id", result.id(),
                "date", request.date(), "workout", request.workout(), "exercisesSaved", result.exercisesSaved()));
    }
}
