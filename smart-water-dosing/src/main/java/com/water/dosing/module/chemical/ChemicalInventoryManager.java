package com.water.dosing.module.chemical;

import com.water.dosing.entity.ChemicalConsumption;
import com.water.dosing.entity.ChemicalInventory;
import com.water.dosing.entity.PurchaseRequisition;
import com.water.dosing.module.chemical.dto.InventoryUpdateResult;
import com.water.dosing.repository.ChemicalConsumptionRepository;
import com.water.dosing.repository.ChemicalInventoryRepository;
import com.water.dosing.repository.PurchaseRequisitionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Component
public class ChemicalInventoryManager {

    private static final Logger log = LoggerFactory.getLogger(ChemicalInventoryManager.class);
    private static final String MODULE_NAME = "ChemicalInventoryManager";
    private static final String MODULE_VERSION = "1.0.0";

    private final ChemicalInventoryRepository inventoryRepo;
    private final ChemicalConsumptionRepository consumptionRepo;
    private final PurchaseRequisitionRepository requisitionRepo;
    private final ChemicalInventoryConfig config;

    public ChemicalInventoryManager(ChemicalInventoryRepository inventoryRepo,
                                     ChemicalConsumptionRepository consumptionRepo,
                                     PurchaseRequisitionRepository requisitionRepo,
                                     ChemicalInventoryConfig config) {
        this.inventoryRepo = inventoryRepo;
        this.consumptionRepo = consumptionRepo;
        this.requisitionRepo = requisitionRepo;
        this.config = config;
    }

    @PostConstruct
    public void init() {
        log.info("[{}] Initializing module version {}", MODULE_NAME, MODULE_VERSION);
        List<ChemicalInventory> active = inventoryRepo.findAllActive();
        if (active.isEmpty()) {
            initializeDefaultInventory();
        }
        log.info("[{}] Module initialized successfully, dynamicSafetyStock={}",
                MODULE_NAME, config.isDynamicSafetyStockEnabled());
    }

    private void initializeDefaultInventory() {
        log.info("[{}] Initializing default chemical inventory", MODULE_NAME);
        Instant now = Instant.now();

        ChemicalInventory coagulant = new ChemicalInventory(now, "PAC", "聚合氯化铝", "coagulant");
        coagulant.setCurrentStock(4500.0);
        coagulant.setStockUnit("kg");
        coagulant.setSafetyStock(1500.0);
        coagulant.setReorderPoint(2500.0);
        coagulant.setMaxStock(8000.0);
        coagulant.setDailyConsumption(250.0);
        coagulant.setConsumption7dAvg(245.0);
        coagulant.setConsumption30dAvg(255.0);
        coagulant.setPredictedDaysRemaining(18);
        coagulant.setStockStatus("normal");
        coagulant.setUnitPrice(1.85);
        coagulant.setSupplier("化工有限公司A");
        coagulant.setLastReplenishmentTime(now.minus(15, ChronoUnit.DAYS));
        coagulant.setLastReplenishmentQty(3000.0);
        coagulant.setLocation("A区储罐1号");
        coagulant.setBatchNumber("PAC-2024-001");
        coagulant.setExpiryDate(now.plus(180, ChronoUnit.DAYS));
        inventoryRepo.save(coagulant);

        ChemicalInventory aid = new ChemicalInventory(now, "PAM", "聚丙烯酰胺", "coagulant_aid");
        aid.setCurrentStock(850.0);
        aid.setStockUnit("kg");
        aid.setSafetyStock(300.0);
        aid.setReorderPoint(500.0);
        aid.setMaxStock(1500.0);
        aid.setDailyConsumption(45.0);
        aid.setConsumption7dAvg(42.0);
        aid.setConsumption30dAvg(48.0);
        aid.setPredictedDaysRemaining(19);
        aid.setStockStatus("normal");
        aid.setUnitPrice(12.50);
        aid.setSupplier("化工有限公司B");
        aid.setLastReplenishmentTime(now.minus(12, ChronoUnit.DAYS));
        aid.setLastReplenishmentQty(500.0);
        aid.setLocation("A区储罐2号");
        aid.setBatchNumber("PAM-2024-001");
        aid.setExpiryDate(now.plus(120, ChronoUnit.DAYS));
        inventoryRepo.save(aid);

        ChemicalInventory disinfectant = new ChemicalInventory(now, "NaOCl", "次氯酸钠", "disinfectant");
        disinfectant.setCurrentStock(3200.0);
        disinfectant.setStockUnit("L");
        disinfectant.setSafetyStock(1000.0);
        disinfectant.setReorderPoint(1800.0);
        disinfectant.setMaxStock(5000.0);
        disinfectant.setDailyConsumption(180.0);
        disinfectant.setConsumption7dAvg(175.0);
        disinfectant.setConsumption30dAvg(185.0);
        disinfectant.setPredictedDaysRemaining(17);
        disinfectant.setStockStatus("normal");
        disinfectant.setUnitPrice(0.85);
        disinfectant.setSupplier("化工有限公司C");
        disinfectant.setLastReplenishmentTime(now.minus(10, ChronoUnit.DAYS));
        disinfectant.setLastReplenishmentQty(2000.0);
        disinfectant.setLocation("B区储罐1号");
        disinfectant.setBatchNumber("NaOCl-2024-001");
        disinfectant.setExpiryDate(now.plus(60, ChronoUnit.DAYS));
        inventoryRepo.save(disinfectant);

        ChemicalInventory acid = new ChemicalInventory(now, "H2SO4", "硫酸", "ph_adjuster");
        acid.setCurrentStock(1800.0);
        acid.setStockUnit("L");
        acid.setSafetyStock(600.0);
        acid.setReorderPoint(1000.0);
        acid.setMaxStock(3000.0);
        acid.setDailyConsumption(65.0);
        acid.setConsumption7dAvg(62.0);
        acid.setConsumption30dAvg(68.0);
        acid.setPredictedDaysRemaining(27);
        acid.setStockStatus("normal");
        acid.setUnitPrice(1.20);
        acid.setSupplier("化工有限公司D");
        acid.setLastReplenishmentTime(now.minus(20, ChronoUnit.DAYS));
        acid.setLastReplenishmentQty(1500.0);
        acid.setLocation("B区储罐2号");
        acid.setBatchNumber("H2SO4-2024-001");
        acid.setExpiryDate(now.plus(365, ChronoUnit.DAYS));
        inventoryRepo.save(acid);

        ChemicalInventory alkali = new ChemicalInventory(now, "NaOH", "氢氧化钠", "ph_adjuster");
        alkali.setCurrentStock(420.0);
        alkali.setStockUnit("kg");
        alkali.setSafetyStock(200.0);
        alkali.setReorderPoint(350.0);
        alkali.setMaxStock(800.0);
        alkali.setDailyConsumption(28.0);
        alkali.setConsumption7dAvg(26.0);
        alkali.setConsumption30dAvg(30.0);
        alkali.setPredictedDaysRemaining(15);
        alkali.setStockStatus("low");
        alkali.setUnitPrice(3.80);
        alkali.setSupplier("化工有限公司E");
        alkali.setLastReplenishmentTime(now.minus(18, ChronoUnit.DAYS));
        alkali.setLastReplenishmentQty(500.0);
        alkali.setLocation("C区储罐");
        alkali.setBatchNumber("NaOH-2024-001");
        alkali.setExpiryDate(now.plus(180, ChronoUnit.DAYS));
        inventoryRepo.save(alkali);
    }

    public Map<String, Object> getModuleInfo() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("moduleName", MODULE_NAME);
        info.put("version", MODULE_VERSION);
        info.put("status", "active");
        info.put("dynamicSafetyStockEnabled", config.isDynamicSafetyStockEnabled());
        info.put("autoCreateRequisition", config.isAutoCreateRequisition());
        info.put("purchaseLeadTimeDays", config.getPurchaseLeadTimeDays());
        info.put("reliabilityHistoryDays", config.getReliabilityHistoryDays());
        info.put("activeChemicals", inventoryRepo.findAllActive().size());
        return info;
    }

    public Map<String, Object> getAllInventoryStatus() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("module", MODULE_NAME);
        result.put("moduleVersion", MODULE_VERSION);

        List<ChemicalInventory> allInventory = inventoryRepo.findAllActive();

        List<Map<String, Object>> inventoryList = new ArrayList<>();
        int lowStockCount = 0;
        int criticalCount = 0;
        int outOfStockCount = 0;

        for (ChemicalInventory inv : allInventory) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("chemicalCode", inv.getChemicalCode());
            item.put("chemicalName", inv.getChemicalName());
            item.put("chemicalType", inv.getChemicalType());
            item.put("currentStock", inv.getCurrentStock());
            item.put("stockUnit", inv.getStockUnit());
            item.put("safetyStock", inv.getSafetyStock());
            item.put("dynamicSafetyStock", inv.getDynamicSafetyStock());
            item.put("reorderPoint", inv.getReorderPoint());
            item.put("dailyConsumption", inv.getDailyConsumption());
            item.put("predictedDaysRemaining", inv.getPredictedDaysRemaining());
            item.put("stockStatus", inv.getStockStatus());
            item.put("unitPrice", inv.getUnitPrice());
            item.put("supplier", inv.getSupplier());
            item.put("supplierReliabilityScore", inv.getSupplierReliabilityScore());
            item.put("safetyStockMultiplier", inv.getSafetyStockMultiplier());
            inventoryList.add(item);

            if ("low".equals(inv.getStockStatus())) lowStockCount++;
            else if ("critical".equals(inv.getStockStatus())) criticalCount++;
            else if ("out_of_stock".equals(inv.getStockStatus())) outOfStockCount++;
        }

        result.put("inventory", inventoryList);
        result.put("totalCount", allInventory.size());
        result.put("lowStockCount", lowStockCount);
        result.put("criticalCount", criticalCount);
        result.put("outOfStockCount", outOfStockCount);
        result.put("needAttentionCount", lowStockCount + criticalCount + outOfStockCount);

        return result;
    }

    public ChemicalInventory getInventoryLatest(String chemicalCode) {
        List<ChemicalInventory> latest = inventoryRepo.findLatestByChemicalCode(chemicalCode);
        return latest.isEmpty() ? null : latest.get(0);
    }

    public List<Map<String, Object>> get7DayDemandForecast(String chemicalCode) {
        List<Map<String, Object>> forecast = new ArrayList<>();
        ChemicalInventory latest = getInventoryLatest(chemicalCode);

        if (latest == null) return forecast;

        double avgConsumption = latest.getConsumption7dAvg() != null ?
                latest.getConsumption7dAvg() : latest.getDailyConsumption();
        double currentStock = latest.getCurrentStock() != null ? latest.getCurrentStock() : 0;
        double safetyStock = latest.getSafetyStock() != null ? latest.getSafetyStock() : 0;

        for (int i = 0; i < 7; i++) {
            Instant date = Instant.now().plus(i, ChronoUnit.DAYS);
            double consumption = avgConsumption * (0.9 + Math.random() * 0.2);
            currentStock = Math.max(0, currentStock - consumption);

            Map<String, Object> day = new LinkedHashMap<>();
            day.put("date", date.toString().substring(0, 10));
            day.put("predictedConsumption", Math.round(consumption * 10.0) / 10.0);
            day.put("estimatedStock", Math.round(currentStock * 10.0) / 10.0);
            day.put("belowSafetyStock", currentStock < safetyStock);
            day.put("safetyStock", safetyStock);
            day.put("dynamicSafetyStock", latest.getDynamicSafetyStock());
            forecast.add(day);
        }

        return forecast;
    }

    @Transactional
    public InventoryUpdateResult updateInventoryAndCheckReorder() {
        log.info("[{}] Starting inventory update", MODULE_NAME);

        InventoryUpdateResult result = new InventoryUpdateResult();
        result.setUpdateId("INV-UPDATE-" + System.currentTimeMillis());
        result.setUpdateTime(Instant.now());

        List<String> chemicalCodes = inventoryRepo.findAllActiveChemicalCodes();
        result.setTotalChemicals(chemicalCodes.size());

        List<InventoryUpdateResult.ChemicalUpdateDetail> updates = new ArrayList<>();
        int reorderCount = 0;
        int newRequisitions = 0;
        Map<String, Map<String, Object>> supplierReliability = new LinkedHashMap<>();

        for (String code : chemicalCodes) {
            InventoryUpdateResult.ChemicalUpdateDetail detail = updateSingleInventory(code);
            updates.add(detail);

            if (detail.isNeedsReorder()) {
                reorderCount++;
                if (config.isAutoCreateRequisition()) {
                    PurchaseRequisition req = createRequisitionIfNotExists(code);
                    if (req != null) newRequisitions++;
                }
            }

            if (detail.getSupplierReliabilityScore() != null) {
                ChemicalInventory inv = getInventoryLatest(code);
                if (inv != null && inv.getSupplier() != null) {
                    Map<String, Object> suppInfo = new LinkedHashMap<>();
                    suppInfo.put("reliabilityScore", detail.getSupplierReliabilityScore());
                    suppInfo.put("safetyStockMultiplier", detail.getSafetyStockMultiplier());
                    suppInfo.put("dynamicSafetyStock", detail.getDynamicSafetyStock());
                    supplierReliability.put(inv.getSupplier(), suppInfo);
                }
            }
        }

        result.setUpdates(updates);
        result.setReorderCount(reorderCount);
        result.setNewRequisitions(newRequisitions);

        Map<String, Object> reliabilitySummary = new LinkedHashMap<>();
        reliabilitySummary.put("totalSuppliers", supplierReliability.size());
        reliabilitySummary.put("supplierDetails", supplierReliability);
        double avgReliability = supplierReliability.values().stream()
                .mapToDouble(m -> (Double) m.getOrDefault("reliabilityScore", 0.0))
                .average()
                .orElse(0.0);
        reliabilitySummary.put("averageReliabilityScore", Math.round(avgReliability * 100.0) / 100.0);
        result.setSupplierReliabilitySummary(reliabilitySummary);

        log.info("[{}] Inventory update complete: reorder={}, newRequisitions={}",
                MODULE_NAME, reorderCount, newRequisitions);
        return result;
    }

    @Async("moduleTaskExecutor")
    public CompletableFuture<InventoryUpdateResult> updateInventoryAsync() {
        log.info("[{}] Starting async inventory update", MODULE_NAME);
        return CompletableFuture.completedFuture(updateInventoryAndCheckReorder());
    }

    private InventoryUpdateResult.ChemicalUpdateDetail updateSingleInventory(String chemicalCode) {
        InventoryUpdateResult.ChemicalUpdateDetail detail = new InventoryUpdateResult.ChemicalUpdateDetail();
        detail.setChemicalCode(chemicalCode);

        List<ChemicalInventory> latestList = inventoryRepo.findLatestByChemicalCode(chemicalCode);
        if (latestList.isEmpty()) {
            detail.setStockStatus("error");
            return detail;
        }

        ChemicalInventory latest = latestList.get(0);
        detail.setChemicalName(latest.getChemicalName());
        Instant now = Instant.now();

        Instant start7d = now.minus(7, ChronoUnit.DAYS);
        Instant start30d = now.minus(30, ChronoUnit.DAYS);

        List<ChemicalConsumption> consumptions7d = consumptionRepo.findActualConsumption(chemicalCode, start7d);
        double avg7d = calculateDailyAverage(consumptions7d, 7);

        List<ChemicalConsumption> consumptions30d = consumptionRepo.findActualConsumption(chemicalCode, start30d);
        double avg30d = calculateDailyAverage(consumptions30d, 30);

        double dailyConsumption = avg30d > 0 ? avg30d :
                (latest.getDailyConsumption() != null ? latest.getDailyConsumption() : 0);

        double currentStock = latest.getCurrentStock() != null ? latest.getCurrentStock() : 0;
        double dailyUse = dailyConsumption / 24;
        currentStock = Math.max(0, currentStock - dailyUse);

        double baseSafetyStock = latest.getSafetyStock() != null ? latest.getSafetyStock() : 0;
        double dynamicSafetyStock = baseSafetyStock;
        double safetyStockMultiplier = 1.0;

        if (config.isDynamicSafetyStockEnabled()) {
            Map<String, Object> reliability = calculateSupplierReliability(latest.getSupplier());
            double reliabilityScore = (Double) reliability.get("reliabilityScore");
            double avgDelayDays = (Double) reliability.get("avgDelayDays");
            double onTimeRate = (Double) reliability.get("onTimeRate");

            safetyStockMultiplier = calculateSafetyStockMultiplier(reliabilityScore, avgDelayDays, onTimeRate);
            dynamicSafetyStock = baseSafetyStock * safetyStockMultiplier;

            detail.setSupplierReliabilityScore(reliabilityScore);
            detail.setSafetyStockMultiplier(safetyStockMultiplier);
            detail.setDynamicSafetyStock(dynamicSafetyStock);

            if (latest.getSupplierReliabilityScore() == null ||
                    Math.abs(latest.getSupplierReliabilityScore() - reliabilityScore) > 0.01) {
                latest.setSupplierReliabilityScore(reliabilityScore);
                latest.setSupplierAvgDelayDays(avgDelayDays);
                latest.setSupplierOnTimeRate(onTimeRate);
                latest.setSafetyStockMultiplier(safetyStockMultiplier);
                latest.setDynamicSafetyStock(dynamicSafetyStock);
                inventoryRepo.save(latest);
            }
        }

        double safetyStock = dynamicSafetyStock > 0 ? dynamicSafetyStock : baseSafetyStock;
        double reorderPoint = latest.getReorderPoint() != null ?
                latest.getReorderPoint() * safetyStockMultiplier : safetyStock * 1.5;

        int daysRemaining = dailyConsumption > 0 ? (int) Math.floor(currentStock / dailyConsumption) : Integer.MAX_VALUE;

        String stockStatus;
        if (currentStock <= 0) {
            stockStatus = "out_of_stock";
        } else if (currentStock < safetyStock) {
            stockStatus = "critical";
        } else if (currentStock < reorderPoint) {
            stockStatus = "low";
        } else {
            stockStatus = "normal";
        }

        boolean needsReorder = currentStock < reorderPoint;

        ChemicalInventory newInv = new ChemicalInventory(now, chemicalCode, latest.getChemicalName(), latest.getChemicalType());
        newInv.setCurrentStock(currentStock);
        newInv.setStockUnit(latest.getStockUnit());
        newInv.setSafetyStock(safetyStock);
        newInv.setReorderPoint(reorderPoint);
        newInv.setMaxStock(latest.getMaxStock());
        newInv.setDailyConsumption(dailyConsumption);
        newInv.setConsumption7dAvg(avg7d);
        newInv.setConsumption30dAvg(avg30d);
        newInv.setPredictedDaysRemaining(daysRemaining);
        newInv.setStockStatus(stockStatus);
        newInv.setUnitPrice(latest.getUnitPrice());
        newInv.setSupplier(latest.getSupplier());
        newInv.setLastReplenishmentTime(latest.getLastReplenishmentTime());
        newInv.setLastReplenishmentQty(latest.getLastReplenishmentQty());
        newInv.setLocation(latest.getLocation());
        newInv.setSupplierReliabilityScore(latest.getSupplierReliabilityScore());
        newInv.setSupplierAvgDelayDays(latest.getSupplierAvgDelayDays());
        newInv.setSupplierOnTimeRate(latest.getSupplierOnTimeRate());
        newInv.setDynamicSafetyStock(dynamicSafetyStock);
        newInv.setSafetyStockMultiplier(safetyStockMultiplier);
        newInv.setMinLeadTimeDays(latest.getMinLeadTimeDays());
        inventoryRepo.save(newInv);

        detail.setCurrentStock(Math.round(currentStock * 100.0) / 100.0);
        detail.setDailyConsumption(Math.round(dailyConsumption * 100.0) / 100.0);
        detail.setDaysRemaining(daysRemaining);
        detail.setStockStatus(stockStatus);
        detail.setNeedsReorder(needsReorder);
        detail.setSafetyStock(safetyStock);
        detail.setReorderPoint(reorderPoint);

        return detail;
    }

    private double calculateDailyAverage(List<ChemicalConsumption> consumptions, int days) {
        if (consumptions.isEmpty()) return 0;
        double total = consumptions.stream()
                .mapToDouble(c -> c.getConsumptionQty() != null ? c.getConsumptionQty() : 0)
                .sum();
        return total / days;
    }

    private PurchaseRequisition createRequisitionIfNotExists(String chemicalCode) {
        List<PurchaseRequisition> pending = requisitionRepo.findPendingByChemicalCode(chemicalCode, "pending");
        if (!pending.isEmpty()) return null;

        ChemicalInventory inv = getInventoryLatest(chemicalCode);
        if (inv == null) return null;

        String reqCode = "PR-" + System.currentTimeMillis() + "-" + chemicalCode;
        Instant now = Instant.now();

        PurchaseRequisition req = new PurchaseRequisition(reqCode, now, chemicalCode);
        req.setChemicalName(inv.getChemicalName());

        double avgConsumption = inv.getConsumption30dAvg() != null ?
                inv.getConsumption30dAvg() : inv.getDailyConsumption();
        double maxStock = inv.getMaxStock() != null ? inv.getMaxStock() : avgConsumption * 30;
        double currentStock = inv.getCurrentStock() != null ? inv.getCurrentStock() : 0;
        double requestedQty = maxStock - currentStock;

        req.setRequestedQty(Math.ceil(requestedQty));
        req.setStockUnit(inv.getStockUnit());

        String urgency;
        if ("critical".equals(inv.getStockStatus())) {
            urgency = "critical";
        } else if ("low".equals(inv.getStockStatus())) {
            urgency = "normal";
        } else {
            urgency = "low";
        }
        req.setUrgencyLevel(urgency);
        req.setExpectedDate(now.plus(config.getPurchaseLeadTimeDays(), ChronoUnit.DAYS));
        req.setCurrentStock(currentStock);
        req.setSafetyStock(inv.getSafetyStock());
        req.setDailyConsumption(avgConsumption);
        req.setEstimatedCost(requestedQty * (inv.getUnitPrice() != null ? inv.getUnitPrice() : 0));
        req.setSupplier(inv.getSupplier());
        req.setStatus("pending");
        req.setReason("库存低于再订货点，自动生成采购申请");
        req.setCreatedBy("system");

        return requisitionRepo.save(req);
    }

    @Transactional
    public PurchaseRequisition createRequisition(PurchaseRequisition requisition) {
        if (requisition.getRequisitionCode() == null) {
            requisition.setRequisitionCode("PR-" + System.currentTimeMillis());
        }
        if (requisition.getCreatedTime() == null) {
            requisition.setCreatedTime(Instant.now());
        }
        if (requisition.getStatus() == null) {
            requisition.setStatus("pending");
        }
        return requisitionRepo.save(requisition);
    }

    public List<PurchaseRequisition> getPendingRequisitions() {
        return requisitionRepo.findByStatusOrderByCreatedTimeDesc("pending");
    }

    public List<PurchaseRequisition> getAllRequisitions(int days) {
        Instant start = Instant.now().minus(days, ChronoUnit.DAYS);
        return requisitionRepo.findByCreatedTimeBetweenOrderByCreatedTimeDesc(start, Instant.now());
    }

    @Transactional
    public PurchaseRequisition approveRequisition(String requisitionCode, String approvedBy) {
        Optional<PurchaseRequisition> opt = requisitionRepo.findByRequisitionCode(requisitionCode);
        if (!opt.isPresent()) return null;

        PurchaseRequisition req = opt.get();
        req.setStatus("approved");
        req.setApprovedBy(approvedBy);
        req.setApprovedTime(Instant.now());
        return requisitionRepo.save(req);
    }

    @Transactional
    public PurchaseRequisition completeRequisition(String requisitionCode, double receivedQty) {
        Optional<PurchaseRequisition> opt = requisitionRepo.findByRequisitionCode(requisitionCode);
        if (!opt.isPresent()) return null;

        PurchaseRequisition req = opt.get();
        req.setStatus("completed");
        req.setCompletedTime(Instant.now());
        req.setActualDeliveryDate(Instant.now());
        req.setDeliveredQty(receivedQty);

        if (req.getExpectedDate() != null) {
            long delayHours = ChronoUnit.HOURS.between(req.getExpectedDate(), Instant.now());
            int delayDays = (int) Math.ceil(Math.max(0, delayHours) / 24.0);
            req.setDeliveryDelayDays(delayDays);
        }

        ChemicalInventory inv = getInventoryLatest(req.getChemicalCode());
        if (inv != null) {
            Instant now = Instant.now();
            ChemicalInventory newInv = new ChemicalInventory(now, req.getChemicalCode(),
                    inv.getChemicalName(), inv.getChemicalType());
            newInv.setCurrentStock(inv.getCurrentStock() + receivedQty);
            newInv.setStockUnit(inv.getStockUnit());
            newInv.setSafetyStock(inv.getSafetyStock());
            newInv.setReorderPoint(inv.getReorderPoint());
            newInv.setMaxStock(inv.getMaxStock());
            newInv.setDailyConsumption(inv.getDailyConsumption());
            newInv.setConsumption7dAvg(inv.getConsumption7dAvg());
            newInv.setConsumption30dAvg(inv.getConsumption30dAvg());
            newInv.setLastReplenishmentTime(now);
            newInv.setLastReplenishmentQty(receivedQty);
            newInv.setStockStatus("normal");
            newInv.setUnitPrice(inv.getUnitPrice());
            newInv.setSupplier(inv.getSupplier());
            newInv.setLocation(inv.getLocation());
            newInv.setSupplierReliabilityScore(inv.getSupplierReliabilityScore());
            newInv.setSupplierAvgDelayDays(inv.getSupplierAvgDelayDays());
            newInv.setSupplierOnTimeRate(inv.getSupplierOnTimeRate());
            newInv.setDynamicSafetyStock(inv.getDynamicSafetyStock());
            newInv.setSafetyStockMultiplier(inv.getSafetyStockMultiplier());
            newInv.setMinLeadTimeDays(inv.getMinLeadTimeDays());
            inventoryRepo.save(newInv);
        }

        log.info("[{}] Purchase requisition completed: {}, received: {}, delayDays: {}",
                MODULE_NAME, requisitionCode, receivedQty, req.getDeliveryDelayDays());
        return requisitionRepo.save(req);
    }

    public Map<String, Object> calculateSupplierReliability(String supplierName) {
        Map<String, Object> result = new LinkedHashMap<>();

        double reliabilityScore = 0.85;
        double avgDelayDays = 0.0;
        double onTimeRate = 0.9;

        if (supplierName != null && !supplierName.isEmpty()) {
            Instant start = Instant.now().minus(config.getReliabilityHistoryDays(), ChronoUnit.DAYS);
            List<PurchaseRequisition> history = requisitionRepo
                    .findBySupplierAndStatusAndCompletedTimeBetween(
                            supplierName, "completed", start, Instant.now());

            if (!history.isEmpty()) {
                int totalDeliveries = history.size();
                int onTimeDeliveries = 0;
                long totalDelayHours = 0;

                for (PurchaseRequisition req : history) {
                    if (req.getExpectedDate() != null && req.getActualDeliveryDate() != null) {
                        long delayHours = ChronoUnit.HOURS.between(
                                req.getExpectedDate(), req.getActualDeliveryDate());
                        if (delayHours <= 0) {
                            onTimeDeliveries++;
                        } else {
                            totalDelayHours += delayHours;
                        }
                    }
                }

                onTimeRate = (double) onTimeDeliveries / totalDeliveries;
                int delayedDeliveries = totalDeliveries - onTimeDeliveries;
                avgDelayDays = delayedDeliveries > 0 ?
                        Math.round((totalDelayHours / 24.0 / delayedDeliveries) * 100.0) / 100.0 : 0.0;

                double delayPenalty = avgDelayDays * 0.05;
                double onTimeBonus = onTimeRate * 0.5;
                reliabilityScore = Math.max(config.getMinReliabilityScore(),
                        Math.min(1.0, 0.5 + onTimeBonus - delayPenalty));
                reliabilityScore = Math.round(reliabilityScore * 100.0) / 100.0;
            }
        }

        result.put("module", MODULE_NAME);
        result.put("supplier", supplierName);
        result.put("reliabilityScore", reliabilityScore);
        result.put("avgDelayDays", avgDelayDays);
        result.put("onTimeRate", Math.round(onTimeRate * 100.0) / 100.0);
        result.put("historyDays", config.getReliabilityHistoryDays());

        return result;
    }

    private double calculateSafetyStockMultiplier(double reliabilityScore,
                                                  double avgDelayDays, double onTimeRate) {
        double baseMultiplier = 1.0;

        double reliabilityFactor = 1.0 + (1.0 - reliabilityScore);
        double delayFactor = 1.0 + (avgDelayDays * 0.1);
        double onTimeFactor = 1.0 + ((1.0 - onTimeRate) * 0.5);

        double multiplier = baseMultiplier * reliabilityFactor * delayFactor * onTimeFactor;

        return Math.round(Math.max(1.0, Math.min(3.0, multiplier)) * 100.0) / 100.0;
    }

    public List<ChemicalConsumption> getConsumptionHistory(String chemicalCode, int hours) {
        Instant start = Instant.now().minus(hours, ChronoUnit.HOURS);
        return consumptionRepo.findByChemicalCodeAndTimeBetweenOrderByTimeAsc(chemicalCode, start, Instant.now());
    }

    public Map<String, Object> getConsumptionStats(int days) {
        Instant start = Instant.now().minus(days, ChronoUnit.DAYS);
        List<Object[]> stats = consumptionRepo.getConsumptionStats(start);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("module", MODULE_NAME);
        List<Map<String, Object>> chemicalStats = new ArrayList<>();
        double totalConsumption = 0;

        for (Object[] row : stats) {
            String code = (String) row[0];
            Double avg = (Double) row[1];
            Double sum = (Double) row[2];

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("chemicalCode", code);
            item.put("avgDailyConsumption", avg != null ? Math.round(avg * 100.0) / 100.0 : null);
            item.put("totalConsumption", sum != null ? Math.round(sum * 100.0) / 100.0 : null);
            chemicalStats.add(item);

            totalConsumption += sum != null ? sum : 0;
        }

        result.put("chemicalStats", chemicalStats);
        result.put("totalConsumption", Math.round(totalConsumption * 100.0) / 100.0);
        result.put("periodDays", days);

        return result;
    }

    @Transactional
    public void recordConsumption(ChemicalConsumption consumption) {
        consumptionRepo.save(consumption);
    }

    public Map<String, Object> getLowStockAlerts() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("module", MODULE_NAME);
        result.put("moduleVersion", MODULE_VERSION);

        List<ChemicalInventory> lowStock = inventoryRepo.findByStockStatusInAndIsActiveTrue(
                Arrays.asList("low", "critical", "out_of_stock"));

        List<Map<String, Object>> alerts = new ArrayList<>();
        for (ChemicalInventory inv : lowStock) {
            Map<String, Object> alert = new LinkedHashMap<>();
            alert.put("chemicalCode", inv.getChemicalCode());
            alert.put("chemicalName", inv.getChemicalName());
            alert.put("currentStock", inv.getCurrentStock());
            alert.put("stockUnit", inv.getStockUnit());
            alert.put("safetyStock", inv.getSafetyStock());
            alert.put("dynamicSafetyStock", inv.getDynamicSafetyStock());
            alert.put("reorderPoint", inv.getReorderPoint());
            alert.put("stockStatus", inv.getStockStatus());
            alert.put("daysRemaining", inv.getPredictedDaysRemaining());
            alert.put("supplier", inv.getSupplier());
            alert.put("supplierReliabilityScore", inv.getSupplierReliabilityScore());
            alerts.add(alert);
        }

        result.put("alerts", alerts);
        result.put("totalAlerts", alerts.size());
        return result;
    }
}
