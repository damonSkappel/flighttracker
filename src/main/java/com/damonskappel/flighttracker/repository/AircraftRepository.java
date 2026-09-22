package com.damonskappel.flighttracker.repository;

import com.damonskappel.flighttracker.model.Aircraft;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface AircraftRepository extends JpaRepository<Aircraft, String> {

    List<Aircraft> findByLastSeenAfter(Instant cutoff);
}