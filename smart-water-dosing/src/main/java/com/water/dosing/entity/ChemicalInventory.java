package com.water.dosing.entity;

import javax.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "chemical_inventory")
public class ChemicalInventory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "time", nullable = false)
    private Instant time;

    @Column(name = "chemical_code", nullable = false, length = 32)
    private String chemicalCode;

    @Column(name = "chemical_name", nullable = false, length = 64)
    private String chemicalName;

    @Column(name = "chemical_type", length = 32)
    private String chemicalType;

    @Column(name = "current_stock")
    private Double currentStock;

    @Column(name = "stock_unit", length = 16)
    private String stockUnit;

    @Column(name = "safety_stock")
    private Double safetyStock;

    @Column(name = "reorder_point")
    private Double reorderPoint;

    @Column(name = "max_stock")
    private Double maxStock;

    @Column(name = "daily_consumption")
    private Double dailyConsumption;

    @Column(name = "consumption_7d_avg")
    private Double consumption7dAvg;

    @Column(name = "consumption_30d_avg")
    private Double consumption30dAvg;

    @Column(name = "predicted_days_remaining")
    private Integer predictedDaysRemaining;

    @Column(name = "stock_status", length = 32)
    private String stockStatus;

    @Column(name = "unit_price")
    private Double unitPrice;

    @Column(name = "supplier", length = 128)
    private String supplier;

    @Column(name = "last_replenishment_time")
    private Instant lastReplenishmentTime;

    @Column(name = "last_replenishment_qty")
    private Double lastReplenishmentQty;

    @Column(name = "location", length = 64)
    private String location;

    @Column(name = "batch_number", length = 64)
    private String batchNumber;

    @Column(name = "expiry_date")
    private Instant expiryDate;

    @Column(name = "supplier_reliability_score")
    private Double supplierReliabilityScore = 0.85;

    @Column(name = "supplier_avg_delay_days")
    private Double supplierAvgDelayDays = 0.0;

    @Column(name = "supplier_on_time_rate")
    private Double supplierOnTimeRate = 0.9;

    @Column(name = "dynamic_safety_stock")
    private Double dynamicSafetyStock;

    @Column(name = "safety_stock_multiplier")
    private Double safetyStockMultiplier = 1.0;

    @Column(name = "min_lead_time_days")
    private Integer minLeadTimeDays = 3;

    @Column(name = "is_active")
    private Boolean isActive = true;

    public ChemicalInventory() {}

    public ChemicalInventory(Instant time, String chemicalCode, String chemicalName, String chemicalType) {
        this.time = time;
        this.chemicalCode = chemicalCode;
        this.chemicalName = chemicalName;
        this.chemicalType = chemicalType;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Instant getTime() { return time; }
    public void setTime(Instant time) { this.time = time; }
    public String getChemicalCode() { return chemicalCode; }
    public void setChemicalCode(String chemicalCode) { this.chemicalCode = chemicalCode; }
    public String getChemicalName() { return chemicalName; }
    public void setChemicalName(String chemicalName) { this.chemicalName = chemicalName; }
    public String getChemicalType() { return chemicalType; }
    public void setChemicalType(String chemicalType) { this.chemicalType = chemicalType; }
    public Double getCurrentStock() { return currentStock; }
    public void setCurrentStock(Double currentStock) { this.currentStock = currentStock; }
    public String getStockUnit() { return stockUnit; }
    public void setStockUnit(String stockUnit) { this.stockUnit = stockUnit; }
    public Double getSafetyStock() { return safetyStock; }
    public void setSafetyStock(Double safetyStock) { this.safetyStock = safetyStock; }
    public Double getReorderPoint() { return reorderPoint; }
    public void setReorderPoint(Double reorderPoint) { this.reorderPoint = reorderPoint; }
    public Double getMaxStock() { return maxStock; }
    public void setMaxStock(Double maxStock) { this.maxStock = maxStock; }
    public Double getDailyConsumption() { return dailyConsumption; }
    public void setDailyConsumption(Double dailyConsumption) { this.dailyConsumption = dailyConsumption; }
    public Double getConsumption7dAvg() { return consumption7dAvg; }
    public void setConsumption7dAvg(Double consumption7dAvg) { this.consumption7dAvg = consumption7dAvg; }
    public Double getConsumption30dAvg() { return consumption30dAvg; }
    public void setConsumption30dAvg(Double consumption30dAvg) { this.consumption30dAvg = consumption30dAvg; }
    public Integer getPredictedDaysRemaining() { return predictedDaysRemaining; }
    public void setPredictedDaysRemaining(Integer predictedDaysRemaining) { this.predictedDaysRemaining = predictedDaysRemaining; }
    public String getStockStatus() { return stockStatus; }
    public void setStockStatus(String stockStatus) { this.stockStatus = stockStatus; }
    public Double getUnitPrice() { return unitPrice; }
    public void setUnitPrice(Double unitPrice) { this.unitPrice = unitPrice; }
    public String getSupplier() { return supplier; }
    public void setSupplier(String supplier) { this.supplier = supplier; }
    public Instant getLastReplenishmentTime() { return lastReplenishmentTime; }
    public void setLastReplenishmentTime(Instant lastReplenishmentTime) { this.lastReplenishmentTime = lastReplenishmentTime; }
    public Double getLastReplenishmentQty() { return lastReplenishmentQty; }
    public void setLastReplenishmentQty(Double lastReplenishmentQty) { this.lastReplenishmentQty = lastReplenishmentQty; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public String getBatchNumber() { return batchNumber; }
    public void setBatchNumber(String batchNumber) { this.batchNumber = batchNumber; }
    public Instant getExpiryDate() { return expiryDate; }
    public void setExpiryDate(Instant expiryDate) { this.expiryDate = expiryDate; }
    public Double getSupplierReliabilityScore() { return supplierReliabilityScore; }
    public void setSupplierReliabilityScore(Double supplierReliabilityScore) { this.supplierReliabilityScore = supplierReliabilityScore; }
    public Double getSupplierAvgDelayDays() { return supplierAvgDelayDays; }
    public void setSupplierAvgDelayDays(Double supplierAvgDelayDays) { this.supplierAvgDelayDays = supplierAvgDelayDays; }
    public Double getSupplierOnTimeRate() { return supplierOnTimeRate; }
    public void setSupplierOnTimeRate(Double supplierOnTimeRate) { this.supplierOnTimeRate = supplierOnTimeRate; }
    public Double getDynamicSafetyStock() { return dynamicSafetyStock; }
    public void setDynamicSafetyStock(Double dynamicSafetyStock) { this.dynamicSafetyStock = dynamicSafetyStock; }
    public Double getSafetyStockMultiplier() { return safetyStockMultiplier; }
    public void setSafetyStockMultiplier(Double safetyStockMultiplier) { this.safetyStockMultiplier = safetyStockMultiplier; }
    public Integer getMinLeadTimeDays() { return minLeadTimeDays; }
    public void setMinLeadTimeDays(Integer minLeadTimeDays) { this.minLeadTimeDays = minLeadTimeDays; }
    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }
}
