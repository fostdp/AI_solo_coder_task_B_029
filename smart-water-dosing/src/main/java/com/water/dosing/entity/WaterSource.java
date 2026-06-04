package com.water.dosing.entity;

import javax.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "water_source")
public class WaterSource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "time", nullable = false)
    private Instant time;

    @Column(name = "source_code", nullable = false, length = 32)
    private String sourceCode;

    @Column(name = "source_name", nullable = false, length = 64)
    private String sourceName;

    @Column(name = "source_type", length = 32)
    private String sourceType;

    @Column(name = "turbidity")
    private Double turbidity;

    @Column(name = "ph")
    private Double ph;

    @Column(name = "temperature")
    private Double temperature;

    @Column(name = "ammonia")
    private Double ammonia;

    @Column(name = "cod")
    private Double cod;

    @Column(name = "conductivity")
    private Double conductivity;

    @Column(name = "hardness")
    private Double hardness;

    @Column(name = "unit_cost")
    private Double unitCost;

    @Column(name = "max_supply")
    private Double maxSupply;

    @Column(name = "current_supply")
    private Double currentSupply;

    @Column(name = "min_allocation_ratio")
    private Double minAllocationRatio = 0.0;

    @Column(name = "max_allocation_ratio")
    private Double maxAllocationRatio = 1.0;

    @Column(name = "max_change_rate_per_hour")
    private Double maxChangeRatePerHour = 0.3;

    @Column(name = "switch_delay_minutes")
    private Integer switchDelayMinutes = 60;

    @Column(name = "last_ratio_change_time")
    private Instant lastRatioChangeTime;

    @Column(name = "is_active")
    private Boolean isActive = true;

    public WaterSource() {}

    public WaterSource(Instant time, String sourceCode, String sourceName, String sourceType) {
        this.time = time;
        this.sourceCode = sourceCode;
        this.sourceName = sourceName;
        this.sourceType = sourceType;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Instant getTime() { return time; }
    public void setTime(Instant time) { this.time = time; }
    public String getSourceCode() { return sourceCode; }
    public void setSourceCode(String sourceCode) { this.sourceCode = sourceCode; }
    public String getSourceName() { return sourceName; }
    public void setSourceName(String sourceName) { this.sourceName = sourceName; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public Double getTurbidity() { return turbidity; }
    public void setTurbidity(Double turbidity) { this.turbidity = turbidity; }
    public Double getPh() { return ph; }
    public void setPh(Double ph) { this.ph = ph; }
    public Double getTemperature() { return temperature; }
    public void setTemperature(Double temperature) { this.temperature = temperature; }
    public Double getAmmonia() { return ammonia; }
    public void setAmmonia(Double ammonia) { this.ammonia = ammonia; }
    public Double getCod() { return cod; }
    public void setCod(Double cod) { this.cod = cod; }
    public Double getConductivity() { return conductivity; }
    public void setConductivity(Double conductivity) { this.conductivity = conductivity; }
    public Double getHardness() { return hardness; }
    public void setHardness(Double hardness) { this.hardness = hardness; }
    public Double getUnitCost() { return unitCost; }
    public void setUnitCost(Double unitCost) { this.unitCost = unitCost; }
    public Double getMaxSupply() { return maxSupply; }
    public void setMaxSupply(Double maxSupply) { this.maxSupply = maxSupply; }
    public Double getCurrentSupply() { return currentSupply; }
    public void setCurrentSupply(Double currentSupply) { this.currentSupply = currentSupply; }
    public Double getMinAllocationRatio() { return minAllocationRatio; }
    public void setMinAllocationRatio(Double minAllocationRatio) { this.minAllocationRatio = minAllocationRatio; }
    public Double getMaxAllocationRatio() { return maxAllocationRatio; }
    public void setMaxAllocationRatio(Double maxAllocationRatio) { this.maxAllocationRatio = maxAllocationRatio; }
    public Double getMaxChangeRatePerHour() { return maxChangeRatePerHour; }
    public void setMaxChangeRatePerHour(Double maxChangeRatePerHour) { this.maxChangeRatePerHour = maxChangeRatePerHour; }
    public Integer getSwitchDelayMinutes() { return switchDelayMinutes; }
    public void setSwitchDelayMinutes(Integer switchDelayMinutes) { this.switchDelayMinutes = switchDelayMinutes; }
    public Instant getLastRatioChangeTime() { return lastRatioChangeTime; }
    public void setLastRatioChangeTime(Instant lastRatioChangeTime) { this.lastRatioChangeTime = lastRatioChangeTime; }
    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }
}
