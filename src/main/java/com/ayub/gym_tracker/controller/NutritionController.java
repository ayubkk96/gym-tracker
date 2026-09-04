package com.ayub.gym_tracker.controller;

import com.ayub.gym_tracker.dto.NutritionRequest;
import com.ayub.gym_tracker.dto.NutritionSaveResult;
import com.ayub.gym_tracker.service.NutritionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class NutritionController {

    private final NutritionService nutritionService;

    public NutritionController(NutritionService nutritionService) {
        this.nutritionService = nutritionService;
    }

    @PostMapping("/api/nutrition")
    public ResponseEntity<Map<String, Object>> saveNutrition(
            @Valid @RequestBody NutritionRequest request
    ) {
        NutritionSaveResult result =
                nutritionService.saveNutrition(request);

        Map<String, Object> response = Map.of(
                "saved", true,
                "date", request.date(),
                "id", result.id(),
                "action", result.created() ? "created" : "updated"
        );

        HttpStatus status = result.created()
                ? HttpStatus.CREATED
                : HttpStatus.OK;

        return ResponseEntity.status(status).body(response);
    }
}