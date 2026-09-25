package com.damonskappel.flighttracker.model;

import jakarta.persistence.*;

/**
 * What an airframe is, from the OpenSky aircraft database. Reference data keyed
 * on the transponder address: it has no relation to {@link Aircraft}, so the
 * hourly cleanup never touches it and an aircraft's type is already known the
 * first time it is seen.
 *
 * <p>Written only by {@code AircraftTypeImporter}, through JDBC batches.
 */
@Entity
@Table(name = "aircraft_type")
public class AircraftType {

    @Id
    @Column(name = "icao24", length = 6, nullable = false)
    private String icao24;

    /** ICAO type designator, e.g. B738 or A388. */
    @Column(name = "typecode", length = 10)
    private String typecode;

    @Column(name = "manufacturer", length = 100)
    private String manufacturer;

    /** The airframe's own model string, e.g. "A380 861". Often blank. */
    @Column(name = "model", length = 100)
    private String model;

    @Column(name = "registration", length = 20)
    private String registration;

    @Column(name = "operator", length = 100)
    private String operator;

    public AircraftType() {}

    public String getIcao24() { return icao24; }
    public String getTypecode() { return typecode; }
    public String getManufacturer() { return manufacturer; }
    public String getModel() { return model; }
    public String getRegistration() { return registration; }
    public String getOperator() { return operator; }
}
