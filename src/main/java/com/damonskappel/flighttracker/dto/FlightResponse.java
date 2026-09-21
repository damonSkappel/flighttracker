package com.damonskappel.flighttracker.dto;

public class FlightResponse {

    private String icao24;
    private String callsign;
    private String originCountry;
    private Double latitude;
    private Double longitude;
    private Double altitudeFeet;
    private Double velocityKnots;
    /** Raw ground speed in m/s, so clients extrapolating position need not un-convert. */
    private Double velocityMps;
    private Double heading;
    private Double verticalRate;
    private Boolean onGround;
    /**
     * Unix epoch seconds of the aircraft's own position report — the epoch any
     * dead-reckoning extrapolation must measure elapsed time from. Null when the
     * aircraft never reported one.
     */
    private Long timePosition;
    /** Unix epoch seconds of last contact of any kind with the aircraft. */
    private Long lastContact;
    /** When our ingest job last saw this airframe (ISO-8601 UTC). */
    private String lastSeen;

    public FlightResponse(String icao24, String callsign, String originCountry,
                          Double latitude, Double longitude, Double altitudeFeet,
                          Double velocityKnots, Double velocityMps, Double heading,
                          Double verticalRate, Boolean onGround,
                          Long timePosition, Long lastContact, String lastSeen) {
        this.icao24 = icao24;
        this.callsign = callsign;
        this.originCountry = originCountry;
        this.latitude = latitude;
        this.longitude = longitude;
        this.altitudeFeet = altitudeFeet;
        this.velocityKnots = velocityKnots;
        this.velocityMps = velocityMps;
        this.heading = heading;
        this.verticalRate = verticalRate;
        this.onGround = onGround;
        this.timePosition = timePosition;
        this.lastContact = lastContact;
        this.lastSeen = lastSeen;
    }

    public String getIcao24() { return icao24; }
    public String getCallsign() { return callsign; }
    public String getOriginCountry() { return originCountry; }
    public Double getLatitude() { return latitude; }
    public Double getLongitude() { return longitude; }
    public Double getAltitudeFeet() { return altitudeFeet; }
    public Double getVelocityKnots() { return velocityKnots; }
    public Double getVelocityMps() { return velocityMps; }
    public Double getHeading() { return heading; }
    public Double getVerticalRate() { return verticalRate; }
    public Boolean getOnGround() { return onGround; }
    public Long getTimePosition() { return timePosition; }
    public Long getLastContact() { return lastContact; }
    public String getLastSeen() { return lastSeen; }
}
