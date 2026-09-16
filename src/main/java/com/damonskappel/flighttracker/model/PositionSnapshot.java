package com.damonskappel.flighttracker.model;

import jakarta.persistence.*;
import java.time.Instant;
import jakarta.persistence.Index;

@Entity
@Table(name = "position_snapshots",
indexes = @Index(name = "idx_snapshot_timestamp", columnList = "timestamp"))

public class PositionSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "icao24", nullable = false)
    private Aircraft aircraft;

    @Column(name = "timestamp", nullable = false)
    private Instant timestamp;

    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "longitude")
    private Double longitude;

    @Column(name = "baro_altitude")
    private Double baroAltitude;

    @Column(name = "velocity")
    private Double velocity;

    @Column(name = "heading")
    private Double heading;

    @Column(name = "vertical_rate")
    private Double verticalRate;

    @Column(name = "on_ground")
    private Boolean onGround;

    public PositionSnapshot() {}

    // Getters and setters
    public Long getId() { return id; }

    public Aircraft getAircraft() { return aircraft; }
    public void setAircraft(Aircraft aircraft) { this.aircraft = aircraft; }

    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }

    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }

    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }

    public Double getBaroAltitude() { return baroAltitude; }
    public void setBaroAltitude(Double baroAltitude) { this.baroAltitude = baroAltitude; }

    public Double getVelocity() { return velocity; }
    public void setVelocity(Double velocity) { this.velocity = velocity; }

    public Double getHeading() { return heading; }
    public void setHeading(Double heading) { this.heading = heading; }

    public Double getVerticalRate() { return verticalRate; }
    public void setVerticalRate(Double verticalRate) { this.verticalRate = verticalRate; }

    public Boolean getOnGround() { return onGround; }
    public void setOnGround(Boolean onGround) { this.onGround = onGround; }
}