package com.water.dosing.entity;

import javax.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "dosing_record")
public class DosingRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "time", nullable = false)
    private Instant time;

    @Column(name = "stage", nullable = false, length = 32)
    private String stage;

    @Column(name = "coagulant_dose")
    private Double coagulantDose;

    @Column(name = "chlorine_dose")
    private Double chlorineDose;

    @Column(name = "actual_dose")
    private Double actualDose;

    @Column(name = "predicted_dose")
    private Double predictedDose;

    @Column(name = "deviation_pct")
    private Double deviationPct;

    public DosingRecord() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Instant getTime() { return time; }
    public void setTime(Instant time) { this.time = time; }
    public String getStage() { return stage; }
    public void setStage(String stage) { this.stage = stage; }
    public Double getCoagulantDose() { return coagulantDose; }
    public void setCoagulantDose(Double coagulantDose) { this.coagulantDose = coagulantDose; }
    public Double getChlorineDose() { return chlorineDose; }
    public void setChlorineDose(Double chlorineDose) { this.chlorineDose = chlorineDose; }
    public Double getActualDose() { return actualDose; }
    public void setActualDose(Double actualDose) { this.actualDose = actualDose; }
    public Double getPredictedDose() { return predictedDose; }
    public void setPredictedDose(Double predictedDose) { this.predictedDose = predictedDose; }
    public Double getDeviationPct() { return deviationPct; }
    public void setDeviationPct(Double deviationPct) { this.deviationPct = deviationPct; }
}
