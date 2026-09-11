package com.damonskappel.flighttracker.dto;

public class FlightHistoryResponse {

    private String timestamp;
    private Double latitude;
    private Double longitude;
    private Double altitudeFeet;
    private Double velocityKnots;
    private Double heading;
    private Double verticalRate;
    private Boolean onGround;

    public FlightHistoryResponse(String timestamp, Double latitude, Double longitude, Double altitudeFeet, Double velocityKnots, Double heading, Double verticalRate, Boolean onGround){
        this.timestamp = timestamp;
        this.latitude = latitude;
        this.longitude = longitude;
        this.altitudeFeet = altitudeFeet;
        this.velocityKnots = velocityKnots;
        this.heading = heading;
        this.verticalRate = verticalRate;
        this.onGround = onGround;
    }

    public String getTimestamp() { return timestamp; }
    public Double getLatitude() { return latitude; }
    public Double getLongitude() { return longitude; }
    public Double getAltitudeFeet() { return altitudeFeet; }
    public Double getVelocityKnots() { return velocityKnots; }
    public Double getHeading() { return heading; }
    public Double getVerticalRate() { return verticalRate; }
    public Boolean getOnGround() { return onGround; }
}
