# Performance backlog

Known work, not yet done. Ordered by value per unit of effort. Numbers here are
measured against production unless noted.

---

## ~~1. Responses are not compressed~~ — DONE

`server.compression` enabled for JSON, HTML, CSS and JS above 1 KB. `/flights`
was **2.01 MB** uncompressed and gzips to **~308 KB**. Verified against a
locally booted jar: `Content-Encoding: gzip` present when requested, absent when
not, and `index.html` measured 26,649 → 8,088 bytes.

---

## ~~2. The browser polls 8× more often than the data changes~~ — DONE

`FETCH_INTERVAL_MS` is now 60000, against a server that refreshes every 120s.
Combined with §1 that is **~26× less traffic**: ~8 MB/min/user → ~308 KB/min.

This was only possible because of dead reckoning. Before it, a fetch was the
only thing that moved an aircraft, so it had to be frequent; now icons coast
from the last fix and a slower fetch looks identical on screen.

**`MAX_EXTRAPOLATION_S` moved with it, 150 → 210.** The browser can hold a fix
for `OpenSky lag (~20s) + server poll (120s) + fetch interval (60s) = 200s`, and
a cap below that freezes aircraft at the cap before their replacement lands.
Shortening either interval should shorten the cap again.

---

## 3. Startup takes 156 seconds

```
Started FlighttrackerApplication in 155.891 seconds (process running for 203.71)
```

Not urgent — it only lengthens deploys. It becomes urgent near Azure's ~230s
startup limit, where deploys begin failing health checks. That ~48s gap before
Spring even starts is JVM boot and unpacking a 51 MB fat jar off App Service's
network-mounted storage.

Try in this order, measuring one at a time:

1. **`WEBSITE_RUN_FROM_PACKAGE=1`** (app setting, no code change) — runs the jar
   from a mounted package instead of copying files onto the share.
2. **Check the App Service tier.** On B1 you get a fraction of a CPU and JVM
   startup is CPU-bound. Most likely explanation for the 48s.
3. **`ddl-auto=update` → `validate`.** Every boot, Hibernate interrogates the
   whole schema through JDBC metadata to discover that nothing changed. Requires
   adopting Flyway first, since `update` is currently how schema changes ship
   (that is how `time_position` and the indexes were created).

**Not recommended:** `spring.main.lazy-initialization=true`. It trades fail-fast
startup for an uncertain gain, and the scheduler needs its beans anyway.

---

## 3b. `/flights` sends more than the map uses

2.01 MB for ~6,000 aircraft is ~350 bytes each. Coordinates carry full double
precision (`40.134899999999998`) when ~5 decimals is under a metre, and fields
like `lastContact` and `originCountry` are not read by the map. Now that §1 has
landed, compression already removes most of the redundancy that verbosity
creates, so this is much lower value than it looked — measure before bothering.

---

## 3c. Serve a 304 when nothing changed

Between server polls the payload is identical. An ETag over the newest snapshot
timestamp would let unchanged requests return `304 Not Modified` with no body.
Largely redundant now that §2 has landed and the fetch interval is 60s against a
120s refresh — at most one duplicate response per cycle.

---

## 4. Hikari pool is 5 connections

Fine today: each `/flights` query is well under a second and traffic is light.
Worth revisiting if concurrent viewers reach the dozens, since every request
holds a connection for the duration of the query. Raise the pool and the Azure
Postgres connection limit together.

---

## 5. Not a problem — recorded so nobody "fixes" it

**OpenSky fetch cost does not scale with viewers.** The scheduler polls on a
timer inside the app, so credit consumption is fixed at 720 calls/day no matter
how many people have the map open. This is the payoff of poll-and-store over
proxying OpenSky per request, which would exhaust the daily budget in under an
hour with a handful of users. Keep it that way.
