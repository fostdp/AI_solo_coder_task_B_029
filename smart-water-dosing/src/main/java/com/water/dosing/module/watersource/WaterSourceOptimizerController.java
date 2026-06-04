package com.water.dosing.module.watersource;

import com.water.dosing.entity.WaterSource;
import com.water.dosing.module.watersource.dto.OptimizationResultDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/module/water-source")
public class WaterSourceOptimizerController {

    private final WaterSourceOptimizer optimizer;

    public WaterSourceOptimizerController(WaterSourceOptimizer optimizer) {
        this.optimizer = optimizer;
    }

    @GetMapping("/info")
    public ResponseEntity<Map<String, Object>> getModuleInfo() {
        return ResponseEntity.ok(optimizer.getModuleInfo());
    }

    @GetMapping("/sources")
    public ResponseEntity<List<WaterSource>> getAllWaterSources() {
        return ResponseEntity.ok(optimizer.getAllSources());
    }

    @GetMapping("/sources/{code}/history")
    public ResponseEntity<List<WaterSource>> getSourceHistory(@PathVariable String code) {
        return ResponseEntity.ok(optimizer.get24HourSourceHistory(code));
    }

    @GetMapping("/distribution/current")
    public ResponseEntity<Map<String, Object>> getCurrentDistribution() {
        return ResponseEntity.ok(optimizer.getOptimizationStatus());
    }

    @PostMapping("/optimize")
    public ResponseEntity<OptimizationResultDto> runOptimization() {
        return ResponseEntity.ok(optimizer.runOptimization());
    }

    @PostMapping("/optimize/async")
    public ResponseEntity<CompletableFuture<OptimizationResultDto>> runOptimizationAsync() {
        return ResponseEntity.ok(optimizer.runOptimizationAsync());
    }

    @GetMapping("/cost-trend")
    public ResponseEntity<List<Map<String, Object>>> getWaterCostTrend(
            @RequestParam(defaultValue = "7") int days) {
        return ResponseEntity.ok(optimizer.getDailyCostTrend(days));
    }

    @PostMapping("/sources")
    public ResponseEntity<WaterSource> updateWaterSource(@RequestBody WaterSource source) {
        if (source.getTime() == null) {
            source.setTime(Instant.now());
        }
        return ResponseEntity.ok(optimizer.updateSource(source));
    }
}
