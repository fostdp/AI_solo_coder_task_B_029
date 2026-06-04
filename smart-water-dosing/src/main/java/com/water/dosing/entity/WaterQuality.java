package com.water.dosing.entity;

import javax.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "water_quality")
public class WaterQuality {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "time", nullable = false)
    private Instant time;

    @Column(name = "stage", nullable = false, length = 32)
    private String stage;

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

    @Column(name = "flow_rate")
    private Double flowRate;

    @Column(name = "residual_chlorine")
    private Double residualChlorine;

    @Column(name = "coagulant_dose")
    private Double coagulantDose;

    public WaterQuality() {}

    public WaterQuality(Instant time, String stage, Double turbidity, Double ph,
                        Double temperature, Double ammonia, Double cod, Double flowRate) {
        this.time = time;
        this.stage = stage;
        this.turbidity = turbidity;
        this.ph = ph;
        this.temperature = temperature;
        this.ammonia = ammonia;
        this.cod = cod;
        this.flowRate = flowRate;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Instant getTime() { return time; }
    public void setTime(Instant time) { this.time = time; }
    public String getStage() { return stage; }
    public void setStage(String stage) { this.stage = stage; }
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
    public Double getFlowRate() { return flowRate; }
    public void setFlowRate(Double flowRate) { this.flowRate = flowRate; }
    public Double getResidualChlorine() { return residualChlorine; }
    public void setResidualChlorine(Double residualChlorine) { this.residualChlorine = residualChlorine; }
    public Double getCoagulantDose() { return coagulantDose; }
    public void setCoagulantDose(Double coagulantDose) { this.coagulantDose = coagulantDose; }
}
