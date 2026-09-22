package com.damonskappel.flighttracker.scheduler;

import com.damonskappel.flighttracker.dto.OpenSkyStateVector;
import com.damonskappel.flighttracker.repository.PositionSnapshotRepository;
import com.damonskappel.flighttracker.service.FlightIngestionService;
import com.damonskappel.flighttracker.service.OpenSkyClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Component
public class FlightPollingScheduler {

    private static final Logger log = LoggerFactory.getLogger(FlightPollingScheduler.class);

    private final OpenSkyClient openSkyClient;
    private final FlightIngestionService flightIngestionService;
    private final PositionSnapshotRepository snapshotRepository;

    /**
     * How much snapshot history to keep. Shorter retention keeps the table small,
     * which is what makes the per-aircraft "latest snapshot" lookup cheap. At a
     * 5-minute poll this is still 12 history points per aircraft per hour.
     */
    private final int retentionHours;

    public FlightPollingScheduler(OpenSkyClient openSkyClient,
                                  FlightIngestionService flightIngestionService,
                                  PositionSnapshotRepository snapshotRepository,
                                  @Value("${flighttracker.retention-hours:6}") int retentionHours) {
        this.openSkyClient = openSkyClient;
        this.flightIngestionService = flightIngestionService;
        this.snapshotRepository = snapshotRepository;
        this.retentionHours = retentionHours;
    }

    // Paced against the OpenSky credit budget, not against what the map wants.
    // This bounding box is 1475 sq deg, so every call costs 4 credits; at 2
    // minutes that is 720 calls a day, 2880 credits, against the 4000/day an
    // authenticated account gets. Do the arithmetic before shortening it
    // further: (86400 / intervalSeconds) * 4 must stay under the tier budget,
    // and running anonymous drops that budget to 400.
    @Scheduled(fixedDelay = 120000, initialDelay = 5000)
    public void poll() {
        log.info("Starting OpenSky poll cycle");

        List<OpenSkyStateVector> states = openSkyClient.fetchCurrentStates();

        if (states.isEmpty()) {
            log.warn("No states returned from OpenSky, skipping ingestion");
            return;
        }

        flightIngestionService.ingest(states);
    }

    @Scheduled(fixedDelay = 3600000, initialDelay = 60000)
    public void cleanupOldSnapshots() {
        Instant cutoff = Instant.now().minus(retentionHours, ChronoUnit.HOURS);
        int deleted = snapshotRepository.deleteSnapshotsOlderThan(cutoff);
        log.info("Snapshot cleanup removed {} rows older than {}h", deleted, retentionHours);
    }
}
