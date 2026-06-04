package com.water.dosing.entity;

import javax.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "chemical_consumption")
public class ChemicalConsumption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "time", nullable = false)
    private Instant time;

    @Column(name = "chemical_code", nullable = false, length = 32)
    private String chemicalCode;

    @Column(name = "chemical_name", length = 64)
    private String chemicalName;

    @Column(name = "consumption_qty")
    private Double consumptionQty;

    @Column(name = "consumption_unit", length = 16)
    private String consumptionUnit;

    @Column(name = "dosing_stage", length = 32)
    private String dosingStage;

    @Column(name = "flow_rate")
    private Double flowRate;

    @Column(name = "dosing_concentration")
    private Double dosingConcentration;

    @Column(name = "turbidity_in")
    private Double turbidityIn;

    @Column(name = "turbidity_out")
    private Double turbidityOut;

    @Column(name = "water_temp")
    private Double waterTemp;

    @Column(name = "ph")
    private Double ph;

    @Column(name = "is_predicted", nullable = false)
    private Boolean isPredicted = false;

    public ChemicalConsumption() {}

    public ChemicalConsumption(Instant time, String chemicalCode, String chemicalName) {
        this.time = time;
        this.chemicalCode = chemicalCode;
        this.chemicalName = chemicalName;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Instant getTime() { return time; }
    public void setTime(Instant time) { this.time = time; }
    public String getChemicalCode() { return chemicalCode; }
    public void setChemicalCode(String chemicalCode) { this.chemicalCode = chemicalCode; }
    public String getChemicalName() { return chemicalName; }
    public void setChemicalName(String chemicalName) { this.chemicalName = chemicalName; }
    public Double getConsumptionQty() { return consumptionQty; }
    public void setConsumptionQty(Double consumptionQty) { this.consumptionQty = consumptionQty; }
    public String getConsumptionUnit() { return consumptionUnit; }
    public void setConsumptionUnit(String consumptionUnit) { this.consumptionUnit = consumptionUnit; }
    public String getDosingStage() { return dosingStage; }
    public void setDosingStage(String dosingStage) { this.dosingStage = dosingStage; }
    public Double getFlowRate() { return flowRate; }
    public void setFlowRate(Double flowRate) { this.flowRate = flowRate; }
    public Double getDosingConcentration() { return dosingConcentration; }
    public void setDosingConcentration(Double dosingConcentration) { this.dosingConcentration = dosingConcentration; }
    public Double getTurbidityIn() { return turbidityIn; }
    public void setTurbidityIn(Double turbidityIn) { this.turbidityIn = turbidityIn; }
    public Double getTurbidityOut() { return turbidityOut; }
    public void setTurbidityOut(Double turbidityOut) { this.turbidityOut = turbidityOut; }
    public Double getWaterTemp() { return waterTemp; }
    public void setWaterTemp(Double waterTemp) { this.waterTemp = waterTemp; }
    public Double getPh() { return ph; }
    public void setPh(Double ph) { this.ph = ph; }
    public Boolean getIsPredicted() { return isPredicted; }
    public void setIsPredicted(Boolean isPredicted) { this.isPredicted = isPredicted; }
}
