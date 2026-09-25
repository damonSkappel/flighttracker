package com.damonskappel.flighttracker.dto;

public class AircraftTypeResponse {

    private String icao24;
    /** ICAO type designator, e.g. B738. */
    private String typecode;
    private String manufacturer;
    private String model;
    private String registration;
    private String operator;

    public AircraftTypeResponse(String icao24, String typecode, String manufacturer,
                                String model, String registration, String operator) {
        this.icao24 = icao24;
        this.typecode = typecode;
        this.manufacturer = manufacturer;
        this.model = model;
        this.registration = registration;
        this.operator = operator;
    }

    public String getIcao24() { return icao24; }
    public String getTypecode() { return typecode; }
    public String getManufacturer() { return manufacturer; }
    public String getModel() { return model; }
    public String getRegistration() { return registration; }
    public String getOperator() { return operator; }
}
