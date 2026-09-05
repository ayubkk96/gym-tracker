package com.ayub.gym_tracker.controller;

import com.ayub.gym_tracker.dto.response.PreviousWorkoutResponse;
import com.ayub.gym_tracker.service.PreviousWorkoutService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;

@RestController
public class PreviousWorkoutController {
    private final PreviousWorkoutService previous;
    public PreviousWorkoutController(PreviousWorkoutService previous) { this.previous = previous; }

    @GetMapping("/api/workouts/previous")
    public ResponseEntity<PreviousWorkoutResponse> find(@RequestParam @NotBlank @Size(max = 100) String name,
                                                       @RequestParam LocalDate before) {
        return previous.find(name, before).map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }
}
