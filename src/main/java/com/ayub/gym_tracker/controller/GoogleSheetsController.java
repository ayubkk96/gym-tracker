package com.ayub.gym_tracker.controller;

import com.ayub.gym_tracker.service.NutritionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;

@RestController
public class GoogleSheetsController {

    private final NutritionService nutritionService;

    public GoogleSheetsController(NutritionService nutritionService) {
        this.nutritionService = nutritionService;
    }

    @GetMapping("/api/sheets/test")
    public List<List<Object>> testConnection() throws IOException {
        return nutritionService.readTracker();
    }
}