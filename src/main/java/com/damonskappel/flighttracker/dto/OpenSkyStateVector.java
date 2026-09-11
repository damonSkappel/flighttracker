package com.damonskappel.flighttracker.dto;

import java.util.List;

public class OpenSkyStateVector {
    private String icao24;
    private String callsign;
    private String originCountry;
    private Double latitude;
    private Double longitude;
    private Double baroAltitude;
    private Boolean onGround;
    private Double velocity;
    private Double heading;
    private Double verticalRate;

    public static OpenSkyStateVector fromArray(List<Object> raw) {
        OpenSkyStateVector sv = new OpenSkyStateVector();

        sv.icao24 = (String) raw.get(0);
        sv.callsign = raw.get(1) != null ? ((String) raw.get(1)).trim() : null;
        sv.originCountry = (String) raw.get(2);
        sv.longitude = raw.get(5) != null ? ((Number) raw.get(5)).doubleValue() : null;
        sv.latitude = raw.get(6) != null ? ((Number) raw.get(6)).doubleValue() : null;
        sv.baroAltitude = raw.get(7) != null ? ((Number) raw.get(7)).doubleValue() : null;
        sv.onGround = (Boolean) raw.get(8);
        sv.velocity = raw.get(9) != null ? ((Number)raw.get(9)).doubleValue() : null;
        sv.heading = raw.get(10) != null ? ((Number)raw.get(10)).doubleValue() : null;
        sv.verticalRate = raw.get(11) !=null ? ((Number)raw.get(11)).doubleValue() : null;

        return sv;
    }
    public String getIcao24() { return icao24; }
    public String getCallsign() { return callsign; }
    public String getOriginCountry() { return originCountry; }
    public Double getLatitude() { return latitude; }
    public Double getLongitude() { return longitude; }
    public Double getBaroAltitude() { return baroAltitude; }
    public Boolean getOnGround() { return onGround; }
    public Double getVelocity() { return velocity; }
    public Double getHeading() { return heading; }
    public Double getVerticalRate() { return verticalRate; }

}
