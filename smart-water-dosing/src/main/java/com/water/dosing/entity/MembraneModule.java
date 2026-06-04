package com.water.dosing.entity;

import javax.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "membrane_module")
public class MembraneModule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "time", nullable = false)
    private Instant time;

    @Column(name = "module_code", nullable = false, length = 32)
    private String moduleCode;

    @Column(name = "module_name", nullable = false, length = 64)
    private String moduleName;

    @Column(name = "membrane_type", length = 32)
    private String membraneType;

    @Column(name = "stage", length = 32)
    private String stage;

    @Column(name = "flux")
    private Double flux;

    @Column(name = "tmd")
    private Double tmd;

    @Column(name = "pressure_in")
    private Double pressureIn;

    @Column(name = "pressure_out")
    private Double pressureOut;

    @Column(name = "permeate_flow")
    private Double permeateFlow;

    @Column(name = "reject_flow")
    private Double rejectFlow;

    @Column(name = "recovery_rate")
    private Double recoveryRate;

    @Column(name = "fouling_index")
    private Double foulingIndex;

    @Column(name = "fouling_level")
    private Integer foulingLevel;

    @Column(name = "last_clean_time")
    private Instant lastCleanTime;

    @Column(name = "predicted_clean_days")
    private Integer predictedCleanDays;

    @Column(name = "clean_urgency")
    private String cleanUrgency;

    @Column(name = "cumulative_volume")
    private Double cumulativeVolume;

    @Column(name = "operating_hours")
    private Double operatingHours;

    @Column(name = "baseline_flux")
    private Double baselineFlux;

    @Column(name = "baseline_tmd")
    private Double baselineTmd;

    @Column(name = "membrane_serial_no")
    private String membraneSerialNo;

    @Column(name = "membrane_replace_time")
    private Instant membraneReplaceTime;

    @Column(name = "membrane_age_hours")
    private Double membraneAgeHours;

    @Column(name = "is_replaced_recently")
    private Boolean isReplacedRecently = false;

    @Column(name = "flux_change_rate")
    private Double fluxChangeRate;

    @Column(name = "is_active")
    private Boolean isActive = true;

    public MembraneModule() {}

    public MembraneModule(Instant time, String moduleCode, String moduleName, String membraneType) {
        this.time = time;
        this.moduleCode = moduleCode;
        this.moduleName = moduleName;
        this.membraneType = membraneType;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Instant getTime() { return time; }
    public void setTime(Instant time) { this.time = time; }
    public String getModuleCode() { return moduleCode; }
    public void setModuleCode(String moduleCode) { this.moduleCode = moduleCode; }
    public String getModuleName() { return moduleName; }
    public void setModuleName(String moduleName) { this.moduleName = moduleName; }
    public String getMembraneType() { return membraneType; }
    public void setMembraneType(String membraneType) { this.membraneType = membraneType; }
    public String getStage() { return stage; }
    public void setStage(String stage) { this.stage = stage; }
    public Double getFlux() { return flux; }
    public void setFlux(Double flux) { this.flux = flux; }
    public Double getTmd() { return tmd; }
    public void setTmd(Double tmd) { this.tmd = tmd; }
    public Double getPressureIn() { return pressureIn; }
    public void setPressureIn(Double pressureIn) { this.pressureIn = pressureIn; }
    public Double getPressureOut() { return pressureOut; }
    public void setPressureOut(Double pressureOut) { this.pressureOut = pressureOut; }
    public Double getPermeateFlow() { return permeateFlow; }
    public void setPermeateFlow(Double permeateFlow) { this.permeateFlow = permeateFlow; }
    public Double getRejectFlow() { return rejectFlow; }
    public void setRejectFlow(Double rejectFlow) { this.rejectFlow = rejectFlow; }
    public Double getRecoveryRate() { return recoveryRate; }
    public void setRecoveryRate(Double recoveryRate) { this.recoveryRate = recoveryRate; }
    public Double getFoulingIndex() { return foulingIndex; }
    public void setFoulingIndex(Double foulingIndex) { this.foulingIndex = foulingIndex; }
    public Integer getFoulingLevel() { return foulingLevel; }
    public void setFoulingLevel(Integer foulingLevel) { this.foulingLevel = foulingLevel; }
    public Instant getLastCleanTime() { return lastCleanTime; }
    public void setLastCleanTime(Instant lastCleanTime) { this.lastCleanTime = lastCleanTime; }
    public Integer getPredictedCleanDays() { return predictedCleanDays; }
    public void setPredictedCleanDays(Integer predictedCleanDays) { this.predictedCleanDays = predictedCleanDays; }
    public String getCleanUrgency() { return cleanUrgency; }
    public void setCleanUrgency(String cleanUrgency) { this.cleanUrgency = cleanUrgency; }
    public Double getCumulativeVolume() { return cumulativeVolume; }
    public void setCumulativeVolume(Double cumulativeVolume) { this.cumulativeVolume = cumulativeVolume; }
    public Double getOperatingHours() { return operatingHours; }
    public void setOperatingHours(Double operatingHours) { this.operatingHours = operatingHours; }
    public Double getBaselineFlux() { return baselineFlux; }
    public void setBaselineFlux(Double baselineFlux) { this.baselineFlux = baselineFlux; }
    public Double getBaselineTmd() { return baselineTmd; }
    public void setBaselineTmd(Double baselineTmd) { this.baselineTmd = baselineTmd; }
    public String getMembraneSerialNo() { return membraneSerialNo; }
    public void setMembraneSerialNo(String membraneSerialNo) { this.membraneSerialNo = membraneSerialNo; }
    public Instant getMembraneReplaceTime() { return membraneReplaceTime; }
    public void setMembraneReplaceTime(Instant membraneReplaceTime) { this.membraneReplaceTime = membraneReplaceTime; }
    public Double getMembraneAgeHours() { return membraneAgeHours; }
    public void setMembraneAgeHours(Double membraneAgeHours) { this.membraneAgeHours = membraneAgeHours; }
    public Boolean getIsReplacedRecently() { return isReplacedRecently; }
    public void setIsReplacedRecently(Boolean isReplacedRecently) { this.isReplacedRecently = isReplacedRecently; }
    public Double getFluxChangeRate() { return fluxChangeRate; }
    public void setFluxChangeRate(Double fluxChangeRate) { this.fluxChangeRate = fluxChangeRate; }
    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }
}
