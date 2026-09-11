package com.damonskappel.flighttracker.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "aircraft")
public class Aircraft {

    @Id
    @Column(name = "icao24", length = 6, nullable = false)
    private String icao24;

    @Column(name = "callsign", length = 10)
    private String callsign;

    @Column(name = "origin_country")
    private String originCountry;

    @Column(name = "last_seen")
    private Instant lastSeen;

    @OneToMany(mappedBy = "aircraft", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<PositionSnapshot> positionSnapshots = new ArrayList<>();

    public Aircraft() {}

    public Aircraft(String icao24) {
        this.icao24 = icao24;
    }

    // Getters and setters
    public String getIcao24() { return icao24; }
    public void setIcao24(String icao24) { this.icao24 = icao24; }

    public String getCallsign() { return callsign; }
    public void setCallsign(String callsign) { this.callsign = callsign; }

    public String getOriginCountry() { return originCountry; }
    public void setOriginCountry(String originCountry) { this.originCountry = originCountry; }

    public Instant getLastSeen() { return lastSeen; }
    public void setLastSeen(Instant lastSeen) { this.lastSeen = lastSeen; }

    public List<PositionSnapshot> getPositionSnapshots() { return positionSnapshots; }
    public void setPositionSnapshots(List<PositionSnapshot> positionSnapshots) {
        this.positionSnapshots = positionSnapshots;
    }
}