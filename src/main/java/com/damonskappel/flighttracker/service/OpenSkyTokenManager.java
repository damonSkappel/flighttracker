package com.damonskappel.flighttracker.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.Map;

/**
 * Holds an OAuth2 client-credentials access token for the OpenSky API.
 *
 * <p>OpenSky no longer accepts basic authentication. Tokens live for 30 minutes,
 * so this caches one and refreshes it slightly early rather than fetching a
 * fresh token per request — the token endpoint costs nothing, but a token that
 * expires mid-flight turns into a 401 on a call that did cost credits.
 *
 * <p>With no credentials configured this stays dormant and {@link #bearerToken()}
 * returns null, which leaves the client on the anonymous tier.
 */
@Component
public class OpenSkyTokenManager {

    private static final Logger log = LoggerFactory.getLogger(OpenSkyTokenManager.class);

    /** Refresh this long before expiry so a request never races the deadline. */
    private static final long REFRESH_MARGIN_SECONDS = 60;
    /** Used when the response omits expires_in; OpenSky's tokens last 30 minutes. */
    private static final long DEFAULT_LIFETIME_SECONDS = 1800;

    private final RestTemplate restTemplate;
    private final String tokenUrl;
    private final String clientId;
    private final String clientSecret;

    private String cachedToken;
    private Instant refreshAfter = Instant.EPOCH;

    public OpenSkyTokenManager(RestTemplate restTemplate,
                               @Value("${opensky.token-url:https://auth.opensky-network.org/auth/realms/opensky-network/protocol/openid-connect/token}")
                               String tokenUrl,
                               @Value("${opensky.client-id:}") String clientId,
                               @Value("${opensky.client-secret:}") String clientSecret) {
        this.restTemplate = restTemplate;
        this.tokenUrl = tokenUrl;
        this.clientId = clientId == null ? "" : clientId.trim();
        this.clientSecret = clientSecret == null ? "" : clientSecret.trim();
    }

    public boolean isConfigured() {
        return !clientId.isBlank() && !clientSecret.isBlank();
    }

    /**
     * @return a usable access token, or null when no credentials are configured
     *         or the token endpoint could not be reached
     */
    public synchronized String bearerToken() {
        if (!isConfigured()) return null;
        if (cachedToken != null && Instant.now().isBefore(refreshAfter)) {
            return cachedToken;
        }
        return fetchToken();
    }

    /** Drops the cached token so the next call fetches a fresh one, after a 401. */
    public synchronized void invalidate() {
        cachedToken = null;
        refreshAfter = Instant.EPOCH;
    }

    private String fetchToken() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    tokenUrl, HttpMethod.POST, new HttpEntity<>(form, headers), Map.class);

            Map body = response.getBody();
            Object token = body != null ? body.get("access_token") : null;
            if (token == null) {
                log.error("OpenSky token endpoint returned no access_token");
                return null;
            }

            Object expiresIn = body.get("expires_in");
            long lifetime = expiresIn instanceof Number
                    ? ((Number) expiresIn).longValue()
                    : DEFAULT_LIFETIME_SECONDS;

            cachedToken = token.toString();
            refreshAfter = Instant.now().plusSeconds(Math.max(1, lifetime - REFRESH_MARGIN_SECONDS));
            log.info("Obtained OpenSky access token, valid for {}s", lifetime);
            return cachedToken;

        } catch (Exception e) {
            // Leave the previous token in place: an expired one still beats none,
            // and the request will surface a 401 that triggers a retry.
            log.error("Failed to obtain OpenSky access token: {}", e.getMessage());
            return cachedToken;
        }
    }
}
