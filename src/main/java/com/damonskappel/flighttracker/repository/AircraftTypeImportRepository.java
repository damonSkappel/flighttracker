package com.damonskappel.flighttracker.repository;

import com.damonskappel.flighttracker.model.AircraftTypeImport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AircraftTypeImportRepository extends JpaRepository<AircraftTypeImport, String> {
}
