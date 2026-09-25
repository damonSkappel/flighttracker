package com.damonskappel.flighttracker.service;

import java.io.Closeable;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Streams rows out of the OpenSky "aircraft-database-complete" CSV.
 *
 * <p>The format is not standard CSV and no stock parser reads it correctly:
 * <ul>
 *   <li>The quote character is {@code '}, not {@code "}, and only some fields
 *       are quoted ({@code 'a0b1c2',0,,Boeing,'737-8H4'}).</li>
 *   <li>A quote inside a quoted field is doubled ({@code 'O''doherty'}).</li>
 *   <li>Quoted fields can span lines, since owner lists are newline-separated,
 *       so a record is not a line.</li>
 * </ul>
 * Columns are found by header name, so a reordering between releases is
 * tolerated. The file is read once, front to back, never held in memory.
 */
public class AircraftTypeCsvReader implements Closeable {

    public record Row(String icao24, String typecode, String manufacturer,
                      String model, String registration, String operator) {}

    private static final char QUOTE = '\'';
    private static final Pattern ICAO24 = Pattern.compile("[0-9a-f]{6}");
    /** ICAO's placeholder designator for a type that has none. */
    private static final String NO_DESIGNATOR = "ZZZZ";

    private final Reader in;
    private int peeked = -2;

    private final int icao24Col;
    private final int typecodeCol;
    private final int manufacturerNameCol;
    private final int manufacturerIcaoCol;
    private final int modelCol;
    private final int registrationCol;
    private final int operatorCol;

    public AircraftTypeCsvReader(Reader in) throws IOException {
        this.in = in;
        List<String> header = readRecord();
        if (header == null) throw new IOException("Aircraft database is empty");
        Map<String, Integer> columns = new HashMap<>();
        for (int i = 0; i < header.size(); i++) columns.put(header.get(i).trim(), i);
        icao24Col = require(columns, "icao24");
        typecodeCol = require(columns, "typecode");
        manufacturerNameCol = require(columns, "manufacturerName");
        manufacturerIcaoCol = require(columns, "manufacturerIcao");
        modelCol = require(columns, "model");
        registrationCol = require(columns, "registration");
        operatorCol = require(columns, "operator");
    }

    /**
     * The next row that says something about the aircraft's type, or null at the
     * end of the file. Rows with a malformed address, or with neither a type
     * designator nor a model, are skipped: most of the file is transponder
     * addresses nobody has catalogued.
     */
    public Row next() throws IOException {
        List<String> record;
        while ((record = readRecord()) != null) {
            String icao24 = clean(field(record, icao24Col));
            if (icao24 == null) continue;
            icao24 = icao24.toLowerCase();
            if (!ICAO24.matcher(icao24).matches()) continue;

            String typecode = clean(field(record, typecodeCol));
            if (typecode != null) typecode = typecode.toUpperCase();
            if (NO_DESIGNATOR.equals(typecode)) typecode = null;
            String model = clean(field(record, modelCol));
            if (typecode == null && model == null) continue;

            String manufacturer = clean(field(record, manufacturerNameCol));
            if (manufacturer == null) manufacturer = clean(field(record, manufacturerIcaoCol));

            return new Row(icao24, typecode, manufacturer, model,
                    clean(field(record, registrationCol)), clean(field(record, operatorCol)));
        }
        return null;
    }

    @Override
    public void close() throws IOException {
        in.close();
    }

    private static int require(Map<String, Integer> columns, String name) throws IOException {
        Integer index = columns.get(name);
        if (index == null) throw new IOException("Aircraft database has no '" + name + "' column");
        return index;
    }

    private static String field(List<String> record, int index) {
        return index < record.size() ? record.get(index) : null;
    }

    /** Blank, and the dataset's own "unknow" placeholder, both mean absent. */
    static String clean(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        if (trimmed.isEmpty() || trimmed.equalsIgnoreCase("unknow")) return null;
        return trimmed;
    }

    /** One record's fields, or null at the end of the input. */
    List<String> readRecord() throws IOException {
        int c = read();
        if (c == -1) return null;

        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean atFieldStart = true;
        boolean quoted = false;

        while (true) {
            if (quoted) {
                if (c == -1) break;                      // unterminated: take what there is
                if (c == QUOTE) {
                    if (peek() == QUOTE) { read(); field.append(QUOTE); }
                    else quoted = false;
                } else {
                    field.append((char) c);
                }
            } else if (c == -1 || c == '\n') {
                break;
            } else if (c == '\r') {
                if (peek() == '\n') read();
                break;
            } else if (c == ',') {
                fields.add(field.toString());
                field.setLength(0);
                atFieldStart = true;
                c = read();
                continue;
            } else if (c == QUOTE && atFieldStart) {
                quoted = true;
            } else {
                field.append((char) c);
            }
            atFieldStart = false;
            c = read();
        }
        fields.add(field.toString());
        return fields;
    }

    private int read() throws IOException {
        if (peeked != -2) {
            int c = peeked;
            peeked = -2;
            return c;
        }
        return in.read();
    }

    private int peek() throws IOException {
        if (peeked == -2) peeked = in.read();
        return peeked;
    }
}
