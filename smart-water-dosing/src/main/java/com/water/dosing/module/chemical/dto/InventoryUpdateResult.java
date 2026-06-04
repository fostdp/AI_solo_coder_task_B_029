package com.water.dosing.module.chemical.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public class InventoryUpdateResult {
    private String updateId;
    private Instant updateTime;
    private int totalChemicals;
    private int reorderCount;
    private int newRequisitions;
    private List<ChemicalUpdateDetail> updates;
    private Map<String, Object> supplierReliabilitySummary;

    public static class ChemicalUpdateDetail {
        private String chemicalCode;
        private String chemicalName;
        private double currentStock;
        private double dailyConsumption;
        private int daysRemaining;
        private String stockStatus;
        private boolean needsReorder;
        private double safetyStock;
        private double reorderPoint;
        private Double supplierReliabilityScore;
        private Double safetyStockMultiplier;
        private Double dynamicSafetyStock;

        public String getChemicalCode() { return chemicalCode; }
        public void setChemicalCode(String chemicalCode) { this.chemicalCode = chemicalCode; }
        public String getChemicalName() { return chemicalName; }
        public void setChemicalName(String chemicalName) { this.chemicalName = chemicalName; }
        public double getCurrentStock() { return currentStock; }
        public void setCurrentStock(double currentStock) { this.currentStock = currentStock; }
        public double getDailyConsumption() { return dailyConsumption; }
        public void setDailyConsumption(double dailyConsumption) { this.dailyConsumption = dailyConsumption; }
        public int getDaysRemaining() { return daysRemaining; }
        public void setDaysRemaining(int daysRemaining) { this.daysRemaining = daysRemaining; }
        public String getStockStatus() { return stockStatus; }
        public void setStockStatus(String stockStatus) { this.stockStatus = stockStatus; }
        public boolean isNeedsReorder() { return needsReorder; }
        public void setNeedsReorder(boolean needsReorder) { this.needsReorder = needsReorder; }
        public double getSafetyStock() { return safetyStock; }
        public void setSafetyStock(double safetyStock) { this.safetyStock = safetyStock; }
        public double getReorderPoint() { return reorderPoint; }
        public void setReorderPoint(double reorderPoint) { this.reorderPoint = reorderPoint; }
        public Double getSupplierReliabilityScore() { return supplierReliabilityScore; }
        public void setSupplierReliabilityScore(Double supplierReliabilityScore) { this.supplierReliabilityScore = supplierReliabilityScore; }
        public Double getSafetyStockMultiplier() { return safetyStockMultiplier; }
        public void setSafetyStockMultiplier(Double safetyStockMultiplier) { this.safetyStockMultiplier = safetyStockMultiplier; }
        public Double getDynamicSafetyStock() { return dynamicSafetyStock; }
        public void setDynamicSafetyStock(Double dynamicSafetyStock) { this.dynamicSafetyStock = dynamicSafetyStock; }
    }

    public String getUpdateId() { return updateId; }
    public void setUpdateId(String updateId) { this.updateId = updateId; }
    public Instant getUpdateTime() { return updateTime; }
    public void setUpdateTime(Instant updateTime) { this.updateTime = updateTime; }
    public int getTotalChemicals() { return totalChemicals; }
    public void setTotalChemicals(int totalChemicals) { this.totalChemicals = totalChemicals; }
    public int getReorderCount() { return reorderCount; }
    public void setReorderCount(int reorderCount) { this.reorderCount = reorderCount; }
    public int getNewRequisitions() { return newRequisitions; }
    public void setNewRequisitions(int newRequisitions) { this.newRequisitions = newRequisitions; }
    public List<ChemicalUpdateDetail> getUpdates() { return updates; }
    public void setUpdates(List<ChemicalUpdateDetail> updates) { this.updates = updates; }
    public Map<String, Object> getSupplierReliabilitySummary() { return supplierReliabilitySummary; }
    public void setSupplierReliabilitySummary(Map<String, Object> supplierReliabilitySummary) { this.supplierReliabilitySummary = supplierReliabilitySummary; }
}
