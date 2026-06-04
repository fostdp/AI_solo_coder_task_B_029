package com.water.dosing.entity;

import javax.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "water_distribution")
public class WaterDistribution {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "time", nullable = false)
    private Instant time;

    @Column(name = "source_code", nullable = false, length = 32)
    private String sourceCode;

    @Column(name = "source_name", length = 64)
    private String sourceName;

    @Column(name = "allocation_ratio")
    private Double allocationRatio;

    @Column(name = "allocation_volume")
    private Double allocationVolume;

    @Column(name = "unit_cost")
    private Double unitCost;

    @Column(name = "total_cost")
    private Double totalCost;

    @Column(name = "mix_turbidity")
    private Double mixTurbidity;

    @Column(name = "mix_ph")
    private Double mixPh;

    @Column(name = "optimization_id", length = 64)
    private String optimizationId;

    @Column(name = "is_current", nullable = false)
    private Boolean isCurrent = false;

    public WaterDistribution() {}

    public WaterDistribution(Instant time, String sourceCode, String sourceName) {
        this.time = time;
        this.sourceCode = sourceCode;
        this.sourceName = sourceName;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Instant getTime() { return time; }
    public void setTime(Instant time) { this.time = time; }
    public String getSourceCode() { return sourceCode; }
    public void setSourceCode(String sourceCode) { this.sourceCode = sourceCode; }
    public String getSourceName() { return sourceName; }
    public void setSourceName(String sourceName) { this.sourceName = sourceName; }
    public Double getAllocationRatio() { return allocationRatio; }
    public void setAllocationRatio(Double allocationRatio) { this.allocationRatio = allocationRatio; }
    public Double getAllocationVolume() { return allocationVolume; }
    public void setAllocationVolume(Double allocationVolume) { this.allocationVolume = allocationVolume; }
    public Double getUnitCost() { return unitCost; }
    public void setUnitCost(Double unitCost) { this.unitCost = unitCost; }
    public Double getTotalCost() { return totalCost; }
    public void setTotalCost(Double totalCost) { this.totalCost = totalCost; }
    public Double getMixTurbidity() { return mixTurbidity; }
    public void setMixTurbidity(Double mixTurbidity) { this.mixTurbidity = mixTurbidity; }
    public Double getMixPh() { return mixPh; }
    public void setMixPh(Double mixPh) { this.mixPh = mixPh; }
    public String getOptimizationId() { return optimizationId; }
    public void setOptimizationId(String optimizationId) { this.optimizationId = optimizationId; }
    public Boolean getIsCurrent() { return isCurrent; }
    public void setIsCurrent(Boolean isCurrent) { this.isCurrent = isCurrent; }
}
