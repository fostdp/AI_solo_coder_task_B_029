package com.water.dosing.entity;

import javax.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "model_state")
public class ModelState {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "model_type", nullable = false, length = 32)
    private String modelType;

    @Column(name = "coefficients", columnDefinition = "TEXT")
    private String coefficients;

    @Column(name = "intercept")
    private Double intercept;

    @Column(name = "feature_means", columnDefinition = "TEXT")
    private String featureMeans;

    @Column(name = "feature_stds", columnDefinition = "TEXT")
    private String featureStds;

    @Column(name = "r_squared")
    private Double rSquared;

    @Column(name = "mae")
    private Double mae;

    public ModelState() {}

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public String getModelType() { return modelType; }
    public void setModelType(String modelType) { this.modelType = modelType; }
    public String getCoefficients() { return coefficients; }
    public void setCoefficients(String coefficients) { this.coefficients = coefficients; }
    public Double getIntercept() { return intercept; }
    public void setIntercept(Double intercept) { this.intercept = intercept; }
    public String getFeatureMeans() { return featureMeans; }
    public void setFeatureMeans(String featureMeans) { this.featureMeans = featureMeans; }
    public String getFeatureStds() { return featureStds; }
    public void setFeatureStds(String featureStds) { this.featureStds = featureStds; }
    public Double getRSquared() { return rSquared; }
    public void setRSquared(Double rSquared) { this.rSquared = rSquared; }
    public Double getMae() { return mae; }
    public void setMae(Double mae) { this.mae = mae; }
}
