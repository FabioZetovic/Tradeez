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

### BUG-023 🟠 `useDrawings.commit` swallows every save failure

**Status:** verified — it actively hid a broken save path for the whole of session 012
**Affects:** `frontend/src/hooks/useDrawings.ts`

Both branches of the commit end in `.catch(() => {})`. The intent was reasonable: an autosave
that throws mid-drag would surface as a crash over a transient network blip, and a failed save is
retried on the next commit because the id is left out of the persisted map.

The cost showed up immediately. Drawings were not saving at all — the backend serving the app was
a stale process without the drawings routes — and **the UI looked completely healthy**. Shapes
appeared, dragged and undid normally; the failure was only found by querying the database
directly and seeing zero rows.

**Likely fix.** Keep swallowing the throw, but surface the state: track consecutive failures and
show an unobtrusive "not saved" indicator once one is pending. A silent retry is fine; a silent
permanent failure is not, because the user only discovers it when their work is gone.

---

### BUG-010 🟡 Market data endpoints have no caching

**Status:** verified by inspection · **deliberately not fixed**
**Affects:** `GeneratedMarketDataProvider`

Every bars request regenerates from scratch; a one-day 1s request builds 86,400 bars each time.

Reviewed in the session-011 batch and **left alone on purpose**. A cache here would have to be
keyed on `(seed, range, timeframe, generatorVersion)` or it would serve stale prices after a
version bump — and the generator version has already changed twice. Adding that machinery before
anything has been measured is how you get a subtle correctness bug in exchange for a speed-up
nobody asked for. The honest position: measure when the replay engine exists and actually
requests overlapping ranges, then cache if the numbers justify it.

---

## Environment

### ENV-004 🟠 An interrupted session leaves its file claims behind

**Status:** verified (session 013)

Session 013 claimed `frontend/src/**`, `docs/**` and the whole backend test tree in
`PATH.md`, then hit Fabio's usage limit before writing any code. The claim survived the session
that made it. It happened to be uncommitted, so it only existed on this machine — but a session
that commits its claim and *then* dies would publish a lock with nobody behind it, and Codex
would correctly refuse to touch most of the repository.

The claim was reverted in the next exchange **without being noted**, which is itself against
`PATH.md`'s rule 4 ("note that you are taking it"). Recorded here to close that gap.

**Likely fix.** Claims carry the session id *and a timestamp*, and a claim older than 24 hours
with no matching commit on the remote is stale by definition. Cheaper still: claim in the same
commit as the first real change, so a claim never exists without work behind it.

---

### ENV-003 🟠 A failed `spring-boot:run` leaves the previous backend serving

**Status:** verified (session 012, cost about twenty minutes)

Restarting the backend while an older instance still holds the port fails with *"Port 8081 was
already in use"* — but the **old process keeps serving**, so the app still responds and looks
fine. Every request is answered by a build that predates the change under test.

This produced a convincing false trail: newly written endpoints returned 401, which read as an
authentication bug in code that had just passed 71 tests. The endpoints simply did not exist in
the running process.

**Workaround.** After starting the backend, check that the change is actually live rather than
that the server merely answers:

```bash
curl -s http://localhost:8081/v3/api-docs | grep -o '"/api/[^"]*"' | sort -u
```

A route missing from the OpenAPI document means a stale process, not a broken route.

---

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
| Demo volume | Invented, and **nothing volume-derived may be trusted** | There is no real volume to generate. Volume profile, VWAP-by-volume, relative volume and delta would all be reading structure out of a formula. Was BUG-009; it is not a defect, it is the nature of simulated data (DEC-033) |
| Rate limiting | In-memory, therefore per-instance | A second replica would allow the full quota again. A speed bump, not a guarantee; a shared store is the upgrade path if this is ever scaled out |
| `X-Forwarded-For` | Trusted as a best effort | The header is client-supplied and forgeable, so per-IP limits can be evaded by rotating it. The real fix belongs to the deployment's proxy, which must overwrite it — verify that wherever this is hosted |
| Deployment | Local only | DEC-002 — nothing is hosted yet, and nothing costs money |

---

## Fixed

> Entries keep their **original description** so the problem is still legible, with a
> **✅ FIXED** block appended saying how it was closed and which test proves it. Nothing is
> deleted — a bug that vanishes silently teaches nobody anything, and the reasoning is often more
> useful than the fix.

Kept for the lesson, not for the changelog.

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

**✅ FIXED — Session 011.**

A 20-second grace window on `AuthService.refresh`. A replay inside it is treated as the
same client racing itself and issues fresh tokens without revoking anything; a replay after it
still revokes the whole family. `@Version` added to `RefreshToken` (migration `V3`)
so two genuinely parallel transactions cannot both mint a successor.

The trade-off is deliberate and worth stating: an attacker replaying within seconds of a real
rotation does get a session. That is a far smaller harm than signing honest users out at random,
and it is what the OAuth security BCP recommends.

**Tests:** `BugFixTest.ConcurrentRefresh` — both the duplicate and the late-reuse paths.

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

**✅ FIXED — Session 011.**

Two layers. `AuthRateLimitFilter` throttles per client IP (20 per 5 minutes) and runs ahead
of Spring Security, so a flood never reaches BCrypt — an unthrottled login endpoint is a CPU
exhaustion vector as much as a credential one. `AuthService.login` additionally throttles per
account (10 per 15 minutes), because an attacker with a pool of addresses can spread guesses at
one account across many IPs and never trip a per-IP limit.

A successful sign-in clears the counter, so someone who mistyped a few times is not left locked
out after getting it right.

**Tests:** `BugFixTest.RateLimiting`.

**Still true, and now in Known limitations:** the limiter is in-memory and therefore per-instance,
and `X-Forwarded-For` is client-supplied and forgeable.

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

**✅ FIXED — Session 011.** In development `SecurityConfig` uses
`setAllowedOriginPatterns` to accept any localhost port. Deployed instances keep the explicit
list, because a wildcard there would let any site make credentialed requests and undo DEC-032.

The discriminator is `tradeez.cookies.secure`, which is false only on plain-HTTP localhost —
the same signal `JwtSecretGuard` uses.

**Verified** by signing in through a browser on port 49900 with no `CORS_ALLOWED_ORIGINS`
override, which is what the fix actually has to survive.

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

**✅ FIXED — Session 011.** `lib/chart/timeAxis.ts` formats the axis in the exchange's own
timezone by default, with a toggle back to UTC in the toolbar. `Intl` handles daylight saving,
so Chicago reads 09:00 in March and 08:00 in January for the same 14:00 UTC — a fixed offset
would have mislabelled every session boundary for half the year and taught killzone timings an
hour wrong.

**Tests:** `timeAxis.test.ts`, including both sides of a DST transition.

**Caused BUG-021** — the remount this required left the chart blank.

---

### BUG-003 🟡 `HealthController` swallows the underlying database error

**Status:** verified by inspection
**Affects:** `backend/.../common/HealthController.java`

The health check catches every exception and reports `DEGRADED` without logging what failed. When
the database is unreachable in a deployed environment, the endpoint says something is wrong but not
what — which is precisely when that information is most useful.

**Likely fix.** Log the exception at `WARN` before returning the degraded response. Do not put the
message in the response body: an unauthenticated endpoint should not leak connection strings.

**✅ FIXED — Session 011.** Logs at `WARN` with the exception before returning `DEGRADED`.
The message stays out of the response body: the endpoint is unauthenticated and a connection
string is not something to hand to the internet.

**Test:** `BugFixTest.healthReportsWithoutLeaking` asserts the body contains no `jdbc:`.

---

### BUG-004 🟡 `refresh_token.replaced_by` has no index

**Status:** verified by reading `V1__baseline.sql`

PostgreSQL does not automatically index foreign keys. `replaced_by` is unindexed, so deleting or
updating a referenced token requires a sequential scan to check the constraint. Invisible at current
scale; worth fixing before the table grows.

**Likely fix.** A new migration adding the index. Never edit `V1__baseline.sql` — DEC-030.

**✅ FIXED — Session 011.** `V3__refresh_token_hardening.sql` adds a partial index on
`replaced_by`. V1 was not edited — DEC-030 holds even for an index it should have had.

**Test:** `BugFixTest.replacedByIsIndexed` queries `pg_indexes`.

---

### BUG-005 🟡 `app_user.settings` exists but is never used

**Status:** verified

The column and its JPA mapping were added in the baseline migration for future user preferences.
Nothing reads or writes it. Harmless, but it is currently unexercised code: the JSONB mapping is
untested, so it may not actually work when someone first relies on it.

**Likely fix.** Either use it or cover it with a test when the first real preference lands.

**✅ FIXED — Session 011,** by covering it rather than removing it. The column stays for user
preferences, and a test now writes nested JSON and reads it back, so the Hibernate JSONB mapping
is exercised instead of merely present.

**Test:** `BugFixTest.settingsRoundTrip`.

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

**✅ CLOSED — Session 011, as not-a-defect.**

There is no real volume to generate, so this cannot be fixed — only disclosed. Moved to
[Known limitations](#known-limitations), where it belongs: it is a property of simulated data
(DEC-033), not a mistake in the code.

The rule it implies still stands: **nothing volume-derived may be built on a `SIMULATED`
series.** Volume profile, VWAP-by-volume, relative volume and delta would all be reading
structure out of a formula.

---

### BUG-027 🔴 The public bars endpoint let any client choose the market it read

**Status:** reasoned — latent, not exploitable before the Practice slice existed
**Fixed:** Session 014, at Fabio's instruction · **Found by:** reading the code while designing task 6
**Affects:** `MarketDataController`, `lib/api/marketData.ts`

`GET /api/market/instruments/{symbol}/bars` accepted a `seed` and any time range, past or future. A
simulated market is a pure function of its seed, so the endpoint was a generator any client could
drive.

That was harmless while the only simulated market was the public chart. It stops being harmless the
moment a practice session exists: whoever learns that session's seed can ask this endpoint for the
bars the replay has not revealed yet, from entirely outside the practice API, and never pass the
cursor check that DEC-009 relies on. One leaked seed — in a log line, an error message, a response
field added carelessly later — and the product's central promise is gone.

**Fix (DEC-038):** the endpoint now serves one fixed public market and rejects a `seed` parameter
with 400. The frontend no longer sends one. Practice seeds will be `SecureRandom`, never equal to the
public seed, withheld until the session completes, and readable only through the practice API.

**Tests:** `MarketDataApiTest.seedCannotBeChosen`, and `App.test` asserting no bars request carries
a seed. The test that used to assert *different seeds give different markets over HTTP* was asserting
the hole itself; it was rewritten, and seed-level determinism is still proven in
`DemoSeriesGeneratorTest`, where it belongs.

**Lesson:** with generated data, the generator's inputs are the data. Securing the replay's cursor is
worthless if another endpoint will regenerate what lies beyond it for anyone who asks with the right
number.

---

### BUG-026 🟡 `PATH.md` described a repository six sessions out of date

**Fixed:** Session 013 · **Found by:** an audit Fabio asked for

`CURRENT STATE.files_in_repo` was written in Session 006 and never updated. It listed the
Phase 0 scaffold — `HealthController`, `SecurityConfig`, one migration — and nothing
since: not the identity, market data or charting packages, not migrations V2–V4, not the drawing
layer. Sessions 007–012 each updated the fields beside it (`phase`, `head`,
`build_passing`) and walked straight past it.

This is the failure the file exists to prevent: a fresh session reading it would have believed
half the application did not exist.

**Fix:** replaced the hand-maintained file list with a module map that changes only when a module
is added, plus the instruction to run `git ls-files` for anything finer.

**Lesson:** a list that duplicates something the tooling already knows will rot, and it rots
silently because nothing fails when it is wrong. Point at the source of truth instead of copying it.

---

### BUG-022 🔴 One user could overwrite another's drawing by naming its id

**Fixed:** Session 012 · **Found by:** `ChartDrawingApiTest.cannotOverwriteAnotherUsersDrawing`

Drawing ids are client-assigned UUIDs that appear in the URL, so a client can name one it does
not own. The repository lookup was scoped by owner — `findByIdAndUserId` — which looked
like enough and was not.

Because the id is assigned rather than generated, Spring Data sees a non-null id, concludes the
entity is not new, and routes `save()` through `merge()`. Merge matches on the **primary
key alone**. The scoped lookup returned empty, the code took the "create" branch, and the merge
then rewrote the existing row — `user_id` included. A second user could silently take
ownership of any drawing whose id they could name.

**Fix:** check ownership against the whole table before creating, and refuse with **404** rather
than 403 — an id the caller cannot write to should be indistinguishable from one that does not
exist, or the endpoint becomes a way to probe which ids are in use.

**Lesson:** an owner-scoped *read* does not make a *write* safe. With a client-assigned primary
key, `save()` is an upsert against the key and nothing else — the ownership column is just
another field it will happily overwrite.

---

### BUG-024 🟡 `created_at` came back null on the create path

**Fixed:** Session 012

Same root cause as BUG-022, different symptom. `@PrePersist` sets the timestamps, but because
the client-assigned id sends `save()` through `merge()`, the persist callback did not fire
reliably and the `NOT NULL` constraint rejected the insert.

**Fix:** set the timestamps in the constructor, keeping `@PrePersist` as a fallback.

**Lesson:** JPA lifecycle callbacks are not a dependable place for required values when the
entity has an assigned id. If a column is `NOT NULL`, set it where the object is built.

---

### BUG-025 🟡 An unknown drawing kind was a 500

**Fixed:** Session 012

The only guard on the tool name was the database `CHECK` constraint, so a client typo arrived
as a `DataIntegrityViolationException` and a stack trace.

**Fix:** validate at the controller and return 400. The constraint stays — it is the guarantee —
but it is no longer the only check.

**Lesson:** a database constraint is a backstop, not an input validator. Both belong, and the one
nearest the client should produce the message.

---

### BUG-021 🟠 The chart went blank when the timezone was toggled

**Fixed:** Session 011 · **Found by:** clicking the toggle, after the suite was green

Switching the axis between exchange time and UTC remounts the chart — Lightweight Charts fixes
its time formatter at creation, so the format cannot be changed on a live instance. The remount
produced a fresh chart with no data, and the effect that loads bars did not re-run because
`bars` had not changed. The user got an empty chart.

**Fix:** the mount effect now seeds the new instance from a ref holding the current bars.

**Lesson:** an effect that rebuilds a stateful object has to restore that object's state too.
Splitting "create it" and "fill it" across two effects with different dependencies means the
second one silently fails to run whenever only the first is triggered.

---

### BUG-017 🔴 A rate limiter broke eleven unrelated tests

**Fixed:** Session 011 · **Found by:** the suite, immediately after adding the limiter

Adding per-IP throttling turned eleven passing market-data tests red with `429`. The limiter is an
application singleton keyed by client IP, and every MockMvc request in the suite arrives from the
same address — so authentication tests spent the quota and everything that ran afterwards was
throttled before it could sign in.

**Fix:** `RateLimiter.clear()`, called from `AbstractIntegrationTest` before every test, so
isolation is inherited rather than remembered per class.

**Lesson:** a singleton holding mutable state is shared by the whole test suite, not just by
production. Any new bean that accumulates anything needs a reset hook in the same commit.

---

### BUG-018 🟠 The reuse test was asserting the bug

**Fixed:** Session 011

Fixing BUG-001 turned `reuseOfRotatedTokenRevokesTheWholeFamily` red. The test replayed a
rotated token *immediately* and expected every session to be revoked — which is exactly the
behaviour that made BUG-001 a bug. The test had faithfully encoded the defect.

**Fix:** the test now ages the rotation past `REUSE_GRACE` before replaying, so it exercises
genuine theft rather than a client racing itself.

**Lesson:** when a fix breaks a test, decide which one is wrong before touching either. Here the
test was — and it would have been easy to "restore" it and undo the fix instead. Same judgement as
BUG-008, in the opposite direction.

---

### BUG-019 🟡 A JWT-tampering test was silently flaky

**Fixed:** Session 011 · **Found by:** an intermittent failure with no code change behind it

`forgedTokenIsRejected` flipped the final character of the token and expected verification to
fail. A 32-byte HMAC-SHA256 signature is 43 base64url characters, and the last one carries only
four significant bits plus two of padding — so characters differing only in those padding bits,
**`A` and `B` among them**, decode to identical bytes. The test's `A`-to-`B` flip changed the
string without changing the signature whenever a token happened to end in `A`.

It passed for several sessions on luck: every run creates a new user, so the signature — and its
last character — differs each time.

**Fix:** tamper with the payload segment instead. Altering what was signed always invalidates.

**Lesson:** "change one character" is not the same as "change the value" in any encoding with
padding. A test that only sometimes exercises its own premise is worse than no test, because the
green run is indistinguishable from a real one.

---

### BUG-020 🟡 An eviction test failed against correct code, on clock granularity

**Fixed:** Session 011

The limiter-eviction test created 500 windows with a 1ms expiry and asserted they were all
evicted. 499 survived. Windows' clock resolution is around a millisecond, so the whole loop and
the eviction that followed it landed inside a single tick — nothing had expired yet.

**Fix:** a 50ms pause before evicting, and `evictExpired` now uses `!isAfter` rather than
`isBefore` so a window expiring exactly on the current instant counts as finished.

**Lesson:** the third test in this project to fail against correct code (after BUG-012 and
BUG-019). All three asserted something the test itself could not reliably produce. When a test
fails, "is my assertion actually testing what I think" comes before touching the implementation.

---

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
