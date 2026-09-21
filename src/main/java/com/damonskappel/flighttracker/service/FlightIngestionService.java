package com.damonskappel.flighttracker.service;

import com.damonskappel.flighttracker.dto.OpenSkyStateVector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class FlightIngestionService {

    private static final Logger log = LoggerFactory.getLogger(FlightIngestionService.class);
    private static final int BATCH_SIZE = 500;

    private final FlightIngestionBatchService batchService;

    public FlightIngestionService(FlightIngestionBatchService batchService) {
        this.batchService = batchService;
    }

    public void ingest(List<OpenSkyStateVector> states) {
        int saved = 0;
        int skippedNoId = 0;
        int skippedNoPosition = 0;

        for (List<OpenSkyStateVector> batch : partition(states, BATCH_SIZE)) {
            IngestResult result = batchService.processBatch(batch);
            saved += result.saved();
            skippedNoId += result.skippedNoId();
            skippedNoPosition += result.skippedNoPosition();
        }

        log.info("Ingest complete: {} received, {} snapshots saved, {} skipped (no position), {} skipped (no icao24)",
                states.size(), saved, skippedNoPosition, skippedNoId);
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
