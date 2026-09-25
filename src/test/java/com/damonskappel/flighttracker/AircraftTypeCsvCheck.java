package com.damonskappel.flighttracker;

import com.damonskappel.flighttracker.service.AircraftTypeCsvReader;
import com.damonskappel.flighttracker.service.AircraftTypeCsvReader.Row;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The OpenSky aircraft database uses ' as its quote, quotes only some fields,
 * doubles quotes inside them and lets them span lines. The rows below are
 * shaped on real ones from the 2025-08 release.
 */
class AircraftTypeCsvCheck {

    private static final String HEADER =
            "'icao24','timestamp','manufacturerIcao','manufacturerName','model','operator','owner','registration','typecode'\n";

    private static List<Row> read(String body) throws IOException {
        List<Row> rows = new ArrayList<>();
        try (AircraftTypeCsvReader reader = new AircraftTypeCsvReader(new StringReader(HEADER + body))) {
            Row row;
            while ((row = reader.next()) != null) rows.add(row);
        }
        return rows;
    }

    @Test
    void readsQuotedAndUnquotedFields() throws IOException {
        List<Row> rows = read(
                "'06a142','2017-09-15 00:10:03',AIRBUS,Airbus,A380 861,Qatar Airways,'',A7-APA,A388\n");
        assertEquals(List.of(new Row("06a142", "A388", "Airbus", "A380 861", "A7-APA", "Qatar Airways")), rows);
    }

    @Test
    void quotedFieldsSpanLinesAndEscapeQuotes() throws IOException {
        List<Row> rows = read(
                "'4ca1d6','1970-01-01',TIGER,'',AA-5,'','O''doherty, Kevin\nbowe, Thomas\r\ndunne, Patrick',EI-FFV,AA5\n"
                        + "'4ca1d7','1970-01-01',BOEING,'','','','',ZS-ZWV,B738\r\n");
        assertEquals(2, rows.size(), "a newline inside quotes must not end the record");
        assertEquals(new Row("4ca1d6", "AA5", "TIGER", "AA-5", "EI-FFV", null), rows.get(0));
        assertEquals(new Row("4ca1d7", "B738", "BOEING", null, "ZS-ZWV", null), rows.get(1),
                "blank manufacturer name falls back to the ICAO manufacturer code");
    }

    @Test
    void skipsRowsThatSayNothingAboutType() throws IOException {
        List<Row> rows = read(
                "'000000','2017-10-19',,'',unknow,'','','',''\n"     // "unknow" is the dataset's placeholder
                        + "'000001','2017-10-19','','','','','','',ZZZZ\n" // ICAO's no-designator code
                        + "'','2017-10-19','','',Model,'','','',C172\n"   // no address
                        + "'XYZ123','2017-10-19','','',Model,'','','',C172\n"
                        + "'A0B1C2','2017-10-19','','','','','','',c172\n");
        assertEquals(List.of(new Row("a0b1c2", "C172", null, null, null, null)), rows,
                "address lower-cased, designator upper-cased, junk dropped");
    }

    @Test
    void failsLoudlyWhenAColumnIsMissing() {
        IOException e = assertThrows(IOException.class,
                () -> new AircraftTypeCsvReader(new StringReader("'icao24','model'\n")));
        assertTrue(e.getMessage().contains("typecode"));
    }
}
