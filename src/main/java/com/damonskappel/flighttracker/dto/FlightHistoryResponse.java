package com.damonskappel.flighttracker.dto;

public class FlightHistoryResponse {

    /** When our ingest job wrote this row (ISO-8601 UTC). */
    private String timestamp;
    /** Unix epoch seconds of the aircraft's own position report. */
    private Long timePosition;
    private Double latitude;
    private Double longitude;
    private Double altitudeFeet;
    private Double velocityKnots;
    private Double heading;
    private Double verticalRate;
    private Boolean onGround;

    public FlightHistoryResponse(String timestamp, Long timePosition, Double latitude,
                                 Double longitude, Double altitudeFeet, Double velocityKnots,
                                 Double heading, Double verticalRate, Boolean onGround) {
        this.timestamp = timestamp;
        this.timePosition = timePosition;
        this.latitude = latitude;
        this.longitude = longitude;
        this.altitudeFeet = altitudeFeet;
        this.velocityKnots = velocityKnots;
        this.heading = heading;
        this.verticalRate = verticalRate;
        this.onGround = onGround;
    }

    public String getTimestamp() { return timestamp; }
    public Long getTimePosition() { return timePosition; }
    public Double getLatitude() { return latitude; }
    public Double getLongitude() { return longitude; }
    public Double getAltitudeFeet() { return altitudeFeet; }
    public Double getVelocityKnots() { return velocityKnots; }
    public Double getHeading() { return heading; }
    public Double getVerticalRate() { return verticalRate; }
    public Boolean getOnGround() { return onGround; }
}
