package com.water.dosing.entity;

import javax.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "purchase_requisition")
public class PurchaseRequisition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "requisition_code", nullable = false, length = 64, unique = true)
    private String requisitionCode;

    @Column(name = "created_time", nullable = false)
    private Instant createdTime;

    @Column(name = "chemical_code", nullable = false, length = 32)
    private String chemicalCode;

    @Column(name = "chemical_name", length = 64)
    private String chemicalName;

    @Column(name = "requested_qty")
    private Double requestedQty;

    @Column(name = "stock_unit", length = 16)
    private String stockUnit;

    @Column(name = "urgency_level", length = 32)
    private String urgencyLevel;

    @Column(name = "expected_date")
    private Instant expectedDate;

    @Column(name = "current_stock")
    private Double currentStock;

    @Column(name = "safety_stock")
    private Double safetyStock;

    @Column(name = "daily_consumption")
    private Double dailyConsumption;

    @Column(name = "estimated_cost")
    private Double estimatedCost;

    @Column(name = "supplier", length = 128)
    private String supplier;

    @Column(name = "status", length = 32)
    private String status;

    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;

    @Column(name = "created_by", length = 64)
    private String createdBy;

    @Column(name = "approved_by", length = 64)
    private String approvedBy;

    @Column(name = "approved_time")
    private Instant approvedTime;

    @Column(name = "completed_time")
    private Instant completedTime;

    @Column(name = "actual_delivery_date")
    private Instant actualDeliveryDate;

    @Column(name = "delivery_delay_days")
    private Integer deliveryDelayDays;

    @Column(name = "delivered_qty")
    private Double deliveredQty;

    @Column(name = "remarks", columnDefinition = "TEXT")
    private String remarks;

    public PurchaseRequisition() {}

    public PurchaseRequisition(String requisitionCode, Instant createdTime, String chemicalCode) {
        this.requisitionCode = requisitionCode;
        this.createdTime = createdTime;
        this.chemicalCode = chemicalCode;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getRequisitionCode() { return requisitionCode; }
    public void setRequisitionCode(String requisitionCode) { this.requisitionCode = requisitionCode; }
    public Instant getCreatedTime() { return createdTime; }
    public void setCreatedTime(Instant createdTime) { this.createdTime = createdTime; }
    public String getChemicalCode() { return chemicalCode; }
    public void setChemicalCode(String chemicalCode) { this.chemicalCode = chemicalCode; }
    public String getChemicalName() { return chemicalName; }
    public void setChemicalName(String chemicalName) { this.chemicalName = chemicalName; }
    public Double getRequestedQty() { return requestedQty; }
    public void setRequestedQty(Double requestedQty) { this.requestedQty = requestedQty; }
    public String getStockUnit() { return stockUnit; }
    public void setStockUnit(String stockUnit) { this.stockUnit = stockUnit; }
    public String getUrgencyLevel() { return urgencyLevel; }
    public void setUrgencyLevel(String urgencyLevel) { this.urgencyLevel = urgencyLevel; }
    public Instant getExpectedDate() { return expectedDate; }
    public void setExpectedDate(Instant expectedDate) { this.expectedDate = expectedDate; }
    public Double getCurrentStock() { return currentStock; }
    public void setCurrentStock(Double currentStock) { this.currentStock = currentStock; }
    public Double getSafetyStock() { return safetyStock; }
    public void setSafetyStock(Double safetyStock) { this.safetyStock = safetyStock; }
    public Double getDailyConsumption() { return dailyConsumption; }
    public void setDailyConsumption(Double dailyConsumption) { this.dailyConsumption = dailyConsumption; }
    public Double getEstimatedCost() { return estimatedCost; }
    public void setEstimatedCost(Double estimatedCost) { this.estimatedCost = estimatedCost; }
    public String getSupplier() { return supplier; }
    public void setSupplier(String supplier) { this.supplier = supplier; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public String getApprovedBy() { return approvedBy; }
    public void setApprovedBy(String approvedBy) { this.approvedBy = approvedBy; }
    public Instant getApprovedTime() { return approvedTime; }
    public void setApprovedTime(Instant approvedTime) { this.approvedTime = approvedTime; }
    public Instant getCompletedTime() { return completedTime; }
    public void setCompletedTime(Instant completedTime) { this.completedTime = completedTime; }
    public Instant getActualDeliveryDate() { return actualDeliveryDate; }
    public void setActualDeliveryDate(Instant actualDeliveryDate) { this.actualDeliveryDate = actualDeliveryDate; }
    public Integer getDeliveryDelayDays() { return deliveryDelayDays; }
    public void setDeliveryDelayDays(Integer deliveryDelayDays) { this.deliveryDelayDays = deliveryDelayDays; }
    public Double getDeliveredQty() { return deliveredQty; }
    public void setDeliveredQty(Double deliveredQty) { this.deliveredQty = deliveredQty; }
    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = remarks; }
}
