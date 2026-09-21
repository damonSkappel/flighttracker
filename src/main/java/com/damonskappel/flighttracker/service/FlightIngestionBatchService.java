package com.damonskappel.flighttracker.service;

import com.damonskappel.flighttracker.dto.OpenSkyStateVector;
import com.damonskappel.flighttracker.model.Aircraft;
import com.damonskappel.flighttracker.model.PositionSnapshot;
import com.damonskappel.flighttracker.repository.AircraftRepository;
import com.damonskappel.flighttracker.repository.PositionSnapshotRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Holds the transactional write. Kept separate from {@link FlightIngestionService}
 * on purpose: {@code @Transactional} is proxy-based, so calling this from a method
 * in the same class would silently bypass the transaction.
 */
@Service
public class FlightIngestionBatchService {

    private final AircraftRepository aircraftRepository;
    private final PositionSnapshotRepository positionSnapshotRepository;

    public FlightIngestionBatchService(AircraftRepository aircraftRepository,
                                       PositionSnapshotRepository positionSnapshotRepository) {
        this.aircraftRepository = aircraftRepository;
        this.positionSnapshotRepository = positionSnapshotRepository;
    }

    @Transactional
    public FlightIngestionService.IngestResult processBatch(List<OpenSkyStateVector> batch) {
        Instant now = Instant.now();
        List<PositionSnapshot> snapshots = new ArrayList<>();
        int skippedNoId = 0;
        int skippedNoPosition = 0;

        for (OpenSkyStateVector sv : batch) {
            if (sv.getIcao24() == null || sv.getIcao24().isBlank()) {
                skippedNoId++;
                continue;
            }
            // Aircraft that report no position serve no endpoint that renders one,
            // and each would still cost an upsert. Drop them before any DB work.
            if (!sv.hasPosition()) {
                skippedNoPosition++;
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
            snapshot.setTimePosition(sv.getTimePosition());
            snapshot.setLastContact(sv.getLastContact());
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
        return new FlightIngestionService.IngestResult(
                snapshots.size(), skippedNoId, skippedNoPosition);
    }
}
