package com.ayub.gym_tracker.controller;

import com.ayub.gym_tracker.dto.request.WorkoutTemplateRequest;
import com.ayub.gym_tracker.dto.response.WorkoutTemplateResponse;
import com.ayub.gym_tracker.service.WorkoutTemplateService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/workout-templates")
public class WorkoutTemplateController {
    private final WorkoutTemplateService templates;
    public WorkoutTemplateController(WorkoutTemplateService templates) { this.templates = templates; }

    @GetMapping
    public List<WorkoutTemplateResponse> list() { return templates.list(); }

    @PostMapping
    public WorkoutTemplateResponse save(@Valid @RequestBody WorkoutTemplateRequest request) {
        return templates.save(request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id) {
        return templates.delete(id) ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }
}
