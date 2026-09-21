package com.damonskappel.flighttracker.repository;

import com.damonskappel.flighttracker.model.PositionSnapshot;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
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

    /**
     * Newest-first history for one aircraft. Pass a {@link Pageable} so the LIMIT
     * runs in SQL — otherwise every row for the aircraft is loaded and most are
     * discarded in memory.
     */
    @Query("SELECT p FROM PositionSnapshot p WHERE p.aircraft.icao24 = :icao24 "
            + "ORDER BY p.timestamp DESC")
    List<PositionSnapshot> findHistoryByIcao24(@Param("icao24") String icao24, Pageable pageable);

    /**
     * Single most recent snapshot for one aircraft. The entity graph pulls the
     * aircraft in the same query, and "Top" becomes a SQL LIMIT, so this reads one
     * row instead of the aircraft's whole history.
     */
    @EntityGraph(attributePaths = "aircraft")
    Optional<PositionSnapshot> findTopByAircraftIcao24OrderByTimestampDesc(String icao24);

    @Query("SELECT MIN(p.timestamp) FROM PositionSnapshot p")
    Optional<Instant> findOldestTimestamp();

    @Query("SELECT MAX(p.timestamp) FROM PositionSnapshot p")
    Optional<Instant> findNewestTimestamp();

    @Modifying
    @Transactional
    @Query("DELETE FROM PositionSnapshot p WHERE p.timestamp < :cutoff")
    int deleteSnapshotsOlderThan(@Param("cutoff") Instant cutoff);

    /**
     * Latest snapshot per aircraft inside the activity window, one row per aircraft.
     *
     * <p>Two things here are load-bearing. {@code JOIN FETCH} loads the aircraft in
     * the same query — without it each result row triggers its own SELECT when the
     * callsign is read, which is thousands of queries per request. And the subquery
     * repeats {@code p2.timestamp > :cutoff}: it is correlated, so it re-runs per
     * candidate row, and without that bound each run searches the entire table
     * rather than just the window.
     */
    @Query("SELECT p FROM PositionSnapshot p JOIN FETCH p.aircraft "
            + "WHERE p.timestamp > :cutoff "
            + "AND p.timestamp = (SELECT MAX(p2.timestamp) FROM PositionSnapshot p2 "
            + "                   WHERE p2.aircraft = p.aircraft AND p2.timestamp > :cutoff)")
    List<PositionSnapshot> findLatestSnapshotPerAircraft(@Param("cutoff") Instant cutoff);

    /**
     * Same as {@link #findLatestSnapshotPerAircraft} but pre-filtered to a lat/lon
     * box, so the precise radius check in Java runs over a small candidate set
     * instead of every active aircraft.
     */
    @Query("SELECT p FROM PositionSnapshot p JOIN FETCH p.aircraft "
            + "WHERE p.timestamp > :cutoff "
            + "AND p.latitude BETWEEN :minLat AND :maxLat "
            + "AND p.longitude BETWEEN :minLon AND :maxLon "
            + "AND p.timestamp = (SELECT MAX(p2.timestamp) FROM PositionSnapshot p2 "
            + "                   WHERE p2.aircraft = p.aircraft AND p2.timestamp > :cutoff)")
    List<PositionSnapshot> findLatestSnapshotPerAircraftInBox(@Param("cutoff") Instant cutoff,
                                                              @Param("minLat") double minLat,
                                                              @Param("maxLat") double maxLat,
                                                              @Param("minLon") double minLon,
                                                              @Param("maxLon") double maxLon);
}
