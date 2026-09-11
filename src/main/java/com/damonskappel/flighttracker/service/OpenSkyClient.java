package com.damonskappel.flighttracker.service;
import com.damonskappel.flighttracker.dto.OpenSkyResponse;
import com.damonskappel.flighttracker.dto.OpenSkyStateVector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Collections;
import java.util.stream.Collectors;

@Service
public class OpenSkyClient {
    private static final Logger log = LoggerFactory.getLogger(OpenSkyClient.class);
    private static final String OPENSKY_URL = "https://opensky-network.org/api/states/all" +
            "?lamin=24.5&lomin=-125.0&lamax=49.5&lomax=-66.0";

    private final RestTemplate restTemplate;
    public OpenSkyClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public List<OpenSkyStateVector> fetchCurrentStates() {
        try{
            OpenSkyResponse response = restTemplate.getForObject(
                    OPENSKY_URL, OpenSkyResponse.class
            );
            if (response == null || response.getStates() == null) {
                log.warn("OpenSky returned null or empty response");
                return Collections.emptyList();
            }
            List<OpenSkyStateVector> states = response.getStates().stream()
                    .map(OpenSkyStateVector::fromArray)
                    .collect(Collectors.toList());

            log.info("Fetched {} aircraft from OpenSky", states.size());
            return states;
        } catch(Exception e) {
            log.error("Failed to fetch from OpenSky: {}", e.getMessage());
            return Collections.emptyList();
        }

    }
}
