package com.damonskappel.flighttracker;

import com.damonskappel.flighttracker.dto.OpenSkyStateVector;
import com.damonskappel.flighttracker.service.FlightIngestionBatchService;
import com.damonskappel.flighttracker.service.FlightIngestionService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Round trips are what the ingest wall clock is made of, so they are what this
 * measures. Binding is replayed against a recording PreparedStatement so the
 * values actually sent are checked too.
 */
class IngestBatchingCheck {

    static class Recording extends JdbcTemplate {
        final List<String> statements = new ArrayList<>();
        final List<Integer> rowCounts = new ArrayList<>();
        final List<Object[]> firstRowOf = new ArrayList<>();

        @Override
        public int[] batchUpdate(String sql, BatchPreparedStatementSetter pss) {
            statements.add(sql.trim().split("\\s+")[0] + " " + sql.trim().split("\\s+")[2]);
            rowCounts.add(pss.getBatchSize());
            try {
                Capture c = new Capture();
                PreparedStatement ps = (PreparedStatement) java.lang.reflect.Proxy.newProxyInstance(
                        PreparedStatement.class.getClassLoader(),
                        new Class<?>[]{PreparedStatement.class}, c);
                if (pss.getBatchSize() > 0) { pss.setValues(ps, 0); firstRowOf.add(c.values()); }
            } catch (SQLException e) { throw new IllegalStateException(e); }
            return new int[pss.getBatchSize()];
        }
        int roundTrips() { return statements.size(); }
    }

    @Test
    void ingestCostsTwoRoundTripsPerChunkNotPerAircraft() {
        Recording jdbc = new Recording();
        FlightIngestionService service =
                new FlightIngestionService(new FlightIngestionBatchService(jdbc));

        int aircraft = 5340;                       // the real production volume
        service.ingest(states(aircraft));

        int chunks = (int) Math.ceil(aircraft / 500.0);
        assertEquals(chunks * 2, jdbc.roundTrips(),
                "two batched statements per chunk of 500");
        assertEquals(22, jdbc.roundTrips(), "5340 aircraft -> 22 round trips");
        assertEquals(aircraft, jdbc.rowCounts.stream().mapToInt(Integer::intValue).sum() / 2,
                "every aircraft is still written");

        // The old design issued one upsert plus one insert per aircraft.
        int before = aircraft * 2;
        System.out.printf("round trips: %d -> %d  (%.0fx fewer)%n",
                before, jdbc.roundTrips(), before / (double) jdbc.roundTrips());
    }

    @Test
    void timestampsAreBoundAsUtcOffsetsNotLocalTimestamps() {
        Recording jdbc = new Recording();
        new FlightIngestionService(new FlightIngestionBatchService(jdbc)).ingest(states(1));

        Object[] snapshotRow = jdbc.firstRowOf.get(1);   // the INSERT, not the upsert
        assertInstanceOf(String.class, snapshotRow[1], "param 1 is icao24");
        assertInstanceOf(OffsetDateTime.class, snapshotRow[2],
                "timestamp bound as OffsetDateTime, immune to JVM default zone");
        assertEquals(java.time.ZoneOffset.UTC, ((OffsetDateTime) snapshotRow[2]).getOffset(),
                "bound at UTC");
        assertInstanceOf(OffsetDateTime.class, snapshotRow[3], "time_position likewise");
    }

    @Test
    void duplicateAircraftAreCollapsed() {
        Recording jdbc = new Recording();
        List<OpenSkyStateVector> withDupes = new ArrayList<>(states(3));
        String repeated = withDupes.get(0).getIcao24();
        withDupes.add(vector(repeated, 1.0));
        new FlightIngestionService(new FlightIngestionBatchService(jdbc)).ingest(withDupes);

        assertEquals(3, jdbc.rowCounts.get(0),
                "repeated icao24 collapses to one row, so ON CONFLICT cannot hit it twice");
    }

    @Test
    void malformedRowsAreDroppedWithoutKillingTheBatch() {
        Recording jdbc = new Recording();
        List<OpenSkyStateVector> rows = new ArrayList<>(states(2));
        rows.add(vector("toolongicao", 5.0));       // exceeds varchar(6)
        rows.add(positionless());
        new FlightIngestionService(new FlightIngestionBatchService(jdbc)).ingest(rows);

        assertEquals(2, jdbc.rowCounts.get(0), "only the two good rows are written");
    }

    // --- fixtures ---

    private static List<OpenSkyStateVector> states(int n) {
        List<OpenSkyStateVector> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) out.add(vector(String.format("%06x", i), 40.0));
        return out;
    }

    private static OpenSkyStateVector vector(String icao24, double lat) {
        long t = Instant.now().getEpochSecond();
        return OpenSkyStateVector.fromArray(Arrays.asList(
                icao24, "TEST    ", "United States", t, t,
                -100.0, lat, 10000.0, false, 231.5, 90.0, 0.0));
    }

    private static OpenSkyStateVector positionless() {
        long t = Instant.now().getEpochSecond();
        return OpenSkyStateVector.fromArray(Arrays.asList(
                "bbbbbb", "NOPOS   ", "United States", t, t,
                null, null, null, false, null, null, null));
    }

    /** Captures parameter binding without a database. */
    static class Capture implements java.lang.reflect.InvocationHandler {
        private final Object[] slots = new Object[16];
        Object[] values() { return slots; }
        public Object invoke(Object proxy, java.lang.reflect.Method m, Object[] args) {
            if (m.getName().startsWith("set") && args != null && args.length >= 2
                    && args[0] instanceof Integer idx) {
                slots[idx] = m.getName().equals("setNull") ? null : args[1];
            }
            return null;
        }
    }
}
