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

`FETCH_INTERVAL_MS` is now 30000, against a server that refreshes every 120s.
Combined with §1 that is **~13× less traffic**: ~8 MB/min/user → ~616 KB/min.
60s was tried first (~26×) and deliberately backed off to 30s, trading some
bandwidth for a shorter extrapolation cap and therefore less guessing.

This was only possible because of dead reckoning. Before it, a fetch was the
only thing that moved an aircraft, so it had to be frequent; now icons coast
from the last fix and a slower fetch looks identical on screen.

**`MAX_EXTRAPOLATION_S` moved with it, 150 → 180.** The browser can hold a fix
for `OpenSky lag (~20s) + server poll (120s) + fetch interval (30s) = 170s`, and
a cap below that sum freezes aircraft at the cap before their replacement lands.
Changing either interval should move the cap too.

---

## 3. Startup takes 156 seconds — accepted, not a bug

```
Started FlighttrackerApplication in 155.891 seconds (process running for 203.71)
```

Roughly 48s of that is JVM boot and unpacking a 51 MB fat jar before Spring
starts; the remaining ~108s is Spring context initialisation. Both are
CPU-bound, and the app runs on a low App Service tier where CPU is a fraction of
a core. That is the dominant factor, not anything in the code.

**The practical consequence:** every deploy takes the site down for ~2.5
minutes. A request during that window gets `ERR_TIMED_OUT`, not an error page.
Worth knowing when choosing deploy timing; it is not a symptom of a broken
change.

**Explicitly ruled out by the owner — do not re-propose:**

- **Upgrading the App Service tier.** Would likely fix most of it, since this is
  CPU-bound, but costs money and the current behaviour is fine for a portfolio
  project.
- **Deployment slots** (deploy to staging, let it warm, swap for zero-downtime).
  The correct fix for the downtime, but requires Standard tier or above.

**Free things still on the table, in order of effort:**

1. **`WEBSITE_RUN_FROM_PACKAGE=1`** — an App Service environment variable, no
   code change. Runs the jar from a mounted package instead of copying files onto
   network storage, which can cut the pre-Spring portion. Cheapest thing to try;
   measure the `Started ... in` line before and after.
2. **`ddl-auto=update` → `validate`, behind Flyway.** Saves the schema
   introspection Hibernate performs on every boot. Likely only a few seconds on
   two tables, so this is mostly worth doing for migration hygiene rather than
   for startup.

**Not recommended:** `spring.main.lazy-initialization=true` trades fail-fast
startup for an uncertain gain, and the scheduler needs its beans anyway.

Since a push costs ~2.5 minutes of downtime, batch documentation-only changes in
with the next real one rather than deploying them alone.

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
Less valuable now that §2 has landed: at a 30s fetch against a 120s refresh,
about three of every four responses are still duplicates, so an ETag would cut
most of the remaining bytes. Worth revisiting if viewer count grows.

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
