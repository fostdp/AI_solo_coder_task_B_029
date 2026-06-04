package com.water.dosing.service;

import com.water.dosing.entity.ChemicalConsumption;
import com.water.dosing.entity.ChemicalInventory;
import com.water.dosing.entity.PurchaseRequisition;
import com.water.dosing.module.chemical.ChemicalInventoryManager;
import com.water.dosing.module.chemical.dto.InventoryUpdateResult;
import com.water.dosing.repository.ChemicalConsumptionRepository;
import com.water.dosing.repository.ChemicalInventoryRepository;
import com.water.dosing.repository.PurchaseRequisitionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ChemicalInventoryService {

    private static final Logger log = LoggerFactory.getLogger(ChemicalInventoryService.class);

    private final ChemicalInventoryRepository inventoryRepo;
    private final ChemicalConsumptionRepository consumptionRepo;
    private final PurchaseRequisitionRepository requisitionRepo;
    private final ChemicalInventoryManager moduleManager;

    private boolean useModule = true;

    public ChemicalInventoryService(ChemicalInventoryRepository inventoryRepo,
                                    ChemicalConsumptionRepository consumptionRepo,
                                    PurchaseRequisitionRepository requisitionRepo,
                                    ChemicalInventoryManager moduleManager) {
        this.inventoryRepo = inventoryRepo;
        this.consumptionRepo = consumptionRepo;
        this.requisitionRepo = requisitionRepo;
        this.moduleManager = moduleManager;
    }

    @PostConstruct
    public void init() {
        log.info("[ChemicalInventoryService] Delegating to ChemicalInventoryManager module");
    }

    @Deprecated
    public void initializeDefaultInventory() {
        List<ChemicalInventory> active = inventoryRepo.findAllActive();
        if (active.isEmpty()) {
            log.info("[ChemicalInventoryService] No active inventory, initialization handled by module");
        }
    }

    public Map<String, Object> getAllInventoryStatus() {
        return moduleManager.getAllInventoryStatus();
    }

    public ChemicalInventory getInventoryLatest(String chemicalCode) {
        return moduleManager.getInventoryLatest(chemicalCode);
    }

    public List<Map<String, Object>> get7DayDemandForecast(String chemicalCode) {
        return moduleManager.get7DayDemandForecast(chemicalCode);
    }

    @Transactional
    public Map<String, Object> updateInventoryAndCheckReorder() {
        InventoryUpdateResult moduleResult = moduleManager.updateInventoryAndCheckReorder();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("updateId", moduleResult.getUpdateId());
        result.put("updateTime", moduleResult.getUpdateTime());
        result.put("reorderCount", moduleResult.getReorderCount());
        result.put("newRequisitions", moduleResult.getNewRequisitions());
        result.put("totalChemicals", moduleResult.getTotalChemicals());
        result.put("delegatedTo", "ChemicalInventoryManager");
        return result;
    }

    @Transactional
    public PurchaseRequisition createRequisition(PurchaseRequisition requisition) {
        return moduleManager.createRequisition(requisition);
    }

    public List<PurchaseRequisition> getPendingRequisitions() {
        return moduleManager.getPendingRequisitions();
    }

    public List<PurchaseRequisition> getAllRequisitions(int days) {
        return moduleManager.getAllRequisitions(days);
    }

    @Transactional
    public PurchaseRequisition approveRequisition(String requisitionCode, String approvedBy) {
        return moduleManager.approveRequisition(requisitionCode, approvedBy);
    }

    @Transactional
    public PurchaseRequisition completeRequisition(String requisitionCode, double receivedQty) {
        return moduleManager.completeRequisition(requisitionCode, receivedQty);
    }

    public Map<String, Object> calculateSupplierReliability(String supplierName) {
        return moduleManager.calculateSupplierReliability(supplierName);
    }

    public List<ChemicalConsumption> getConsumptionHistory(String chemicalCode, int hours) {
        return moduleManager.getConsumptionHistory(chemicalCode, hours);
    }

    public Map<String, Object> getConsumptionStats(int days) {
        return moduleManager.getConsumptionStats(days);
    }

    @Transactional
    public void recordConsumption(ChemicalConsumption consumption) {
        moduleManager.recordConsumption(consumption);
    }

    public Map<String, Object> getLowStockAlerts() {
        return moduleManager.getLowStockAlerts();
    }
}
