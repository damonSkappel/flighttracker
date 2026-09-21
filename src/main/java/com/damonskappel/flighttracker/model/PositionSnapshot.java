package com.damonskappel.flighttracker.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "position_snapshots",
        indexes = {
                @Index(name = "idx_snapshot_timestamp", columnList = "timestamp"),
                // Supports "latest snapshot per aircraft" and the history lookups.
                // Postgres does not index FK columns automatically, and without
                // this both degrade to full table scans.
                @Index(name = "idx_snapshot_icao24_timestamp", columnList = "icao24, timestamp")
        })
public class PositionSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "icao24", nullable = false)
    private Aircraft aircraft;

    /** When this row was written by the ingest job — NOT when the aircraft reported. */
    @Column(name = "timestamp", nullable = false)
    private Instant timestamp;

    /**
     * OpenSky "time_position": when the aircraft actually reported this position.
     * This is the epoch dead reckoning must extrapolate from. Null when the
     * aircraft has never sent a position.
     */
    @Column(name = "time_position")
    private Instant timePosition;

    /** OpenSky "last_contact": last time any signal was received from the aircraft. */
    @Column(name = "last_contact")
    private Instant lastContact;

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

    public Instant getTimePosition() { return timePosition; }
    public void setTimePosition(Instant timePosition) { this.timePosition = timePosition; }

    public Instant getLastContact() { return lastContact; }
    public void setLastContact(Instant lastContact) { this.lastContact = lastContact; }

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
