package com.ayub.gym_tracker.controller;

import com.ayub.gym_tracker.dto.request.TrackerImportRequest;
import com.ayub.gym_tracker.dto.result.TrackerImportResult;
import com.ayub.gym_tracker.service.TrackerImportService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TrackerImportController {

    private final TrackerImportService trackerImportService;

    public TrackerImportController(
            TrackerImportService trackerImportService
    ) {
        this.trackerImportService = trackerImportService;
    }

    @PostMapping("/api/import")
    public ResponseEntity<TrackerImportResult> importTracker(
            @Valid @RequestBody TrackerImportRequest request
    ) {
        return ResponseEntity.ok(
                trackerImportService.importTracker(request)
        );
    }
}
