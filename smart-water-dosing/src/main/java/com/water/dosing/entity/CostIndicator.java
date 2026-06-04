package com.water.dosing.entity;

import javax.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "cost_indicator")
public class CostIndicator {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "time", nullable = false)
    private Instant time;

    @Column(name = "alum_consumption")
    private Double alumConsumption;

    @Column(name = "chlorine_consumption")
    private Double chlorineConsumption;

    @Column(name = "electricity_consumption")
    private Double electricityConsumption;

    public CostIndicator() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Instant getTime() { return time; }
    public void setTime(Instant time) { this.time = time; }
    public Double getAlumConsumption() { return alumConsumption; }
    public void setAlumConsumption(Double alumConsumption) { this.alumConsumption = alumConsumption; }
    public Double getChlorineConsumption() { return chlorineConsumption; }
    public void setChlorineConsumption(Double chlorineConsumption) { this.chlorineConsumption = chlorineConsumption; }
    public Double getElectricityConsumption() { return electricityConsumption; }
    public void setElectricityConsumption(Double electricityConsumption) { this.electricityConsumption = electricityConsumption; }
}
