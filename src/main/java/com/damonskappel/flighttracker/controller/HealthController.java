package com.damonskappel.flighttracker.controller;

import com.damonskappel.flighttracker.dto.StatsResponse;
import com.damonskappel.flighttracker.service.FlightQueryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    private final FlightQueryService flightQueryService;

    public HealthController(FlightQueryService flightQueryService) {
        this.flightQueryService = flightQueryService;
    }

    @GetMapping("/stats")
    public ResponseEntity<StatsResponse> getStats() {
        return ResponseEntity.ok(flightQueryService.getStats());
    }
}
