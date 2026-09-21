package com.damonskappel.flighttracker.service;

import com.damonskappel.flighttracker.dto.FlightHistoryResponse;
import com.damonskappel.flighttracker.dto.FlightResponse;
import com.damonskappel.flighttracker.dto.StatsResponse;
import com.damonskappel.flighttracker.model.Aircraft;
import com.damonskappel.flighttracker.model.PositionSnapshot;
import com.damonskappel.flighttracker.repository.AircraftRepository;
import com.damonskappel.flighttracker.repository.PositionSnapshotRepository;
import com.damonskappel.flighttracker.util.UsTailNumber;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
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

    /** Miles per degree of latitude; good enough for a bounding-box prefilter. */
    private static final double MILES_PER_DEGREE_LAT = 69.0;

    private final AircraftRepository aircraftRepository;
    private final PositionSnapshotRepository snapshotRepository;

    /**
     * How long an aircraft stays "active" after its last snapshot. Must stay
     * comfortably larger than the poll interval: if the two are equal, every
     * aircraft expires before its replacement lands and the map blanks out
     * between polls.
     */
    private final int activeWindowMinutes;

    public FlightQueryService(AircraftRepository aircraftRepository,
                              PositionSnapshotRepository snapshotRepository,
                              @Value("${flighttracker.active-window-minutes:15}")
                              int activeWindowMinutes) {
        this.aircraftRepository = aircraftRepository;
        this.snapshotRepository = snapshotRepository;
        this.activeWindowMinutes = activeWindowMinutes;
    }

    // GET /flights — latest position of every currently active aircraft
    @Transactional(readOnly = true)
    public List<FlightResponse> getActiveFlights() {
        List<PositionSnapshot> latestSnapshots =
                snapshotRepository.findLatestSnapshotPerAircraft(activityCutoff());

        return latestSnapshots.stream()
                .map(snapshot -> toFlightResponse(snapshot.getAircraft(), snapshot))
                .collect(Collectors.toList());
    }

    // GET /flights/{icao24} — one specific aircraft
    @Transactional(readOnly = true)
    public Optional<FlightResponse> getFlightByIcao24(String icao24) {
        Optional<PositionSnapshot> latest = snapshotRepository.findTopByAircraftIcao24OrderByTimestampDesc(icao24);
        if (latest.isPresent()) {
            return Optional.of(toFlightResponse(latest.get().getAircraft(), latest.get()));
        }
        // Known airframe that has no snapshot left in retention.
        return aircraftRepository.findById(icao24)
                .map(aircraft -> toFlightResponse(aircraft, null));
    }

    // GET /flights/{icao24}/history — position history, newest first
    @Transactional(readOnly = true)
    public List<FlightHistoryResponse> getFlightHistory(String icao24, int limit) {
        List<PositionSnapshot> snapshots = snapshotRepository.findHistoryByIcao24(
                icao24, PageRequest.of(0, limit));

        return snapshots.stream()
                .map(this::toHistoryResponse)
                .collect(Collectors.toList());
    }

    // GET /flights/area — active aircraft within radius miles of a point
    @Transactional(readOnly = true)
    public List<FlightResponse> getFlightsInArea(double lat, double lon,
                                                 double radiusMiles) {
        double latDelta = radiusMiles / MILES_PER_DEGREE_LAT;
        double minLat = Math.max(-90.0, lat - latDelta);
        double maxLat = Math.min(90.0, lat + latDelta);

        // Degrees of longitude shrink toward the poles. Near them, or when the box
        // would wrap the antimeridian, fall back to the full range and let the
        // haversine filter below do the real work.
        double cosLat = Math.cos(Math.toRadians(lat));
        double lonDelta = Math.abs(cosLat) < 1e-6
                ? 180.0
                : radiusMiles / (MILES_PER_DEGREE_LAT * Math.abs(cosLat));
        boolean wraps = lonDelta >= 180.0 || lon - lonDelta < -180.0 || lon + lonDelta > 180.0;
        double minLon = wraps ? -180.0 : lon - lonDelta;
        double maxLon = wraps ? 180.0 : lon + lonDelta;

        List<PositionSnapshot> candidates =
                snapshotRepository.findLatestSnapshotPerAircraftInBox(
                        activityCutoff(), minLat, maxLat, minLon, maxLon);

        return candidates.stream()
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

    private Instant activityCutoff() {
        return Instant.now().minus(activeWindowMinutes, ChronoUnit.MINUTES);
    }

    private FlightResponse toFlightResponse(Aircraft aircraft,
                                            PositionSnapshot snapshot) {
        return new FlightResponse(
                aircraft.getIcao24(),
                aircraft.getCallsign(),
                UsTailNumber.fromIcao24(aircraft.getIcao24()),
                aircraft.getOriginCountry(),
                snapshot != null ? snapshot.getLatitude() : null,
                snapshot != null ? snapshot.getLongitude() : null,
                snapshot != null ? toFeet(snapshot.getBaroAltitude()) : null,
                snapshot != null ? toKnots(snapshot.getVelocity()) : null,
                snapshot != null ? snapshot.getVelocity() : null,
                snapshot != null ? snapshot.getHeading() : null,
                snapshot != null ? snapshot.getVerticalRate() : null,
                snapshot != null ? snapshot.getOnGround() : null,
                snapshot != null ? formatEpochSeconds(snapshot.getTimePosition()) : null,
                snapshot != null ? formatEpochSeconds(snapshot.getLastContact()) : null,
                aircraft.getLastSeen() != null
                        ? FORMATTER.format(aircraft.getLastSeen()) : null
        );
    }

    private FlightHistoryResponse toHistoryResponse(PositionSnapshot snapshot) {
        return new FlightHistoryResponse(
                FORMATTER.format(snapshot.getTimestamp()),
                formatEpochSeconds(snapshot.getTimePosition()),
                snapshot.getLatitude(),
                snapshot.getLongitude(),
                toFeet(snapshot.getBaroAltitude()),
                toKnots(snapshot.getVelocity()),
                snapshot.getHeading(),
                snapshot.getVerticalRate(),
                snapshot.getOnGround()
        );
    }

    /** Epoch seconds, so the browser can do elapsed-time math without parsing. */
    private static Long formatEpochSeconds(Instant instant) {
        return instant != null ? instant.getEpochSecond() : null;
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
