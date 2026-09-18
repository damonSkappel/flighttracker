package com.damonskappel.flighttracker.service;

import com.damonskappel.flighttracker.dto.OpenSkyStateVector;
import com.damonskappel.flighttracker.model.Aircraft;
import com.damonskappel.flighttracker.model.PositionSnapshot;
import com.damonskappel.flighttracker.repository.AircraftRepository;
import com.damonskappel.flighttracker.repository.PositionSnapshotRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class FlightIngestionService {

    private static final Logger log = LoggerFactory.getLogger(FlightIngestionService.class);
    private static final int BATCH_SIZE = 500;

    private final AircraftRepository aircraftRepository;
    private final PositionSnapshotRepository positionSnapshotRepository;

    public FlightIngestionService(AircraftRepository aircraftRepository,
                                  PositionSnapshotRepository positionSnapshotRepository) {
        this.aircraftRepository = aircraftRepository;
        this.positionSnapshotRepository = positionSnapshotRepository;
    }

    public void ingest(List<OpenSkyStateVector> states) {
        int total = 0;
        int skipped = 0;

        List<List<OpenSkyStateVector>> batches = partition(states, BATCH_SIZE);

        for (List<OpenSkyStateVector> batch : batches) {
            IngestResult result = ingestBatch(batch);
            total += result.saved();
            skipped += result.skipped();
        }

        log.info("Ingested {} aircraft, {} snapshots saved, {} skipped",
                states.size() - skipped, total, skipped);
    }

    @Transactional
    public IngestResult ingestBatch(List<OpenSkyStateVector> batch) {
        Instant now = Instant.now();
        List<PositionSnapshot> snapshots = new ArrayList<>();
        int skipped = 0;

        for (OpenSkyStateVector sv : batch) {
            if (sv.getIcao24() == null || sv.getIcao24().isBlank()) {
                skipped++;
                continue;
            }

            aircraftRepository.upsert(
                    sv.getIcao24(),
                    sv.getCallsign(),
                    sv.getOriginCountry(),
                    now
            );

            Aircraft aircraft = aircraftRepository.getReferenceById(sv.getIcao24());

            PositionSnapshot snapshot = new PositionSnapshot();
            snapshot.setAircraft(aircraft);
            snapshot.setTimestamp(now);
            snapshot.setLatitude(sv.getLatitude());
            snapshot.setLongitude(sv.getLongitude());
            snapshot.setBaroAltitude(sv.getBaroAltitude());
            snapshot.setVelocity(sv.getVelocity());
            snapshot.setHeading(sv.getHeading());
            snapshot.setVerticalRate(sv.getVerticalRate());
            snapshot.setOnGround(sv.getOnGround());

            snapshots.add(snapshot);
        }

        positionSnapshotRepository.saveAll(snapshots);
        return new IngestResult(snapshots.size(), skipped);
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