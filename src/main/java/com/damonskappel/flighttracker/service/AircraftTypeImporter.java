package com.damonskappel.flighttracker.service;

import com.damonskappel.flighttracker.model.AircraftTypeImport;
import com.damonskappel.flighttracker.repository.AircraftTypeImportRepository;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Loads the OpenSky aircraft database into {@code aircraft_type}, so a popup can
 * say what an aircraft is. The live state vectors carry no type at all.
 *
 * <p><b>Runs on its own thread, deliberately not through {@code @Scheduled}.</b>
 * Spring's default scheduler has a single thread, and the download is ~100MB and
 * several minutes of batched writes; on that thread it would hold up the flight
 * poll for the whole import. Here the map works from the first poll and types
 * simply start appearing as the rows land. Each batch commits on its own for the
 * same reason: a lookup finds whatever has loaded so far.
 *
 * <p>Checks for a new release on startup and every few hours afterwards. A
 * release already recorded in {@code aircraft_type_import} is skipped, so a
 * deploy costs one small listing request, not a re-download.
 */
@Service
public class AircraftTypeImporter {

    private static final Logger log = LoggerFactory.getLogger(AircraftTypeImporter.class);

    /** Monthly releases; the newest is picked by name, which sorts by date. */
    private static final Pattern RELEASE_KEY = Pattern.compile(
            "<Key>(metadata/aircraft-database-complete-\\d{4}-\\d{2}\\.csv)</Key>");
    private static final String LISTING_PREFIX = "metadata/aircraft-database-complete-";

    private static final int BATCH_SIZE = 1000;
    private static final int PROGRESS_EVERY = 100_000;
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    /** Per read, not for the whole body: the download legitimately takes a while. */
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(60);

    private static final String UPSERT = """
            INSERT INTO aircraft_type (icao24, typecode, manufacturer, model, registration, operator)
            VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT (icao24) DO UPDATE SET
                typecode = EXCLUDED.typecode,
                manufacturer = EXCLUDED.manufacturer,
                model = EXCLUDED.model,
                registration = EXCLUDED.registration,
                operator = EXCLUDED.operator
            """;

    /** Column widths from {@code AircraftType}, enforced so one long value cannot fail a batch. */
    private static final int TYPECODE_MAX = 10;
    private static final int TEXT_MAX = 100;
    private static final int REGISTRATION_MAX = 20;

    private final JdbcTemplate jdbcTemplate;
    private final AircraftTypeImportRepository importRepository;
    private final String bucketUrl;
    private final boolean enabled;
    private final long checkIntervalHours;

    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "aircraft-type-import");
        thread.setDaemon(true);
        return thread;
    });

    public AircraftTypeImporter(JdbcTemplate jdbcTemplate,
                                AircraftTypeImportRepository importRepository,
                                @Value("${flighttracker.aircraft-types.bucket-url:https://s3.opensky-network.org/data-samples}")
                                String bucketUrl,
                                @Value("${flighttracker.aircraft-types.enabled:true}") boolean enabled,
                                @Value("${flighttracker.aircraft-types.check-interval-hours:6}")
                                long checkIntervalHours) {
        this.jdbcTemplate = jdbcTemplate;
        this.importRepository = importRepository;
        this.bucketUrl = bucketUrl;
        this.enabled = enabled;
        this.checkIntervalHours = checkIntervalHours;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        if (!enabled) {
            log.info("Aircraft type import disabled");
            return;
        }
        executor.scheduleWithFixedDelay(this::refreshSafely,
                0, TimeUnit.HOURS.toMinutes(checkIntervalHours), TimeUnit.MINUTES);
    }

    @PreDestroy
    public void stop() {
        executor.shutdownNow();
    }

    /** An exception escaping a scheduled task cancels every later run, so none may. */
    private void refreshSafely() {
        try {
            refresh();
        } catch (Exception e) {
            log.warn("Aircraft type import failed, will retry in {}h: {}", checkIntervalHours, e.toString());
        }
    }

    void refresh() throws IOException {
        String key = latestReleaseKey();
        if (key == null) {
            log.warn("No aircraft database release found under {}", bucketUrl);
            return;
        }
        if (importRepository.existsById(key)) {
            log.debug("Aircraft database {} already imported", key);
            return;
        }

        log.info("Importing aircraft types from {}", key);
        long started = System.nanoTime();
        long rows;
        try (InputStream body = open(bucketUrl + "/" + key);
             AircraftTypeCsvReader reader = new AircraftTypeCsvReader(new BufferedReader(
                     new InputStreamReader(body, StandardCharsets.UTF_8), 1 << 16))) {
            rows = load(reader);
        }
        importRepository.save(new AircraftTypeImport(key, rows, Instant.now()));
        log.info("Aircraft type import of {} finished: {} types in {}s",
                key, rows, (System.nanoTime() - started) / 1_000_000_000);
    }

    /**
     * Writes every row in batches, each its own transaction. Returns the row count.
     *
     * <p>Keyed by icao24 so a repeated address overwrites within its batch: Postgres
     * refuses to let one ON CONFLICT statement touch the same row twice, and the
     * rewritten batch is one statement. No release so far repeats an address.
     */
    long load(AircraftTypeCsvReader reader) throws IOException {
        Map<String, AircraftTypeCsvReader.Row> batch = new LinkedHashMap<>();
        long total = 0;
        AircraftTypeCsvReader.Row row;
        while ((row = reader.next()) != null) {
            batch.put(row.icao24(), row);
            if (batch.size() == BATCH_SIZE) {
                write(batch);
                total += batch.size();
                batch.clear();
                if (total % PROGRESS_EVERY == 0) log.info("Aircraft type import: {} rows loaded", total);
            }
            if (Thread.currentThread().isInterrupted()) throw new IOException("Import interrupted");
        }
        write(batch);
        return total + batch.size();
    }

    private void write(Map<String, AircraftTypeCsvReader.Row> batch) {
        if (batch.isEmpty()) return;
        jdbcTemplate.batchUpdate(UPSERT, List.copyOf(batch.values()), batch.size(), (ps, row) -> {
            ps.setString(1, row.icao24());
            ps.setString(2, truncate(row.typecode(), TYPECODE_MAX));
            ps.setString(3, truncate(row.manufacturer(), TEXT_MAX));
            ps.setString(4, truncate(row.model(), TEXT_MAX));
            ps.setString(5, truncate(row.registration(), REGISTRATION_MAX));
            ps.setString(6, truncate(row.operator(), TEXT_MAX));
        });
    }

    private String latestReleaseKey() throws IOException {
        String listing;
        try (InputStream body = open(bucketUrl + "?prefix=" + LISTING_PREFIX)) {
            listing = new String(body.readAllBytes(), StandardCharsets.UTF_8);
        }
        String latest = null;
        Matcher m = RELEASE_KEY.matcher(listing);
        while (m.find()) {
            if (latest == null || m.group(1).compareTo(latest) > 0) latest = m.group(1);
        }
        return latest;
    }

    private static InputStream open(String url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
        connection.setConnectTimeout((int) CONNECT_TIMEOUT.toMillis());
        connection.setReadTimeout((int) READ_TIMEOUT.toMillis());
        int status = connection.getResponseCode();
        if (status != HttpURLConnection.HTTP_OK) {
            connection.disconnect();
            throw new IOException("HTTP " + status + " from " + url);
        }
        return connection.getInputStream();
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
