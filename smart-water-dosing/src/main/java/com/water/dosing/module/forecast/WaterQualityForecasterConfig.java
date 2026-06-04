package com.water.dosing.module.forecast;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;
import java.util.Arrays;
import java.util.List;

@Configuration
@ConfigurationProperties(prefix = "water-quality.forecaster")
public class WaterQualityForecasterConfig {

    private int predictionHours = 24;
    private int historyHours = 168;
    private double mutationSensitivity = 0.05;
    private int minDataPoints = 24;
    private int maxConcurrentPredictions = 5;
    private boolean enableMutationDetection = true;
    private boolean enableConfidenceCalculation = true;
    private List<String> predictedIndicators = Arrays.asList("turbidity", "cod", "ammonia", "ph", "conductivity");
    private double defaultConfidenceLevel = 0.85;

    @PostConstruct
    public void init() {
        if (predictionHours < 1) predictionHours = 1;
        if (predictionHours > 168) predictionHours = 168;
        if (historyHours < predictionHours) historyHours = predictionHours;
        if (mutationSensitivity < 0.01) mutationSensitivity = 0.01;
        if (mutationSensitivity > 0.5) mutationSensitivity = 0.5;
    }

    public int getPredictionHours() { return predictionHours; }
    public void setPredictionHours(int predictionHours) { this.predictionHours = predictionHours; }

    public int getHistoryHours() { return historyHours; }
    public void setHistoryHours(int historyHours) { this.historyHours = historyHours; }

    public double getMutationSensitivity() { return mutationSensitivity; }
    public void setMutationSensitivity(double mutationSensitivity) { this.mutationSensitivity = mutationSensitivity; }

    public int getMinDataPoints() { return minDataPoints; }
    public void setMinDataPoints(int minDataPoints) { this.minDataPoints = minDataPoints; }

    public int getMaxConcurrentPredictions() { return maxConcurrentPredictions; }
    public void setMaxConcurrentPredictions(int maxConcurrentPredictions) { this.maxConcurrentPredictions = maxConcurrentPredictions; }

    public boolean isEnableMutationDetection() { return enableMutationDetection; }
    public void setEnableMutationDetection(boolean enableMutationDetection) { this.enableMutationDetection = enableMutationDetection; }

    public boolean isEnableConfidenceCalculation() { return enableConfidenceCalculation; }
    public void setEnableConfidenceCalculation(boolean enableConfidenceCalculation) { this.enableConfidenceCalculation = enableConfidenceCalculation; }

    public List<String> getPredictedIndicators() { return predictedIndicators; }
    public void setPredictedIndicators(List<String> predictedIndicators) { this.predictedIndicators = predictedIndicators; }

    public double getDefaultConfidenceLevel() { return defaultConfidenceLevel; }
    public void setDefaultConfidenceLevel(double defaultConfidenceLevel) { this.defaultConfidenceLevel = defaultConfidenceLevel; }
}
