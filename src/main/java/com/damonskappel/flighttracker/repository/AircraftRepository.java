package com.damonskappel.flighttracker.repository;

import com.damonskappel.flighttracker.model.Aircraft;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface AircraftRepository extends JpaRepository<Aircraft, String> {

    @Modifying
    @Query(value = """
        INSERT INTO aircraft (icao24, callsign, origin_country, last_seen)
        VALUES (:icao24, :callsign, :originCountry, :lastSeen)
        ON CONFLICT (icao24)
        DO UPDATE SET
            callsign = EXCLUDED.callsign,
            origin_country = EXCLUDED.origin_country,
            last_seen = EXCLUDED.last_seen
        """, nativeQuery = true)
    void upsert(@Param("icao24") String icao24,
                @Param("callsign") String callsign,
                @Param("originCountry") String originCountry,
                @Param("lastSeen") Instant lastSeen);

    List<Aircraft> findByLastSeenAfter(Instant cutoff);
}