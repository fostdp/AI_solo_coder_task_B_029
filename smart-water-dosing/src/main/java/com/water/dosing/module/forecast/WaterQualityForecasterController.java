package com.water.dosing.module.forecast;

import com.water.dosing.module.forecast.dto.WaterQualityPredictionResult;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/v1/water-quality-forecaster")
@CrossOrigin(origins = "*")
public class WaterQualityForecasterController {

    private final WaterQualityForecasterManager manager;

    public WaterQualityForecasterController(WaterQualityForecasterManager manager) {
        this.manager = manager;
    }

    @GetMapping("/module-info")
    public ResponseEntity<Map<String, Object>> getModuleInfo() {
        return ResponseEntity.ok(manager.getModuleInfo());
    }

    @PostMapping("/forecast/{stage}")
    public ResponseEntity<WaterQualityPredictionResult> forecastSync(@PathVariable String stage) {
        return ResponseEntity.ok(manager.forecastSync(stage));
    }

    @PostMapping("/forecast/{stage}/async")
    public ResponseEntity<Map<String, Object>> forecastAsync(@PathVariable String stage) {
        CompletableFuture<WaterQualityPredictionResult> future = manager.forecastAsync(stage);
        Map<String, Object> response = new HashMap<>();
        response.put("module", "WaterQualityForecasterManager");
        response.put("taskId", "ASYNC-FORECAST-" + stage + "-" + System.currentTimeMillis());
        response.put("stage", stage);
        response.put("status", "started");
        response.put("message", "Async forecast task started");
        response.put("runningTasks", manager.getRunningTaskCount());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/forecast/{stage}/latest")
    public ResponseEntity<Map<String, Object>> getLatestForecast(@PathVariable String stage) {
        Map<String, Object> result = new HashMap<>();
        result.put("module", "WaterQualityForecasterManager");
        result.put("stage", stage);
        result.put("forecast", manager.getLatestForecast(stage));
        return ResponseEntity.ok(result);
    }

    @GetMapping("/forecast/{stage}/summary")
    public ResponseEntity<Map<String, Object>> getForecastSummary(@PathVariable String stage) {
        return ResponseEntity.ok(manager.getForecastSummary(stage));
    }

    @GetMapping("/warnings/active")
    public ResponseEntity<Map<String, Object>> getActiveWarnings() {
        Map<String, Object> result = new HashMap<>();
        result.put("module", "WaterQualityForecasterManager");
        result.put("warnings", manager.getActiveWarnings());
        result.put("totalWarnings", manager.getActiveWarnings().size());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/tasks/running")
    public ResponseEntity<Map<String, Object>> getRunningTasks() {
        Map<String, Object> result = new HashMap<>();
        result.put("module", "WaterQualityForecasterManager");
        result.put("runningTasks", manager.getRunningTaskCount());
        result.put("maxConcurrentPredictions", 5);
        return ResponseEntity.ok(result);
    }
}
