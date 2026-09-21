package com.damonskappel.flighttracker.dto;

import java.time.Instant;
import java.util.List;

public class OpenSkyStateVector {
    private String icao24;
    private String callsign;
    private String originCountry;
    private Instant timePosition;
    private Instant lastContact;
    private Double latitude;
    private Double longitude;
    private Double baroAltitude;
    private Boolean onGround;
    private Double velocity;
    private Double heading;
    private Double verticalRate;

    /**
     * Maps one OpenSky state vector array. Index order is defined by the OpenSky
     * REST API and is positional, so these offsets must not be reordered:
     * 0 icao24, 1 callsign, 2 origin_country, 3 time_position, 4 last_contact,
     * 5 longitude, 6 latitude, 7 baro_altitude, 8 on_ground, 9 velocity,
     * 10 true_track, 11 vertical_rate.
     */
    public static OpenSkyStateVector fromArray(List<Object> raw) {
        OpenSkyStateVector sv = new OpenSkyStateVector();

        sv.icao24 = (String) raw.get(0);
        sv.callsign = raw.get(1) != null ? ((String) raw.get(1)).trim() : null;
        sv.originCountry = (String) raw.get(2);
        sv.timePosition = toInstant(raw.get(3));
        sv.lastContact = toInstant(raw.get(4));
        sv.longitude = raw.get(5) != null ? ((Number) raw.get(5)).doubleValue() : null;
        sv.latitude = raw.get(6) != null ? ((Number) raw.get(6)).doubleValue() : null;
        sv.baroAltitude = raw.get(7) != null ? ((Number) raw.get(7)).doubleValue() : null;
        sv.onGround = (Boolean) raw.get(8);
        sv.velocity = raw.get(9) != null ? ((Number) raw.get(9)).doubleValue() : null;
        sv.heading = raw.get(10) != null ? ((Number) raw.get(10)).doubleValue() : null;
        sv.verticalRate = raw.get(11) != null ? ((Number) raw.get(11)).doubleValue() : null;

        return sv;
    }

    /** OpenSky sends these as Unix epoch seconds. */
    private static Instant toInstant(Object value) {
        return value != null ? Instant.ofEpochSecond(((Number) value).longValue()) : null;
    }

    /** True when the aircraft reported a usable position. */
    public boolean hasPosition() {
        return latitude != null && longitude != null;
    }

    public String getIcao24() { return icao24; }
    public String getCallsign() { return callsign; }
    public String getOriginCountry() { return originCountry; }
    public Instant getTimePosition() { return timePosition; }
    public Instant getLastContact() { return lastContact; }
    public Double getLatitude() { return latitude; }
    public Double getLongitude() { return longitude; }
    public Double getBaroAltitude() { return baroAltitude; }
    public Boolean getOnGround() { return onGround; }
    public Double getVelocity() { return velocity; }
    public Double getHeading() { return heading; }
    public Double getVerticalRate() { return verticalRate; }
}
