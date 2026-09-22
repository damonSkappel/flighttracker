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

Test coverage is thin. `FlighttrackerApplicationTests` is an empty context-load
stub that needs a live database. Two real ones need no database:
`OpenSkyAuthCheck` runs the client against a stub HTTP server and covers the
bearer token, refresh-and-retry on a 401, token caching and the anonymous
fallback. `IngestBatchingCheck` counts JDBC round trips and asserts the UTC
timestamp binding, the icao24 dedupe and that malformed rows are dropped rather
than failing a whole batch. Run both with
`./mvnw test -Dtest='OpenSkyAuthCheck,IngestBatchingCheck'`. Everything else is
verified by running the app.

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
| `service/OpenSkyClient.java` | Fetches state vectors. Sends a bearer token when credentials are configured, refreshes once on a 401, logs remaining credits, and reports a 429 distinctly. Returns an empty list on any failure. |
| `service/OpenSkyTokenManager.java` | OAuth2 client-credentials token, cached and refreshed 60s before expiry. Dormant with no credentials configured. |
| `service/FlightIngestionService.java` | Dedupes by icao24, chunks, times the run. Holds the `IngestResult` record. |
| `service/FlightIngestionBatchService.java` | The `@Transactional` write, via `JdbcTemplate` batches rather than JPA — see "Gotchas". **Separate class on purpose.** |
| `service/FlightQueryService.java` | All read logic + unit conversion (m→ft, m/s→kts) + haversine. |
| `repository/AircraftRepository.java` | Native `INSERT … ON CONFLICT` upsert. |
| `repository/PositionSnapshotRepository.java` | JPQL queries incl. `findLatestSnapshotPerAircraft`. |
| `model/Aircraft.java` | PK is `icao24` (6-char hex string, not generated). |
| `model/PositionSnapshot.java` | Generated Long id, `@ManyToOne(LAZY)` → Aircraft. |
| `config/AppConfig.java` | `RestTemplate` bean (5s connect / 10s read) + `@EnableScheduling`. |
| `config/WebConfig.java` | CORS for `/flights/**` and `/stats`. |
| `dto/` | `OpenSkyStateVector` (ingest), `FlightResponse` / `FlightHistoryResponse` / `StatsResponse` (egress). |
| `util/UsTailNumber.java` | Derives a US tail number from icao24. The FAA encodes N-numbers directly into the A00001-ADF7C7 block, so this is a pure function — no lookup, no API call. Returns null outside that block; other countries allocate from registries and cannot be derived. |

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
- `OPENSKY_CLIENT_ID` / `OPENSKY_CLIENT_SECRET` — OAuth2 client credentials from
  https://opensky-network.org/my-opensky/account. Blank means anonymous tier.
  `opensky.states-url` and `opensky.credits-per-call` are overridable together if
  the bounding box changes — they must stay consistent or the "polls left"
  estimate in the logs will lie.
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
2. **The poll interval is set by the credit budget, not by what the map wants.**
   OpenSky prices `/states/all` by bounding box area: this box is 25° × 59° =
   1,475 sq°, which is over the 400 sq° threshold and so costs **4 credits per
   call**. At the current 2-minute poll that is 720 calls = **2,880 credits/day**.

   | Tier | Credits/day |
   |---|---|
   | Anonymous (per IP) | 400 |
   | Registered (free) | 4,000 |
   | Active feeder | 8,000 |

   Anonymous therefore runs dry about 8 hours in, after which every poll 429s
   and — because nothing refreshes — the whole map ages out of the activity
   window and goes empty. Set `OPENSKY_CLIENT_ID` / `OPENSKY_CLIENT_SECRET` to
   run authenticated. Before shortening the poll, do the credit arithmetic:
   `(86400 / interval_seconds) × 4` must stay under the tier's budget. At 2
   minutes that is 72% of an authenticated budget, so there is not much room
   left.

   **Four values move together** when the interval changes: `fixedDelay`,
   `flighttracker.active-window-minutes` (≈3 cycles), and `MAX_EXTRAPOLATION_S`
   (≈1 cycle) / `STALE_FADE_S` in index.html. Changing one alone produces a map
   that either blanks between polls or dims aircraft that are actually fresh.
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
7. **Ingest writes through `JdbcTemplate`, not JPA, and must stay that way.**
   Two batched statements per 500-row chunk — 22 round trips for a 5,340
   aircraft poll, against 10,680 when each aircraft cost an upsert plus an
   insert. That was ~124s of wall clock per poll.

   Do not "simplify" this back to `repository.save`/`saveAll`.
   `PositionSnapshot` uses `GenerationType.IDENTITY`, and Hibernate cannot
   batch inserts for identity columns — it executes each one separately to read
   back the generated key, which silently makes
   `hibernate.jdbc.batch_size` inert. Raw JDBC sidesteps it because nothing
   here needs the generated id.
8. **Timestamps are bound as `OffsetDateTime` at UTC.** Both timestamp columns
   are `timestamp(6) with time zone`. Binding a `java.sql.Timestamp` instead
   would be interpreted through the JVM default zone and shift every row on any
   host not set to UTC.
9. **State vectors are deduplicated by icao24 before writing.** A repeat would
   put two snapshots at an identical timestamp — which the latest-per-aircraft
   query returns twice — and Postgres refuses to let `ON CONFLICT` touch the
   same row twice within one statement.

## Map icons

`createAircraftIcon()` returns an inline SVG whose nose points to 12 o'clock at
rotation 0, so the CSS `rotate()` takes the compass heading verbatim (0 = north,
90 = east). It previously used the `✈` glyph (U+2708), whose resting orientation
is font-dependent — it pointed east, which put the *left wing* on the course
line. Don't go back to a glyph; keep the geometry in the SVG path.

There is **no marker clustering** — it was removed deliberately; every aircraft
draws individually. What keeps that affordable is viewport culling (see below),
not clustering. Don't reintroduce `L.markerClusterGroup`: it indexes marker
positions on insert and does not reindex on `setLatLng`, so moving markers
inside a cluster group corrupts it, which rules it out for dead reckoning.

## Dead reckoning

Implemented. Aircraft coast along their heading between polls instead of hopping
every 15 seconds.

**How a position is produced.** For each visible track, every frame:

```
age      = now - fix.timePosition        seconds since the AIRCRAFT reported
carried  = min(age, MAX_EXTRAPOLATION_S) capped, because an old fix may have turned
distance = speed * carried               metres travelled
delta    = distance / 6371008.8          that distance as an angle at Earth's centre
position = great-circle destination from the fix, on bearing `heading`
```

`projectPosition()` is the great-circle step; a flat lat/lon offset drifts at
altitude and high latitude. Everything derives from **absolute fix time**, never
from accumulated per-frame deltas, so a backgrounded tab resumes correct rather
than drifting.

**Why `timePosition` and not `timestamp`.** `PositionSnapshot.timestamp` is when
our ingest job wrote the row; `timePosition` is when the aircraft actually
reported. They differ by poll latency plus OpenSky's own staleness — an aircraft
outside receiver coverage is returned with a `time_position` many minutes old.
At 450 kts a jet covers 7.5 nm per minute, so extrapolating from the wrong epoch
puts the icon miles from the aircraft, moving smoothly and looking correct.

**Three things the naive version gets wrong**, all handled:

1. **Unbounded coasting.** Past `MAX_EXTRAPOLATION_S` the icon stops advancing,
   and from `STALE_FADE_S` it dims toward `STALE_MIN_OPACITY` so a coasting
   aircraft visibly reads as less certain.
2. **Fixes disagreeing with the guess.** When a real fix lands it will not match
   where we had projected. `upsertTrack` records that gap as `entry.blend` and
   `renderTracks` decays it over `SNAP_MS` with an ease-out, so the icon glides
   onto the truth. Without this, every poll teleports every aircraft.
3. **Heading wrap.** `unwrapHeading` rewrites the target as the nearest
   equivalent angle, so 350° → 10° turns 20° forward rather than 340° backward.

Aircraft that are `onGround`, or missing heading or speed, or slower than
`MIN_ANIMATE_SPEED_MPS`, are drawn at their raw fix and never coast.

**Cost.** The loop is `requestAnimationFrame`, throttled to
`RENDER_INTERVAL_MS` (~15fps — aircraft move slowly enough on screen that more
buys nothing visible). Viewport culling keeps it to the few hundred markers
actually on screen. The trigonometry is not the bottleneck; the DOM writes are,
so `entry.shown` records what is currently rendered and every write is skipped
when the value has not changed.

**Tuning** is the constants block near the top of the script:
`MAX_EXTRAPOLATION_S`, `STALE_FADE_S`, `STALE_MIN_OPACITY`, `SNAP_MS`,
`RENDER_INTERVAL_MS`, `MIN_ANIMATE_SPEED_MPS`. `MAX_EXTRAPOLATION_S` is set to
roughly one poll interval: long enough to keep motion continuous between polls,
short enough that an unseen turn cannot throw an icon absurdly far.

**Known limitation.** The 5-minute poll makes this inherently approximate — a
turn is invisible until the next fix. Shortening the poll needs OpenSky
authentication first (see Gotchas).
