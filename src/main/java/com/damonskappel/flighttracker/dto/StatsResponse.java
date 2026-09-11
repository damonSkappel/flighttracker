package com.damonskappel.flighttracker.dto;

public class StatsReponse {

    private long totalAircraft;
    private long totalSnapshots;
    private String oldestSnapshot;
    private String newestSnapshot;

    public StatsReponse(long totalAircraft, long totalSnapshots, String oldestSnapshot, String newestSnapshot) {

        this.totalAircraft = totalAircraft;
        this.totalSnapshots = totalSnapshots;
        this.oldestSnapshot = oldestSnapshot;
        this.newestSnapshot = newestSnapshot;
    }

    public long getTotalAircraft() { return totalAircraft; }
    public long getTotalSnapshots() { return totalSnapshots; }
    public String getOldestSnapshot() { return oldestSnapshot; }
    public String getNewestSnapshot() { return newestSnapshot; }
}
