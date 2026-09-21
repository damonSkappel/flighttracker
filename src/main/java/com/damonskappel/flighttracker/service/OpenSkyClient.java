package com.damonskappel.flighttracker.service;

import com.damonskappel.flighttracker.dto.OpenSkyResponse;
import com.damonskappel.flighttracker.dto.OpenSkyStateVector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class OpenSkyClient {

    private static final Logger log = LoggerFactory.getLogger(OpenSkyClient.class);

    // Bounding box area decides the credit cost: >400 sq degrees costs 4 credits
    // per call, and this box is 25 x 59 = 1475. At a 5 minute poll that is about
    // 1120 credits a day -- well past the 400/day anonymous allowance, and about
    // 28% of the 4000/day a free registered account gets.
    private static final String REMAINING_HEADER = "X-Rate-Limit-Remaining";
    private static final String RETRY_AFTER_HEADER = "X-Rate-Limit-Retry-After-Seconds";

    private final RestTemplate restTemplate;
    private final OpenSkyTokenManager tokenManager;
    private final String statesUrl;
    /** Credits one call costs, derived from the bounding box area. */
    private final int creditsPerCall;

    public OpenSkyClient(RestTemplate restTemplate,
                         OpenSkyTokenManager tokenManager,
                         @Value("${opensky.states-url:https://opensky-network.org/api/states/all?lamin=24.5&lomin=-125.0&lamax=49.5&lomax=-66.0}")
                         String statesUrl,
                         @Value("${opensky.credits-per-call:4}") int creditsPerCall) {
        this.restTemplate = restTemplate;
        this.tokenManager = tokenManager;
        this.statesUrl = statesUrl;
        this.creditsPerCall = creditsPerCall;
    }

    public List<OpenSkyStateVector> fetchCurrentStates() {
        try {
            ResponseEntity<OpenSkyResponse> response = get();

            // A token lasts 30 minutes; an expired one is a 401. Refresh and retry
            // once, since the failed call itself did not spend credits.
            if (response.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                log.info("OpenSky token rejected, refreshing and retrying");
                tokenManager.invalidate();
                response = get();
            }

            logCredits(response.getHeaders());

            if (!response.getStatusCode().is2xxSuccessful()) {
                log.warn("OpenSky returned {}", response.getStatusCode());
                return Collections.emptyList();
            }

            OpenSkyResponse body = response.getBody();
            if (body == null || body.getStates() == null) {
                log.warn("OpenSky returned null or empty response");
                return Collections.emptyList();
            }

            List<OpenSkyStateVector> states = body.getStates().stream()
                    .map(OpenSkyStateVector::fromArray)
                    .collect(Collectors.toList());

            log.info("Fetched {} aircraft from OpenSky", states.size());
            return states;

        } catch (HttpClientErrorException.TooManyRequests e) {
            // Distinguished from a generic failure on purpose: exhausting the
            // daily credit budget looks nothing like a network blip, and used to
            // be indistinguishable in the logs.
            String retryAfter = e.getResponseHeaders() != null
                    ? e.getResponseHeaders().getFirst(RETRY_AFTER_HEADER) : null;
            log.error("OpenSky credit budget exhausted (429). Retry after {}s. Tier is {}.",
                    retryAfter != null ? retryAfter : "unknown",
                    tokenManager.isConfigured() ? "authenticated" : "ANONYMOUS (400/day)");
            return Collections.emptyList();

        } catch (Exception e) {
            log.error("Failed to fetch from OpenSky: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private ResponseEntity<OpenSkyResponse> get() {
        HttpHeaders headers = new HttpHeaders();
        String token = tokenManager.bearerToken();
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return restTemplate.exchange(
                statesUrl, HttpMethod.GET, new HttpEntity<>(headers), OpenSkyResponse.class);
    }

    /** OpenSky reports the remaining balance on every response; surface it. */
    private void logCredits(HttpHeaders headers) {
        String remaining = headers.getFirst(REMAINING_HEADER);
        if (remaining == null) return;

        try {
            int credits = Integer.parseInt(remaining.trim());
            int callsLeft = creditsPerCall > 0 ? credits / creditsPerCall : credits;
            if (credits < 100) {
                log.warn("OpenSky credits remaining: {} (~{} polls left today)", credits, callsLeft);
            } else {
                log.info("OpenSky credits remaining: {} (~{} polls left today)", credits, callsLeft);
            }
        } catch (NumberFormatException e) {
            log.info("OpenSky credits remaining: {}", remaining);
        }
    }
}
