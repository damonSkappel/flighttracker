package com.damonskappel.flighttracker.repository;

import com.damonskappel.flighttracker.model.AircraftType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AircraftTypeRepository extends JpaRepository<AircraftType, String> {
}
