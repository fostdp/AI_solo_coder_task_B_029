package com.water.dosing.module.watersource;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "module.watersource")
public class WaterSourceOptimizerConfig {

    private double totalDemand = 8333.0;
    private boolean autoOptimize = true;
    private double switchPenaltyWeight = 0.3;
    private boolean enforceSwitchDelay = true;
    private int maxIterations = 100;
    private int populationSize = 50;

    public double getTotalDemand() { return totalDemand; }
    public void setTotalDemand(double totalDemand) { this.totalDemand = totalDemand; }
    public boolean isAutoOptimize() { return autoOptimize; }
    public void setAutoOptimize(boolean autoOptimize) { this.autoOptimize = autoOptimize; }
    public double getSwitchPenaltyWeight() { return switchPenaltyWeight; }
    public void setSwitchPenaltyWeight(double switchPenaltyWeight) { this.switchPenaltyWeight = switchPenaltyWeight; }
    public boolean isEnforceSwitchDelay() { return enforceSwitchDelay; }
    public void setEnforceSwitchDelay(boolean enforceSwitchDelay) { this.enforceSwitchDelay = enforceSwitchDelay; }
    public int getMaxIterations() { return maxIterations; }
    public void setMaxIterations(int maxIterations) { this.maxIterations = maxIterations; }
    public int getPopulationSize() { return populationSize; }
    public void setPopulationSize(int populationSize) { this.populationSize = populationSize; }
}
