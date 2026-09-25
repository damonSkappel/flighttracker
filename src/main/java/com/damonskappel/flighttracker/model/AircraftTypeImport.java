package com.damonskappel.flighttracker.model;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * One row per aircraft database release that finished loading. The importer
 * skips a release that already has a row, which is what stops every deploy from
 * re-downloading 100MB. The row is written only after the last batch, so an
 * import cut short by a restart is simply run again.
 */
@Entity
@Table(name = "aircraft_type_import")
public class AircraftTypeImport {

    /** The release's object key in the OpenSky bucket. */
    @Id
    @Column(name = "source_key", nullable = false)
    private String sourceKey;

    @Column(name = "row_count", nullable = false)
    private long rowCount;

    @Column(name = "imported_at", nullable = false)
    private Instant importedAt;

    public AircraftTypeImport() {}

    public AircraftTypeImport(String sourceKey, long rowCount, Instant importedAt) {
        this.sourceKey = sourceKey;
        this.rowCount = rowCount;
        this.importedAt = importedAt;
    }

    public String getSourceKey() { return sourceKey; }
    public long getRowCount() { return rowCount; }
    public Instant getImportedAt() { return importedAt; }
}
