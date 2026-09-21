package com.damonskappel.flighttracker;

import com.damonskappel.flighttracker.config.AppConfig;
import com.damonskappel.flighttracker.dto.OpenSkyStateVector;
import com.damonskappel.flighttracker.service.OpenSkyClient;
import com.damonskappel.flighttracker.service.OpenSkyTokenManager;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** Drives the client against a stub OpenSky so the auth paths are actually exercised. */
class OpenSkyAuthCheck {

    private static final String STATES =
        "{\"time\":1,\"states\":[[\"a1b2c3\",\"TEST123 \",\"United States\",1700000000,1700000000,"
        + "-100.0,40.0,10000.0,false,231.5,90.0,0.0,null,10500.0,null,false,0]]}";

    @Test
    void authenticatesRefreshesOn401AndReportsCredits() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        AtomicInteger tokensIssued = new AtomicInteger();
        AtomicInteger stateCalls = new AtomicInteger();
        List<String> seenAuth = new ArrayList<>();

        server.createContext("/token", ex -> {
            new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String body = "{\"access_token\":\"tok-" + tokensIssued.incrementAndGet()
                    + "\",\"expires_in\":1800}";
            respond(ex, 200, body, null);
        });
        server.createContext("/states", ex -> {
            int n = stateCalls.incrementAndGet();
            seenAuth.add(String.valueOf(ex.getRequestHeaders().getFirst("Authorization")));
            if (n == 2) { respond(ex, 401, "{}", null); return; }   // expired token
            respond(ex, 200, STATES, "3200");
        });
        server.start();
        int port = server.getAddress().getPort();

        try {
            RestTemplate rest = new AppConfig().restTemplate();
            String base = "http://localhost:" + port;
            OpenSkyTokenManager tokens =
                    new OpenSkyTokenManager(rest, base + "/token", "cid", "secret");
            OpenSkyClient client = new OpenSkyClient(rest, tokens, base + "/states", 4);

            assertTrue(tokens.isConfigured(), "credentials present -> configured");

            List<OpenSkyStateVector> first = client.fetchCurrentStates();
            assertEquals(1, first.size(), "parses the state vector");
            assertEquals("a1b2c3", first.get(0).getIcao24());
            assertEquals(1, tokensIssued.get(), "one token fetched");
            assertEquals("Bearer tok-1", seenAuth.get(0), "bearer token sent");

            // Second poll reuses the cached token rather than re-fetching.
            List<OpenSkyStateVector> second = client.fetchCurrentStates();
            assertEquals(1, second.size(), "recovers from the 401 and returns data");
            assertEquals(2, tokensIssued.get(), "401 triggered exactly one refresh");
            assertEquals(3, stateCalls.get(), "401 was retried once, not looped");
            assertEquals("Bearer tok-2", seenAuth.get(2), "retry used the NEW token");

            // Third poll: token still valid, no new token.
            client.fetchCurrentStates();
            assertEquals(2, tokensIssued.get(), "valid token is cached, not re-fetched");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void withoutCredentialsStaysAnonymous() {
        OpenSkyTokenManager anon =
                new OpenSkyTokenManager(new RestTemplate(), "http://unused", "", "");
        assertFalse(anon.isConfigured(), "blank credentials -> not configured");
        assertNull(anon.bearerToken(), "no token attempted without credentials");
    }

    private static void respond(com.sun.net.httpserver.HttpExchange ex, int code,
                                String body, String creditsRemaining) throws java.io.IOException {
        byte[] out = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json");
        if (creditsRemaining != null) {
            ex.getResponseHeaders().add("X-Rate-Limit-Remaining", creditsRemaining);
        }
        ex.sendResponseHeaders(code, out.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(out); }
    }

}
