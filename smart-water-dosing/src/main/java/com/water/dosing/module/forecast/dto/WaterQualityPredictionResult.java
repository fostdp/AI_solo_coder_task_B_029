package com.water.dosing.module.forecast.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public class WaterQualityPredictionResult {
    private String predictionId;
    private Instant predictionTime;
    private int predictionHours;
    private int dataPointsUsed;
    private String modelVersion;
    private double overallConfidence;
    private String status;
    private List<IndicatorPrediction> predictions;
    private MutationDetectionResult mutationDetection;
    private Map<String, Object> modelMetadata;

    public static class IndicatorPrediction {
        private String indicatorCode;
        private String indicatorName;
        private double currentValue;
        private double predictedValue;
        private double minPredicted;
        private double maxPredicted;
        private double confidence;
        private String trend;
        private String level;
        private String remarks;
        private double trendRate;
        private double deviationFromNormal;
        private List<TimePoint> hourlyForecast;

        public String getIndicatorCode() { return indicatorCode; }
        public void setIndicatorCode(String indicatorCode) { this.indicatorCode = indicatorCode; }
        public String getIndicatorName() { return indicatorName; }
        public void setIndicatorName(String indicatorName) { this.indicatorName = indicatorName; }
        public double getCurrentValue() { return currentValue; }
        public void setCurrentValue(double currentValue) { this.currentValue = currentValue; }
        public double getPredictedValue() { return predictedValue; }
        public void setPredictedValue(double predictedValue) { this.predictedValue = predictedValue; }
        public double getMinPredicted() { return minPredicted; }
        public void setMinPredicted(double minPredicted) { this.minPredicted = minPredicted; }
        public double getMaxPredicted() { return maxPredicted; }
        public void setMaxPredicted(double maxPredicted) { this.maxPredicted = maxPredicted; }
        public double getConfidence() { return confidence; }
        public void setConfidence(double confidence) { this.confidence = confidence; }
        public String getTrend() { return trend; }
        public void setTrend(String trend) { this.trend = trend; }
        public String getLevel() { return level; }
        public void setLevel(String level) { this.level = level; }
        public String getRemarks() { return remarks; }
        public void setRemarks(String remarks) { this.remarks = remarks; }
        public double getTrendRate() { return trendRate; }
        public void setTrendRate(double trendRate) { this.trendRate = trendRate; }
        public double getDeviationFromNormal() { return deviationFromNormal; }
        public void setDeviationFromNormal(double deviationFromNormal) { this.deviationFromNormal = deviationFromNormal; }
        public List<TimePoint> getHourlyForecast() { return hourlyForecast; }
        public void setHourlyForecast(List<TimePoint> hourlyForecast) { this.hourlyForecast = hourlyForecast; }
    }

    public static class TimePoint {
        private Instant time;
        private double value;
        private double lowerBound;
        private double upperBound;

        public TimePoint() {}

        public TimePoint(Instant time, double value) {
            this.time = time;
            this.value = value;
        }

        public Instant getTime() { return time; }
        public void setTime(Instant time) { this.time = time; }
        public double getValue() { return value; }
        public void setValue(double value) { this.value = value; }
        public double getLowerBound() { return lowerBound; }
        public void setLowerBound(double lowerBound) { this.lowerBound = lowerBound; }
        public double getUpperBound() { return upperBound; }
        public void setUpperBound(double upperBound) { this.upperBound = upperBound; }
    }

    public static class MutationDetectionResult {
        private boolean mutationDetected;
        private int mutationCount;
        private String mutationDetails;
        private List<MutationEvent> mutationEvents;
        private double controlLimit;
        private double sensitivity;

        public boolean isMutationDetected() { return mutationDetected; }
        public void setMutationDetected(boolean mutationDetected) { this.mutationDetected = mutationDetected; }
        public int getMutationCount() { return mutationCount; }
        public void setMutationCount(int mutationCount) { this.mutationCount = mutationCount; }
        public String getMutationDetails() { return mutationDetails; }
        public void setMutationDetails(String mutationDetails) { this.mutationDetails = mutationDetails; }
        public List<MutationEvent> getMutationEvents() { return mutationEvents; }
        public void setMutationEvents(List<MutationEvent> mutationEvents) { this.mutationEvents = mutationEvents; }
        public double getControlLimit() { return controlLimit; }
        public void setControlLimit(double controlLimit) { this.controlLimit = controlLimit; }
        public double getSensitivity() { return sensitivity; }
        public void setSensitivity(double sensitivity) { this.sensitivity = sensitivity; }
    }

    public static class MutationEvent {
        private String indicator;
        private Instant time;
        private double cusumValue;
        private double deviation;
        private String severity;
        private String description;

        public String getIndicator() { return indicator; }
        public void setIndicator(String indicator) { this.indicator = indicator; }
        public Instant getTime() { return time; }
        public void setTime(Instant time) { this.time = time; }
        public double getCusumValue() { return cusumValue; }
        public void setCusumValue(double cusumValue) { this.cusumValue = cusumValue; }
        public double getDeviation() { return deviation; }
        public void setDeviation(double deviation) { this.deviation = deviation; }
        public String getSeverity() { return severity; }
        public void setSeverity(String severity) { this.severity = severity; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
    }

    public String getPredictionId() { return predictionId; }
    public void setPredictionId(String predictionId) { this.predictionId = predictionId; }
    public Instant getPredictionTime() { return predictionTime; }
    public void setPredictionTime(Instant predictionTime) { this.predictionTime = predictionTime; }
    public int getPredictionHours() { return predictionHours; }
    public void setPredictionHours(int predictionHours) { this.predictionHours = predictionHours; }
    public int getDataPointsUsed() { return dataPointsUsed; }
    public void setDataPointsUsed(int dataPointsUsed) { this.dataPointsUsed = dataPointsUsed; }
    public String getModelVersion() { return modelVersion; }
    public void setModelVersion(String modelVersion) { this.modelVersion = modelVersion; }
    public double getOverallConfidence() { return overallConfidence; }
    public void setOverallConfidence(double overallConfidence) { this.overallConfidence = overallConfidence; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public List<IndicatorPrediction> getPredictions() { return predictions; }
    public void setPredictions(List<IndicatorPrediction> predictions) { this.predictions = predictions; }
    public MutationDetectionResult getMutationDetection() { return mutationDetection; }
    public void setMutationDetection(MutationDetectionResult mutationDetection) { this.mutationDetection = mutationDetection; }
    public Map<String, Object> getModelMetadata() { return modelMetadata; }
    public void setModelMetadata(Map<String, Object> modelMetadata) { this.modelMetadata = modelMetadata; }
}
