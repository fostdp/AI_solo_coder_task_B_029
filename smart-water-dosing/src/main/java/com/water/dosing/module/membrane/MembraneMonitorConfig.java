package com.water.dosing.module.membrane;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "module.membrane")
public class MembraneMonitorConfig {

    private double ufBaseFlux = 60.0;
    private double roBaseFlux = 25.0;
    private double ufBaseTmd = 0.8;
    private double roBaseTmd = 10.0;
    private double foulingWarningThreshold = 0.75;
    private double foulingAlarmThreshold = 0.5;
    private int urgentDays = 3;
    private int soonDays = 7;
    private double replacementDetectionThreshold = 0.3;
    private int baselineWarmupHours = 72;
    private boolean streamProcessingEnabled = false;
    private int streamParallelism = 2;

    public double getUfBaseFlux() { return ufBaseFlux; }
    public void setUfBaseFlux(double ufBaseFlux) { this.ufBaseFlux = ufBaseFlux; }
    public double getRoBaseFlux() { return roBaseFlux; }
    public void setRoBaseFlux(double roBaseFlux) { this.roBaseFlux = roBaseFlux; }
    public double getUfBaseTmd() { return ufBaseTmd; }
    public void setUfBaseTmd(double ufBaseTmd) { this.ufBaseTmd = ufBaseTmd; }
    public double getRoBaseTmd() { return roBaseTmd; }
    public void setRoBaseTmd(double roBaseTmd) { this.roBaseTmd = roBaseTmd; }
    public double getFoulingWarningThreshold() { return foulingWarningThreshold; }
    public void setFoulingWarningThreshold(double foulingWarningThreshold) { this.foulingWarningThreshold = foulingWarningThreshold; }
    public double getFoulingAlarmThreshold() { return foulingAlarmThreshold; }
    public void setFoulingAlarmThreshold(double foulingAlarmThreshold) { this.foulingAlarmThreshold = foulingAlarmThreshold; }
    public int getUrgentDays() { return urgentDays; }
    public void setUrgentDays(int urgentDays) { this.urgentDays = urgentDays; }
    public int getSoonDays() { return soonDays; }
    public void setSoonDays(int soonDays) { this.soonDays = soonDays; }
    public double getReplacementDetectionThreshold() { return replacementDetectionThreshold; }
    public void setReplacementDetectionThreshold(double replacementDetectionThreshold) { this.replacementDetectionThreshold = replacementDetectionThreshold; }
    public int getBaselineWarmupHours() { return baselineWarmupHours; }
    public void setBaselineWarmupHours(int baselineWarmupHours) { this.baselineWarmupHours = baselineWarmupHours; }
    public boolean isStreamProcessingEnabled() { return streamProcessingEnabled; }
    public void setStreamProcessingEnabled(boolean streamProcessingEnabled) { this.streamProcessingEnabled = streamProcessingEnabled; }
    public int getStreamParallelism() { return streamParallelism; }
    public void setStreamParallelism(int streamParallelism) { this.streamParallelism = streamParallelism; }
}
