package com.water.dosing.controller;

import com.water.dosing.entity.*;
import com.water.dosing.predictor.WaterQualityForecaster;
import com.water.dosing.service.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/api")
public class AdvancedFeaturesController {

    private final WaterDistributionService waterDistributionService;
    private final MembraneService membraneService;
    private final ChemicalInventoryService chemicalInventoryService;
    private final WaterQualityForecaster waterQualityForecaster;

    public AdvancedFeaturesController(WaterDistributionService waterDistributionService,
                                       MembraneService membraneService,
                                       ChemicalInventoryService chemicalInventoryService,
                                       WaterQualityForecaster waterQualityForecaster) {
        this.waterDistributionService = waterDistributionService;
        this.membraneService = membraneService;
        this.chemicalInventoryService = chemicalInventoryService;
        this.waterQualityForecaster = waterQualityForecaster;
    }

    @GetMapping("/water-sources")
    public ResponseEntity<List<WaterSource>> getAllWaterSources() {
        return ResponseEntity.ok(waterDistributionService.getAllSources());
    }

    @GetMapping("/water-sources/{code}/history")
    public ResponseEntity<List<WaterSource>> getSourceHistory(@PathVariable String code) {
        return ResponseEntity.ok(waterDistributionService.get24HourSourceHistory(code));
    }

    @GetMapping("/water-distribution/current")
    public ResponseEntity<Map<String, Object>> getCurrentDistribution() {
        return ResponseEntity.ok(waterDistributionService.getOptimizationStatus());
    }

    @PostMapping("/water-distribution/optimize")
    public ResponseEntity<Map<String, Object>> runOptimization() {
        return ResponseEntity.ok(waterDistributionService.runOptimization());
    }

    @GetMapping("/water-distribution/cost-trend")
    public ResponseEntity<List<Map<String, Object>>> getWaterCostTrend(
            @RequestParam(defaultValue = "7") int days) {
        return ResponseEntity.ok(waterDistributionService.getDailyCostTrend(days));
    }

    @PostMapping("/water-sources")
    public ResponseEntity<WaterSource> updateWaterSource(@RequestBody WaterSource source) {
        if (source.getTime() == null) {
            source.setTime(Instant.now());
        }
        return ResponseEntity.ok(waterDistributionService.updateSource(source));
    }

    @GetMapping("/membrane/modules")
    public ResponseEntity<Map<String, Object>> getAllMembraneModules() {
        return ResponseEntity.ok(membraneService.getAllModulesStatus());
    }

    @GetMapping("/membrane/{code}")
    public ResponseEntity<MembraneModule> getModuleLatest(@PathVariable String code) {
        return ResponseEntity.ok(membraneService.getModuleLatest(code));
    }

    @GetMapping("/membrane/{code}/fouling-trend")
    public ResponseEntity<List<Map<String, Object>>> getFoulingTrend(
            @PathVariable String code,
            @RequestParam(defaultValue = "24") int hours) {
        return ResponseEntity.ok(membraneService.getFoulingTrend(code, hours));
    }

    @PostMapping("/membrane/evaluate")
    public ResponseEntity<Map<String, Object>> evaluateFouling() {
        return ResponseEntity.ok(membraneService.evaluateFoulingAndPredictCleaning());
    }

    @GetMapping("/membrane/cleaning-schedule")
    public ResponseEntity<List<Map<String, Object>>> getCleaningSchedule() {
        return ResponseEntity.ok(membraneService.getCleaningSchedule());
    }

    @PostMapping("/membrane/cleaning")
    public ResponseEntity<MembraneCleanRecord> recordCleaning(@RequestBody MembraneCleanRecord record) {
        if (record.getCleanTime() == null) {
            record.setCleanTime(Instant.now());
        }
        return ResponseEntity.ok(membraneService.recordCleaning(record));
    }

    @GetMapping("/membrane/cleaning-history")
    public ResponseEntity<List<MembraneCleanRecord>> getCleanHistory(
            @RequestParam(required = false) String moduleCode,
            @RequestParam(defaultValue = "30") int days) {
        return ResponseEntity.ok(membraneService.getCleanHistory(moduleCode, days));
    }

    @GetMapping("/membrane/cleaning-stats")
    public ResponseEntity<Map<String, Object>> getCleaningStats(
            @RequestParam(defaultValue = "30") int days) {
        return ResponseEntity.ok(membraneService.getCleaningStats(days));
    }

    @GetMapping("/chemical/inventory")
    public ResponseEntity<Map<String, Object>> getAllInventory() {
        return ResponseEntity.ok(chemicalInventoryService.getAllInventoryStatus());
    }

    @GetMapping("/chemical/inventory/{code}")
    public ResponseEntity<ChemicalInventory> getInventoryLatest(@PathVariable String code) {
        return ResponseEntity.ok(chemicalInventoryService.getInventoryLatest(code));
    }

    @GetMapping("/chemical/{code}/demand-forecast")
    public ResponseEntity<List<Map<String, Object>>> get7DayDemandForecast(@PathVariable String code) {
        return ResponseEntity.ok(chemicalInventoryService.get7DayDemandForecast(code));
    }

    @PostMapping("/chemical/inventory/update")
    public ResponseEntity<Map<String, Object>> updateInventoryAndReorder() {
        return ResponseEntity.ok(chemicalInventoryService.updateInventoryAndCheckReorder());
    }

    @GetMapping("/chemical/consumption-history/{code}")
    public ResponseEntity<List<ChemicalConsumption>> getConsumptionHistory(
            @PathVariable String code,
            @RequestParam(defaultValue = "168") int hours) {
        return ResponseEntity.ok(chemicalInventoryService.getConsumptionHistory(code, hours));
    }

    @GetMapping("/chemical/consumption-stats")
    public ResponseEntity<Map<String, Object>> getConsumptionStats(
            @RequestParam(defaultValue = "30") int days) {
        return ResponseEntity.ok(chemicalInventoryService.getConsumptionStats(days));
    }

    @PostMapping("/chemical/consumption")
    public ResponseEntity<Map<String, Object>> recordConsumption(@RequestBody ChemicalConsumption consumption) {
        if (consumption.getTime() == null) {
            consumption.setTime(Instant.now());
        }
        chemicalInventoryService.recordConsumption(consumption);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("recorded", consumption);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/chemical/low-stock-alerts")
    public ResponseEntity<Map<String, Object>> getLowStockAlerts() {
        return ResponseEntity.ok(chemicalInventoryService.getLowStockAlerts());
    }

    @GetMapping("/chemical/purchase/pending")
    public ResponseEntity<List<PurchaseRequisition>> getPendingRequisitions() {
        return ResponseEntity.ok(chemicalInventoryService.getPendingRequisitions());
    }

    @GetMapping("/chemical/purchase/all")
    public ResponseEntity<List<PurchaseRequisition>> getAllRequisitions(
            @RequestParam(defaultValue = "30") int days) {
        return ResponseEntity.ok(chemicalInventoryService.getAllRequisitions(days));
    }

    @PostMapping("/chemical/purchase")
    public ResponseEntity<PurchaseRequisition> createRequisition(@RequestBody PurchaseRequisition requisition) {
        return ResponseEntity.ok(chemicalInventoryService.createRequisition(requisition));
    }

    @PostMapping("/chemical/purchase/{code}/approve")
    public ResponseEntity<PurchaseRequisition> approveRequisition(
            @PathVariable String code,
            @RequestParam(defaultValue = "admin") String approvedBy) {
        return ResponseEntity.ok(chemicalInventoryService.approveRequisition(code, approvedBy));
    }

    @PostMapping("/chemical/purchase/{code}/complete")
    public ResponseEntity<PurchaseRequisition> completeRequisition(
            @PathVariable String code,
            @RequestParam double receivedQty) {
        return ResponseEntity.ok(chemicalInventoryService.completeRequisition(code, receivedQty));
    }

    @PostMapping("/forecast/run")
    public ResponseEntity<Map<String, Object>> runForecast() {
        WaterQualityForecaster.ForecastResult result = waterQualityForecaster.forecastOutletQuality();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("predictionCount", result.getPredictions().size());
        response.put("hasWarning", result.isHasWarning());
        response.put("hasAlarm", result.isHasAlarm());
        response.put("warnings", result.getWarnings());
        response.put("alarms", result.getAlarms());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/forecast/latest")
    public ResponseEntity<List<Map<String, Object>>> getLatestForecast(
            @RequestParam(defaultValue = "outlet") String stage) {
        return ResponseEntity.ok(waterQualityForecaster.getLatestForecast(stage));
    }

    @GetMapping("/forecast/summary")
    public ResponseEntity<Map<String, Object>> getForecastSummary(
            @RequestParam(defaultValue = "outlet") String stage) {
        return ResponseEntity.ok(waterQualityForecaster.getForecastSummary(stage));
    }

    @GetMapping("/forecast/warnings")
    public ResponseEntity<List<Map<String, Object>>> getActiveWarnings() {
        return ResponseEntity.ok(waterQualityForecaster.getActiveWarnings());
    }

    @GetMapping("/advanced/status")
    public ResponseEntity<Map<String, Object>> getAdvancedFeaturesStatus() {
        Map<String, Object> result = new LinkedHashMap<>();

        Map<String, Object> distribution = waterDistributionService.getOptimizationStatus();
        Map<String, Object> membrane = membraneService.getAllModulesStatus();
        Map<String, Object> inventory = chemicalInventoryService.getAllInventoryStatus();
        Map<String, Object> forecast = waterQualityForecaster.getForecastSummary("outlet");

        result.put("waterDistribution", distribution);
        result.put("membraneSystem", membrane);
        result.put("chemicalInventory", inventory);
        result.put("waterQualityForecast", forecast);

        return ResponseEntity.ok(result);
    }
}
