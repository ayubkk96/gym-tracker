package com.ayub.gym_tracker.controller;

import com.ayub.gym_tracker.dto.request.UpdateTargetsRequest;
import com.ayub.gym_tracker.service.TargetService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
public class TargetController {
    private final TargetService targets;
    public TargetController(TargetService targets) { this.targets = targets; }

    @PutMapping("/api/targets")
    public UpdateTargetsRequest update(@Valid @RequestBody UpdateTargetsRequest request) {
        targets.save(request);
        return request;
    }
}
