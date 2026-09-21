# Flight Tracker — agent orientation

Live ADS-B tracker: polls the OpenSky Network for aircraft over the continental
US, stores position snapshots in Postgres, and renders them on a Leaflet map.

**Stack:** Java 21, Spring Boot 4.1.1 (WebMVC + Data JPA), PostgreSQL, vanilla JS
frontend served as a static resource. Deployed to Azure App Service at
`flighttracker.damonskappel.com`.

## Build & run

```bash
./mvnw compile              # ~30s warm; needs network for plugin resolution
./mvnw package -DskipTests  # produces target/app.jar (finalName=app)
./mvnw spring-boot:run      # needs Postgres on localhost:5433
```

There is one test file (`FlighttrackerApplicationTests`) and it is an empty
context-load stub. There is effectively **no test coverage** — verify changes by
running the app, not by running tests.

## Request path in one picture

```
OpenSky REST API
      │  every 5 min (FlightPollingScheduler.poll)
      ▼
OpenSkyClient.fetchCurrentStates()        → List<OpenSkyStateVector>
      │  (raw JSON arrays → DTO via OpenSkyStateVector.fromArray)
      ▼
FlightIngestionService.ingest()           → chunks into batches of 500
      ▼
FlightIngestionBatchService.processBatch() @Transactional
      │  aircraft upsert (native ON CONFLICT) + saveAll(snapshots)
      ▼
   Postgres: aircraft, position_snapshots
      ▲
      │  FlightQueryService (@Transactional readOnly)
      │
FlightController /flights, /flights/{icao24}, /flights/{icao24}/history, /flights/area
HealthController /stats
      ▲
      │  fetch('/flights') every 15s
static/index.html  (Leaflet + markercluster, all inline)
```

## Where things live

| Path (under `src/main/java/com/damonskappel/flighttracker/`) | Role |
|---|---|
| `FlighttrackerApplication.java` | Entry point. Loads `.env` into system properties before boot (no-op in prod). |
| `scheduler/FlightPollingScheduler.java` | `poll()` every 5 min; `cleanupOldSnapshots()` hourly, deletes snapshots >24h old. |
| `service/OpenSkyClient.java` | Single hardcoded URL with the US bbox. Swallows all exceptions → returns empty list. |
| `service/FlightIngestionService.java` | Batching + logging only. Holds the `IngestResult` record. |
| `service/FlightIngestionBatchService.java` | The `@Transactional` write. **Separate class on purpose** — see "Gotchas". |
| `service/FlightQueryService.java` | All read logic + unit conversion (m→ft, m/s→kts) + haversine. |
| `repository/AircraftRepository.java` | Native `INSERT … ON CONFLICT` upsert. |
| `repository/PositionSnapshotRepository.java` | JPQL queries incl. `findLatestSnapshotPerAircraft`. |
| `model/Aircraft.java` | PK is `icao24` (6-char hex string, not generated). |
| `model/PositionSnapshot.java` | Generated Long id, `@ManyToOne(LAZY)` → Aircraft. |
| `config/AppConfig.java` | `RestTemplate` bean (5s connect / 10s read) + `@EnableScheduling`. |
| `config/WebConfig.java` | CORS for `/flights/**` and `/stats`. |
| `dto/` | `OpenSkyStateVector` (ingest), `FlightResponse` / `FlightHistoryResponse` / `StatsResponse` (egress). |

Frontend is a single file: `src/main/resources/static/index.html` — styles,
markup and all JS inline. No build step, no npm. Leaflet and markercluster come
from cdnjs.

## Data model

```
aircraft                          position_snapshots
  icao24  PK varchar(6)   ◄────────  icao24  FK (column name is "icao24")
  callsign   varchar(10)            id  PK bigserial
  origin_country                    timestamp      (idx_snapshot_timestamp)
  last_seen  timestamp              latitude, longitude
                                    baro_altitude, velocity, heading
                                    vertical_rate, on_ground
                                    time_position   <- aircraft's own fix time
                                    last_contact
                                    (idx_snapshot_icao24_timestamp)
```

Schema is managed by `spring.jpa.hibernate.ddl-auto=update` — **there are no
migration files**. Changing an entity changes the prod schema on next deploy.
Postgres does *not* auto-index FK columns, so the composite
`idx_snapshot_icao24_timestamp` is declared explicitly on the entity — the
latest-per-aircraft and history lookups both collapse to table scans without it.

**Deploying a new index is not free.** `ddl-auto=update` runs `CREATE INDEX` at
startup, which locks writes while it builds and can stall the boot long enough
for Azure's health check to notice. On a large table, create it by hand with
`CREATE INDEX CONCURRENTLY` first, or let retention shrink the table first.

## Config & deploy

- `application.properties` — local defaults, DB on `localhost:5433`.
- `application-prod.properties` — Azure Postgres, Hikari pool capped at 5.
- `.env` (gitignored, untracked) — local `DB_*` values.
- `flighttracker.active-window-minutes` (default 15) — how long an aircraft
  stays "active" after its last snapshot.
- `flighttracker.retention-hours` (default 6) — snapshot retention; the hourly
  in-process cleanup job deletes past this. It only runs while the JVM is up,
  so Azure **Always On** must be enabled or both cleanup and polling stop when
  the app idles out. Postgres reclaims the freed space via autovacuum, not
  immediately.
- `.github/workflows/deploy.yml` — on push to `main`: `mvn package -DskipTests`
  then `azure/webapps-deploy@v3` with `package: target/*.jar`.

**The Dockerfile is not used by the deploy pipeline.** CI ships the bare jar, so
the Dockerfile's `-Dspring.profiles.active=prod` never applies in production —
the prod profile is active only because `SPRING_PROFILES_ACTIVE` is set in Azure
App Settings. If that setting is ever lost the app silently falls back to
`localhost:5433`. Keep the two in sync if you touch either.

`applicationinsights.json` deliberately has an empty connection string and
sampling at 0 — App Insights was disabled to resolve an agent conflict
(commit 462294b). Not a bug.

## Gotchas

1. **Ingestion is split across two classes on purpose.** `@Transactional` is
   proxy-based, so a self-invocation inside one class would bypass it. That was
   fixed in commit 0078d79 — do not merge `FlightIngestionBatchService` back
   into `FlightIngestionService`.
2. **Polling is deliberately slow (5 min).** Commit 2e7a71a widened it to avoid
   exhausting the free anonymous OpenSky quota. Don't tighten it without
   adding authentication first.
3. **`flighttracker.active-window-minutes` must stay well above the poll
   interval.** They answer different questions and were once both 5 minutes,
   which meant every aircraft expired before its replacement landed and
   `/flights` returned an empty list between polls. 15 minutes gives three
   cycles of slack so two failed polls still leave a populated map.
4. **Latest-per-aircraft queries carry two load-bearing details.** `JOIN FETCH
   p.aircraft` (without it, reading the callsign fires one SELECT per row — an
   N+1 of thousands per request) and the repeated `p2.timestamp > :cutoff`
   inside the correlated subquery (without it, each re-run scans the whole
   table instead of the window). Keep both if you rewrite them.
5. **Aircraft reporting no position are dropped at ingest**, so every stored
   snapshot has coordinates.
6. **Popup values are escaped via `esc()`** in index.html. Callsign and country
   come from OpenSky and are operator-supplied, so they are untrusted; popup
   HTML is assigned as innerHTML. Don't interpolate a new field without it.

## Map icons

`createAircraftIcon()` returns an inline SVG whose nose points to 12 o'clock at
rotation 0, so the CSS `rotate()` takes the compass heading verbatim (0 = north,
90 = east). It previously used the `✈` glyph (U+2708), whose resting orientation
is font-dependent — it pointed east, which put the *left wing* on the course
line. Don't go back to a glyph; keep the geometry in the SVG path.

Markers are cluster-managed (`L.markerClusterGroup`, clustering off above
zoom 7) and the whole layer is `clearLayers()`-ed and rebuilt on every 15s poll.

## If you are here for dead reckoning

The data plumbing is in place; the animation is not.

**Already done:** `time_position` and `last_contact` are captured from OpenSky
(raw indices 3 and 4), persisted on `PositionSnapshot`, and exposed on
`FlightResponse` as `timePosition` / `lastContact` in **Unix epoch seconds**, so
the browser can do elapsed-time math without parsing. `FlightResponse` also
carries `velocityMps` alongside `velocityKnots` so extrapolation does not have to
un-convert.

**Why fix age matters:** `PositionSnapshot.timestamp` is when *our ingest job
wrote the row*, not when the aircraft reported. Those differ by the poll latency
plus however stale OpenSky's own data was — an aircraft outside receiver coverage
can be returned with a `time_position` many minutes old. Extrapolating from the
wrong epoch places the aircraft confidently in the wrong place: at 450 kts a jet
covers 7.5 nm per minute, so a 3-minute-stale fix treated as fresh and pushed
2 minutes forward lands the icon ~15 nm off, moving smoothly and looking correct.
Always extrapolate from `timePosition`, falling back to `timestamp` only when it
is null.

**Still to do, in order:**

1. **Give markers stable identity.** `updateFlights()` currently calls
   `flightLayer.clearLayers()` and rebuilds every marker each poll, so nothing
   persists to animate. Key markers by `icao24` in a `Map`, then add/update/remove
   against it instead of clearing.
2. **Separate the data tick from the render tick.** The fetch stays on its
   interval; add a `requestAnimationFrame` loop that advances each marker from its
   last known fix. There is plenty of headroom — the page polls far more often
   than the data actually changes.
3. **Bound the extrapolation.** Decide a maximum age (2 minutes is a reasonable
   start) past which you stop moving the icon and fade or drop it. Without a
   bound, aircraft that lost coverage sail off across the map forever.
4. **Great-circle forward projection**, not flat-earth. From lat/lon, `heading`
   (degrees true) and `velocityMps` over elapsed seconds, use the standard
   destination-point formula; a linear lat/lon offset visibly drifts at altitude
   and high latitude.
5. **Interpolate rotation the short way.** Heading wraps at 360, so animating
   350° → 10° must go forward 20°, not backward 340°.
6. **Snap, don't jump.** When a real fix arrives it will disagree with the
   extrapolated position. Ease the marker to the true position over a few hundred
   ms rather than teleporting it.
