package com.water.dosing.module.watersource.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public class OptimizationResultDto {
    private String optimizationId;
    private Instant optimizationTime;
    private double totalDemand;
    private double totalCost;
    private double mixedTurbidity;
    private double mixedPh;
    private boolean feasible;
    private double fitnessScore;
    private Map<String, Double> allocations;
    private List<AllocationDetail> allocationDetails;
    private boolean usedSwitchConstraint;
    private String switchConstraintNote;

    public static class AllocationDetail {
        private String sourceCode;
        private String sourceName;
        private double ratio;
        private double volume;
        private double unitCost;
        private double totalCost;

        public AllocationDetail(String sourceCode, String sourceName, double ratio,
                                 double volume, double unitCost, double totalCost) {
            this.sourceCode = sourceCode;
            this.sourceName = sourceName;
            this.ratio = ratio;
            this.volume = volume;
            this.unitCost = unitCost;
            this.totalCost = totalCost;
        }

        public String getSourceCode() { return sourceCode; }
        public void setSourceCode(String sourceCode) { this.sourceCode = sourceCode; }
        public String getSourceName() { return sourceName; }
        public void setSourceName(String sourceName) { this.sourceName = sourceName; }
        public double getRatio() { return ratio; }
        public void setRatio(double ratio) { this.ratio = ratio; }
        public double getVolume() { return volume; }
        public void setVolume(double volume) { this.volume = volume; }
        public double getUnitCost() { return unitCost; }
        public void setUnitCost(double unitCost) { this.unitCost = unitCost; }
        public double getTotalCost() { return totalCost; }
        public void setTotalCost(double totalCost) { this.totalCost = totalCost; }
    }

    public String getOptimizationId() { return optimizationId; }
    public void setOptimizationId(String optimizationId) { this.optimizationId = optimizationId; }
    public Instant getOptimizationTime() { return optimizationTime; }
    public void setOptimizationTime(Instant optimizationTime) { this.optimizationTime = optimizationTime; }
    public double getTotalDemand() { return totalDemand; }
    public void setTotalDemand(double totalDemand) { this.totalDemand = totalDemand; }
    public double getTotalCost() { return totalCost; }
    public void setTotalCost(double totalCost) { this.totalCost = totalCost; }
    public double getMixedTurbidity() { return mixedTurbidity; }
    public void setMixedTurbidity(double mixedTurbidity) { this.mixedTurbidity = mixedTurbidity; }
    public double getMixedPh() { return mixedPh; }
    public void setMixedPh(double mixedPh) { this.mixedPh = mixedPh; }
    public boolean isFeasible() { return feasible; }
    public void setFeasible(boolean feasible) { this.feasible = feasible; }
    public double getFitnessScore() { return fitnessScore; }
    public void setFitnessScore(double fitnessScore) { this.fitnessScore = fitnessScore; }
    public Map<String, Double> getAllocations() { return allocations; }
    public void setAllocations(Map<String, Double> allocations) { this.allocations = allocations; }
    public List<AllocationDetail> getAllocationDetails() { return allocationDetails; }
    public void setAllocationDetails(List<AllocationDetail> allocationDetails) { this.allocationDetails = allocationDetails; }
    public boolean isUsedSwitchConstraint() { return usedSwitchConstraint; }
    public void setUsedSwitchConstraint(boolean usedSwitchConstraint) { this.usedSwitchConstraint = usedSwitchConstraint; }
    public String getSwitchConstraintNote() { return switchConstraintNote; }
    public void setSwitchConstraintNote(String switchConstraintNote) { this.switchConstraintNote = switchConstraintNote; }
}
