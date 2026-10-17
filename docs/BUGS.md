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

*No open bugs.* An audit at the end of session 017 — Fabio asked for every tracked bug to be fixed,
and none was open — found four that had never been logged (BUG-040 to BUG-043); a review before the
merge of T7 found three more (BUG-044 to BUG-046), building T8 three more (BUG-047 to BUG-049), and
building T13 one (BUG-050), the fresh-clone check of T14 one (BUG-051), the upgrade to the newest
versions three (BUG-052 to BUG-054, one a security bug), Fabio's practice report two (BUG-055,
BUG-056), the bug hunt Fabio asked for four (BUG-057 to BUG-060), building T16 two (BUG-061, BUG-062), and
building T35 two (BUG-063, BUG-064), and its fresh-clone check one (BUG-065). All twenty-six are fixed, and
ENV-007 with them.
Every closed entry is under [Fixed](#fixed) with its original description intact.

---

## Environment

### ENV-011 🟡 A mutation run counted a build that never started as fourteen kills

**Status:** verified and fixed (session 017, T35, 2026-10-16)

The backend mutation script ran `mvnw.cmd` through Node, and Node's shell on this machine does not look
in the working directory: every run failed at once with "not recognized", and the script counted any
failure as a test catching the mutation. Fourteen kills arrived in seconds, which is what gave it away —
a Testcontainers run takes a minute or two. The sources were intact (each restore ran). Fixed in the
runner: it calls `.\mvnw.cmd`, runs the unmutated code first and stops unless that passes, counts a
kill only when the output shows a failing test, and reports a compile error or a launch failure as
what it is. The frontend runner got the same checks and was re-run. While fixing it, a backtick inside a
double-quoted shell string was executed as a command and started a stray test run (stopped): scripts
with backticks are written to a file, never inlined.

### ENV-010 🟡 Summing Surefire's report headers undercounts tests in nested classes

**Status:** verified (session 017, 2026-10-16) — **worked around**

The fresh-clone check added up the `tests=` attribute of each `TEST-*.xml` report. For
`BracketOutcomeApiTest` that attribute says 10 while the file holds 12 `<testcase>` elements — its two
`@Nested` groups are partly left out of the header — so the clone seemed to run 431 tests against
Maven's 433. Nothing was missing. **Count `<testcase>` elements** (and `<failure` / `<error` ones) instead;
the check does so from now on. A stale report from a deleted test class can also linger in a local
`target/`; a fresh clone has none.

### ENV-009 🟡 knip sometimes cannot allocate its parser's memory

**Status:** verified (session 017, after DEC-056) — **fixed**

Since Node 24 and knip 6.41, `npx knip` sometimes stops with "RangeError: Array buffer allocation
failed" inside `oxc-parser`, which reserves one large block of memory per parse. In the fresh-clone
check of `chore/upgrade-dependencies` it failed twice and passed on the next runs; in the project
folder it passed every time. When it runs it reports nothing unused. Not a code problem: it depends on
how much memory the machine can hand out at that moment (16 GB, about 4 free with Docker running).
**If it happens, run it again.** Revisit if it starts failing every time.

**✅ FIXED — the same session.** It did start failing every time: the machine's committed memory was
nearly used up (5.4 of 27.4 GB free) by the apps open on it, and V8 itself could not grow. knip has a
switch for it — `KNIP_DISABLE_RAW_TRANSFER=1` parses without the big block. knip is now a locked dev
dependency run as `npm run knip`, which sets the switch from `frontend/knip.env`. Three runs in a
row clean, and a planted unused export is still caught.

### ENV-007 🟠 Dependencies updated outside the session stopped the frontend tests on Node 22.14

**Status:** verified (session 017, T14) — **fixed** (DEC-056)

While the session was paused at a usage limit (2026-10-15, 18:47 local), something outside it ran an
npm install or update in `frontend/`: `package-lock.json` and `node_modules` moved several packages
up within their ranges, among them `undici` 8.10.0 → 8.12.2. That version requires Node ≥ 22.19; this
machine has 22.14, and every vitest worker now fails to start ("Cannot read properties of undefined
(reading 'bind')" in `undici/lib/interceptor/decompress.js`). Lint, typecheck, knip and the build are
unaffected; only the test runner loads undici.

Not undone here, because the change was not this session's to undo: the lockfile change is left
uncommitted in the working tree and kept out of every commit, and the tests were run on the committed
lockfile in a scratch copy (`npm ci`), where all pass. A fresh clone is unaffected. **To fix, either**
upgrade Node to ≥ 22.19 (then the update can be committed), **or** discard it:
`git checkout frontend/package-lock.json` then `npm ci` in `frontend/`.

**✅ FIXED — Session 017 (DEC-056).** The project's Node was 22.14, so the update was first discarded
— which showed the old undici had five high-severity advisories. Fabio: update everything to the
newest. Node is now 24.20 LTS, npm 12.2, every package at its newest, `npm audit` clean, and the tests
run here again. (Discarding it the first time, `npm ci` failed on a file held open by a dev server
started from VS Code and left `node_modules` half emptied; restoring one package's metadata from an
identical copy let `npm install` finish without touching the locked file.)

### ENV-008 🟡 Two mutation-check runs at once swapped two source files

**Status:** verified and fixed (session 017, T14)

A mutation script backs a file up, plants a bug, runs the tests and restores the backup. It was
started twice by accident, and both runs shared one backup file: each restored the other's file, and
`RuleText.java` ended up holding `LessonService.java`'s source while `LessonService.java` kept a
planted bug. Caught by checking the files after the run; both were rebuilt (`RuleText` from the
session's own record of it, `LessonService` from the copy that had landed in the wrong file) and every
test re-run. Lessons: a mutation run's backups are named per mutation and per file, a run is started
once and waited for, and the sources are grepped for every planted string before anything else.

### ENV-006 🟡 The Vite dev server can serve a file from before its last edit

**Status:** verified (session 017, T7)

Checking T7 in the browser, the Practice screen had the clock overlay but not the clock switches,
though both were in the source. The switches had been added by a shell `sed -i`, which replaces a
file rather than writing into it; on Windows, Vite's watcher can miss that and keep serving the old
module. Restarting the dev server fixed it. Not a code bug: tests, typecheck and the production build
read the files on disk.

Two lessons. When the browser disagrees with the source, restart the dev server before debugging the
code. And when checking what Vite serves, remember it serves compiled JavaScript: JSX such as
`<ClockToggles shown=` appears as `_jsxDEV(ClockToggles, { shown:`, so searching for the JSX text
finds nothing even when the module is current.

**✅ MITIGATED — Session 017.** Source files are no longer edited with `sed -i` while a dev server
runs: edits go through the editor tool or a script that writes the file in place, and Vite picks both
up (checked: the layout change that followed was served at once).

### ENV-005 🔴 A real API key was typed into the committed template file

**Status:** verified (session 017) — caught before any commit

`backend/.env` is gitignored; `backend/.env.example`, the template next to it, is committed. Fabio
put his new Massive key in the template instead of the private file. Nothing pushed it: the next
commit staged only the docs, by name. But one `git add -A` would have published the key to GitHub,
and a key that reaches a remote has to be treated as leaked even after it is deleted.

The two files differ by one word and look identical in an editor, so this mistake will recur.

**Mitigation.** `EnvExampleTest` fails the backend build whenever any `*_API_KEY=` line in
`.env.example` has a value, naming the variable but never printing the value. It failed exactly
once, on Fabio's local file, and the committed template stays clean. Commits in this repository
stage files by name, never `-A`.

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


**✅ FIXED — Session 015, in the protocol.** `PATH.md` rule 5: claim in the same commit as the
first real change, with the session id and date; a claim older than 24 hours with no matching commit
on the remote is stale and may be taken (noting it, per rule 4). Followed in sessions 014 and 015.

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


**✅ MITIGATED — Session 015.** `/api/health` now reports `startedAt` (when this process
started) and `builtAt` (when the code it runs was built, from Spring Boot's build-info). One request
now says whether the process answering is the one just built:

```bash
curl -s http://localhost:8081/api/health
```

The underlying behaviour — a second instance cannot bind the port while the first keeps serving — is
how processes work, not a defect to fix. `.claude/launch.json` also runs the backend as one managed
process that is stopped, not orphaned. Tested in `HealthEndpointTest`.

---

### ENV-002 🟡 Docker Desktop occupies ports 8080 and 5173 on this machine

**Status:** verified (task 4)

`com.docker.backend.exe` and `wslrelay.exe` listen on both 8080 and 5173, so the backend
and the Vite dev server cannot use their defaults here. Spring reports *"Port 8080 was already in
use"* and the preview tooling refuses 5173.

**Workaround.** `PORT=8081` for the backend; Vite reads `process.env.PORT` so the harness can
assign one. Remember BUG-013: a non-default frontend port needs a matching `CORS_ALLOWED_ORIGINS`.

Not an application defect, but it costs time on every fresh checkout on this machine.


**✅ MITIGATED — Session 015.** The workaround is now configuration rather than memory:
`.claude/launch.json` starts the backend on 8081 and the frontend on a free port behind Vite's proxy,
where the browser sees one origin and CORS does not apply. What Docker listens on is a property of
this machine, not of the repository.

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


**✅ MITIGATED — Session 015.** The daemon stopping cannot be fixed from the repository, but the
misleading symptom can. `AbstractIntegrationTest` checks for Docker before starting PostgreSQL and
fails with *"ENV-001: Docker is not reachable … This is not a code failure. Start Docker Desktop…"*
instead of `ExceptionInInitializerError`. Tested in `DockerCheckTest`.


**Recurred in session 016.** This time it stopped the running backend rather than the tests:
startup failed with *"Connection to localhost:5432 refused"*. Restarting Docker Desktop brought the
daemon back in about five seconds, and the database container restarted with it.
It stopped again overnight before the 2026-09-30 re-check (third occurrence in two days); the same
restart fixed it in about fifteen seconds. Frequent enough that a first step of any session should be
`docker version` — the Server line, not just the client.

**Recurred in session 017 (2026-10-16), mid-session:** this time Docker Desktop had quit altogether, not
just its daemon — no Docker process was left, so the database and the running backend went with it.
Starting Docker Desktop again brought the daemon back within a minute and the database container with
it, its data intact. Possibly memory pressure (the machine had little free); not investigated further.

---

## Known limitations

Not bugs. Deliberate decisions, listed so nobody mistakes them for oversights.

| Area | Limitation | Why |
|---|---|---|
| Auth | ~~No login or registration **UI**~~ | *Out of date since task 4, which shipped both screens. Struck through rather than deleted (session 014).* |
| Auth | No email verification or password reset | Out of scope for the vertical slice |
| Market data | ~~No real NQ data anywhere in the project~~ | *Out of date since session 017: 697,003 real NQ one-minute bars from Massive (DEC-042), in the local database only. Struck through rather than deleted.* |
| Market data | Real NQ covers about two years (Oct 2024 →), one-minute bars only | Massive's free plan (DEC-042). Five or ten years back and one-second bars need Databento or a paid plan — Fabio's decision |
| Market data | Real data is local and private (`IMPORTED_PRIVATE`) | DEC-035 / Q-09: showing it to others, the jury included, needs the provider's written permission. Massive's free plan says "individual use" |
| Chart | Coarser timeframes are aggregated from minutes on every request | A 1-hour window reads ~220,000 stored minutes (≈0.4 s locally); 1m and 5m answer in under 60 ms. Stored aggregates are the upgrade path if it ever matters |
| Chart | The New York clock takes ~0.65 s on the widest window (1h, 160 days) | Built from every stored minute in the window plus two weeks before it, on each request, in parallel with the bars — it appears a moment after the candles. 14 days on 5m: 0.16 s. Same upgrade path as the bars: stored daily aggregates. On an old simulated (DEMO) practice session the first clock request takes ~1 s while three weeks of seconds are generated; later ones ~35 ms |
| Chart | Around a contract roll, the clock shows fewer levels | A level measured on the old contract is not drawn over the new one, and the week's open and PWH/PWL stop at the roll (DEC-047). Correct, but the roll week looks bare |
| Chart | The macro times are the commonly cited schedule | `ICT_CONCEPTS.md` ICT-54 flags them for checking against ICT's own lectures; a correction will be `sessions-1.1.0` |
| Chart | ~~Levels do not yet show whether they have been taken~~ | *Out of date since T12 (DEC-053): PDH/PDL, PWH/PWL and killzone highs and lows end at the minute that took them, with a small cross. Struck through rather than deleted.* |
| Chart | ~~A detected FVG is drawn over its own three candles only, never extended to the right~~ | *Out of date since T10 (DEC-050): the FVG state overlay draws each gap on until it is filled, inverted or expired. The plain FVG switch still draws the three candles only. Struck through rather than deleted.* |
| Chart | ~~`fvg-1.0.0` marks about one 5-minute candle in nine on real NQ~~ | *Measured in T10 (DEC-050): ~3 per 30 candles on every timeframe, inside the range DEC-039 accepted; the rule is kept. Struck through rather than deleted.* |
| Chart | The first look at a stretch waits for its scan | Two weeks of 5m, FVG only: 0.2 s; all eight rules: 1.2 s; 160 days of 1h, FVG: 0.5 s; again: 33–180 ms. Which stretches were scanned is remembered per process, so after a restart the first look rescans — inserting nothing new |
| Chart | A gap's lifecycle is followed for 500 candles | About two trading days on 5m, a week on 15m. A gap still unfilled then is drawn as expired, not as open: the bound that keeps `fvg-state-1.0.0`'s lookback finite (DEC-050). An older gap on a lower timeframe is better read on a higher one |
| Chart | `vi-1.0.0`'s minimum is 4 ticks, not a share of the range | Measured: body gaps do not grow with the timeframe. Four ticks is one NQ point — right for NQ and MNQ, and to be measured again for any other instrument |
| Chart | Opening gaps on charts of an hour or less; opening range gaps on 1m–15m | A 4-hour or daily candle straddles the closure, and a 30-minute or hourly candle has no close at 16:15 New York |
| Chart | With several rules on, a zoomed-out chart is busy | Every rule is its own switch, off by default; finished gaps are drawn faint. A detector-aware decluttering (e.g. hiding filled gaps) is a later design choice |
| Chart | Swings are dense: about 13 per 30 candles | `swing-1.0.0` is ICT's one-candle swing (DEC-052). Read the chart with the ranks (ITH/LTH) or the labels instead; the plain swings switch is for learning what a swing is |
| Chart | Structure rules read at most 200 candles back (ranks 300) | The bound that keeps their lookback finite (DEC-052). Far above anything measured on real NQ, but a structure reaching further would not be labelled |
| Chart | BOS and CHoCH are not named while structure is undefined | Only after an HH and an HL (bullish) or an LL and an LH (bearish) — mixed structure has no trend for a break to agree or disagree with |
| Chart | A replay with a resting clock level asks for the clock every step | While any reference level still rests it could be taken in the next minute, so the replay re-asks each step (DEC-053) instead of waiting for the next clock moment. Saying when a level will be taken would leak the future |
| Chart | Sweeps of PDH/PWH and other reference levels are not reported as turtle soup | Turtle soup is detected on equal highs/lows only; a reference level shows as taken in the clock layer, and whether it was swept is read off the chart (DEC-053) |
| Chart | A swing level is followed for 500 candles | Like a gap's lifecycle: 95% are taken within that on real NQ; the rest are drawn as expired, not resting |
| Chart | A block is followed for 500 candles; three breakers in four are unicorns | Like gaps and liquidity (DEC-061): 83% of order blocks are invalidated within that, the rest drawn as expired. A unicorn is a breaker overlapped by its own displacement's gap, and on real NQ that is usually so — the rule reports it; it does not make it rare |
| Chart | Daily charts find few gaps, and so almost no blocks | `fvg-1.0.0` asks for three consecutive candles, and a weekend breaks every triple of daily candles across it: 281 daily candles hold 12 gaps and 3 displacement legs, and no order block (T17). Whether a daily gap may span a weekend is a question for a later version of the gap rule |
| Chart | No infinite scroll into older history | Each view loads a fixed window ending at the chosen date; the date picker and the 1W–2Y jumps move it |
| Training | Drills built on simulated data cannot be graded | DEC-034 — teaching from invented data would be circular |
| Fill engine | `AMBIGUOUS` is a legitimate outcome, not a failure | DEC-008 — candle data genuinely cannot always say which of TP/SL came first |
| Order flow | Not implemented, likely never | Impossible without licensed depth data (open question Q-03) |
| Demo volume | Invented, and **nothing volume-derived may be trusted** | There is no real volume to generate. Volume profile, VWAP-by-volume, relative volume and delta would all be reading structure out of a formula. Was BUG-009; it is not a defect, it is the nature of simulated data (DEC-033) |
| Rate limiting | In-memory, therefore per-instance | A second replica would allow the full quota again. A speed bump, not a guarantee; a shared store is the upgrade path if this is ever scaled out |
| `X-Forwarded-For` | Trusted as a best effort | The header is client-supplied and forgeable, so per-IP limits can be evaded by rotating it. The real fix belongs to the deployment's proxy, which must overwrite it — verify that wherever this is hosted |
| Deployment | Local only | DEC-002 — nothing is hosted yet, and nothing costs money |
| Practice | ~~Simulated instruments only~~ | *Out of date since T4 (DEC-045): practice runs on real NQ behind a hidden date. Struck through rather than deleted.* |
| Practice | Real sessions resolve on one-minute bars | Massive's free plan has no one-second bars (DEC-042). A minute that touches both stop and target is reported `AMBIGUOUS` — more often than on seconds. One-second data is T32 |
| Practice | The price level hints at the era | NQ at 20,000 is late 2024, at 30,000 mid-2026. The date, weekday-to-date mapping and season are hidden (DEC-045); the level is not, because shifting real prices would change the levels ICT reads |
| Practice | A determined learner could find the moment on the Chart tab | By scrolling the same imported history for the same pattern. The app does not try to stop that: it is self-deception, not a leak through the API |
| Practice | Drawings on a practice chart are not saved | They are analysis of a market the learner cannot otherwise see; saving them to the instrument would put them on the public chart |
| Practice | ~~One bracket trade per session~~ | *Out of date since T5 (DEC-046): a replay holds up to 50 trades, one at a time. Struck through rather than deleted.* |
| Practice | Bracket orders at market only, one trade running at a time | No limit entries, no scaling in or out, no partial targets (T28), no moving a stop once locked (DEC-013's revision log is later work) |
| Practice | A replay steps at most 3 days past its start; 50 trades per replay; 60 new sessions per user per hour; journal lists the latest 50 | Each bounds how much data one request has to generate |
| Practice | Playback is one request per bar | At 8× the server's response time, not the timer, sets the pace. Fine locally; a WebSocket stream (DEC-014) is the upgrade if it ever is not |
| Practice | A resumed session reopens on the timeframe it started on | The timeframe being viewed is a choice of the screen, not stored with the session (DEC-051). Starting is still 1m, 5m or 15m; viewing is 1m to 1h |
| Chart | Day ticks on the time axis sit at UTC midnight | Labelled with their true exchange-local weekday and time (e.g. *Wed 18:00*). Moving them to local midnight would mean shifting every timestamp handed to the renderer, which breaks where the zone's offset changes (BUG-029) |
| Drills | On simulated data every drill is a developer fixture: labelled, never graded, no rating | DEC-034, enforced by a CHECK constraint. Graded drills need permitted real data |
| Drills | FVG identification only, on 5m and 15m | One-minute drills are refused: the simulated market produces almost no qualifying gaps there, and it is deliberately not tuned to (DEC-039) |
| Drills | No drill history or statistics screen | Nothing may be aggregated into a score from fixture drills (DEC-034); answered drills are stored and immutable |
| Practice | Results on simulated data are practice, not evidence | Stated when the session ends. A replay's own score (T5) is per session; no cross-session score exists yet, and when one does (T23), DEC-034 applies |

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


**⚠️ Covered one ordering only — completed in session 015 as BUG-034.** The grace window handles a
duplicate that arrives after the first refresh commits. Two refreshes that genuinely overlapped still
failed on the version column and signed the user out, until BUG-034.

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


**⚠️ Only half fixed — found in session 014, completed in session 015 as BUG-029.** The fix above set
the crosshair's formatter only; the axis tick marks stayed in UTC until BUG-029.

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

### BUG-065 🟡 A drill test recomputed its answer key with too little history after the daily halt

**Status:** verified — failed in the fresh-clone check of T35 (session 017, 2026-10-16), passed locally
**Affects:** `backend/src/test/.../drills/DrillApiTest.java` `answerKeyIsTheRule` — the test, not the app

**What happened.** The test checks a drill's answer key against the FVG rule run again independently,
over the drill's window and "its lookback", written as 15 × 5 minutes before the window. The window is
random. This time it opened at 18:25 New York, 25 minutes after the daily reopen, so those 75 minutes
held five candles. The rule measures a gap against the 14 candles before it, so the recomputation could
not see the gap at 18:50 that the app had found correctly, with the candles before the 17:00 halt.
Recomputed on that exact window: five candles of context give the test's expectation, four days give
exactly the app's answer key. A window that close to a reopen comes up a few runs in a hundred, which
is why it had not failed before. The same calendar-for-candles mistake as BUG-064, in a test.

**✅ FIXED — Session 017.** The recomputation starts four days before the window, past any halt or
weekend; more history than the lookback changes nothing (DetectorContractTest). DrillApiTest 16/16.

### BUG-064 🟠 On coarse charts a detector saw less history than its lookback

**Status:** verified — found while measuring setup detection on 4h and daily charts (session 017, T35)
**Affects:** `backend/.../analysis/SetupScanner.java` `history`

**What happened.** Before scanning a stretch the scanner hands each detector the candles it needs before
it — its lookback, 200 or 503 candles — "exactly that many, so what is found never depends on where a
scan started" (DEC-049). It guessed the calendar span as lookback × candle length and, when that held too
few candles, widened it once by ten days for closures. Ten days covers an hourly chart's weekends. A
daily chart loses two days in seven: 200 daily candles span about 290 calendar days, and the guess
found about 147. So on 4h and 1d a scan's results could depend on where it began. On master, which stops
at 1h, only the 503-candle rules (liquidity, sweeps, turtle soup) could come up a few candles short, in
a stretch with a holiday in it.

**✅ FIXED — Session 017, T35.** It reaches back until it has the candles, stopping only at a stretch
with none at all, where the data begins. `ScanHistoryTest` builds a market shut at weekends and checks
a daily chart gets 200 candles, a 4h chart 503, the data's start ends the search, and a short lookback is
the end of a long one; planting the old single widening fails it. The same change reads a stretch's
candles and contract rolls once for every rule in a request (`SetupScannerTest`: rules scanned together
store exactly what each stores alone) — nine rules on a cold daily chart went from 9.8 s to 3.0 s.

### BUG-063 🟡 The New York clock vanished on 4h and daily charts

**Status:** verified — found in the browser check of T35, before it was merged (session 017)
**Affects:** `frontend/.../lib/chart/dateNavigation.ts`, `components/ChartWorkspace.tsx`

**What happened.** The clock is asked for the chart's whole window, and the server covers at most 200
days at a time (`ClockService.MAX_RANGE`). The new 4h window was 240 days and the daily one 400, so
the request was refused — and since a clock that fails costs only the overlay, nothing said so: the
switches stayed on and nothing was drawn.

**✅ FIXED — Session 017, T35.** The 4h window is 200 days, so the clock covers all of it, and a test
holds every window the clock is drawn on to that range. A daily chart draws no clock — each of its
windows would be a sliver inside one candle — and asks for none; its switches give way to "On intraday
charts, up to 4h" (`ClockToggles.test`).

### BUG-062 🟡 The random TP/SL test could fail by chance

**Status:** verified — failed in the fresh-clone check of T16 (session 017, 2026-10-16), passed locally
**Affects:** `backend/src/test/.../practice/BracketOutcomeApiTest.java` `randomBrackets` (written the same day)

**What happened.** The test trades 40 seeded brackets and checks each against an independent replay —
every one agreed — and then checks that the sample exercised the rules: some ambiguity, some exit at a
gapped open. But each session started at a moment the app picked at random, so the sample was not the
same from run to run, and in the fresh clone no trade happened to exit at a gap. A test that checks
its own coverage cannot leave that coverage to chance. The same lesson as BUG-051.

**✅ FIXED — Session 017.** The moments are seeded too: each session is planted at a seeded open minute,
so the 40 trades are the same every run. Run twice, green both times; the fresh clone with it.

### BUG-061 🟡 The curriculum's buttons read "ICT-20Fair value gap", and a test clicked the wrong one

**Status:** verified — found while building T16 (session 017, 2026-10-16); tests fail on the old code
**Affects:** `frontend/src/components/learn/LearnView.tsx` (since T14)

**What happened.** Each concept in the Learn tab's ladder is a button holding two spans, the id and the
name, laid out side by side. With no text between them their accessible name — what a screen reader
says, and what tests find buttons by — ran together: "F-1Candle anatomy". The test "opens a concept
from the list" asked for a button named "F-1 Candle anatomy", never matched the list, and clicked a
link of that name on the lesson card instead — passing while testing something else. It showed when
T16 added a third button of the same name.

**✅ FIXED — Session 017 (T16).** A space between the spans (invisible in the flex row) names each button
"F-1 Candle anatomy"; the ladder is a named landmark ("Curriculum ladder") and the tests look inside
it. A mutation removing the space is caught.

### BUG-060 🟡 A practice test passed only because of the order the test classes ran in

**Status:** verified — failed as soon as a new test class sorted before it (session 017, 2026-10-16)
**Affects:** `backend/src/test/.../practice/PracticeApiTest.java` `defaults` (since T4)

**What happened.** The test checks that starting a session with nothing imported says so. It assumed
an empty market table, but the test classes share one database, and the real-data ones (clock,
setups, drills, lessons) import minutes and leave them there. It passed only while it happened to
run before all of them; a new test class that imported NQ and sorted earlier made it fail.

**✅ FIXED — Session 017.** The test empties the market itself first, as the drills' and lessons' own
"nothing imported" tests already did.

### BUG-059 🟡 A target the market opened beyond was explained as if it were a stop

**Status:** verified — reasoned from the card, then a test fails on the old code (session 017, 2026-10-16)
**Affects:** `frontend/src/components/practice/ResultCard.tsx` (since session 014)

**What happened.** A gapped exit was explained with one sentence for both: "filled at the opening price
rather than at your level. Real stops do this too." For a stop that is right — the fill is worse. For
a target it is backwards: a resting limit the market opens beyond fills at the open, *better* than the
target, and nothing about stops applies.

**✅ FIXED — Session 017.** One sentence each: worse than your stop, or better than your target.
`ResultCard.test` (new) checks both; a mutation swapping them is caught.

### BUG-058 🟡 A fraction of a contract printed money like "$553.50.000000000007276"

**Status:** verified — in the app at 1920px and in a test (session 017, 2026-10-16)
**Affects:** `frontend/src/lib/practice/plan.ts` `formatCents` (since session 014)

**What happened.** The contracts box takes any number while it is typed. 2.7 contracts on a 41-tick
stop is 55,350.000000000007 cents in floating point, and the formatter printed the remainder as it
was. The plan was refused ("Contracts must be a whole number") — but next to a price that looked
broken.

**✅ FIXED — Session 017.** The formatter rounds to a whole cent first, and takes the sign after it (a
first version printed "−$0.00" for −0.4 cents; its own test caught that). Checked in the app: "41 ticks
· $553.50". `plan.test` and `PracticeView.test` cover it.

### BUG-057 🟡 Close at market, refused, gave Skip to result's reason

**Status:** verified — seen in the end-to-end TP/SL check against the dev API (session 017, 2026-10-16)
**Affects:** `backend/.../practice/PracticeService.java` (since DEC-057)

**What happened.** Closing when no trade was running — a trade that had just ended during play, a
double click — answered 409 with "There is no running trade to skip to the end of." Both commands
threw the same exception, written for Skip.

**✅ FIXED — Session 017.** The exception names the command: "…to close." / "…to skip to the end of."
`PracticeApiTest` checks both messages.

### BUG-055 🟠 A drafted bracket looked like a placed trade, and could not be thrown away

**Status:** verified — reproduced in the app and through the API (session 017, 2026-10-16); tests fail
on the old code
**Affects:** `frontend/src/components/practice/PlanPanel.tsx`, `PracticeSession.tsx` (since T5)

**What happened.** Fabio: "hitting stop loss or take profit doesnt close the trade, trade keeps running
until the session ends", and "there is no way to cancel trade once you press go long or go short". The
server was right: a *placed* trade closed exactly at the first minute that touched its stop or target
(checked on his own session's trades against the stored bars, and replayed through the API and the
UI). But "Place long" did not place anything — it drew a bracket on the chart, titled "Long at
market", with the same lines a running trade has. Until "Lock plan" there was no trade, so price
crossing the lines did nothing and the bracket stayed; and there was no button to remove it. A screen
that looks like a position must be one, or say clearly that it is not.

**✅ FIXED — Session 017 (DEC-057).** The buttons say *Plan long* / *Plan short*; the panel is titled
*Draft · long at market* with a *not placed* chip and a **Discard** button; *Place trade* (was *Lock
plan*) is the only thing that sends it. `PlanPanel.test` checks the title and Discard;
`PracticeView.test` discards a draft and checks nothing was sent. Mutations caught: Discard doing
nothing, Discard removing another drawing, the chip removed. Checked live at 1920px on real NQ.

### BUG-056 🟠 A placed trade could not be left before its stop or target

**Status:** verified (session 017, 2026-10-16) — a missing feature with a bug's effect
**Affects:** practice (since session 014)

**What happened.** Once placed, a trade could only end at its stop, its target or the session's end.
On a real platform you can always close at market; here a learner who changed their mind had to sit
through the rest of the trade, and a wide bracket could run for the rest of the replay.

**✅ FIXED — Session 017 (DEC-057).** *Close at market* on the running trade: it exits at the next
open on the fill engine's bars, as the entry does (`fill-1.2.0`, `CLOSED`), stored as its own
immutable record (V15 `practice_close`) beside the untouched planned result; if the plan's own stop or
target fills first, that exit is revealed instead. `FillEngineTest` (five cases), `PracticeApiTest`
(close, close at once, the plan exiting first, the rules) and `PracticeView.test`. Thirteen backend
mutations caught (the fill ignoring a touch at the open or before it, the fill left out of MAE or MFE,
the cursor left on the fill, the status left in trade, refusing instead of revealing the plan's exit,
the score or a reload ignoring the close, the close not stored, and an older target-gap MFE check
added on the way), and two frontend (the button calling Skip, the timeframe dropped).

### BUG-054 🔴 A rejected password was written to the server log

**Status:** verified — seen in the test log while checking the Boot 4 upgrade (session 017); a test
fails on the old code
**Affects:** every controller with a validated request body; registration above all (since session 008)

**What happened.** A request body that failed validation was left to Spring's default handling,
which logs the failure at WARN with each *rejected value* in it. For registration that value is the
password: a password one character too short — quite possibly one its owner uses elsewhere — went
into the server log in plain text. It had been there since registration was built; nobody had read
that line of a test log closely.

**✅ FIXED — Session 017 (DEC-056).** `common/ValidationExceptionHandler` handles validation failures
for every controller: nothing is logged, and the reply names the field and the rule ("password: size
must be between 12 and 200"), never the value. `AuthFlowTest` registers with an eleven-character
password and checks neither the captured log nor the reply contains it; it fails without the handler.

### BUG-053 🟡 The API types script asked another app on this machine for its contract

**Status:** verified (session 017)
**Affects:** `frontend/package.json` `api:types` (DEC-007)

**What happened.** The script generating TypeScript types from the backend's OpenAPI document pointed
at port 8080 — the port the backend used before session 017 moved it to 8081, and on this machine the
port of a different project's backend. Nothing runs the script yet, so nothing broke; the first run
would have generated another app's types. And with TypeScript 7 its tool no longer runs at all.

**✅ FIXED — Session 017 (DEC-056).** Port 8081, and the tool runs through `npx` with its own
TypeScript 5. Run against the dev backend: it generates the lesson and drill types.

### BUG-052 🟡 The time-zone button named today's zone, not the chart's

**Status:** verified — caught by the newer oxlint's purity rule (session 017); a test fails on the old code
**Affects:** `frontend/src/components/ChartWorkspace.tsx`

**What happened.** The axis button labels the New York zone (EDT or EST), and read the clock to do
it — so a chart of January candles viewed in October said EDT.

**✅ FIXED — Session 017.** It names the zone of the last candle on screen: January reads EST, checked
in the app. `App.test` loads January bars and expects EST.

### BUG-051 🟡 A setups test failed whenever its random replay held no market structure shift

**Status:** verified — failed once in the fresh-clone check of T14, after passing in every run before
**Affects:** `backend/src/test/.../analysis/SetupApiTest.java` (since T11–T12)

**What happened.** `practice()` compares a replay's setups with the Chart tab's over the same stretch,
then asserted that every one of the 18 rules had a hit in it, "so the comparison covers them all". But
the replay starts at a random moment, and a rare rule can have nothing in four days of the fixture:
that run's window held no MSS. A test that asserts a fact about random data is flaky by construction;
it had simply been lucky.

**✅ FIXED — Session 017 (T14).** The replay comparison now asks for at least 12 of the rules — a
broad comparison, which every window gives — and a new `everyRule()` checks over a fixed fortnight of
the fixture that every rule comes through the endpoint. Run three times, green each time.

### BUG-050 🟡 The FVG drill counted a mark as wrong on a candle nobody could judge

**Status:** verified — measured on real NQ while building T13; the fixture drill's partial-answer
test, which marks "the first candle that is not a gap", landed on one
**Affects:** `backend/.../drills/DrillService.java` (before T13)

**What happened.** The answer key held only gaps with all three candles on screen — rightly, since a
learner cannot find a pattern whose first candle they were never shown. But a mark on the middle
candle of a gap the window *cut* (candle 1 just before the window, or candle 3 just after it) was
scored as an extra mark: wrong, for a candle whose answer depended on candles the chart did not
show. On 5-minute real NQ a quarter of FVG drills have such a candle; for the sweep drill T13 adds,
almost nine in ten would have (sweeps of levels drawn before the window).

**✅ FIXED — Session 017 (T13, DEC-054).** Hits anchored in the window that the window cuts are
*excused*: stored with the drill when it is made, a mark on one counts neither way, and the result
names them and why. `RealDrillApiTest` checks every hit of the rule anchored in a window is either
asked or excused, and that a mark on an excused candle is neither found nor extra; dropping the
excused list fails both.

### BUG-047 🟠 Undo after dragging a drawing lost where it had been

**Status:** verified — found reading the drawing store while building T8; tests fail on the old code
**Affects:** `frontend/src/lib/drawing/store.ts`, `components/DrawingOverlay.tsx`

**What happened.** A drag moves a drawing with `preview` actions (no history), then records one
`update` when the pointer is released. But `update` saved the state *as it was at release* — already
holding the last preview — as the step to undo to. So after dragging a shape, the first Undo changed
nothing, the second removed the shape altogether, and the position before the drag was gone for
good. Creating a shape by dragging cost the same extra, empty Undo; and every plain click on a drawing
added an undo step that changed nothing.

**✅ FIXED — Session 017.** A gesture keeps the drawing as it found it, and `update` takes it as
`from`: history records that, and records nothing at all when nothing changed. A creation's undo step
is the `add` itself; the previews only shape what it added. `drawing.test` replays the overlay's own
sequence of actions and fails on the old store at the first Undo.

### BUG-048 🟡 Labels of lines that ended together printed over each other

**Status:** verified — seen on the real chart once screenshots worked again (the 09:30 open fifteen
ticks under the PDH); a test fails on the old renderer
**Affects:** `frontend/src/lib/clock/render.ts`, `lib/drawing/render.ts`

**What happened.** Every clock line is labelled at its right end, and lines of one day end together at
17:00. Two of them a few ticks apart printed their labels on top of each other, legible as neither.
The new Fibonacci had the same problem on a short leg, where 0.62, 0.705 and 0.79 sit pixels apart.

**✅ FIXED — Session 017.** `lib/chart/labelSpace.ts`: labels are placed after their lines, most
important first (PWH/PWL, PDH/PDL, session levels, opens; on a Fibonacci 0.705, 0.5, the ends, the
main targets), each into free space — a line higher if it must, left out if there is none. The line is
always drawn; only words give way. On the real chart the 09:30 open now sits a line above the PDH.

### BUG-049 🟡 A click with a two-point tool left an invisible shape behind

**Status:** verified — found checking T8 in the browser: a zero-length Fibonacci printed its ten
labels at one price; a test fails on the old overlay
**Affects:** `frontend/src/components/DrawingOverlay.tsx`

**What happened.** A trend line, rectangle, zone or Fibonacci is created by pressing and dragging. A
press released without moving created one anyway, with both points in the same place: invisible for a
line or a box, a stack of ten labels for a Fibonacci — and saved to the server either way.

**✅ FIXED — Session 017.** A creation whose points all coincide when the pointer is released is
forgotten (no history, nothing saved). The position tool, which seeds a bracket from a single click on
purpose, is unaffected.

### BUG-044 🟡 The clock could name a later price after the time it was named for

**Status:** verified — found reviewing T7 before its merge (Fabio: "check everything for bugs"); a
test fails on the old rules
**Affects:** `backend/.../clock/ClockLayerBuilder.java`

**What happened.** `opens-1.0.0` takes "the first one-minute candle at or after the time", with no
limit. Where the data begins part-way through a day — the first day of the import, or a gap — the
"midnight open" became whatever printed at 10:00. Killzone highs and lows were measured from whatever
part of the killzone the data covered, and the weekly open from the week's first stored minute,
wherever that fell. PDH/PDL and PWH/PWL already refused periods not seen from their start; the opens,
the weekly open and the session levels did not. Reachable on the Chart tab with the 2Y jump.

**✅ FIXED — Session 017.** An opening price is taken only if its first minute comes within 15
minutes of its time (NQ trades nearly every minute; a few quiet ones are allowed); a killzone level
only if the killzone traded within 15 minutes of opening; the weekly open only if the week's first
minute opened a trading day — which still allows the holiday weeks whose Sunday session does not open,
and replaces the 30-hour allowance that let a week starting Monday 10:00 pass.
`ClockLayerBuilderTest.dataStartsMidDay`; its first run also caught that 30-hour hole.

### BUG-045 🟡 An unanswered "End the session?" came back after a trade

**Status:** verified — found in the same review; `PracticeView.test` fails on the old code
**Affects:** `frontend/src/components/practice/PracticeSession.tsx`

**What happened.** Asking to end a session and then, instead of answering, placing a trade left the
question open. It was hidden while the trade ran and reappeared, unasked, when the learner was flat
again.

**✅ FIXED — Session 017.** The question remembers how many trades there were when it was asked and
lapses when another is placed — derived on render, no effect resetting it.

### BUG-046 🟡 A trade that had just closed could stay hidden behind an earlier one

**Status:** verified — found in the same review; `PracticeView.test` fails on the old code
**Affects:** `frontend/src/components/practice/PracticeSession.tsx`

**What happened.** Picking a trade in the log kept it open for good. The next trade then closed —
playback stops exactly so its result can be read — and the card went on showing the old trade.

**✅ FIXED — Session 017.** A pick remembers how many trades had closed when it was made and lapses
when another closes, so a new result is always shown first; the older ones stay a click away.

### BUG-040 🔴 The app stopped working fifteen minutes after it was opened

**Status:** verified — hit repeatedly in the browser during session 017 (every request answered 401
until a reload); found in an audit Fabio asked for ("fix all the bugs you tracked"), never logged
before
**Affects:** `frontend/src/lib/api/client.ts`, `lib/api/auth.ts`, `hooks/useSession.ts`

**What happened.** The access token lives fifteen minutes; the refresh token thirty days. The client
refreshed only once, when the page loaded (`restoreSession`), although its own comment said refresh
was "called on a 401". After fifteen minutes every request — the next bar of a replay, a plan, a
drawing save — failed with 401 and the app showed generic errors while still looking signed in. A
reload fixed it, which is why it looked like flakiness. For a replay, which can easily last half an
hour, it was a hard stop in the middle of a session.

**✅ FIXED — Session 017.** `apiFetch` answers a 401 by refreshing once and sending the request again
(with its body, and the CSRF cookie as it is after the refresh). Requests that fail together share one
refresh — two racing refreshes is how BUG-034 signed users out. Sign-in, registration, refresh and
logout are never retried: none uses an access token, so their 401 is an answer. Only a refused refresh
(401/403) ends the session; a network failure or a server error does not. When it does end, the app
returns to the sign-in form and says so ("You were signed out… your practice sessions and drawings are
saved") — but only for someone who was signed in, not a fresh visitor. `client.test.ts` (7 tests) and
`App.test` cover each case; run against the old client, five of the seven fail (the other two check
that nothing happens, which the old client also got right). Checked live with the access token cut to
ten seconds: eighteen seconds after signing in, opening Practice got two 401s, one shared refresh
(200), both requests retried (200), and the journal loaded — no error, still signed in.

### BUG-041 🟡 Undo after a trade brought its old bracket back

**Status:** verified — found in the same audit; `PracticeView.test` fails on the old code
**Affects:** `frontend/src/components/practice/PracticeSession.tsx`, `lib/drawing/store.ts`

**What happened.** Locking a plan removed its draft bracket with an ordinary `remove`, which is an undo
step. Once the trade closed and the learner was flat, pressing Undo restored the old draft — pinned to
the current price, ready to lock again — and the bracket edits made before the lock were still in the
history behind it. Undo is for the learner's own analysis; a locked plan is history, not a draft.

**✅ FIXED — Session 017.** A new store action, `forget`, removes a drawing from the present *and* from
every past and future step, adding none, and drops steps that became identical so no undo is spent
changing nothing. Locking a plan forgets the draft. The first test checked only after four undos — by
then history had walked past the draft to the empty start — and passed on the broken code; it now
checks after every undo, and fails on the old code at the first.

### BUG-042 🟡 A trade's details could be opened only with a mouse

**Status:** verified by reading — found in the same audit
**Affects:** `frontend/src/components/practice/TradeLog.tsx`

**What happened.** Rows of the trade log opened their trade on click, but a table row cannot take
keyboard focus, so a keyboard user could not reach any trade's details; and `aria-selected` on a plain
table row is not valid ARIA.

**✅ FIXED — Session 017.** Each finished trade's number is a real button (`aria-pressed`, "Show trade
N"); clicking anywhere on the row still works as a convenience. `TradeLog.test` opens a trade with
Enter.

### BUG-043 🟠 One request could keep the server busy for twenty seconds

**Status:** verified — measured in the browser during the same audit
**Affects:** `backend/.../marketdata/MarketDataService.java`, `clock/ClockService.java`

**What happened.** A simulated market is generated one second at a time, so a request's cost is the
time it covers, not the bars it returns. The public bars endpoint allowed 400 days: 399 days of hourly
`DEMO-NQ` bars is under the bar cap and took **21 seconds** of server time; the new clock endpoint
(200 days, plus two weeks of lookback) took **11 seconds**. Any signed-in user could repeat either in a
loop. The limits were set when the simulated market was the Chart tab's main market; since V8 nothing
in the app asks for more than a few days of it.

**✅ FIXED — Session 017.** `MarketDataService.requireAffordable`: the public endpoints serve a
simulated market at most 14 days at a time (`MAX_SIMULATED_RANGE`), checked before anything is
generated; real data is read, not generated, and keeps its limits. Practice and Drills generate through
`internalBars`, which bound their own ranges. `MarketDataApiTest.simulatedRangeIsBounded`.

### BUG-039 🟡 The total R disagreed with the R column it summed

**Status:** verified — seen in the browser while playing T5 on real NQ, session 017; a test fails on the old code
**Affects:** `backend/.../practice/PracticeService.java` (`score`)

**What happened.** The replay's score added up its trades' R multiples *unrounded*, then rounded the
total; each trade's R is shown rounded to two decimals. On the first real two-trade session the log
read −1.07R and +1.98R, and the total read **+0.90R** — while anyone adding the column gets +0.91R.
(−43 and +79 ticks on 40 ticks of risk are −1.075R and +1.975R; unrounded they sum to exactly 0.9.)
The unrounded total is arguably more precise, but a learner checking the arithmetic sees a wrong sum,
and a scorecard that does not add up is not trusted.

**✅ FIXED — Session 017.** The total is now the sum of the R column as shown. In the browser the
same session reads +0.91R in the log and in the journal. `PracticeApiTest.totalMatchesTheColumn`
writes exactly those two trades and fails on the old code (`expected: 0.91 but was: 0.9`).

### BUG-038 🟠 A slow drag did not move a drawing sideways at all

**Status:** verified — found while testing BUG-035's fix in the browser, session 017; a test fails on the old code
**Affects:** `frontend/src/components/DrawingOverlay.tsx` (moving a drawing)

**What happened.** Moving a drawing translated its *last preview* by the few pixels of each
pointermove, and the chart's projection snaps a pixel to the nearest bar's time. A step smaller than
half a bar therefore rounded straight back to the same bar, every time: a drag of a pixel or two per
event — an ordinary slow drag — never moved the shape sideways. Faster drags drifted instead,
accumulating the rounding: in the browser, the same 100px drag moved a rectangle 17 bars in one burst
and 20 bars in ten steps.

**How it was found.** The BUG-035 fix needed proof that drags were unchanged, so the same scripted
drag (a burst of 10 moves, then 10 paced moves) was run on the old and the new code. Both gave
identical results — the fix was safe — and both showed the burst and the paced drag disagreeing.

**✅ FIXED — Session 017.** A move is now measured from where the drag started, against the anchors as
they were then: `translateAnchors(origin, totalDx, totalDy)`, so rounding happens once, not per
event. In the browser the burst, the paced drag and a 100 × 1px drag all move the full distance. The
test projection used by the overlay tests never snapped, which is why no test caught it; the new test
uses one that snaps to 10px bars and fails on the old code (`expected 1200 to be 1500`).

### BUG-035 🟡 Seven lint warnings predate the practice slice

**Status:** verified — `npm run lint` (oxlint), session 015
**Affects:** `PriceChart.tsx` (2), `useDrawings.ts` (2), `DrawingOverlay.tsx` (2), `ChartWorkspace.tsx` (1)

Found while double-checking the practice PR, which is the first time anyone ran the project's own
`lint` script. The PR had added seven warnings of its own; those were fixed before merging, leaving
exactly the seven that `master` already had:

- six `react(refs)` — the "latest value" pattern, `someRef.current = value` written during
  render. React 19 may discard a render, which can leave a ref holding a value from a render that
  never committed;
- one `react(set-state-in-effect)` — `ChartWorkspace` resets loading and error state
  synchronously at the start of its bars effect.

**Why not fixed with the rest.** Two of the refs are the drawing overlay's gesture state, read by
pointer handlers during a drag. Moving the assignment into an effect changes *when* a handler sees
the new value, and if a render has not committed between two pointer moves, a dragged shape could
jump. That needs hands-on browser testing of drags, which the session-015 browser pane could not
provide (it was not rendering frames). Deferred to the next batch rather than changed blind.

**✅ FIXED — Session 017, before the merge of `feat/real-nq-data`.** All seven warnings are gone and
`npm run lint` now runs `oxlint --deny-warnings`, so a new one fails the check.

- *Refs written during render* (`DrawingOverlay`, `useDrawings`, `PriceChart`): each ref is now
  written in a `useLayoutEffect` — at commit, before any passive effect reads it. The overlay writes
  its two refs **together**, because a drag reads them as a pair (the gesture's position and the
  drawing it moves); one newer than the other is exactly the jump this entry worried about.
- *setState inside an effect* (`ChartWorkspace`): loading and error are no longer stored. Each bars
  response is tagged with the request it answers, and "loading" is derived as "the newest response is
  not for the current request".
- **Drag behaviour checked in the browser** with the same scripted drags on the old and the new code:
  identical results. That comparison is what exposed BUG-038.

### BUG-037 🟠 The Vite starter template's stylesheet was never removed

**Status:** verified — Fabio's screenshot, session 017 (2026-10-13)
**Affects:** `frontend/src/index.css`, `App.css`, every screen

**What happened.** The frontend was scaffolded from Vite's React template in session 006 and its
`index.css` was never deleted. It pinned `#root` to a 1126px column with borders on both sides,
centred all text, set 56px headings and switched palettes on the OS light/dark setting. The app's own
`App.css` was written on top and fought it rule by rule. On Fabio's 1920px screen the result was a
boxed column, a sign-in card stranded on the left of it and an indented title: *"this looks extremely
ugly and nothing is positioned correctly."*

**Lesson.** Two earlier bugs were symptoms of this same leftover — BUG-031 (the title vanished on a
light-mode machine because the template coloured headings) and BUG-032 (a sideways scroll because the
template made `#root` a flex column). Each was patched where it showed instead of removing the
cause. Scaffolding has to be cleared in the session that creates it.

**✅ FIXED — Session 017.** Both stylesheets deleted along with the template's assets (Vite and React
logos, `hero.png`, an icon sheet, the template README, the Vite favicon) and the unused
`react-router-dom`. All styling is Tailwind now (DEC-044). `styles.test.ts` fails if `#root`,
centred text, the 1126px column or an OS-dependent palette ever returns; the BUG-032 width rule is
asserted on the rendered `<main>` in `App.test.tsx`.

### BUG-036 🟠 The JVM was never set to UTC, so stored session times read back an hour late

**Status:** verified — `MassiveImporterTest` failed on it, session 017
**Found:** Session 017, building the Massive importer (T1)
**Affects:** `TradeezApplication`, `application.yml`, every `LocalTime` / `LocalDate` read through
Hibernate (today: `Instrument.sessionOpenLocal` / `sessionCloseLocal`)

**What happened.** `application.yml` said the JVM timezone was "set at the JVM level so no code
path can accidentally use the host timezone", under a property `tradeez.timezone: UTC`. Nothing
read that property. The application, and the test suite, ran in the host's zone — Europe/Zagreb.
Hibernate is configured to read SQL `TIME` values as UTC (`hibernate.jdbc.time_zone: UTC`) and
converts them into the JVM's zone, so NQ's session open, stored as 17:00, came back as **18:00**
(19:00 in summer).

Nothing used those fields until the Massive importer, which computes each trading day's session
window from them. Its test asked the fake server for Monday's minutes from 18:00 Chicago time
instead of 17:00 and came up 120 bars short — the first hour of one session and the whole of the
next were missing. Instants (`TIMESTAMPTZ`) were never affected, which is why sixteen sessions of
timestamp-heavy code never showed it.

**Lesson.** A configuration comment is not configuration. "Set at the JVM level" described an
intention nobody implemented, and the property's name made it look done.

**✅ FIXED — Session 017.** `TradeezApplication.useUtc()` sets the JVM default zone to UTC before
Spring starts; `AbstractIntegrationTest` calls it too, because tests start Spring without
`main()`. The dead property is gone and its comment says where the zone is really set.
`InstrumentSessionTimesTest` asserts the JVM is UTC and that NQ's session reads back as exactly
17:00–16:00 Chicago time.

### BUG-034 🟠 Two refreshes at the same instant still signed the user out

**Status:** verified — seen twice in the backend log on 2026-09-29, then reproduced by a test
**Fixed:** Session 015, same session · **Found by:** double-checking the practice PR in the browser
**Affects:** `AuthController.refresh` · **Related:** BUG-001

Reloading the app after the 15-minute access token had expired landed on the sign-in screen. The
network log showed why: two `GET /api/me` (401), then two `POST /api/auth/refresh` with the same
refresh cookie at the same moment — the first 200, the second 401. React's development mode runs a
component's startup effect twice, so the app raced itself; two tabs opening at once would do the same
in production.

BUG-001's fix covers a duplicate that arrives *after* the first refresh commits: the token is already
rotated, the grace window recognises a duplicate, fresh tokens are issued. It did not cover a
duplicate that arrives *during* it. Both requests read the token unrotated; `RefreshToken`'s version
column lets exactly one rotation commit; the other failed with `StaleObjectStateException`, which had
no handler and reached the browser as a 401. The server log for both occurrences shows exactly that.

**✅ FIXED.** The refresh endpoint catches the optimistic-lock failure and tries once more. By the
time the failure is thrown the winner has committed, so the retry finds the token rotated moments ago
— inside the grace window — and is issued fresh tokens, revoking nothing. The version column still
guarantees a single rotation; losing that race is simply no longer mistaken for being signed out.

**Tests:** `ConcurrentRefreshTest` (2) — a spy makes one refresh lose the race deterministically
and asserts the retry succeeds and its session works; six refreshes of one token fired at once all
return 200. **Both fail without the fix**, the concurrent one with the very
`StaleObjectStateException` from the server log.

**Lesson:** a race fixed for "one after the other" is not fixed for "at the same time". BUG-001's
test sent its duplicate after the first request had finished, which is the one ordering that
cannot overlap.

---

### BUG-033 🟡 Practice regenerates its market on every request, inside a transaction

**Status:** reasoned from the code; timings observed in tests (~0.3 s to create a session)
**Affects:** `PracticeService` · **Related:** BUG-010

Creating or resuming a session generates four-plus days of one-second data to aggregate the visible
history; locking a plan generates up to a trading day of one-second bars to resolve it. All of it
happens inside the service's `@Transactional` methods, so a database connection is held while the CPU
works. Fine for one learner on a laptop; with many concurrent users the pool would run dry before the
CPU did.

Not fixed on purpose, for BUG-010's reason: measure first. The likely fixes, in order: generate
outside the transaction (load, release, compute, then a short write), then cache per
`(seed, range, generatorVersion)` if resumes turn out to dominate.

**✅ FIXED — Session 015.** `PracticeService` no longer uses method-level transactions for its
commands. Each is three steps: read a snapshot (read-only, `REPEATABLE READ`, so session, plan and
result are one consistent moment); generate with no transaction open; then a short write that
re-reads the session and answers 409 if its version moved in between — the bars were generated for a
state that no longer exists. Lock-plan now resolves the fill from the request before any row is
written.

**Tests:** `PracticeTransactionTest` (3) — a spy on `MarketDataService` records
`isActualTransactionActive()` for every generation call across create, resume, advance, lock and
skip, and every one is false; a version bump injected mid-generation makes *advance* return 409 and
leaves the cursor where it was; the same for *lock*, and no plan or result row is stored.

---

### BUG-032 🟡 One pixel of horizontal scroll on long pages

**Status:** verified — `scrollWidth` 947 vs `clientWidth` 946 on both the Chart and Practice tabs
**Affects:** page layout (template `#root` rules in `index.css` are the first suspect)

Only appears when the page is tall enough for a vertical scrollbar. A horizontal scrollbar appears for
a single pixel. Cosmetic, pre-dates session 014.

**✅ FIXED — Session 015.** Reproduced at a 961 px window on a practice session: `main.shell`
measured 946.594 px inside a 946 px `#root`. The suspect named above was wrong — the cause was in
`App.css`. `#root` is a column flexbox, and `.shell`'s `margin: 0 auto` switches off stretching, so
the shell was sized to fit its content. Its content includes the chart, which keeps the pixel width it
last measured; when a vertical scrollbar took 15 px, the chart held the shell at the old width. Fix:
`width: 100%; min-width: 0` on `.shell`. **Browser:** 946 = 946 afterwards. Guarded by
`styles.test.ts`.

---

### BUG-031 🟡 The "Tradeez" title is nearly invisible

**Status:** verified — visible in every session-014 screenshot, including the sign-in screen
**Affects:** `index.css` / `App.css`

The `h1` renders dark grey on the dark background. Leftover Vite template heading colour
(`--text-h`) that the app's own palette never overrode.

**✅ FIXED — Session 015.** Wider than the title: the template's `--text-h` also colours `h2` and
`code`, and the seed on the practice result card is a `<code>` — on a light-mode machine it would have
been dark text on a dark box. `App.css` now defines `--text-h` in its own `:root`, so the app's
palette owns it whatever the OS prefers. **Browser:** with the OS in light mode, the title computes to
`rgb(230, 237, 243)`. Guarded by `styles.test.ts`.

---

### BUG-030 🟡 The test setup's canvas stub never installs

**Status:** verified · **Affects:** `frontend/src/test/setup.ts`

The stub is guarded by `!HTMLCanvasElement.prototype.getContext`. jsdom *does* define `getContext` —
it just logs "Not implemented" and returns null — so the guard is false and the stub is skipped.
Every test that mounts the drawing overlay prints that line: 55 of them in a full run as of session
014. Harmless to results (the overlay already handles a null context), but the noise buries real
warnings.

**Fix (batch):** install the stub unconditionally in the test setup.

**✅ FIXED — Session 015.** The stub is installed unconditionally. "Not implemented" lines in a
full frontend run: 55 → 0.

---

### BUG-029 🟠 The time axis labels in UTC; only the crosshair uses exchange time

**Status:** verified — seen in the browser in session 014
**Found by:** browser check of the Practice tab · **Affects:** `lib/chart/lightweightAdapter.ts`

The practice header said *Thu 02:35 CST* while the tick marks under the latest bars read `08:00`.
Hovering the same bar put `02:20` in the crosshair label. So the axis is in UTC and the crosshair is
in Chicago time, on the same chart, at once.

Cause: BUG-014's fix set `localization.timeFormatter`, which in Lightweight Charts v5 formats the
**crosshair** label only. The tick marks along the axis come from `timeScale.tickMarkFormatter`,
which was never set, so they fall back to UTC. The BUG-014 test checks the formatter function, not
what the axis draws — which is why it passed.

This matters more now than when it was written: a practice session is read in session terms (the
Asian range, the New York open), and an axis six hours off teaches the wrong times.

**Fix (batch):** set `timeScale.tickMarkFormatter` from the same `createTimeFormatter`, with a test
that the adapter passes a tick-mark formatter at all.

**✅ FIXED — Session 015.** The adapter now sets `timeScale.tickMarkFormatter` from
`createTickMarkFormatter`, in the same zone as the crosshair. Hour and minute ticks show exchange time.
Day ticks are labelled with their true local weekday and time — e.g. *Wed 18:00* — because the
renderer places them at UTC midnight, and moving them would mean shifting every timestamp given to
it, which breaks wherever the zone's offset changes. A date there would falsely suggest local
midnight; the weekday and time are exact. Documented at the formatter.

**Tests:** `timeAxis.test.ts` (4 new) and `lightweightAdapter.test.ts` (3) — the second mocks the
renderer and asserts the adapter hands a formatter *to the axis*, which is precisely the step BUG-014's
fix skipped. **Browser:** mounted the real adapter on the real library and read back its options —
axis and crosshair both say 09:00 for 14:00 UTC in exchange mode, both 14:00 in UTC mode.

**Lesson:** test what you hand the library, not only the function you wrote. BUG-014's formatter was
correct and tested; nothing checked that the axis ever received it.

---

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

**✅ FIXED — Session 015.** `useDrawings` now reports `syncError`, with load and save failures
tracked separately so a later successful save cannot hide that loading failed. `commit()` resolves
to whether everything is in sync, and a failed shape is still retried on the next commit. `retry()`
saves unsaved work first and reloads only if that succeeded — a reload replaces local drawings with
the server's, so reloading over unsaved work would lose it. The chart shows the message as an alert
with a *Retry now* button.

**Tests:** `useDrawings.test.ts` (6) — load failure reported; save failure reported and cleared by
a successful retry; delete failure reported; retry reloads after a failed load; retry does not reload
over unsaved work; a save does not hide a load failure. **Browser:** with the drawings request forced
to fail, the alert appeared, and it cleared once loading worked again.

**Lesson:** "non-fatal" and "silent" are two separate decisions. The original code needed the first
and got the second for free, which is how it hid a broken save path for a whole session.

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

**✅ FIXED — Session 015**, after measuring, as this entry asked. Four days of practice
history is ~169,000 one-second bars: ~105 ms to generate and ~22 ms to aggregate, paid again on every
session create and resume for the same seed and the same days.

`DayBarCache` holds aggregated bars per `(seed, generatorVersion, timeframe, UTC day)`, LRU, bounded by
bars held (200,000, about 20 MB) rather than by entries. One-second data is never cached. The
correctness worry above is answered two ways: the generator version is in the key, and — the part
that matters for DEC-009 — the cache only serves ranges aligned to the timeframe (`covers()`). For
those, every bar that starts in the range also ends in it, so a cached day, which holds hours a
replay has not revealed, can never contribute a bar past the cursor. Anything else goes straight to
the generator.

**Tests:** `DayBarCacheTest` (7) — bar-for-bar equality with direct generation over 24 random ranges
across six timeframes; no bar past a mid-morning cursor; unaligned ranges bypass the cache; seeds,
timeframes and versions never collide; eviction stays in budget. **Browser, afterwards:** a session
resume took 27–33 ms and returned exactly the bars the create had.

---

### BUG-028 🔴 The revealed seed was rounded by the browser, so it reproduced a different market

**Status:** verified — found in the browser, before the feature was committed
**Fixed:** Session 014, same session · **Found by:** browser check of a completed practice session
**Affects:** `PracticeDtos.PracticeState.seed`, `lib/api/practice.ts`

After a trade completes, the result card shows the seed so the outcome can be reproduced (DEC-012).
The database held `1713776017923440752`; the page showed `1713776017923440600`.

The seed is a 64-bit `SecureRandom` long, sent as a JSON number. JavaScript numbers are doubles and
hold integers exactly only up to 2^53, so the browser silently rounded it. Nearly every practice seed
is above that. The number on screen looked authoritative and would regenerate a *different* market —
a reproducibility claim that was false in almost every case.

Every backend test passed, because Jackson and Java read the JSON back as a long without loss. The bug
only exists in a JavaScript client, and only a real browser showed it.

**Fix:** the API sends the seed as a decimal **string**; the frontend types it as `string` and never
parses it.

**Tests:** `PracticeApiTest` asserts the seed is textual and equal to the stored value;
`PracticeView.test` uses a seed above 2^53 and asserts the exact digits are displayed.

**Lesson:** a 64-bit identifier is not a number to a JavaScript client. Anything that must survive
exactly — seeds, snowflake ids, large counters — crosses the wire as a string.

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
