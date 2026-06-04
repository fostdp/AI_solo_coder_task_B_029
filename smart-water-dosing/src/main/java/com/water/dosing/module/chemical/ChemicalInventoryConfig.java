package com.water.dosing.module.chemical;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "module.chemical")
public class ChemicalInventoryConfig {

    private int purchaseLeadTimeDays = 3;
    private int securityDays = 7;
    private boolean autoCreateRequisition = true;
    private int reliabilityHistoryDays = 90;
    private boolean dynamicSafetyStockEnabled = true;
    private double minReliabilityScore = 0.5;

    public int getPurchaseLeadTimeDays() { return purchaseLeadTimeDays; }
    public void setPurchaseLeadTimeDays(int purchaseLeadTimeDays) { this.purchaseLeadTimeDays = purchaseLeadTimeDays; }
    public int getSecurityDays() { return securityDays; }
    public void setSecurityDays(int securityDays) { this.securityDays = securityDays; }
    public boolean isAutoCreateRequisition() { return autoCreateRequisition; }
    public void setAutoCreateRequisition(boolean autoCreateRequisition) { this.autoCreateRequisition = autoCreateRequisition; }
    public int getReliabilityHistoryDays() { return reliabilityHistoryDays; }
    public void setReliabilityHistoryDays(int reliabilityHistoryDays) { this.reliabilityHistoryDays = reliabilityHistoryDays; }
    public boolean isDynamicSafetyStockEnabled() { return dynamicSafetyStockEnabled; }
    public void setDynamicSafetyStockEnabled(boolean dynamicSafetyStockEnabled) { this.dynamicSafetyStockEnabled = dynamicSafetyStockEnabled; }
    public double getMinReliabilityScore() { return minReliabilityScore; }
    public void setMinReliabilityScore(double minReliabilityScore) { this.minReliabilityScore = minReliabilityScore; }
}
