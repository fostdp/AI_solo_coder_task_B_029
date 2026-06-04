package com.water.dosing.module.membrane.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public class FoulingEvaluationResult {
    private String evaluationId;
    private Instant evaluationTime;
    private int totalModules;
    private int urgentCount;
    private int soonCount;
    private int normalCount;
    private List<ModuleFoulingStatus> moduleStatuses;
    private boolean usedStreamProcessing;
    private String processingMode;
    private Map<String, Object> streamStats;

    public static class ModuleFoulingStatus {
        private String moduleCode;
        private String moduleName;
        private String moduleType;
        private double currentFlux;
        private double baselineFlux;
        private double currentTmd;
        private double baselineTmd;
        private double foulingIndex;
        private String foulingLevel;
        private int predictedCleanDays;
        private String cleanUrgency;
        private boolean isRecentlyReplaced;
        private Instant replacementTime;
        private String notes;

        public String getModuleCode() { return moduleCode; }
        public void setModuleCode(String moduleCode) { this.moduleCode = moduleCode; }
        public String getModuleName() { return moduleName; }
        public void setModuleName(String moduleName) { this.moduleName = moduleName; }
        public String getModuleType() { return moduleType; }
        public void setModuleType(String moduleType) { this.moduleType = moduleType; }
        public double getCurrentFlux() { return currentFlux; }
        public void setCurrentFlux(double currentFlux) { this.currentFlux = currentFlux; }
        public double getBaselineFlux() { return baselineFlux; }
        public void setBaselineFlux(double baselineFlux) { this.baselineFlux = baselineFlux; }
        public double getCurrentTmd() { return currentTmd; }
        public void setCurrentTmd(double currentTmd) { this.currentTmd = currentTmd; }
        public double getBaselineTmd() { return baselineTmd; }
        public void setBaselineTmd(double baselineTmd) { this.baselineTmd = baselineTmd; }
        public double getFoulingIndex() { return foulingIndex; }
        public void setFoulingIndex(double foulingIndex) { this.foulingIndex = foulingIndex; }
        public String getFoulingLevel() { return foulingLevel; }
        public void setFoulingLevel(String foulingLevel) { this.foulingLevel = foulingLevel; }
        public int getPredictedCleanDays() { return predictedCleanDays; }
        public void setPredictedCleanDays(int predictedCleanDays) { this.predictedCleanDays = predictedCleanDays; }
        public String getCleanUrgency() { return cleanUrgency; }
        public void setCleanUrgency(String cleanUrgency) { this.cleanUrgency = cleanUrgency; }
        public boolean isRecentlyReplaced() { return isRecentlyReplaced; }
        public void setRecentlyReplaced(boolean recentlyReplaced) { isRecentlyReplaced = recentlyReplaced; }
        public Instant getReplacementTime() { return replacementTime; }
        public void setReplacementTime(Instant replacementTime) { this.replacementTime = replacementTime; }
        public String getNotes() { return notes; }
        public void setNotes(String notes) { this.notes = notes; }
    }

    public String getEvaluationId() { return evaluationId; }
    public void setEvaluationId(String evaluationId) { this.evaluationId = evaluationId; }
    public Instant getEvaluationTime() { return evaluationTime; }
    public void setEvaluationTime(Instant evaluationTime) { this.evaluationTime = evaluationTime; }
    public int getTotalModules() { return totalModules; }
    public void setTotalModules(int totalModules) { this.totalModules = totalModules; }
    public int getUrgentCount() { return urgentCount; }
    public void setUrgentCount(int urgentCount) { this.urgentCount = urgentCount; }
    public int getSoonCount() { return soonCount; }
    public void setSoonCount(int soonCount) { this.soonCount = soonCount; }
    public int getNormalCount() { return normalCount; }
    public void setNormalCount(int normalCount) { this.normalCount = normalCount; }
    public List<ModuleFoulingStatus> getModuleStatuses() { return moduleStatuses; }
    public void setModuleStatuses(List<ModuleFoulingStatus> moduleStatuses) { this.moduleStatuses = moduleStatuses; }
    public boolean isUsedStreamProcessing() { return usedStreamProcessing; }
    public void setUsedStreamProcessing(boolean usedStreamProcessing) { this.usedStreamProcessing = usedStreamProcessing; }
    public String getProcessingMode() { return processingMode; }
    public void setProcessingMode(String processingMode) { this.processingMode = processingMode; }
    public Map<String, Object> getStreamStats() { return streamStats; }
    public void setStreamStats(Map<String, Object> streamStats) { this.streamStats = streamStats; }
}
