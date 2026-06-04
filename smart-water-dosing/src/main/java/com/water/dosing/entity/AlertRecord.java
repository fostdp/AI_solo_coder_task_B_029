package com.water.dosing.entity;

import javax.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "alert_record")
public class AlertRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "time", nullable = false)
    private Instant time;

    @Column(name = "level", nullable = false)
    private Short level;

    @Column(name = "type", nullable = false, length = 64)
    private String type;

    @Column(name = "message")
    private String message;

    @Column(name = "acknowledged")
    private Boolean acknowledged = false;

    public AlertRecord() {}

    public AlertRecord(Instant time, Short level, String type, String message) {
        this.time = time;
        this.level = level;
        this.type = type;
        this.message = message;
    }

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public Instant getTime() { return time; }
    public void setTime(Instant time) { this.time = time; }
    public Short getLevel() { return level; }
    public void setLevel(Short level) { this.level = level; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public Boolean getAcknowledged() { return acknowledged; }
    public void setAcknowledged(Boolean acknowledged) { this.acknowledged = acknowledged; }
}
