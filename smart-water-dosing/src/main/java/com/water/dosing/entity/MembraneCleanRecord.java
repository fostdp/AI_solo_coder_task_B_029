package com.water.dosing.entity;

import javax.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "membrane_clean_record")
public class MembraneCleanRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "clean_time", nullable = false)
    private Instant cleanTime;

    @Column(name = "module_code", nullable = false, length = 32)
    private String moduleCode;

    @Column(name = "module_name", length = 64)
    private String moduleName;

    @Column(name = "clean_type", length = 32)
    private String cleanType;

    @Column(name = "clean_reason", length = 256)
    private String cleanReason;

    @Column(name = "chemical_type", length = 64)
    private String chemicalType;

    @Column(name = "chemical_dosage")
    private Double chemicalDosage;

    @Column(name = "clean_duration_minutes")
    private Integer cleanDurationMinutes;

    @Column(name = "temperature")
    private Double temperature;

    @Column(name = "ph")
    private Double ph;

    @Column(name = "flux_before")
    private Double fluxBefore;

    @Column(name = "flux_after")
    private Double fluxAfter;

    @Column(name = "flux_recovery_pct")
    private Double fluxRecoveryPct;

    @Column(name = "tmd_before")
    private Double tmdBefore;

    @Column(name = "tmd_after")
    private Double tmdAfter;

    @Column(name = "operator", length = 64)
    private String operator;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "is_scheduled")
    private Boolean isScheduled = false;

    public MembraneCleanRecord() {}

    public MembraneCleanRecord(Instant cleanTime, String moduleCode, String cleanType) {
        this.cleanTime = cleanTime;
        this.moduleCode = moduleCode;
        this.cleanType = cleanType;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Instant getCleanTime() { return cleanTime; }
    public void setCleanTime(Instant cleanTime) { this.cleanTime = cleanTime; }
    public String getModuleCode() { return moduleCode; }
    public void setModuleCode(String moduleCode) { this.moduleCode = moduleCode; }
    public String getModuleName() { return moduleName; }
    public void setModuleName(String moduleName) { this.moduleName = moduleName; }
    public String getCleanType() { return cleanType; }
    public void setCleanType(String cleanType) { this.cleanType = cleanType; }
    public String getCleanReason() { return cleanReason; }
    public void setCleanReason(String cleanReason) { this.cleanReason = cleanReason; }
    public String getChemicalType() { return chemicalType; }
    public void setChemicalType(String chemicalType) { this.chemicalType = chemicalType; }
    public Double getChemicalDosage() { return chemicalDosage; }
    public void setChemicalDosage(Double chemicalDosage) { this.chemicalDosage = chemicalDosage; }
    public Integer getCleanDurationMinutes() { return cleanDurationMinutes; }
    public void setCleanDurationMinutes(Integer cleanDurationMinutes) { this.cleanDurationMinutes = cleanDurationMinutes; }
    public Double getTemperature() { return temperature; }
    public void setTemperature(Double temperature) { this.temperature = temperature; }
    public Double getPh() { return ph; }
    public void setPh(Double ph) { this.ph = ph; }
    public Double getFluxBefore() { return fluxBefore; }
    public void setFluxBefore(Double fluxBefore) { this.fluxBefore = fluxBefore; }
    public Double getFluxAfter() { return fluxAfter; }
    public void setFluxAfter(Double fluxAfter) { this.fluxAfter = fluxAfter; }
    public Double getFluxRecoveryPct() { return fluxRecoveryPct; }
    public void setFluxRecoveryPct(Double fluxRecoveryPct) { this.fluxRecoveryPct = fluxRecoveryPct; }
    public Double getTmdBefore() { return tmdBefore; }
    public void setTmdBefore(Double tmdBefore) { this.tmdBefore = tmdBefore; }
    public Double getTmdAfter() { return tmdAfter; }
    public void setTmdAfter(Double tmdAfter) { this.tmdAfter = tmdAfter; }
    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public Boolean getIsScheduled() { return isScheduled; }
    public void setIsScheduled(Boolean isScheduled) { this.isScheduled = isScheduled; }
}
