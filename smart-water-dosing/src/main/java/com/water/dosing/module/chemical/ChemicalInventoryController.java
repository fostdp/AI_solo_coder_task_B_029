package com.water.dosing.module.chemical;

import com.water.dosing.entity.ChemicalConsumption;
import com.water.dosing.entity.ChemicalInventory;
import com.water.dosing.entity.PurchaseRequisition;
import com.water.dosing.module.chemical.dto.InventoryUpdateResult;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/v1/chemical-inventory")
@CrossOrigin(origins = "*")
public class ChemicalInventoryController {

    private final ChemicalInventoryManager manager;

    public ChemicalInventoryController(ChemicalInventoryManager manager) {
        this.manager = manager;
    }

    @GetMapping("/module-info")
    public ResponseEntity<Map<String, Object>> getModuleInfo() {
        return ResponseEntity.ok(manager.getModuleInfo());
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getAllInventoryStatus() {
        return ResponseEntity.ok(manager.getAllInventoryStatus());
    }

    @GetMapping("/chemical/{code}/latest")
    public ResponseEntity<Map<String, Object>> getInventoryLatest(@PathVariable String code) {
        ChemicalInventory inv = manager.getInventoryLatest(code);
        if (inv == null) {
            return ResponseEntity.notFound().build();
        }
        Map<String, Object> result = new HashMap<>();
        result.put("module", "ChemicalInventoryManager");
        result.put("chemicalCode", inv.getChemicalCode());
        result.put("chemicalName", inv.getChemicalName());
        result.put("chemicalType", inv.getChemicalType());
        result.put("currentStock", inv.getCurrentStock());
        result.put("stockUnit", inv.getStockUnit());
        result.put("safetyStock", inv.getSafetyStock());
        result.put("dynamicSafetyStock", inv.getDynamicSafetyStock());
        result.put("reorderPoint", inv.getReorderPoint());
        result.put("maxStock", inv.getMaxStock());
        result.put("dailyConsumption", inv.getDailyConsumption());
        result.put("consumption7dAvg", inv.getConsumption7dAvg());
        result.put("consumption30dAvg", inv.getConsumption30dAvg());
        result.put("predictedDaysRemaining", inv.getPredictedDaysRemaining());
        result.put("stockStatus", inv.getStockStatus());
        result.put("unitPrice", inv.getUnitPrice());
        result.put("supplier", inv.getSupplier());
        result.put("supplierReliabilityScore", inv.getSupplierReliabilityScore());
        result.put("supplierAvgDelayDays", inv.getSupplierAvgDelayDays());
        result.put("supplierOnTimeRate", inv.getSupplierOnTimeRate());
        result.put("safetyStockMultiplier", inv.getSafetyStockMultiplier());
        result.put("location", inv.getLocation());
        result.put("lastReplenishmentTime", inv.getLastReplenishmentTime());
        result.put("lastReplenishmentQty", inv.getLastReplenishmentQty());
        result.put("minLeadTimeDays", inv.getMinLeadTimeDays());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/chemical/{code}/forecast")
    public ResponseEntity<List<Map<String, Object>>> get7DayDemandForecast(@PathVariable String code) {
        return ResponseEntity.ok(manager.get7DayDemandForecast(code));
    }

    @PostMapping("/update")
    public ResponseEntity<InventoryUpdateResult> updateInventory() {
        return ResponseEntity.ok(manager.updateInventoryAndCheckReorder());
    }

    @PostMapping("/update/async")
    public ResponseEntity<Map<String, Object>> updateInventoryAsync() {
        CompletableFuture<InventoryUpdateResult> future = manager.updateInventoryAsync();
        Map<String, Object> response = new HashMap<>();
        response.put("module", "ChemicalInventoryManager");
        response.put("taskId", "async-inv-" + System.currentTimeMillis());
        response.put("status", "started");
        response.put("message", "Async inventory update started");
        return ResponseEntity.ok(response);
    }

    @GetMapping("/chemical/{code}/consumption")
    public ResponseEntity<List<ChemicalConsumption>> getConsumptionHistory(
            @PathVariable String code,
            @RequestParam(defaultValue = "24") int hours) {
        return ResponseEntity.ok(manager.getConsumptionHistory(code, hours));
    }

    @GetMapping("/consumption-stats")
    public ResponseEntity<Map<String, Object>> getConsumptionStats(
            @RequestParam(defaultValue = "7") int days) {
        return ResponseEntity.ok(manager.getConsumptionStats(days));
    }

    @PostMapping("/consumption")
    public ResponseEntity<Map<String, Object>> recordConsumption(@RequestBody ChemicalConsumption consumption) {
        manager.recordConsumption(consumption);
        Map<String, Object> response = new HashMap<>();
        response.put("module", "ChemicalInventoryManager");
        response.put("status", "success");
        response.put("message", "Consumption recorded");
        return ResponseEntity.ok(response);
    }

    @GetMapping("/requisitions/pending")
    public ResponseEntity<Map<String, Object>> getPendingRequisitions() {
        Map<String, Object> result = new HashMap<>();
        result.put("module", "ChemicalInventoryManager");
        result.put("requisitions", manager.getPendingRequisitions());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/requisitions")
    public ResponseEntity<Map<String, Object>> getAllRequisitions(
            @RequestParam(defaultValue = "30") int days) {
        Map<String, Object> result = new HashMap<>();
        result.put("module", "ChemicalInventoryManager");
        result.put("requisitions", manager.getAllRequisitions(days));
        return ResponseEntity.ok(result);
    }

    @PostMapping("/requisitions")
    public ResponseEntity<PurchaseRequisition> createRequisition(@RequestBody PurchaseRequisition requisition) {
        return ResponseEntity.ok(manager.createRequisition(requisition));
    }

    @PutMapping("/requisitions/{code}/approve")
    public ResponseEntity<PurchaseRequisition> approveRequisition(
            @PathVariable String code,
            @RequestParam(defaultValue = "system") String approvedBy) {
        PurchaseRequisition req = manager.approveRequisition(code, approvedBy);
        if (req == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(req);
    }

    @PutMapping("/requisitions/{code}/complete")
    public ResponseEntity<PurchaseRequisition> completeRequisition(
            @PathVariable String code,
            @RequestParam double receivedQty) {
        PurchaseRequisition req = manager.completeRequisition(code, receivedQty);
        if (req == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(req);
    }

    @GetMapping("/supplier-reliability")
    public ResponseEntity<Map<String, Object>> getSupplierReliability(
            @RequestParam String supplierName) {
        return ResponseEntity.ok(manager.calculateSupplierReliability(supplierName));
    }

    @GetMapping("/alerts/low-stock")
    public ResponseEntity<Map<String, Object>> getLowStockAlerts() {
        return ResponseEntity.ok(manager.getLowStockAlerts());
    }
}
