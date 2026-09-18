package com.damonskappel.flighttracker.service;

import com.damonskappel.flighttracker.dto.FlightHistoryResponse;
import com.damonskappel.flighttracker.dto.FlightResponse;
import com.damonskappel.flighttracker.dto.StatsResponse;
import com.damonskappel.flighttracker.model.Aircraft;
import com.damonskappel.flighttracker.model.PositionSnapshot;
import com.damonskappel.flighttracker.repository.AircraftRepository;
import com.damonskappel.flighttracker.repository.PositionSnapshotRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class FlightQueryService {

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
                    .withZone(ZoneOffset.UTC);

    private final AircraftRepository aircraftRepository;
    private final PositionSnapshotRepository snapshotRepository;

    public FlightQueryService(AircraftRepository aircraftRepository,
                              PositionSnapshotRepository snapshotRepository) {
        this.aircraftRepository = aircraftRepository;
        this.snapshotRepository = snapshotRepository;
    }

    // GET /flights — all active aircraft in last 5 minutes
    @Transactional(readOnly = true)
    public List<FlightResponse> getActiveFlights() {
        Instant cutoff = Instant.now().minus(5, ChronoUnit.MINUTES);

        List<PositionSnapshot> latestSnapshots =
                snapshotRepository.findLatestSnapshotPerAircraft(cutoff);

        return latestSnapshots.stream()
                .map(snapshot -> toFlightResponse(snapshot.getAircraft(), snapshot))
                .collect(Collectors.toList());
    }

    // GET /flights/{icao24} — one specific aircraft
    @Transactional(readOnly = true)
    public Optional<FlightResponse> getFlightByIcao24(String icao24) {
        Optional<Aircraft> aircraft = aircraftRepository.findById(icao24);
        if (aircraft.isEmpty()) return Optional.empty();

        List<PositionSnapshot> history =
                snapshotRepository.findHistoryByIcao24(icao24);
        PositionSnapshot latest = history.isEmpty() ? null : history.get(0);

        return Optional.of(toFlightResponse(aircraft.get(), latest));
    }

    // GET /flights/{icao24}/history — position history
    @Transactional(readOnly = true)
    public List<FlightHistoryResponse> getFlightHistory(String icao24, int limit) {
        List<PositionSnapshot> snapshots =
                snapshotRepository.findHistoryByIcao24(icao24);

        return snapshots.stream()
                .limit(limit)
                .map(this::toHistoryResponse)
                .collect(Collectors.toList());
    }

    // GET /flights/area — aircraft within radius miles of a point
    @Transactional(readOnly = true)
    public List<FlightResponse> getFlightsInArea(double lat, double lon,
                                                 double radiusMiles) {
        Instant cutoff = Instant.now().minus(5, ChronoUnit.MINUTES);
        List<PositionSnapshot> recent =
                snapshotRepository.findRecentWithCoordinates(cutoff);

        return recent.stream()
                .filter(p -> distanceMiles(lat, lon, p.getLatitude(), p.getLongitude())
                        <= radiusMiles)
                .map(p -> toFlightResponse(p.getAircraft(), p))
                .collect(Collectors.toList());
    }

    // GET /stats
    @Transactional(readOnly = true)
    public StatsResponse getStats() {
        long totalAircraft = aircraftRepository.count();
        long totalSnapshots = snapshotRepository.count();

        String oldest = snapshotRepository.findOldestTimestamp()
                .map(FORMATTER::format)
                .orElse("N/A");

        String newest = snapshotRepository.findNewestTimestamp()
                .map(FORMATTER::format)
                .orElse("N/A");

        return new StatsResponse(totalAircraft, totalSnapshots, oldest, newest);
    }

    // --- Private helpers ---

    private FlightResponse toFlightResponse(Aircraft aircraft,
                                            PositionSnapshot snapshot) {
        return new FlightResponse(
                aircraft.getIcao24(),
                aircraft.getCallsign(),
                aircraft.getOriginCountry(),
                snapshot != null ? snapshot.getLatitude() : null,
                snapshot != null ? snapshot.getLongitude() : null,
                snapshot != null ? toFeet(snapshot.getBaroAltitude()) : null,
                snapshot != null ? toKnots(snapshot.getVelocity()) : null,
                snapshot != null ? snapshot.getHeading() : null,
                snapshot != null ? snapshot.getVerticalRate() : null,
                snapshot != null ? snapshot.getOnGround() : null,
                aircraft.getLastSeen() != null
                        ? FORMATTER.format(aircraft.getLastSeen()) : null
        );
    }

    private FlightHistoryResponse toHistoryResponse(PositionSnapshot snapshot) {
        return new FlightHistoryResponse(
                FORMATTER.format(snapshot.getTimestamp()),
                snapshot.getLatitude(),
                snapshot.getLongitude(),
                toFeet(snapshot.getBaroAltitude()),
                toKnots(snapshot.getVelocity()),
                snapshot.getHeading(),
                snapshot.getVerticalRate(),
                snapshot.getOnGround()
        );
    }

    private static double distanceMiles(double lat1, double lon1,
                                        double lat2, double lon2) {
        final double R = 3958.8;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1))
                * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    private static Double toFeet(Double meters) {
        return meters != null ? meters * 3.28084 : null;
    }

    private static Double toKnots(Double metersPerSecond) {
        return metersPerSecond != null ? metersPerSecond * 1.94384 : null;
    }
}