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
        int total = 0;
        int skipped = 0;

        List<List<OpenSkyStateVector>> batches = partition(states, BATCH_SIZE);

        for (List<OpenSkyStateVector> batch : batches) {
            IngestResult result = batchService.processBatch(batch);
            total += result.saved();
            skipped += result.skipped();
        }

        log.info("Ingested {} aircraft, {} snapshots saved, {} skipped",
                states.size() - skipped, total, skipped);
    }

    private <T> List<List<T>> partition(List<T> list, int size) {
        List<List<T>> partitions = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            partitions.add(list.subList(i, Math.min(i + size, list.size())));
        }
        return partitions;
    }

    public record IngestResult(int saved, int skipped) {}
}