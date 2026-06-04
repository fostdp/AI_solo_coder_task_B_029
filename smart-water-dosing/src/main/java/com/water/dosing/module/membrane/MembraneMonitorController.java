package com.water.dosing.module.membrane;

import com.water.dosing.entity.MembraneCleanRecord;
import com.water.dosing.entity.MembraneModule;
import com.water.dosing.module.membrane.dto.FoulingEvaluationResult;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/module/membrane")
public class MembraneMonitorController {

    private final MembraneMonitor monitor;

    public MembraneMonitorController(MembraneMonitor monitor) {
        this.monitor = monitor;
    }

    @GetMapping("/info")
    public ResponseEntity<Map<String, Object>> getModuleInfo() {
        return ResponseEntity.ok(monitor.getModuleInfo());
    }

    @GetMapping("/modules")
    public ResponseEntity<Map<String, Object>> getAllMembraneModules() {
        return ResponseEntity.ok(monitor.getAllModulesStatus());
    }

    @GetMapping("/modules/{code}")
    public ResponseEntity<MembraneModule> getModuleLatest(@PathVariable String code) {
        return ResponseEntity.ok(monitor.getModuleLatest(code));
    }

    @GetMapping("/modules/{code}/history")
    public ResponseEntity<List<MembraneModule>> getModuleHistory(
            @PathVariable String code,
            @RequestParam(defaultValue = "24") int hours) {
        return ResponseEntity.ok(monitor.getModuleHistory(code, hours));
    }

    @GetMapping("/modules/{code}/fouling-trend")
    public ResponseEntity<List<Map<String, Object>>> getFoulingTrend(
            @PathVariable String code,
            @RequestParam(defaultValue = "24") int hours) {
        return ResponseEntity.ok(monitor.getFoulingTrend(code, hours));
    }

    @PostMapping("/evaluate")
    public ResponseEntity<FoulingEvaluationResult> evaluateFouling(
            @RequestParam(defaultValue = "false") boolean useStreamProcessing) {
        return ResponseEntity.ok(monitor.evaluateFoulingAndPredictCleaning(useStreamProcessing));
    }

    @PostMapping("/evaluate/async")
    public ResponseEntity<CompletableFuture<FoulingEvaluationResult>> evaluateFoulingAsync(
            @RequestParam(defaultValue = "false") boolean useStreamProcessing) {
        return ResponseEntity.ok(monitor.evaluateFoulingAsync(useStreamProcessing));
    }

    @GetMapping("/cleaning-schedule")
    public ResponseEntity<List<Map<String, Object>>> getCleaningSchedule() {
        return ResponseEntity.ok(monitor.getCleaningSchedule());
    }

    @PostMapping("/cleaning")
    public ResponseEntity<MembraneCleanRecord> recordCleaning(@RequestBody MembraneCleanRecord record) {
        if (record.getCleanTime() == null) {
            record.setCleanTime(Instant.now());
        }
        return ResponseEntity.ok(monitor.recordCleaning(record));
    }

    @GetMapping("/cleaning-history")
    public ResponseEntity<List<MembraneCleanRecord>> getCleanHistory(
            @RequestParam(required = false) String moduleCode,
            @RequestParam(defaultValue = "30") int days) {
        return ResponseEntity.ok(monitor.getCleanHistory(moduleCode, days));
    }

    @GetMapping("/cleaning-stats")
    public ResponseEntity<Map<String, Object>> getCleaningStats(
            @RequestParam(defaultValue = "30") int days) {
        return ResponseEntity.ok(monitor.getCleaningStats(days));
    }

    @PostMapping("/replacement")
    public ResponseEntity<Map<String, Object>> recordReplacement(
            @RequestParam String moduleCode,
            @RequestParam String serialNo,
            @RequestParam(defaultValue = "system") String operator) {
        return ResponseEntity.ok(monitor.recordMembraneReplacement(moduleCode, serialNo, operator));
    }

    @GetMapping("/stream/stats")
    public ResponseEntity<Map<String, Object>> getStreamStats() {
        return ResponseEntity.ok(monitor.getStreamStats());
    }

    @PostMapping("/stream/start")
    public ResponseEntity<Map<String, Object>> startStreamProcessing() {
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        boolean started = monitor.startStreamProcessing();
        result.put("success", started);
        result.put("message", started ? "Stream processing started" : "Stream processing already running");
        result.put("running", monitor.getStreamStats().get("running"));
        return ResponseEntity.ok(result);
    }

    @PostMapping("/stream/stop")
    public ResponseEntity<Map<String, Object>> stopStreamProcessing() {
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        boolean stopped = monitor.stopStreamProcessing();
        result.put("success", stopped);
        result.put("message", stopped ? "Stream processing stopped" : "Stream processing not running");
        result.put("running", monitor.getStreamStats().get("running"));
        return ResponseEntity.ok(result);
    }
}
