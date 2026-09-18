package com.damonskappel.flighttracker.repository;

import com.damonskappel.flighttracker.model.PositionSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface PositionSnapshotRepository extends JpaRepository<PositionSnapshot, Long> {

    List<PositionSnapshot> findByAircraftIcao24OrderByTimestampDesc(String icao24);

    @Query("SELECT p FROM PositionSnapshot p WHERE p.aircraft.icao24 = :icao24 " + "Order BY p.timestamp DESC")
    List<PositionSnapshot> findHistoryByIcao24(@Param("icao24") String icao24);

    @Query("SELECT p FROM PositionSnapshot p WHERE p.timestamp > :cutoff " +
            "AND p.latitude IS NOT NULL AND p.longitude IS NOT NULL")
    List<PositionSnapshot> findRecentWithCoordinates(@Param("cutoff") Instant cutoff);

    @Query("SELECT MIN(p.timestamp) FROM PositionSnapshot p")
    Optional<Instant> findOldestTimestamp();

    @Query("SELECT MAX(p.timestamp) FROM PositionSnapshot p")
    Optional<Instant> findNewestTimestamp();

    @Modifying
    @Transactional
    @Query("DELETE FROM PositionSnapshot p WHERE p.timestamp < :cutoff")
    void deleteSnapshotsOlderThan(@Param("cutoff") Instant cutoff);

    @Query("SELECT p FROM PositionSnapshot p WHERE p.timestamp > :cutoff " +
            "AND p.latitude IS NOT NULL AND p.longitude IS NOT NULL " +
            "AND p.timestamp = (SELECT MAX(p2.timestamp) FROM PositionSnapshot p2 " +
            "WHERE p2.aircraft = p.aircraft)")
    List<PositionSnapshot> findLatestSnapshotPerAircraft(@Param("cutoff") Instant cutoff);
}
