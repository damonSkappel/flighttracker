package com.damonskappel.flighttracker;

import com.damonskappel.flighttracker.dto.OpenSkyStateVector;
import com.damonskappel.flighttracker.dto.StatsResponse;
import com.damonskappel.flighttracker.service.FlightQueryService;
import com.damonskappel.flighttracker.service.OpenSkyClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;


@RestController
public class HealthController {

    private final OpenSkyClient openSkyClient;
    private final FlightQueryService flightQueryService;

    public HealthController(OpenSkyClient openSkyClient, FlightQueryService flightQueryService) {
        this.openSkyClient = openSkyClient;
        this.flightQueryService = flightQueryService;
    }

    //adding comment so I can push to github
    @GetMapping("/stats")
    public ResponseEntity<StatsResponse> getStats() {
        return ResponseEntity.ok(flightQueryService.getStats());
    }
}
