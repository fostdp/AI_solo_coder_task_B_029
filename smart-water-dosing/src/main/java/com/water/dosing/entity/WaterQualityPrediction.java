package com.water.dosing.entity;

import javax.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "water_quality_prediction")
public class WaterQualityPrediction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "prediction_time", nullable = false)
    private Instant predictionTime;

    @Column(name = "target_time", nullable = false)
    private Instant targetTime;

    @Column(name = "stage", nullable = false, length = 32)
    private String stage;

    @Column(name = "parameter_name", length = 32)
    private String parameterName;

    @Column(name = "predicted_value")
    private Double predictedValue;

    @Column(name = "lower_bound")
    private Double lowerBound;

    @Column(name = "upper_bound")
    private Double upperBound;

    @Column(name = "confidence")
    private Double confidence;

    @Column(name = "actual_value")
    private Double actualValue;

    @Column(name = "prediction_error")
    private Double predictionError;

    @Column(name = "model_version", length = 32)
    private String modelVersion;

    @Column(name = "features_used", columnDefinition = "TEXT")
    private String featuresUsed;

    @Column(name = "is_warning")
    private Boolean isWarning = false;

    @Column(name = "is_alarm")
    private Boolean isAlarm = false;

    @Column(name = "threshold_value")
    private Double thresholdValue;

    @Column(name = "warning_level", length = 32)
    private String warningLevel;

    @Column(name = "process_adjustment", columnDefinition = "TEXT")
    private String processAdjustment;

    @Column(name = "dosing_adjustment", columnDefinition = "TEXT")
    private String dosingAdjustment;

    @Column(name = "remarks", columnDefinition = "TEXT")
    private String remarks;

    public WaterQualityPrediction() {}

    public WaterQualityPrediction(Instant predictionTime, Instant targetTime, String stage, String parameterName) {
        this.predictionTime = predictionTime;
        this.targetTime = targetTime;
        this.stage = stage;
        this.parameterName = parameterName;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Instant getPredictionTime() { return predictionTime; }
    public void setPredictionTime(Instant predictionTime) { this.predictionTime = predictionTime; }
    public Instant getTargetTime() { return targetTime; }
    public void setTargetTime(Instant targetTime) { this.targetTime = targetTime; }
    public String getStage() { return stage; }
    public void setStage(String stage) { this.stage = stage; }
    public String getParameterName() { return parameterName; }
    public void setParameterName(String parameterName) { this.parameterName = parameterName; }
    public Double getPredictedValue() { return predictedValue; }
    public void setPredictedValue(Double predictedValue) { this.predictedValue = predictedValue; }
    public Double getLowerBound() { return lowerBound; }
    public void setLowerBound(Double lowerBound) { this.lowerBound = lowerBound; }
    public Double getUpperBound() { return upperBound; }
    public void setUpperBound(Double upperBound) { this.upperBound = upperBound; }
    public Double getConfidence() { return confidence; }
    public void setConfidence(Double confidence) { this.confidence = confidence; }
    public Double getActualValue() { return actualValue; }
    public void setActualValue(Double actualValue) { this.actualValue = actualValue; }
    public Double getPredictionError() { return predictionError; }
    public void setPredictionError(Double predictionError) { this.predictionError = predictionError; }
    public String getModelVersion() { return modelVersion; }
    public void setModelVersion(String modelVersion) { this.modelVersion = modelVersion; }
    public String getFeaturesUsed() { return featuresUsed; }
    public void setFeaturesUsed(String featuresUsed) { this.featuresUsed = featuresUsed; }
    public Boolean getIsWarning() { return isWarning; }
    public void setIsWarning(Boolean isWarning) { this.isWarning = isWarning; }
    public Boolean getIsAlarm() { return isAlarm; }
    public void setIsAlarm(Boolean isAlarm) { this.isAlarm = isAlarm; }
    public Double getThresholdValue() { return thresholdValue; }
    public void setThresholdValue(Double thresholdValue) { this.thresholdValue = thresholdValue; }
    public String getWarningLevel() { return warningLevel; }
    public void setWarningLevel(String warningLevel) { this.warningLevel = warningLevel; }
    public String getProcessAdjustment() { return processAdjustment; }
    public void setProcessAdjustment(String processAdjustment) { this.processAdjustment = processAdjustment; }
    public String getDosingAdjustment() { return dosingAdjustment; }
    public void setDosingAdjustment(String dosingAdjustment) { this.dosingAdjustment = dosingAdjustment; }
    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = remarks; }
}
