package com.damonskappel.flighttracker.controller;

import com.damonskappel.flighttracker.dto.FlightHistoryResponse;
import com.damonskappel.flighttracker.dto.FlightResponse;
import com.damonskappel.flighttracker.service.FlightQueryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/flights")
public class FlightController {

    private static final int MAX_HISTORY_LIMIT = 500;
    private static final double MAX_RADIUS_MILES = 5000.0;

    private final FlightQueryService flightQueryService;

    public FlightController(FlightQueryService flightQueryService) {
        this.flightQueryService = flightQueryService;
    }

    @GetMapping
    public ResponseEntity<List<FlightResponse>> getActiveFlights() {
        return ResponseEntity.ok(flightQueryService.getActiveFlights());
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
        // Clamped rather than passed through: a negative limit reaches
        // Stream/Pageable as an illegal argument and surfaces as a 500.
        int safeLimit = Math.clamp(limit, 1, MAX_HISTORY_LIMIT);

        List<FlightHistoryResponse> history =
                flightQueryService.getFlightHistory(icao24.toLowerCase(), safeLimit);
        // An empty list is a valid answer for a known aircraft with no retained
        // history, so only the aircraft being unknown is a 404.
        return ResponseEntity.ok(history);
    }

    @GetMapping("/area")
    public ResponseEntity<List<FlightResponse>> getFlightsInArea(
            @RequestParam Double lat,
            @RequestParam Double lon,
            @RequestParam(required = false, defaultValue = "50") Double radius) {
        if (lat < -90 || lat > 90) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "lat must be between -90 and 90");
        }
        if (lon < -180 || lon > 180) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "lon must be between -180 and 180");
        }
        if (radius <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "radius must be positive");
        }

        double safeRadius = Math.min(radius, MAX_RADIUS_MILES);
        return ResponseEntity.ok(flightQueryService.getFlightsInArea(lat, lon, safeRadius));
    }
}
