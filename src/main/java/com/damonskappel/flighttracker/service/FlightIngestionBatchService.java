package com.damonskappel.flighttracker.service;

import com.damonskappel.flighttracker.dto.OpenSkyStateVector;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/**
 * Writes one chunk of state vectors.
 *
 * <p>Both statements are issued as JDBC batches rather than per aircraft. The
 * previous version called a repository upsert inside the loop and let Hibernate
 * insert each snapshot, which meant two round trips per aircraft — over 10,000
 * per poll, and roughly two minutes of wall clock against Azure Postgres. Note
 * that Hibernate could not have batched the snapshot inserts anyway: the entity
 * uses {@code GenerationType.IDENTITY}, and Hibernate must execute those inserts
 * one at a time to read back each generated key, which silently defeats
 * {@code hibernate.jdbc.batch_size}. Going through JDBC sidesteps that entirely,
 * since nothing here needs the generated id.
 *
 * <p>Kept separate from {@link FlightIngestionService} because
 * {@code @Transactional} is proxy-based and a self-invocation would bypass it.
 */
@Service
public class FlightIngestionBatchService {

    private static final String UPSERT_AIRCRAFT = """
            INSERT INTO aircraft (icao24, callsign, origin_country, last_seen)
            VALUES (?, ?, ?, ?)
            ON CONFLICT (icao24) DO UPDATE SET
                callsign = EXCLUDED.callsign,
                origin_country = EXCLUDED.origin_country,
                last_seen = EXCLUDED.last_seen
            """;

    private static final String INSERT_SNAPSHOT = """
            INSERT INTO position_snapshots
                (icao24, timestamp, time_position, last_contact, latitude, longitude,
                 baro_altitude, velocity, heading, vertical_rate, on_ground)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    /** Column widths, enforced here so one oversized value cannot fail a whole batch. */
    private static final int ICAO24_LENGTH = 6;
    private static final int CALLSIGN_MAX = 10;

    private final JdbcTemplate jdbcTemplate;

    public FlightIngestionBatchService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public FlightIngestionService.IngestResult processBatch(List<OpenSkyStateVector> batch) {
        Instant now = Instant.now();
        List<OpenSkyStateVector> writable = new ArrayList<>(batch.size());
        int skippedNoId = 0;
        int skippedNoPosition = 0;

        for (OpenSkyStateVector sv : batch) {
            String icao24 = sv.getIcao24();
            // Length is checked, not truncated: a shortened address is a different
            // aircraft, so such a row is dropped rather than silently misattributed.
            if (icao24 == null || icao24.isBlank() || icao24.length() > ICAO24_LENGTH) {
                skippedNoId++;
                continue;
            }
            // Aircraft that report no position serve no endpoint that renders one.
            // A bounding-box query filters on position anyway, so this is defensive.
            if (!sv.hasPosition()) {
                skippedNoPosition++;
                continue;
            }
            writable.add(sv);
        }

        if (!writable.isEmpty()) {
            jdbcTemplate.batchUpdate(UPSERT_AIRCRAFT, new BatchPreparedStatementSetter() {
                @Override public int getBatchSize() { return writable.size(); }
                @Override public void setValues(PreparedStatement ps, int i) throws SQLException {
                    OpenSkyStateVector sv = writable.get(i);
                    ps.setString(1, sv.getIcao24());
                    setString(ps, 2, sv.getCallsign(), CALLSIGN_MAX);
                    setString(ps, 3, sv.getOriginCountry(), 255);
                    setInstant(ps, 4, now);
                }
            });

            jdbcTemplate.batchUpdate(INSERT_SNAPSHOT, new BatchPreparedStatementSetter() {
                @Override public int getBatchSize() { return writable.size(); }
                @Override public void setValues(PreparedStatement ps, int i) throws SQLException {
                    OpenSkyStateVector sv = writable.get(i);
                    ps.setString(1, sv.getIcao24());
                    setInstant(ps, 2, now);
                    setInstant(ps, 3, sv.getTimePosition());
                    setInstant(ps, 4, sv.getLastContact());
                    setDouble(ps, 5, sv.getLatitude());
                    setDouble(ps, 6, sv.getLongitude());
                    setDouble(ps, 7, sv.getBaroAltitude());
                    setDouble(ps, 8, sv.getVelocity());
                    setDouble(ps, 9, sv.getHeading());
                    setDouble(ps, 10, sv.getVerticalRate());
                    setBoolean(ps, 11, sv.getOnGround());
                }
            });
        }

        return new FlightIngestionService.IngestResult(
                writable.size(), skippedNoId, skippedNoPosition);
    }

    /**
     * Both timestamp columns are {@code timestamp with time zone}, so binding an
     * OffsetDateTime at UTC is exact. Binding a java.sql.Timestamp instead would
     * be interpreted through the JVM's default zone and shift every row on any
     * host not set to UTC.
     */
    private static void setInstant(PreparedStatement ps, int index, Instant value)
            throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.TIMESTAMP_WITH_TIMEZONE);
        } else {
            ps.setObject(index, OffsetDateTime.ofInstant(value, ZoneOffset.UTC));
        }
    }

    private static void setString(PreparedStatement ps, int index, String value, int max)
            throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.VARCHAR);
        } else {
            ps.setString(index, value.length() > max ? value.substring(0, max) : value);
        }
    }

    private static void setDouble(PreparedStatement ps, int index, Double value)
            throws SQLException {
        if (value == null) ps.setNull(index, Types.DOUBLE); else ps.setDouble(index, value);
    }

    private static void setBoolean(PreparedStatement ps, int index, Boolean value)
            throws SQLException {
        if (value == null) ps.setNull(index, Types.BOOLEAN); else ps.setBoolean(index, value);
    }
}
