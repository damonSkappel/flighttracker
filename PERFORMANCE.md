# Performance backlog

Known work, not yet done. Ordered by value per unit of effort. Numbers here are
measured against production unless noted.

---

## 1. Responses are not compressed — 6.5× on the wire, one property

**Measured:** `/flights` returns **2.01 MB** with no `Content-Encoding` header.
The same payload gzips to **308 KB**.

```properties
server.compression.enabled=true
server.compression.mime-types=application/json,text/html,text/css,application/javascript
server.compression.min-response-size=1024
```

Traffic scales with viewers (the OpenSky poll does not — see §7), so this
multiplies across every user. At a 15s browser poll one viewer currently pulls
~480 MB/hour.

**Risk:** essentially none. Costs a little CPU per response.

---

## 2. The browser polls 8× more often than the data changes

`setInterval(updateFlights, 15000)` in index.html, against a server that
refreshes every 120s. Seven of every eight responses are byte-identical.

This was reasonable before dead reckoning, when a fetch was the only thing that
moved an aircraft. It no longer is — icons animate continuously from the last
fix, so a slower fetch looks the same on screen.

Raising it to 60s cuts requests 4×. Combined with §1 that is **~26× less
traffic**: 8 MB/min/user → ~308 KB/min/user.

**Watch:** `flighttracker.active-window-minutes` (6) must stay comfortably above
the browser interval too, not just the server poll.

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

## 4. `/flights` sends more than the map uses

2.01 MB for ~6,000 aircraft is ~350 bytes each. Coordinates carry full double
precision (`40.134899999999998`) when ~5 decimals is under a metre, and fields
like `lastContact` and `originCountry` are not read by the map. Trimming and
rounding could roughly halve it — do §1 first, since compression already removes
most of the redundancy that verbosity creates.

---

## 5. Serve a 304 when nothing changed

Between server polls the payload is identical. An ETag over the newest snapshot
timestamp would let unchanged requests return `304 Not Modified` with no body.
Mostly redundant if §2 lands; worth it if the browser interval stays short.

---

## 6. Hikari pool is 5 connections

Fine today: each `/flights` query is well under a second and traffic is light.
Worth revisiting if concurrent viewers reach the dozens, since every request
holds a connection for the duration of the query. Raise the pool and the Azure
Postgres connection limit together.

---

## 7. Not a problem — recorded so nobody "fixes" it

**OpenSky fetch cost does not scale with viewers.** The scheduler polls on a
timer inside the app, so credit consumption is fixed at 720 calls/day no matter
how many people have the map open. This is the payoff of poll-and-store over
proxying OpenSky per request, which would exhaust the daily budget in under an
hour with a handful of users. Keep it that way.
