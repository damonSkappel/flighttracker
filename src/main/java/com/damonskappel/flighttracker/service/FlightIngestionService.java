package com.damonskappel.flighttracker.service;

import com.damonskappel.flighttracker.dto.OpenSkyStateVector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class FlightIngestionService {

    private static final Logger log = LoggerFactory.getLogger(FlightIngestionService.class);

    /** Rows per transaction. Each chunk costs two JDBC batches, not two per aircraft. */
    private static final int BATCH_SIZE = 500;

    private final FlightIngestionBatchService batchService;

    public FlightIngestionService(FlightIngestionBatchService batchService) {
        this.batchService = batchService;
    }

    public void ingest(List<OpenSkyStateVector> states) {
        long startNs = System.nanoTime();

        List<OpenSkyStateVector> unique = deduplicate(states);
        int duplicates = states.size() - unique.size();

        int saved = 0;
        int skippedNoId = 0;
        int skippedNoPosition = 0;
        int batches = 0;

        for (List<OpenSkyStateVector> batch : partition(unique, BATCH_SIZE)) {
            FlightIngestionService.IngestResult result = batchService.processBatch(batch);
            saved += result.saved();
            skippedNoId += result.skippedNoId();
            skippedNoPosition += result.skippedNoPosition();
            batches++;
        }

        long elapsedMs = (System.nanoTime() - startNs) / 1_000_000;
        long rate = elapsedMs > 0 ? (saved * 1000L) / elapsedMs : saved;

        log.info("Ingest complete: {} received, {} saved, {} skipped (no position), "
                        + "{} skipped (no icao24), {} duplicates in {}ms ({} aircraft/s, {} db batches)",
                states.size(), saved, skippedNoPosition, skippedNoId, duplicates,
                elapsedMs, rate, batches * 2);
    }

    /**
     * Keeps the last state vector per icao24. OpenSky should not repeat an
     * aircraft within one response, but a duplicate would put two snapshots at
     * the identical timestamp — which the latest-per-aircraft query would then
     * return twice — and would also break a multi-row upsert, since Postgres
     * refuses to let ON CONFLICT touch the same row twice in one statement.
     */
    private List<OpenSkyStateVector> deduplicate(List<OpenSkyStateVector> states) {
        Map<String, OpenSkyStateVector> byIcao = new LinkedHashMap<>(states.size() * 2);
        List<OpenSkyStateVector> unkeyed = new ArrayList<>();
        for (OpenSkyStateVector sv : states) {
            if (sv.getIcao24() == null || sv.getIcao24().isBlank()) {
                unkeyed.add(sv);   // counted as skipped downstream, not as a duplicate
            } else {
                byIcao.put(sv.getIcao24(), sv);
            }
        }
        List<OpenSkyStateVector> result = new ArrayList<>(byIcao.values());
        result.addAll(unkeyed);
        return result;
    }

    private <T> List<List<T>> partition(List<T> list, int size) {
        List<List<T>> partitions = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            partitions.add(list.subList(i, Math.min(i + size, list.size())));
        }
        return partitions;
    }

    public record IngestResult(int saved, int skippedNoId, int skippedNoPosition) {}
}
