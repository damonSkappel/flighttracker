package com.damonskappel.flighttracker.repository;

import com.damonskappel.flighttracker.model.Aircraft;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Repository
public interface AircraftRepository extends JpaRepository<Aircraft, String> {

    List<Aircraft> findByLastSeenAfter(Instant cutoff);

    /**
     * Deletes airframes that no longer have any snapshot. Snapshot retention
     * removes positions but never the aircraft they belonged to, so without this
     * the table only grows.
     *
     * <p>NOT EXISTS is what keeps the foreign key safe, and it is served by the
     * leading icao24 column of idx_snapshot_icao24_timestamp. The last_seen bound
     * covers an ingest committing concurrently: Postgres re-checks the row's own
     * columns after waiting on its lock, so a freshly upserted aircraft drops out.
     */
    @Modifying
    @Transactional
    @Query(value = "DELETE FROM aircraft a WHERE a.last_seen < :cutoff "
            + "AND NOT EXISTS (SELECT 1 FROM position_snapshots p WHERE p.icao24 = a.icao24)",
            nativeQuery = true)
    int deleteAircraftWithoutSnapshots(@Param("cutoff") Instant cutoff);
}
