package com.damonskappel.flighttracker.service;

import com.damonskappel.flighttracker.dto.OpenSkyStateVector;
import com.damonskappel.flighttracker.model.PositionSnapshot;
import com.damonskappel.flighttracker.model.Aircraft;
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
    private final AircraftRepository aircraftRepository;
    private final PositionSnapshotRepository positionSnapshotRepository;

    public FlightIngestionService(AircraftRepository aircraftRepository, PositionSnapshotRepository positionSnapshotRepository) {
        this.aircraftRepository = aircraftRepository;
        this.positionSnapshotRepository = positionSnapshotRepository;
    }

    @Transactional
    public void ingest(List<OpenSkyStateVector> states) {

        Instant now = Instant.now();
        List<PositionSnapshot> snapshots = new ArrayList<>();
        int skipped = 0;

        for (OpenSkyStateVector sv : states) {

            if (sv.getIcao24() == null || sv.getIcao24().isBlank()) {
                skipped++;
                continue;
            }

            //Upsert the aircraft record
            aircraftRepository.upsert(
                    sv.getIcao24(),
                    sv.getCallsign(),
                    sv.getOriginCountry(),
                    now
            );

            //Get a reference to teh aircraft for the foreign key
            Aircraft aircraft = aircraftRepository.getReferenceById(sv.getIcao24());

            //Build snapshot of plan
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
        log.info("Ingested {} aircraft, {} snapshots saved, {} skipped", states.size() - skipped, snapshots.size(), skipped);
    }

}
