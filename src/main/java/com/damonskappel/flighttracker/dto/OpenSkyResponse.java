package com.damonskappel.flighttracker.dto;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class OpenSkyResponse {
    @JsonProperty("time")
    private long time;

    @JsonProperty("states")
    private List<List<Object>> states;

    public long getTime() {return time; }
    public void setTime(long time) { this.time = time; }

    public List<List<Object>> getStates() {return states; }
    public void setStates(List<List<Object>> states) { this.states = states; }
}
