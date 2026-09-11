package com.damonskappel.flighttracker.controller;

import com.damonskappel.flighttracker.dto.FlightHistoryResponse;
import com.damonskappel.flighttracker.dto.FlightResponse;
import com.damonskappel.flighttracker.dto.StatsResponse;
import com.damonskappel.flighttracker.service.FlightQueryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/flights")
public class FlightController {

    private final FlightQueryService flightQueryService;

    public FlightController(FlightQueryService flightQueryService) {
        this.flightQueryService = flightQueryService;
    }

    @GetMapping
    public ResponseEntity<List<FlightResponse>> getActiveFlights() {
        List<FlightResponse> flights = flightQueryService.getActiveFlights();
        return ResponseEntity.ok(flights);
    }

    @GetMapping("/{icao24}")
    public ResponseEntity<FlightResponse> getFlightByIcao24(
            @PathVariable String icao24) {
        Optional<FlightResponse> flight =
                flightQueryService.getFlightByIcao24(icao24.toLowerCase());
        return flight.map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{icao24}/history")
    public ResponseEntity<List<FlightHistoryResponse>> getFlightHistory(
            @PathVariable String icao24,
            @RequestParam(required = false, defaultValue = "50") int limit) {
        List<FlightHistoryResponse> history =
                flightQueryService.getFlightHistory(icao24.toLowerCase(), limit);
        if (history.isEmpty()) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(history);
    }

    @GetMapping("/area")
    public ResponseEntity<List<FlightResponse>> getFlightsInArea(
            @RequestParam Double lat,
            @RequestParam Double lon,
            @RequestParam(required = false, defaultValue = "50") Double radius) {
        List<FlightResponse> flights =
                flightQueryService.getFlightsInArea(lat, lon, radius);
        return ResponseEntity.ok(flights);
    }
}