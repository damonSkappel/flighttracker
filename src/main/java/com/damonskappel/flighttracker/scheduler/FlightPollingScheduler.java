package com.damonskappel.flighttracker.scheduler;

import com.damonskappel.flighttracker.dto.OpenSkyStateVector;
import com.damonskappel.flighttracker.service.FlightIngestionService;
import com.damonskappel.flighttracker.service.OpenSkyClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.List;

@Component
public class FlightPollingScheduler {

    private static final Logger log = LoggerFactory.getLogger(FlightPollingScheduler.class);

    private final OpenSkyClient openSkyClient;
    private final FlightIngestionService flightIngestionService;

    public FlightPollingScheduler(OpenSkyClient openSkyClient, FlightIngestionService flightIngestionService) {

        this.openSkyClient = openSkyClient;
        this.flightIngestionService = flightIngestionService;
    }

    @Scheduled(fixedDelay = 15000, initialDelay = 5000)
    public void poll() {
        log.info("Starting OpenSky poll cycle");

        List<OpenSkyStateVector> states = openSkyClient.fetchCurrentStates();

        if (states.isEmpty()) {
            log.warn("No states returned from OpenSky, skipping ingestion");
            return;
        }

        flightIngestionService.ingest(states);
    }
}
