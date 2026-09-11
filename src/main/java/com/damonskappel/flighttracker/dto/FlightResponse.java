package com.damonskappel.flighttracker.dto;

public class FlightResponse {

    private String icao24;
    private String callsign;
    private String originCountry;
    private Double latitude;
    private Double longitude;
    private Double altitudeFeet;
    private Double velocityKnots;
    private Double heading;
    private Double verticalRate;
    private Boolean onGround;
    private String lastSeen;

    //Constructor
    public FlightResponse(String icao24, String callsign, String originCountry
                            , Double latitude, Double longitude, Double altitudeFeet
                            ,  Double velocityKnots, Double heading, Double verticalRate
                            ,  Boolean onGround, String lastSeen) {
        this.icao24 = icao24;
        this.callsign = callsign;
        this.originCountry = originCountry;
        this.latitude = latitude;
        this.longitude = longitude;
        this.altitudeFeet = altitudeFeet;
        this.velocityKnots = velocityKnots;
        this.heading = heading;
        this.verticalRate = verticalRate;
        this.onGround = onGround;
        this.lastSeen = lastSeen;
    }

    public String getIcao24() { return icao24; }
    public String getCallsign() { return callsign; }
    public String getOriginCountry() { return originCountry; }
    public Double getLatitude() { return latitude; }
    public Double getLongitude() { return longitude; }
    public Double getAltitudeFeet() { return altitudeFeet; }
    public Double getVelocityKnots() { return velocityKnots; }
    public Double getHeading() { return heading; }
    public Double getVerticalRate() { return verticalRate; }
    public Boolean getOnGround() { return onGround; }
    public String getLastSeen() { return lastSeen; }
}
