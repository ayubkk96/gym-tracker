package com.ayub.gym_tracker.controller;

import com.ayub.gym_tracker.service.ProgressService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;

@RestController
public class ProgressController {
    private final ProgressService progress;
    public ProgressController(ProgressService progress) { this.progress = progress; }
    @GetMapping("/api/progress")
    public ProgressService.Progress get(@RequestParam LocalDate through,
            @RequestParam(defaultValue = "90") @Min(7) @Max(365) int days,
            @RequestParam(defaultValue = "") @Size(max = 100) String exercise) {
        return progress.get(through, days, exercise);
    }
}
