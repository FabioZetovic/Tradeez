# BUGS.md — Known Defects and Limitations

> **Purpose.** One place for everything currently wrong, everything deliberately left undone, and
> everything already fixed that carries a lesson worth not relearning.
>
> **Maintained by both LLMs.** If you find a bug and do not fix it in the same session, it goes here
> before the session ends. If you fix one, move it to [Fixed](#fixed) with the lesson — do not
> delete it. A bug that was fixed silently teaches nobody anything.
>
> **Honesty rule.** Every entry states whether it is **verified** (reproduced, or proven by a
> failing test) or **reasoned** (identified by reading the code, not yet demonstrated). Never
> present the second as the first.

**Severity:** 🔴 breaks correctness or security · 🟠 degrades the product · 🟡 annoyance or debt

---

## Open

### BUG-001 🔴 Concurrent refresh requests can log a user out of every device

**Status:** reasoned, not yet reproduced by a test
**Found:** Session 009, reading `AuthService.refresh`
**Affects:** `backend/.../identity/AuthService.java`

**What happens.** Refresh tokens rotate on every use, and replaying an already-rotated token is
treated as theft — it revokes every session for the account (DEC-032, and BUG-007 below explains
why that revocation is deliberate). But a legitimate client can present the same token twice
without any attacker involved.

If the SPA has two requests in flight and both receive a 401, both call `/api/auth/refresh` with
the same cookie. Serialised, the first rotates successfully; the second then sees
`isAlreadyRotated() == true`, concludes the token was stolen, and invalidates every session for
that account. **The user is signed out everywhere because their own app made two requests at once.**

Two tabs, a flaky connection, or a page that fires several API calls on load are all enough. This is
a well-known failure mode of refresh-token rotation, not an exotic edge case.

**Secondary effect.** `RefreshToken` has no `@Version`, so there is no optimistic locking. Two
*truly* parallel transactions could instead both succeed and mint two valid successors, orphaning
one. Less damaging than the false logout, still wrong.

**Likely fix.** A short grace window: if the presented token was rotated within, say, 30 seconds and
its successor is still active, treat it as a duplicate request rather than theft and return the
existing successor. Real reuse by an attacker happens long after rotation, so a narrow window
preserves the security property. Add `@Version` to `RefreshToken` at the same time.

**Test to write first:** two concurrent refreshes with the same token must both resolve without
revoking the account.

---

### BUG-002 🟠 No rate limiting on authentication endpoints

**Status:** verified (no such code exists)
**Found:** Session 008, noted in `AuthExceptionHandler`
**Affects:** all of `/api/auth/**`

`/api/auth/login` accepts unlimited attempts, so passwords can be brute-forced. `/api/auth/register`
returns `409 Conflict` for an address that already exists — which is unavoidable, since a
registration form has to tell you the address is taken — making it an account-enumeration oracle
that can be walked at full speed.

Vague error messages do not fix this; **rate limiting is the actual mitigation** and its absence is
the real gap. Deliberately not built in Session 008 to keep that change reviewable, but it should
land before anything is publicly deployed.

**Likely fix.** Per-IP and per-account limits with exponential backoff (Bucket4j, or a filter over a
small in-memory/Redis counter).

---

### BUG-003 🟡 `HealthController` swallows the underlying database error

**Status:** verified by inspection
**Affects:** `backend/.../common/HealthController.java`

The health check catches every exception and reports `DEGRADED` without logging what failed. When
the database is unreachable in a deployed environment, the endpoint says something is wrong but not
what — which is precisely when that information is most useful.

**Likely fix.** Log the exception at `WARN` before returning the degraded response. Do not put the
message in the response body: an unauthenticated endpoint should not leak connection strings.

---

### BUG-004 🟡 `refresh_token.replaced_by` has no index

**Status:** verified by reading `V1__baseline.sql`

PostgreSQL does not automatically index foreign keys. `replaced_by` is unindexed, so deleting or
updating a referenced token requires a sequential scan to check the constraint. Invisible at current
scale; worth fixing before the table grows.

**Likely fix.** A new migration adding the index. Never edit `V1__baseline.sql` — DEC-030.

---

### BUG-005 🟡 `app_user.settings` exists but is never used

**Status:** verified

The column and its JPA mapping were added in the baseline migration for future user preferences.
Nothing reads or writes it. Harmless, but it is currently unexercised code: the JSONB mapping is
untested, so it may not actually work when someone first relies on it.

**Likely fix.** Either use it or cover it with a test when the first real preference lands.

---

### BUG-009 🟡 Demo bar volume is invented and must never be analysed

**Status:** verified (by design, recorded so it is not forgotten)
**Affects:** `DemoSeriesGenerator`, every `SIMULATED` series

Volume on a generated bar is derived from that bar's range. It correlates with price movement,
which makes charts look plausible, and it means **absolutely nothing**. Anything volume-derived —
volume profile, VWAP weighted by volume, relative volume, delta — would be reading structure out of
a formula.

Not fixable: there is no real volume to generate. Recorded because a plausible-looking number is
more dangerous than an obviously missing one. Documented on `Bar#volume`.

---

### BUG-010 🟡 Market data endpoints have no caching

**Status:** verified by inspection
**Affects:** `GeneratedMarketDataProvider`

Every bars request regenerates from scratch. A one-day 1s request builds 86,400 bars each time it is
asked for. Correct and currently fast enough, but the replay engine will request overlapping ranges
constantly, so this is worth measuring before the Practice slice ships.

Deliberately not cached yet: a cache keyed on anything other than
`(seed, range, generatorVersion)` would serve stale data after a version bump, which is a worse
bug than being slow.

---

### BUG-013 🟡 Vite's proxy forwards the browser Origin, so CORS must match the dev port

**Status:** verified (hit while previewing task 4)
**Affects:** `frontend/vite.config.ts`, `tradeez.cors.allowed-origins`

The dev proxy is configured with `changeOrigin: true`, which rewrites the `Host` header but
still forwards the browser's real `Origin`. Spring therefore sees a cross-origin request and
applies CORS. It happens to work on port 5173 because that is the configured allowed origin — on
any other port every authenticated call fails with **403**, which looks like a CSRF or auth problem
and is neither.

Hit immediately here because Docker Desktop occupies 5173 on this machine (see ENV-002), so the dev
server landed on a random port.

**Workaround.** Pass the actual origin when starting the backend:

```bash
CORS_ALLOWED_ORIGINS="http://localhost:5173,http://localhost:<actual-port>" ./mvnw spring-boot:run
```

**Likely fix.** Allow a localhost wildcard pattern in development only, via
`CorsConfiguration.setAllowedOriginPatterns`. Deployed environments keep the explicit list —
a wildcard there would defeat the point of DEC-032.

---

### BUG-014 🟡 Chart time axis is UTC-only

**Status:** verified by inspection
**Affects:** `lightweightAdapter.ts`

The time axis is formatted as UTC. That is consistent with how everything is stored (INVARIANT 4)
and unambiguous, but it is not how a trader reads a chart: an ICT curriculum is built on New York
and London session times, and mentally offsetting every label is exactly the kind of friction that
makes killzone training harder than it needs to be.

**Likely fix.** Render in the instrument's exchange timezone, which the API already returns as
`exchangeTimezone`, with a toggle for UTC. Needs care around daylight-saving transitions —
`SessionCalendar` already concentrates that logic on the backend.

---

## Environment

### ENV-002 🟡 Docker Desktop occupies ports 8080 and 5173 on this machine

**Status:** verified (task 4)

`com.docker.backend.exe` and `wslrelay.exe` listen on both 8080 and 5173, so the backend
and the Vite dev server cannot use their defaults here. Spring reports *"Port 8080 was already in
use"* and the preview tooling refuses 5173.

**Workaround.** `PORT=8081` for the backend; Vite reads `process.env.PORT` so the harness can
assign one. Remember BUG-013: a non-default frontend port needs a matching `CORS_ALLOWED_ORIGINS`.

Not an application defect, but it costs time on every fresh checkout on this machine.

---

### ENV-001 🟠 Docker Desktop's daemon stops on its own, breaking the backend test suite

**Status:** verified, repeatedly (Sessions 006, 008)

The backend tests need Docker (Testcontainers). On this machine the daemon has gone away
mid-session more than once: `docker --version` still answers, because that is only the CLI binary,
while `docker version` fails with *"failed to connect to the docker API at
npipe:////./pipe/dockerDesktopLinuxEngine"*.

The tests then fail with `ExceptionInInitializerError` / `NoClassDefFoundError` on
`AbstractIntegrationTest`, which looks like a compilation or wiring problem and is not one. **Check
the daemon before debugging the code** — this has cost time twice.

**Workaround.** Start Docker Desktop and wait for the daemon, not just the process:

```bash
until docker version --format '{{.Server.Version}}' >/dev/null 2>&1; do sleep 5; done
```

Not an application defect; recorded because the symptom is so misleading.

---

## Known limitations

Not bugs. Deliberate decisions, listed so nobody mistakes them for oversights.

| Area | Limitation | Why |
|---|---|---|
| Auth | No login or registration **UI** | API complete and tested; screens arrive with the first real pages |
| Auth | No email verification or password reset | Out of scope for the vertical slice |
| Market data | No real NQ data anywhere in the project | DEC-033 — `DEMO-NQ` only, and real data needs written permission (DEC-020, DEC-035) |
| Training | Drills built on simulated data cannot be graded | DEC-034 — teaching from invented data would be circular |
| Fill engine | `AMBIGUOUS` is a legitimate outcome, not a failure | DEC-008 — candle data genuinely cannot always say which of TP/SL came first |
| Order flow | Not implemented, likely never | Impossible without licensed depth data (open question Q-03) |
| Deployment | Local only | DEC-002 — nothing is hosted yet, and nothing costs money |

---

## Fixed

Kept for the lesson, not for the changelog.

### BUG-015 🔴 Generated prices ran away to roughly minus 417,000

**Fixed:** Session 010 · **Found by:** looking at the chart, not by a test

The price function added a linear drift term, `days * driftTicksPerDay`. The bug is in what
`days` counted: seconds since the **Unix epoch** divided by 86,400, so about 19,800 days by
2024 — not days into the series. Multiplied by a few hundred ticks a day, the chart opened at
about **minus 417,000 index points**. A futures price cannot be negative.

**What makes this worth recording is that fourteen passing tests did not catch it.** Every
integrity check compared prices *to each other* — high at least low, open inside the range,
strictly increasing timestamps, plausible volatility spread — and all of those hold perfectly well
on a series centred on minus 417,000. Nothing asserted the price level itself was sane, because it
never occurred to me that it might not be.

**Fix:** replaced the unbounded drift with a slow seasonal sine, so every component of the price is
bounded. Generator version bumped to `demo-gen-1.2.0`.

**Test added:** prices must be positive and within an order of magnitude of the product's real
scale, checked across four dates spanning 2013–2031 and three seeds.

**Lesson:** relational invariants are not the same as absolute ones. A suite that only checks
quantities against each other will happily certify output that is nonsense on sight — and the
cheapest way to find this class of bug is to *look at the thing you built*.

---

### BUG-016 🟡 PriceChart remounted on every parent render

**Fixed:** Session 010 · **Found by:** `PriceChart.test.tsx`

`adapterFactory` was listed in the mount effect's dependencies. Any caller passing an inline
arrow — the natural way to write it — produced a new reference every render, so the chart was
destroyed and rebuilt each time. During a replay the parent re-renders on every bar, which would
have discarded the user's zoom and pan continuously.

**Fix:** hold the factory in a ref, read once at mount.

**Lesson:** a dependency that is only *read* at mount does not belong in the dependency array.

---

### BUG-011 🟠 Demo generator produced barely-varying volatility regimes

**Fixed:** Session 009 · **Found by:** `DemoSeriesGeneratorTest.volatilityRegimesExist`

Regimes were selected with `new Random(seed * 7919 + regimeBlock).nextInt(4)`. Regime blocks are
consecutive integers by construction, and consecutive LCG seeds produce correlated first outputs, so
the "random" regime barely changed: the busiest three-hour block was only about 1.6x wider than the
quietest, where it should have been several times wider. The generated market had one texture, which
would have made regime a thing the app could not teach.

**Fix:** a splitmix64 avalanche of the composed key, so structurally related inputs give unrelated
outputs. Regime spread widened too (1.5 / 4 / 10 / 22 ticks).

**Lesson:** `new Random(x).nextInt(n)` for systematically related `x` is not random. If a
seed is composed from structured inputs — a counter, a timestamp, an id — mix it before use.

**Second lesson, about the debugging:** the first attempt to diagnose this reimplemented Java's
`nextInt(4)` in JavaScript and concluded the distribution was fine. It was wrong — Java takes the
*high* bits for a power-of-two bound and the reimplementation took the low bits, which are exactly
the bits an LCG is worst at. A reimplementation used as evidence has to be verified against the real
thing first.

---

### BUG-012 🟡 A generator test failed against correct code

**Fixed:** Session 009 (the test was rewritten, the code was not)

`volatilityRegimesExist` originally compared the 99th percentile bar range against the median
across a single generated day. Regimes change every three hours, so one day samples only a handful
of them and the violent regime frequently never appeared — the assertion failed on output that met
the requirement.

**Fix:** compare per-block medians across a full trading week, which measures the property the
requirement actually names.

**Lesson:** a statistical assertion needs a sample large enough for the effect it claims to measure.
This test failed twice before it was right, and only the second failure was the code's fault.

---

### BUG-006 🔴 `deleteAll()` violated the self-referencing foreign key

**Fixed:** Session 008 · **Found by:** `AuthFlowTest` setup failing on 14 tests

`refresh_token.replaced_by` points at `refresh_token`. Spring Data's `deleteAll()` issues one
`DELETE` per row, so deleting a token still referenced by its successor fails immediately.

**Fix:** `deleteAllInBatch()` — a single bulk `DELETE` defers the constraint check to the end of the
statement, by which point every row is gone.

**Lesson:** any self-referencing table has this trap in test cleanup.

---

### BUG-007 🔴 The reuse-detection revocation was silently rolled back

**Fixed:** Session 008 · **Found by:** the test asserting the *victim's* token also dies

This is the most instructive bug in the project so far.

`refresh()` is `@Transactional`. On detecting a replayed token it revoked every session for the
account and then threw `TokenReuseDetectedException` to reject the request. **The throw rolled the
transaction back and undid the revocation.**

From the outside everything looked correct — the attacker received a `401`. But the victim's
sessions stayed alive, so the security response was a no-op that nobody would have noticed. The
obvious test ("does the attacker get rejected?") passes against the broken code. Only asserting the
*victim's current token also stops working* exposes it.

**Fix:** `@Transactional(noRollbackFor = TokenReuseDetectedException.class)`.

**Lesson, worth generalising:** **any "record something, then reject the request" path has this
trap.** Rejecting by throwing inside a transaction discards whatever you just recorded — audit
entries, lockout counters, fraud flags. Whenever a side effect must survive a rejection, say so
explicitly. And a security feature that silently does nothing is worse than none, because it is
trusted.

---

### BUG-008 🟡 `logout` returned 403 in tests

**Fixed:** Session 008 — **by fixing the test, not the code**

`POST /api/auth/logout` rejected a request carrying a valid session cookie but no CSRF token. The
first instinct was to add logout to the CSRF exemption list. That would have been wrong: a forced
logout is a real nuisance attack, and the SPA always holds a CSRF token while signed in, so
requiring one costs nothing.

**Fix:** the test now sends a CSRF token. Only `login`, `register` and `refresh` are exempt.

**Lesson:** when a test fails against security config, decide which one is wrong before changing
either. The failing test was describing correct behaviour.
