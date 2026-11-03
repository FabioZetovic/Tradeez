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

### CODEX security and UX audit — 2026-11-01 (Session 018)

**Scope:** the current working copy on `fix/security-audit`, HEAD `70c4d33`, including the source edits already
present when this audit began. Logging only, at Fabio's request. Claude's email work and existing changes were
not edited. **35 new findings: 19 verified, 16 reasoned.** These are additional to the older entries below;
old closed bugs have not been reopened merely because a related, different bypass exists.

**Evidence and reruns:** [audit report and instructions](audits/2026-11-01/README.md),
[12 backend probes](audits/2026-11-01/BackendAuditTest.java),
[8 frontend hook/dialog probes](audits/2026-11-01/frontend-audit.test.tsx), and
[3 frontend page probes](audits/2026-11-01/frontend-pages-audit.test.tsx).
All 23 probes passed; a passing forensic probe means its documented behavior occurred, **not** that the app is safe.
One frontend probe is a successful control: email verification survives React StrictMode and submits once.
The backend used a real local HTTP server and a disposable PostgreSQL container; no existing user database,
real mailbox, production service, or paid market-data API was attacked. `npm audit` reported zero advisories.
No claim of complete vulnerability coverage or a full regression-suite pass is made.

**Repair order:** BUG-137, BUG-138, BUG-140 first; then the other account/recovery and availability issues;
then drawing persistence, practice state, journal, and the remaining UX findings. See T58 in PATH.md.

**Fixed so far (Claude, T58, 2026-11-02):** BUG-137 to BUG-144, BUG-147 to BUG-155, BUG-158, BUG-160 to BUG-171 (and BUG-172, found on the way), moved to [Fixed](#fixed). BUG-146, BUG-156, BUG-157 and BUG-159 fixed too (2026-11-03). Still open: BUG-145 (Fabio's call). Codex's probes
in `BackendAuditTest.java` for these now fail, as they should; their turned-round versions are `SessionRecoveryTest`,
`ExportRevealTest`, `DeployedServerTest` and `ProdProfileTest`.

---

### BUG-145 🟠 The email-verification migration declares every old account verified without proof

**Status:** reasoned · **Affects:** `V34__email_verification.sql`

**What.** `UPDATE app_user SET email_verified_at = created_at` marks every pre-migration account as email-verified.
If someone previously registered another person's address under the old behavior, installing the new feature leaves
that person signed in with an account now labelled verified. No previously impersonated real account is alleged here.

**Impact.** Verification protects new registrations but does not repair the exact email-ownership defect for existing
accounts. This matters if any old database with uncontrolled registrations is retained or deployed.

**Future fix / acceptance.** Decide the migration of existing accounts explicitly: require mailbox proof and revoke
old sessions for unproven accounts; exempt only explicitly controlled development fixtures. Test an unverified legacy
account upgrading through V34 and prove it cannot sign in or refresh until its mailbox owner confirms.

**Claude, 2026-11-02 — waiting on Fabio.** V34 has run with accounts in it only on the local development database: every
account there is Fabio's, the test account or a throwaway probe. A new host starts with an empty table, so V34 marks
nothing there, and every account on it proves its address. Recommendation: no migration. Revisit only if a database
with sign-ups from outside is ever migrated across V34; none exists. The alternative, un-confirming every existing
account, would cost only a local re-confirmation through the outbox.

---

### Security audit, 2026-10-30 — read first

Fabio: "act as a hacker … find every vulnerability possible … We need to protect this user data with our lives. After
that find all the bugs that could affect user activities … THe next run after that we will start fiying all of that."
T57 (2026-10-31) fixed BUG-120 to BUG-127, BUG-130 to BUG-132 and BUG-135 — moved to [Fixed](#fixed). T56 (2026-11-01) fixed BUG-119 and BUG-128.
BUG-133 fixed and BUG-134 mostly fixed on 2026-11-02. Left here: BUG-129, the rest of BUG-134.

**How it was done.** Every endpoint mapped, every request parameter and body read for limits, every lookup by id
checked for the owner, the identity package read line by line, the frontend searched for raw HTML, stored tokens and
redirects, git history searched for every real key, and the built Docker image attacked live at http://localhost:8090
with throwaway `@example.test` accounts (script in the session's scratchpad, `attack.mjs`) — then run against a brand-new
empty database, as a new host gets it.

**What held — no change needed.** One account cannot read, change, advance, end or delete another's practice
sessions, drills or drawings (all 404, the drawing untouched); writes without the CSRF token are refused (403); a
hostile origin gets no CORS grant (preflight 403); path tricks on the page (`..%2f`, `/WEB-INF`, `/.env`,
`/BOOT-INF/classes/application.yml`) return only the page or a 400, never a file; no drill or replay response carries
its answer; sign-in takes the same time for a known and an unknown address (82 ms against 80 ms, median of six); error
bodies name no class, table or SQL; tokens are only in HttpOnly cookies and nothing on the page uses raw HTML; no real
API key has ever been committed (Massive and FRED checked against every commit); the access token's signature, issuer
and generation are checked, refresh tokens are random, hashed and rotated with reuse detection.

In order of how much they matter:

---

### BUG-136 🟡 A management-drill test failed once on the chart it happened to pick

**Status:** seen once (2026-10-31, the full suite during T57), passed alone straight after · **Affects:**
`ManagementDrillApiTest.answered`

The test expected "neither level within 100 candles" in the drill's explanation; the example it drew that run had the
market open beyond the stop 51 candles later ("Hold: … −5.33R"). Examples are picked at random, so the test assumes
something about the example that is not always true — the same family as BUG-093 and BUG-117. To fix: pin the example
(a seed or a fixed date) or assert on what holds for every example.

---

### BUG-129 🟠 The planned free database has no backups and expires

**Status:** reasoned (2026-10-30) · **Affects:** the deployment (DOCUMENTATION "Deploying")

**What.** Render's free Postgres (the easiest free option written in "Deploying") has no backups and is deleted 30 days
after creation. No backup is taken by the app either.

**Why it matters.** "Protect this user data with our lives": one expiry, one mistaken migration or one bad deploy and
every account and record is gone with no way back.

**How to fix.** Before real users: a host with daily backups, or a scheduled `pg_dump` of the user tables (not the
market data, which can be re-imported) to storage Fabio controls, encrypted, kept 14 days; a restore tried once.

---

### BUG-134 🟠 On a new deployment with no market data, a new user walks into dead ends

**Status:** partly fixed on `fix/security-audit` (T57, 2026-11-02) — verified (2026-10-30, the image on an empty
database) · **Affects:** Home, Drills, the drill screen, Chart, News, lesson examples

**What.** Before any history is imported (as on every new host): Home still invites "Start the first lesson" and shows
the path; every drill opens to "No real NQ history has been imported yet…" with a **Try again** button that can never
succeed; Practice cannot start a session; the Chart tab is an empty frame with a faint line of text; lesson examples
are empty; News says "No high-impact news today or tomorrow" when the truth is that the calendar was never loaded (no
FRED key).

**Why it matters.** This is exactly what the jury or a beta tester would see on Fabio's first deploy: an app that looks
broken.

**How to fix.** One "market data not loaded yet" state from the API (instruments carry whether they have history),
shown once and plainly wherever a chart would be; no "Try again" when trying again cannot help; News says the calendar
is not loaded rather than that there is no news. And the deploy checklist imports the data before anyone is invited.

**Done (T57, 2026-11-02).** Drills and Practice answer 503 `{"code":"NO_MARKET_DATA"}` with a plain message
(`NoHistoryException`, `MarketDataExceptionHandler`) instead of a generic error; the drill screen shows it with no
**Try again**; the Chart tab says the market data has not been loaded yet; News says "The economic calendar has not
been loaded yet." when the calendar was never checked. Tests: `RealDrillApiTest.nothingImported`,
`DrillView.test` (no retry on `NO_MARKET_DATA`), `HomePage.test` (calendar never loaded); three mutants caught.
**Still open:** Home still invites "Start the first lesson" and lesson examples are empty boxes on an empty database.

---

### BUG-093 🟡 The SMT lesson's test failed once: ES's first candle an hour before NQ's

**Status:** seen once, not reproduced (2026-10-22). **Affects:** `SmtDrillApiTest.lesson`, perhaps `LessonService.around`

In one full-suite run on `fix/bug-hunt`, an ICT-66 lesson example's ES candles began at 06:10 UTC on 8 May 2025 and its
NQ candles at 07:10 — `es.getFirst() >= nq.getFirst()` failed (1746684600 < 1746688200). The code should not be able
to do that: ES is read from the first NQ candle's time on. Five runs of the test alone, and every later full run on the
branch, passed. Examples are chosen at random, so a rare example, or what earlier test classes left in the shared
database, may be the cause; it was not caught again to look at.

**Next:** if it fails again, keep the surefire XML (the example's times) and the classes run before it.

---

### BUG-101 🟠 The landing page is blank below its chart until each section is scrolled to

**Status:** verified (design critique, 2026-10-28): a full-page screenshot at 1920 shows the hero, the drawn replay,
then about 900px of empty background down to the footer (`docs/redesign/critique/landing.jpg`). **Affects:**
`LandingPage.tsx` — the method, "What Tradeez will and won't tell you" and the closing call to sign up.

**What.** The three sections under the replay start invisible and only appear when they scroll into the window.
**Why it happens.** The motion pass (R-5) gave them `reveal`: `initial: { opacity: 0, y: 24 }` with `whileInView`.
An IntersectionObserver turns each one on only when it enters the viewport. Anything that looks at the page without
scrolling through it in a real window sees nothing there: a full-page screenshot, printing, a link preview, a jump to
the bottom, a slow device that has not run the script yet. The computer's "reduce motion" setting does not help:
`MotionConfig reducedMotion="user"` switches off movement (transforms) but keeps opacity fades, so the sections still
start at 0.
**Why it matters.** The landing page is what the jury and every new user see first; a screenshot of it in the thesis
would be empty below the fold.
**How to fix.** Never hide content for an effect: drop `opacity` from `reveal` and keep only a small rise (`y`), so
a section that has not been reached yet is still fully visible — or remove the scroll reveal and keep only the hero's
entrance. Test: render the landing page and assert no section starts with `opacity: 0`. Check live with a full-page
screenshot.

**⏸ KEPT — 2026-10-28, Fabio:** "thats okay". The scroll reveal stays as it is; nothing to fix. Revisit if a full-page screenshot of the landing page is needed for the thesis or the jury (scroll through it first, or take it with motion off in code).

---

### BUG-117 🟡 A practice test failed once while a browser audit loaded the machine

**Status:** seen once, not reproduced (2026-10-28, `feat/mobile`). **Affects:** `PracticeView.test.tsx`, "shows New York
wall-clock time and keeps the date hidden".

One full frontend run failed this test while puppeteer was driving the dev servers; three runs of the practice tests
right after, and every full run since, passed. The test reads no clock; most likely one of its `findBy` waits (one
second by default) timed out under load, as in the one-off failure noted with the motion pass (DOCUMENTATION,
2026-10-28). **Next:** if it fails again, run it alone at once and keep the output; if it is the wait, give the session
start a longer `findBy` timeout.


**Seen again** (2026-11-01, T56): `DrillView.test.tsx` "lets its candles take the width: two empty bars after the last"
failed once in the full frontend run while the backend suite loaded the machine, and passed three times alone straight
after — the same load-dependent family.
---

*BUG-075 to BUG-092 are the bug hunt of 2026-10-21* — Fabio: "go through the app and mark every single bug you can
find … dont touch them yet", thinking like an attacker for the backend and a trader for the frontend. Logged
untouched, each saying whether it was reproduced against the running app or read from the code; then all eighteen
fixed on `fix/bug-hunt` (Fabio: "merge, then fix all the bugs"), under [Fixed](#fixed).

*Before it:* an audit at the end of session 017 — Fabio asked for every tracked bug to be fixed,
and none was open — found four that had never been logged (BUG-040 to BUG-043); a review before the
merge of T7 found three more (BUG-044 to BUG-046), building T8 three more (BUG-047 to BUG-049), and
building T13 one (BUG-050), the fresh-clone check of T14 one (BUG-051), the upgrade to the newest
versions three (BUG-052 to BUG-054, one a security bug), Fabio's practice report two (BUG-055,
BUG-056), the bug hunt Fabio asked for four (BUG-057 to BUG-060), building T16 two (BUG-061, BUG-062), and
building T35 two (BUG-063, BUG-064), its fresh-clone check one (BUG-065), and the second bug hunt and security
pass Fabio asked for eight (BUG-066 to BUG-073). All but BUG-073 are fixed, and ENV-007 with them.
Every closed entry is under [Fixed](#fixed) with its original description intact.

---

## Environment

### ENV-014 🟠 A mutation run's restoring write failed on Windows and left a mutant in the source

**Status:** verified (session 018, 2026-10-26) · **Affects:** the scratchpad mutation scripts, not the product

Mid-run, Windows refused a write to `PracticeService.java` (`UNKNOWN`, errno -4094 — a transient lock, likely the
build or an indexer holding the file). The script stopped with the second mutant written and the original never put
back: `FillModel model = FillModel.BASIC;` sat where `costs.model()` belongs. Found by checking the file against
`master` before anything was committed, and put back. The scripts now retry every write, read it back to confirm it,
and the run compares the sources with a copy taken before it starts. **Lesson:** ENV-012 again by another road — a
mutation run is not over until the sources are proven to be what they were.

### ENV-013 🟡 The edit scripts read "$$" in a replacement as "$"

**Status:** verified (session 017, 2026-10-19)
**Affects:** the scratchpad scripts that edit files by exact anchors (`edit`), not the product

They passed the new text to `String.prototype.replace` as a string, and JavaScript reads `$$`, `$&`, `$'`
and `` $` `` in a string replacement as patterns. A template literal `$${…}` — a dollar sign before a value —
came out as `${…}`, and the risk-limit error read "A risk limit is $1 to 1,000,000." The T23 PracticeView test
caught it before anything was committed. Every earlier script was searched for the four patterns: only this one
had one. The scripts now share one helper that passes the replacement as a function, which is never read as a
pattern.

### ENV-012 🟠 A mutation run cut off by the session's end left a mutated line in the source

**Status:** verified (session 017, 2026-10-19)
**Affects:** the mutation runners in the scratchpad (one mutation at a time, restored in `finally`)

The session ended while T22's backend run was testing its sixteenth mutation. A `finally` block runs when
the code fails, not when the process is killed, so `PlanContextService` was left with `if (false)` where
the simulated-market check belongs — a working copy that compiled, and would have let a daily-bias
reading be taught from invented data. Found before anything was committed: the next session ran each
runner's anchor check first (`ONLY=nothing`), which names every mutation whose original line is missing.

**Lesson, now the routine:** after any interrupted mutation run, run the anchor check before anything else,
restore what it names, and rerun the affected tests. Here: one line restored, the four mutations not yet
run were run (all caught), and T22's tests passed again on the restored code.

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

**Recurred 2026-10-29 (T53):** Docker Desktop had quit again, and the backend's restart failed on "Connection to
localhost:5432 refused". Starting Docker Desktop brought the daemon back, but the database container stayed stopped
this time ("Exited (0) 11 hours ago") until `docker start tradeez-postgres`. So the check after a restart is
`docker ps` too, not only `docker version`.

---

## Known limitations

Not bugs. Deliberate decisions, listed so nobody mistakes them for oversights.

| Area | Limitation | Why |
|---|---|---|
| Auth | ~~No login or registration **UI**~~ | *Out of date since task 4, which shipped both screens. Struck through rather than deleted (session 014).* |
| Auth | No email verification or password reset | Out of scope for the vertical slice. A signed-in account can change its password (DEC-064); a forgotten one needs email, which the app does not send |
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
| Practice | One partial a trade, and the stop moves only to the entry | DEC-074. Two partials, trailing stops and a stop moved elsewhere are later work; a one-contract trade has nothing to split (MNQ is not offered) |
| Practice | A breakeven stop moves from the bar after the partial's, never inside it | The order of the partial's fill and a dip back to the entry inside one minute cannot be known from one-minute data; the engine never guesses it (INVARIANT 6) |
| Chart | ~~SMT is drawn on one chart; the other market's swings are only named in the words~~ | *Out of date since DEC-075: ES is drawn under NQ in the Chart tab, the lesson's examples and the SMT drill. Struck through rather than deleted.* |
| Practice | The practice replay shows one market | ES beside NQ there would have to be revealed candle by candle with the cursor (DEC-009); the Chart tab, the lesson and the drill show both (DEC-075) |
| Evidence | SMT's counts are made again when this market's data changes, not the other's | `fresh` checks the market the evidence is for; after importing more ES alone, NQ's SMT counts wait for NQ's next import or a new rule version |
| Market data | ES starts two weeks after NQ (22 October 2024) | Massive's free plan reaches two years back from the day of the import; NQ was imported earlier. No SMT is found in NQ's first two weeks |
| Chart | BOS and CHoCH are not named while structure is undefined | Only after an HH and an HL (bullish) or an LL and an LH (bearish) — mixed structure has no trend for a break to agree or disagree with |
| Chart | A replay with a resting clock level asks for the clock every step | While any reference level still rests it could be taken in the next minute, so the replay re-asks each step (DEC-053) instead of waiting for the next clock moment. Saying when a level will be taken would leak the future |
| Chart | Sweeps of PDH/PWH and other reference levels are not reported as turtle soup | Turtle soup is detected on equal highs/lows only; a reference level shows as taken in the clock layer, and whether it was swept is read off the chart (DEC-053) |
| Chart | A swing level is followed for 500 candles | Like a gap's lifecycle: 95% are taken within that on real NQ; the rest are drawn as expired, not resting |
| Chart | A block is followed for 500 candles; three breakers in four are unicorns | Like gaps and liquidity (DEC-061): 83% of order blocks are invalidated within that, the rest drawn as expired. A unicorn is a breaker overlapped by its own displacement's gap, and on real NQ that is usually so — the rule reports it; it does not make it rare |
| Chart | The dealing range is one reading of a judgement | ICT-40 is R/J: which swings make the range is a choice. Tradeez reads the last displacement that broke structure, to its first intermediate-term turn (DEC-062); a learner's own range may well differ — the rule is named beside it, never graded as the right one |
| Chart | Daily charts find few gaps, and so almost no blocks | `fvg-1.0.0` asks for three consecutive candles, and a weekend breaks every triple of daily candles across it: 281 daily candles hold 12 gaps and 3 displacement legs, and no order block (T17). Whether a daily gap may span a weekend is a question for a later version of the gap rule |
| Chart | The IPDA ranges are often missing, the 60-day one nearly always | `ipda-1.0.0` draws a look-back only when all its trading days were on the day's own contract, the clock's roll rule (DEC-047, DEC-063). NQ rolls about every 60 trading days: on two years of real NQ the 20-day range is drawn on 70% of days, the 40-day on 39%, the 60-day on 7%. A back-adjusted series would draw all three every day, at the price of levels that never traded; a possible later task. Simulated markets have none |
| Chart | The time rules are drawn only on charts fine enough to read them | Power of three on charts of an hour or less, the daily profile and the Judas swing on 30 minutes or less — coarser candles do not start at midnight, 08:30 or 09:30 (DEC-063). The weekly profile is on every chart |
| Chart | The London Judas swing is common | On 60% of days in the midnight-to-05:00 window, against the levels still resting at midnight (12% in New York's first half hour). ICT's word for it does not say how rare it is; the number is measured, not chosen (DEC-063) |
| Chart | Some of ICT's profiles are not named | The consolidation day, the consolidation Thursday reversal and the seek-and-destroy Friday need a judgement ("no expansion", "a choppy week") that a rule would invent; a week whose extremes came the other way round (15% of weeks) is not named either (DEC-063) |
| Learn | Evidence is recounted after the app starts, not the moment data is imported | `EvidenceRefresher` (DEC-065) brings the counts up to date in the background after each start — about half a minute for two years on four timeframes. After an import, a card shows the older counts until the next start, with the dates they cover |
| Learn | Some claims are not counted yet | The evidence counts what the rules emit: hits, kinds, how lives ended. Reach rates no rule emits — equilibrium reached, a deviation level reached, the nearer liquidity taken first — need their own counting (DEC-065) |
| Practice | A model's checklist reads the session's own timeframe | The steps are checked on the chart the session was started on (1m, 5m or 15m), whatever timeframe the learner is viewing (DEC-066). ICT often reads the shift on one timeframe and enters on a lower one; a multi-timeframe checklist is later work |
| Practice | A review reads four rules and the model's | The review tells liquidity, sweeps, MSS, FVG and the named model's own rules on the session's timeframe (DEC-068); the other rules are on the chart's switches, and a review on another timeframe is later work |
| Practice | A risk limit in dollars, not a share of an account | The limit is a fixed amount per trade (DEC-067). There is no account balance yet, so no "1% of the account" rule, no drawdown and no daily limit (T26) |
| Practice | Calibration counts every trade with a confidence, from the first | No window: early trades weigh as much as recent ones. A learner who has improved sees it slowly; a rolling view belongs with the journal statistics (T25) |
| Practice | Models are checked, not found | A learner can name a model and have it checked; the app does not yet find every past instance of a model, or start a session just before one (ICT_CONCEPTS.md Part 9, points 1 and 2) |
| Chart | No infinite scroll into older history | Each view loads a fixed window ending at the chosen date; the date picker and the 1W–2Y jumps move it |
| Training | Drills built on simulated data cannot be graded | DEC-034 — teaching from invented data would be circular |
| Fill engine | `AMBIGUOUS` is a legitimate outcome, not a failure | DEC-008 — candle data genuinely cannot always say which of TP/SL came first |
| Order flow | Not implemented, likely never | Impossible without licensed depth data (open question Q-03) |
| Demo volume | Invented, and **nothing volume-derived may be trusted** | There is no real volume to generate. Volume profile, VWAP-by-volume, relative volume and delta would all be reading structure out of a formula. Was BUG-009; it is not a defect, it is the nature of simulated data (DEC-033) |
| Rate limiting | In-memory, therefore per-instance | A second replica would allow the full quota again. A speed bump, not a guarantee; a shared store is the upgrade path if this is ever scaled out |
| ~~`X-Forwarded-For`~~ | ~~Trusted as a best effort~~ | *Out of date since session 017: it was a bug (BUG-070) and is fixed — the header is ignored unless `TRUSTED_PROXIES` says how many proxies to believe. Struck through rather than deleted.* |
| Auth | The breach check fails open | When Have I Been Pwned does not answer within 3 seconds, sign-up goes ahead on the other rules and the miss is logged (DEC-064): failing closed would stop all sign-ups whenever a third party is down |
| Auth | Existing passwords are not re-checked | password-1.0.0 applies to new passwords. An account made under the old rule (12 to 200 characters) keeps its password until it is changed; checking it at sign-in against breaches is a possible later step |
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
| Drills | ~~FVG identification only, on 5m and 15m~~ | *Out of date since T13 (DEC-054): any rule with a drill, identification and classification, and prediction drills since T15 (DEC-058), on 1m to 15m of real NQ. Struck through rather than deleted.* |
| Drills | No drill history or statistics screen | Nothing may be aggregated into a score from fixture drills (DEC-034); answered drills are stored and immutable |
| Practice | Results on simulated data are practice, not evidence | Stated when the session ends. A replay's own score (T5) is per session; no cross-session score exists yet, and when one does (T23), DEC-034 applies |

---

## Fixed

> Entries keep their **original description** so the problem is still legible, with a
> **✅ FIXED** block appended saying how it was closed and which test proves it. Nothing is
> deleted — a bug that vanishes silently teaches nobody anything, and the reasoning is often more
> useful than the fix.

Kept for the lesson, not for the changelog.

### BUG-146 🟠 Several expensive authenticated operations escape the read/work budget

**Status:** ✅ fixed on `fix/security-audit` (T58, 2026-11-03) — reasoned (route and work-path review, not a saturation attack) · **Affects:** `ReadRateLimit.LIMITED`,
`PracticeService.create/get/context/review/finished`, `AccountExport.of`, `LearningService.state`

**Attack paths.** Repeat full practice-session GETs, draft-context POSTs, trade reviews, account exports, statistics,
calibration, or learning-state reads. None is in `ReadRateLimit.LIMITED`. Statistics/calibration/trade listing read the
account's whole trade history before limiting output; export materializes all owned rows. Practice creation checks its
successful-session cap in `saveCapped` **after** selecting a hidden moment and loading history, so rejected attempts can
still repeat the expensive preliminary work. A failed drill search inserts no row and therefore consumes no successful-drill quota.

**Impact.** A single signed-in script can spend database/CPU work through routes outside the budget that was added for
BUG-080. The scanner's two permits limit simultaneous scans, but waiting uses `acquireUninterruptibly` with no bounded
queue/deadline; it does not bound incoming work. Large-account export/history makes the cost grow with account age.

**Future fix / acceptance.** Budget attempted work before it starts, cover these routes, and bound concurrency/queue
waits. Paginate history in SQL and stream or queue large exports. Test allowed and rejected work with small configured
budgets and prove the expensive service is not called after exhaustion; keep ordinary replay playback usable.

**✅ FIXED** (T58, 2026-11-03). The per-account work budget (`ReadRateLimit`, 240 a minute) now covers a full session read, a draft's context, a trade's review, statistics, calibration, the journal, the data export and learning progress, beside the chart reads it had. Stepping a replay (advance, skip, close) stays outside it, so playback is never slowed; the refusal no longer says "chart". Practice creation checks the hourly session cap before it picks a moment and reads days of history, not only when saving. `ReadRateLimitTest.perAccount`: each newly covered route is 429 once the budget is spent, and advance is not. `PracticeApiTest.creationIsCapped`: past the cap with no NQ history, the answer is 429, not "no market data", so no work was done first. Two mutants caught. **Not done:** the history queries are still read whole before paging, and the setup scanner's queue has no deadline. These are bounded now by the budget, not removed.

### BUG-156 🟠 Older open practice sessions disappear from every resume list

**Status:** ✅ fixed on `fix/security-audit` (T58, 2026-11-02) — reasoned · **Affects:** `PracticeSessionRepository.findTop50ByUserIdOrderByCreatedAtDesc`, `PracticeView`, Home

**Reproduce.** Leave a session open and create at least 50 newer sessions. `/api/practice/sessions` returns only the
newest 50 of all statuses, and neither it nor the UI has a next page or a separate all-open query. The original session
is omitted even if it is still in a trade. Its saved URL can still open it, but the normal resume UI cannot find it.

**Future fix / acceptance.** Query open sessions separately with pagination and offer older completed sessions too.
Seed over 50 newer sessions and prove the older open one remains reachable from the UI.

**✅ FIXED** (T58, 2026-11-02). The session list is the newest fifty plus every session not ended, however old, newest first. `PracticeApiTest.oldOpenSessionListed` (one open session behind 55 newer ended ones); mutant caught.

### BUG-157 🟠 The phone journal cannot load its full history and filters can falsely say there are no trades

**Status:** ✅ fixed on `fix/security-audit` (T58, 2026-11-02; desktop 2026-11-03) — verified (rendered pagination/filter probe; server maximum read in code) · **Affects:** `PhoneJournal`,
`listTrades`, `PracticeRecordController.trades`

**Reproduce.** With more than 200 finished trades, the phone loads 50, then increases the requested limit by 20:
70, 90, ... 190, **210**. The API rejects any limit above 200, so the next page fails and older trades remain inaccessible.
The probe observed the `limit=210` request. Selecting Losers/Winners/Long/Short/Ambiguous also sets `further` false:
only already-loaded rows are filtered, and zero matches says "No trades of that kind yet" even if older matching rows exist.
The desktop journal likewise offers only its initially loaded rows without older-page navigation.

**Future fix / acceptance.** Add actual server pagination with a stable cursor and server-side filters, and make every
layout reach all history. Test 250+ mixed trades, filtered matches only on later pages, and repeated load failures.

**✅ FIXED** (T58, 2026-11-02; desktop 2026-11-03). `/api/practice/trades` takes `offset` and `kind` (WINNERS, LOSERS, LONG, SHORT, AMBIGUOUS), filtering before paging. The phone journal pages by offset, 50 at a time, and each filter asks the server. `PracticeJournalApiTest.pagesAndFilters`; `PracticeView.test` "BUG-157" (250 trades, every one reached once, a filter match on the last page); three mutants caught. The desktop journal has **Load older trades**, the next 50 from where it ended, while trades remain: `PracticeView.test` "the journal on a desktop"; mutant caught.

### BUG-159 🟡 Home's practice counts are truncated and can disagree with its own statistics

**Status:** ✅ fixed on `fix/security-audit` (T58, 2026-11-02) — reasoned · **Affects:** `HomePage`'s `trades` and `sessions.length`, the 50-session endpoint

**What.** Home sums `s.trades` from the newest 50 sessions and presents that as its practice count; those trade counts
include a currently running trade. The statistics endpoint counts all finished trades across all sessions. After enough
sessions, the phone's trade count/footer can shrink or disagree with the all-time R/trade statistics shown on the same page.

**Future fix / acceptance.** Use explicit all-time aggregates, labelled by closed versus placed trades; treat recent
sessions as a recent list. Verify Home with more than 50 sessions and one still-running trade.

**✅ FIXED** (T58, 2026-11-02). Home counts "finished trades" from the all-time statistics, the source of its total R, not a sum over the newest fifty sessions that included a running trade. `HomePage.test` "BUG-159"; mutant caught.

### BUG-162 🟡 Phone sheets announce a modal dialog but keyboard focus escapes behind them

**Status:** ✅ fixed on `fix/security-audit` (T58, 2026-11-02) — verified (Tab navigation probe) · **Affects:** `Sheet` in `ui.tsx` and its account, chart-layer, trade-plan consumers

**Reproduce.** Open a sheet and Tab beyond its last control. The probe moved focus onto a button behind the scrim.
The component sets `aria-modal=true` and initially focuses the panel, but does not trap focus, make background content
inert, restore the opener's focus, or lock background scrolling.

**Impact.** Keyboard/screen-reader users can interact with obscured page controls; phone scrolling can move the page
behind an overlay. Focus escape is reproduced; scroll/focus-restoration consequences are code-based follow-ups.

**Future fix / acceptance.** Use a proper modal primitive/native dialog or implement focus containment, inert background,
focus return and scroll handling. Test Tab/Shift-Tab, Escape, nested sheets, closing, and small-screen scroll.

**✅ FIXED** (T58, 2026-11-02). `Sheet` is modal in fact. While it is open, Tab and Shift-Tab wrap round its own controls, every other child of the page is marked `inert`, and the page does not scroll. Closing it, by Escape, the scrim or a choice, gives focus back to what opened it. `ui.test` "BUG-162"; two mutants caught. Nested sheets do not occur in the app and were not tested.

### BUG-163 🟡 The news page stops following time and calendar updates while it stays open

**Status:** ✅ fixed on `fix/security-audit` (T58, 2026-11-02) — reasoned · **Affects:** `NewsPage` and Home's fixed `now` snapshots

**What.** `now` is captured once and news is fetched only on mount. Leave the page open across a release, midnight,
or a backend schedule refresh: "Today", passed-event shading, dates and displayed schedule stay based on the old
snapshot. Resuming a background tab does not refresh them. The server's six-hour refresh does not update an open page.

**Future fix / acceptance.** Refresh time at appropriate boundaries and refetch on visibility/focus or a modest interval.
Test a release crossing, New York midnight, week rollover, and a changed schedule while the page remains mounted.

**✅ FIXED** (T58, 2026-11-02). A new `useNow` hook moves the time on once a minute; the News page and Home's greeting, date and "Today/Tomorrow" news read it. The News page also asks for the schedule again when its tab is looked at again, and every half hour, keeping what it shows if that fails. `NewsPage.test` "BUG-163": a release greys out once its minute passes, and a return to the tab fetches again; two mutants caught.

### BUG-166 🟡 The built page's older CSP contradicts the backend's newer CSP

**Status:** ✅ fixed on `fix/security-audit` (T58, 2026-11-02) — reasoned from both policies and built assets; no fresh browser CSP capture · **Affects:** `vite.config.ts`
`contentSecurityPolicy`, `SecurityConfig.CONTENT_SECURITY_POLICY`

**What.** The build injects a meta policy with `style-src 'self'` and no `font-src`, so fonts fall back to
`default-src 'self'`. The backend allows inline styles and `font-src 'self' data:`, but a permissive header does not
relax the meta policy: both are enforced. The current built CSS contains a data-URL font, and Lightweight Charts
contains code for an inline attribution stylesheet. **The app disables its attribution logo**, so that stylesheet
is not an active failure here. The demonstrated asset mismatch is the embedded JetBrains Mono Cyrillic-extended
font subset (U+0460–052F and related ranges): when requested, its data URL conflicts with the meta policy. Direct CSSOM
style-property assignments are not being claimed universally blocked.

**Impact.** Text needing that font subset can fall back in the built app, with CSP violations. This is a limited font/
policy consistency issue, not a claim that normal Latin text or the chart is broken. Tests against Vite do not inject
this build-only meta policy.

**Future fix / acceptance.** Maintain one consistent policy, including its required font/style sources, without
loosening scripts unnecessarily. Open the actual production build, capture CSP violations, and request/render the
affected font subset. [Multiple CSP policies intersect](https://developer.mozilla.org/en-US/docs/Web/HTTP/Reference/Headers/Content-Security-Policy),
[font-src fallback](https://developer.mozilla.org/en-US/docs/Web/HTTP/Reference/Headers/Content-Security-Policy/font-src).

**✅ FIXED** (T58, 2026-11-02). The build no longer writes a CSP into the page (`vite.config.ts`). Since DEC-084 the server that serves the page sends the one policy, `SecurityConfig.CONTENT_SECURITY_POLICY`, with `font-src 'self' data:`, which T57 checked in a browser against the built image (fonts loaded, no violations). The two were enforced together, so the older meta refused what the header allowed. Checked by building: `dist/index.html` has no `Content-Security-Policy`. No browser capture of the Cyrillic subset was made.

### BUG-158 🟠 The journal orders activity by the random historical market date

**Status:** ✅ fixed on `fix/security-audit` (T58, 2026-11-02) — reasoned · **Affects:** `PracticeService.journal(UUID, int)`

**Reproduce.** Make a trade in a replay of a recent market date, then make another in an older randomly selected replay.
The listing sorts `TradePlan.getSubmittedCursor()` (the historical date), not `submittedAt` (when the learner placed it).
Today's new trade can appear behind months of earlier practice or outside the first 50 rows entirely.

**Future fix / acceptance.** Order recent activity by real submission time with a stable tie-breaker, while keeping
the replay's date hidden/displayed according to its existing rule. Test two sessions whose historical and creation orders disagree.

**✅ FIXED** (T58, 2026-11-02). `PracticeService.journal` sorts by `TradePlan.submittedAt`, when the learner placed the trade, newest first, with the plan's id to break ties. The replay's own moment is still what each row shows, hidden or revealed as before. `PracticeJournalApiTest.journalRows` makes the earlier trade the later in real time; mutant caught.

### BUG-167 🟡 A lesson displays as unlocked while progress is loading or has failed

**Status:** ✅ fixed on `fix/security-audit` (T58, 2026-11-02) — reasoned · **Affects:** `LessonPage`'s independent learning/lesson fetches and `locked` calculation

**Reproduce.** Open a later lesson directly and delay/fail `/api/learning` while its lesson request succeeds.
`locked` is false when `progress` is undefined, and the failed progress request is silently swallowed. Full lesson
content and practice actions are then shown as open even though Learn can show the same lesson locked.

**Future fix / acceptance.** Represent progress loading/failure separately and do not silently treat unknown progress
as an unlocked lesson. Offer recovery/retry and test delayed and failed progress responses. These are pedagogical locks,
not an authorization claim over paid or private content.

**✅ FIXED** (T58, 2026-11-02). `LessonPage` treats progress it does not have as not known: while it loads, the lesson shows "Checking your progress…" instead of its content and its "Practise it" panel. If it fails, an alert says so with **Try again**. The title and summary still show. `LessonPage.test` "BUG-167": a failed load, then a retry that opens it; mutant caught.

### BUG-171 🟡 One failed practice-clock request can disable its overlays for the rest of the mount

**Status:** ✅ fixed on `fix/security-audit` (T58, 2026-11-02) — reasoned · **Affects:** `PracticeSession`'s clock-loading effect

**Reproduce.** Enter a session and let its initial `getPracticeClock` fail once. The catch does nothing, so `clock`
remains null and `clockStale` remains true. The effect depends on `[clockStale, sessionId, clock]`, not the cursor or
a retry counter. Advancing the replay or enabling a clock layer leaves those dependencies unchanged; the effect
does not run again. If a later refresh fails after an existing layer became stale, the same unchanged-true state can
leave that old layer displayed indefinitely. A failed request is therefore more than a single missing frame.

**Future fix / acceptance.** Track the requested cursor and explicit loading/failure state; retry on a later relevant
cursor, a user retry, or a bounded recovery trigger without a tight retry loop. Show a failure when a requested layer
is unavailable. Test one rejected response followed by a healthy response, advance/toggle, and recovery without reload.

**✅ FIXED** (T58, 2026-11-02). `PracticeSession` remembers where a clock request failed (`clockFailedAt`) and asks again once the replay has moved from there. That is one try a position, so no loop, and a step while a request is out does not resend it (the cursor is read through a ref, not a dependency). `PracticeView.test` "BUG-171": a 500, no retry on the spot, a retry after the next bar; two mutants caught.

### BUG-154 🟠 Reloading a newly created practice session can restore its original state instead of its current state

**Status:** ✅ fixed on `fix/security-audit` (T58, 2026-11-02) — verified (hook remount with retained creation state); full refresh sequence traced through routing ·
**Affects:** `PracticeView`'s `location.state.created`, `usePracticeSession`'s seeded fast path

**Reproduce.** Start a session, advance or trade, then reload or revisit its browser-history entry. Creation put the
whole original `SessionResponse` into history state. If that object is supplied again, the hook treats it as current
and skips `getSession` entirely. The remount probe confirmed the original cursor and **no server read**.

**Impact.** The chart appears to lose progress or an open trade; the next command can jump to a different server moment.
A draft made from the stale price can be submitted against the actual later cursor. The server's stored data is not erased.

**Future fix / acceptance.** Consume and clear the creation handoff once, and validate state on subsequent mounts.
Test a real refresh/back after advancing, placing a trade, and completing the session; the current cursor/status must show.

**✅ FIXED** (T58, 2026-11-02). `PracticeView` replaces the history entry as soon as the session has taken its first answer (`navigate(…, { replace: true, state: null })`). The seed is still used once, so a new session is not fetched twice, but a reload or Back now asks the server for the session as it is. `PracticeView.test` "BUG-154" reads the history state after starting; mutant caught.

### BUG-155 🟠 Opening a practice-session link directly drops the saved risk limit and cost display

**Status:** ✅ fixed on `fix/security-audit` (T58, 2026-11-02) — verified (rendered direct route) · **Affects:** `PracticeView.load` and props passed to `PracticeSession`

**Reproduce.** Set a risk limit, then open or reload `/practice/<id>` directly. The effect calls `load()` only when
`openId === null`; therefore settings are never requested on a direct session route. The probe observed no settings
fetch and `riskLimitCents`/`costs` both undefined on the session component.

**Impact.** Pre-trade warnings and the "at the stop with costs" display disappear, although the backend still records
the saved limit and standard costs. A later scorecard can judge a limit the planning screen did not show.

**Future fix / acceptance.** Load account practice settings independently of whether the home or a session is open.
Test direct entry and reload with a saved limit and standard costs, including a settings-load failure state.

**✅ FIXED** (T58, 2026-11-02). The practice settings (risk limit and standard costs) load in an effect of their own on every mount, whether the Practice home or a session's address is open. `PracticeView.test` "BUG-155" opens `/practice/<id>` directly; mutant caught.

### BUG-160 🟠 A failed sign-out looks successful while the browser remains authenticated

**Status:** ✅ fixed on `fix/security-audit` (T58, 2026-11-02) — verified (network-failure/remount probe) · **Affects:** `useSession.logout/logoutEverywhere`, account-menu actions

**Reproduce.** Let sign-out fail before reaching the server. Both hooks set `signedOut` in `finally`, even though neither
HttpOnly cookie was cleared and the server revoked nothing. The probe showed signed out, then immediately signed back
in after remounting. The menu discards the rejected promise with `void`, so there is no useful completion/error state.

**Impact.** On a shared device the user can leave believing the account was secured; reload reopens it. "Sign out of
every device" can similarly imply that remote sessions ended when its request failed.

**Future fix / acceptance.** Report that server sign-out could not be completed and offer a retry; only report global
revocation after confirmation. Hiding private content locally is fine, but distinguish that state from successful
server sign-out. Test offline, 403 and 500 responses followed by reload.

**✅ FIXED** (T58, 2026-11-02). `logout` and `logoutEverywhere` sign this browser out only after the server has, and throw otherwise. The account menu then says "You are still signed in: the sign-out did not reach the server…" and both rows can be tried again. The cookies are HttpOnly, so only the server can clear them: a sign-out that looked done but was not left the next reload signed in. `App.test` "BUG-160": a 500, then a retry that works; mutant caught.

### BUG-153 🟠 The Chart tab can show NQ prices under an ES heading while ES is loading

**Status:** ✅ fixed on `fix/security-audit` (T58, 2026-11-02) — verified (rendered page with delayed ES response) · **Affects:** `ChartPage`'s fallback `series`, price,
instrument economics, clock and setup response keys

**Reproduce.** Load NQ and switch to ES while delaying ES bars. The page immediately says **ES** but retains NQ candles
and a NQ last price (the probe showed 20,000 under ES). The new instrument's economics are passed to the old series and
drawing overlay. Clock responses are rendered from `clock?.layer` without checking the window/symbol key; setup keys
also omit the requested date window, so old-window overlays can remain during a date change.

**Impact.** The displayed market and its prices/overlays can disagree at exactly the moment a user is analysing or
drawing. Keeping a previous frame is useful only if its identity remains correct.

**Future fix / acceptance.** Retain a complete, internally consistent displayed snapshot, or withhold price-dependent
controls until the selected market/window is ready. Key every overlay by the same request identity. Test market, date,
and timeframe switches with delayed and failed responses.

**✅ FIXED** (T58, 2026-11-02). The previous frame stays up while the next one loads only when it is of the same market: a date or timeframe change still keeps the last candles on screen, but a market switch shows the new market's name over no candles, no last price and no drawing tools until its own candles come. The clock overlay is drawn only from the answer to the current request (`clock.key === requestKey`), and the setups' key now includes the window's end, so a date change cannot leave the last window's setups over the new candles. `App.test` "BUG-153" holds ES's candles back after a switch: no NQ price under ES; mutant caught. The clock and setups keys are reasoned, not tested separately.

### BUG-149 🟠 Drawing saves can finish backwards and silently restore an older position

**Status:** ✅ fixed on `fix/security-audit` (T58, 2026-11-02) — verified (controlled out-of-order saves) · **Affects:** `useDrawings.commit`, drawing updates

**Reproduce.** Drag a saved line to A, then to B before A's save completes. Let B's request finish first and A's finish
last. The probe ended with B on screen, A persisted, and **no sync error**. `commit` starts independent writes and has
no per-drawing queue or version check; response completion also rewrites the persisted snapshot out of order.

**Impact.** A normal slow connection can lose the latest drawing edit on reload. BUG-135 fixed duplicate inserts,
not ordering of updates.

**Future fix / acceptance.** Serialize/coalesce writes per drawing and use a server version or conditional update for
multiple tabs. Force response/commit order inversion in a test; reloading must retain the last intended position.

**✅ FIXED** (T58, 2026-11-02). `useDrawings.commit` runs one commit at a time: each waits for the last and diffs the drawings as they are when its turn comes. A later drag's save always goes after an earlier one's, and changes made while a save is out merge into the next. One tab only: two tabs editing the same drawing still have the last save win, with no server version (not needed for one person's chart). `useDrawings.test` "BUG-149" holds the first save until the second is queued; mutant caught.

### BUG-150 🟠 Deleting a new drawing while its first save is pending does not delete it on the server

**Status:** ✅ fixed on `fix/security-audit` (T58, 2026-11-02) — verified · **Affects:** `useDrawings.commit`, its `persistedRef` deletion diff

**Reproduce.** Create a drawing and delay its first PUT; delete it and commit before that PUT finishes. Because its id
has not reached `persistedRef`, the removal sends no DELETE. The pending PUT later succeeds. The probe confirmed an
empty local drawing list, no DELETE at all, and no sync error.

**Impact.** The deleted shape returns on reload. The same sequence can happen with Undo immediately after creation.

**Future fix / acceptance.** Track in-flight creations and deletion intent; send the delete after creation completes
or cancel it safely. Test create/delete, create/undo, and delete/recreate races with delayed responses.

**✅ FIXED** (T58, 2026-11-02). Since commits run in turn (BUG-149), a delete straight after a creation runs once the creating save has landed and is in the persisted map, so its DELETE is sent. Created and removed before any commit ran, nothing is sent at all. `useDrawings.test` "BUG-150"; caught by the BUG-149 mutant.

### BUG-151 🟠 A slow drawing load replaces annotations made while it was pending

**Status:** ✅ fixed on `fix/security-audit` (T58, 2026-11-02) — verified · **Affects:** the initial/retry loading effect in `useDrawings`

**Reproduce.** Delay `listDrawings`, add a line while the chart is usable, then resolve the load with the earlier server
snapshot. The effect unconditionally dispatches `load`, clearing the new line and its undo history without a warning.
The probe observed exactly that. Candles and drawings are fetched separately, so drawing controls can be ready first.

**Future fix / acceptance.** Gate editing until the drawing snapshot is ready or merge local changes made after load
started. Apply the same rule to retry loads. A delayed empty server response must never erase a newly drawn annotation.

**✅ FIXED** (T58, 2026-11-02). A load that arrives keeps every local drawing the server's snapshot does not have, and saves them. A failed load no longer clears the chart: what is drawn stays and is saved with the next change. `useDrawings.test` "BUG-151"; mutant caught.

### BUG-152 🟠 Switching markets leaves the previous market's drawings editable through the new URL

**Status:** ✅ fixed on `fix/security-audit` (T58, 2026-11-02) — verified (hook instrument-switch probe) · **Affects:** `useDrawings`, `ChartPage`, `ChartDrawingController.save/delete`

**Reproduce.** Load NQ drawings, switch to ES, and delay ES's drawings response. The hook keeps NQ's state and persisted
map while `symbolRef` changes to ES. Delete a visible NQ line now: the probe observed `deleteDrawing('ES', nqDrawingId)`.
The backend validates that ES exists but deletes by id and owner only; it does not require that the row belongs to ES.
Updates similarly ignore an existing row's instrument after the owner lookup.

**Impact.** An ordinary market switch on a slow connection can delete or change the user's annotations on the other
market. Owner checks still hold; this is not a cross-account deletion.

**Future fix / acceptance.** Bind loaded drawing state and pending mutations to a symbol, disable old edits during a
switch, and scope backend updates/deletes to owner **and instrument**. Delay the new load and prove no old-market write occurs.

**✅ FIXED** (T58, 2026-11-02). The page drops the old market's drawings and its persisted map the moment the market changes, not when the new market's drawings arrive, so no old id can be sent to the new address. The server scopes both writes to the market: a delete matches owner, id and instrument (`deleteByIdAndUserIdAndInstrumentId`), and a save that finds the caller's drawing on another market answers 404. `useDrawings.test` "BUG-152", `ChartDrawingApiTest.boundToItsMarket`; three mutants caught.

### BUG-143 🟠 Forgot-password timing tells an attacker which email addresses have accounts

**Status:** ✅ fixed on `fix/security-audit` (T58, 2026-11-02) — reasoned · **Affects:** `AuthService.forgotPassword`, `AccountEmails.sendReset/sendUnconfirmed`, `ResendMailer.send`

**Attack.** Compare POST `/api/auth/password/forgot` for a known and unknown address. The unknown address performs a
database lookup and returns; an existing address synchronously performs Resend's HTTP request, potentially waiting
up to ten seconds. Both return 202, but their work is observably different. No live Resend timing campaign was run.

**Impact.** The generic response does not conceal membership; the first few requests also trigger mail. The sign-in
timing tests do not cover this newly added endpoint.

**Future fix / acceptance.** Queue delivery independently of the public response using a reliable outbox, apply the
same public timing/work policy for absent addresses, and measure response distributions with a delayed fake mail
provider for known, unknown, and mail-throttled addresses. Do not hold a user database transaction across the HTTP send.

**✅ FIXED** (T58, 2026-11-02). Every account email is handed to the mail provider after the request's transaction commits, on a virtual thread of its own (`AccountEmails.deliver`). The answer to forgot password no longer waits for Resend, and a rolled-back request sends nothing. What is left between an address with an account and one without is one small insert of the link: milliseconds against a provider's seconds. `MailTimingTest`: with a provider that takes two seconds, both answer in under one, and the mail still arrives after; mutant caught. Not done: a durable outbox with retries. A failed send is logged and lost, as before, and the person asks again (BUG-169 keeps their earlier link working). Tests send on their own thread (`tradeez.test.mail-async`).

### BUG-148 🟡 Racing verified-email registrations can still finish as a server error

**Status:** ✅ fixed on `fix/security-audit` (T58, 2026-11-02) — reasoned · **Affects:** `AuthService.signUpByEmail` inside transactional `register`

**What.** Two new-address sign-ups can both find no account. The losing `saveAndFlush` throws a data-integrity exception;
the helper catches it and returns the normal result **inside the same transaction**. The persistence transaction has
already been marked rollback-only, so committing the outer service call can throw instead of returning the promised 202.
`RegisterRaceTest` mocks the repository and tests the email-verification-disabled branch, so it does not prove this path.

**Impact.** Double submission or two simultaneous sign-ups for an address can show a misleading failure even though
the winning registration exists. No duplicate-account or takeover claim is made.

**Future fix / acceptance.** Handle a uniqueness race outside the failed transaction, or use an atomic database insert
with a separate successful lookup. Add a deterministic two-transaction, email-verification-enabled registration test.

**✅ FIXED** (T58, 2026-11-02). A new address is inserted with `INSERT … ON CONFLICT DO NOTHING` (`AppUserRepository.insertIfAbsent`). A second sign-up for the same address waits for the first and inserts nothing, with no failed statement to spoil its transaction, then gets its own link like any sign-up to an unconfirmed address. `EmailSignUpTest.simultaneousSignUps` holds the address with an uncommitted row until both sign-ups wait on it, then lets go: 202 to both, one account, two links; mutant caught.

### BUG-168 🟡 Reclaiming an unconfirmed email keeps the first registrant's display name

**Status:** ✅ fixed on `fix/security-audit` (T58, 2026-11-02) — reasoned · **Affects:** `AuthService.signUpByEmail`, `AccountEmails`' token snapshot, `AuthService.verify`

**Reproduce.** Register someone else's address with one name; before confirmation, its real owner signs up using
their own password and name and opens their own link. The link correctly installs the owner's chosen password, but
the existing-account branch never saves the new display name and the token carries only a password hash. The owner
inherits the first registrant's name. There is no profile-name editing UI to repair it.

**Future fix / acceptance.** Bind the chosen display name to each sign-up attempt just as the password is bound,
and apply the appropriate validated name when that attempt is verified. Preserve the anti-pre-hijacking password
behavior. Test two sign-ups for one unconfirmed address with different names/passwords and links opened in either order.

**✅ FIXED** (T58, 2026-11-02). V35 adds `email_token.display_name`. A sign-up's link carries the name chosen at that sign-up, as it carries the password, and opening it sets both. `EmailSignUpTest.nameTravelsWithTheLink`, in both orders; mutant caught.

### BUG-169 🟠 A failed reset-email send invalidates the last working link without arranging a retry

**Status:** ✅ fixed on `fix/security-audit` (T58, 2026-11-02) — reasoned · **Affects:** `AccountEmails.issue(Purpose.RESET)`, `ResendMailer.send`

**Reproduce.** Request a reset successfully, then request another during a Resend refusal/network failure.
`issue` spends all previous RESET links before delivery. The mailer logs a non-2xx/network failure and returns normally;
the transaction commits the replacement token, the old delivered link stops working, and the new one was never delivered.
There is no durable delivery queue/retry; the request also consumes the shared email budget.

**Impact.** The owner can lose the only usable recovery link after being told another was sent, making a temporary
mail-provider problem into a recovery dead end.

**Future fix / acceptance.** Use a durable mail outbox and retry/acknowledgement policy; do not irretrievably invalidate
a delivered recovery path merely because delivery of a replacement was attempted. Keep public responses generic.
Test provider 429/500/timeouts, retry delivery, token expiry and the previous link's intended lifecycle.

**✅ FIXED** (T58, 2026-11-02). A new reset link no longer spends the earlier ones. Opening any of them spends the rest (`AccountEmails.use`), so recovery stays single-use, and at most three go out an address a quarter-hour, each good for an hour. A replacement that fails to arrive no longer takes away the link that did, and since BUG-143 a failed send cannot fail the request. `EmailSignUpTest.earlierResetLinkSurvives`; mutant caught. Durable retries are not built (BUG-143).

### BUG-161 🟠 Email verification and password-reset links are discarded when a session already exists

**Status:** ✅ fixed on `fix/security-audit` (T58, 2026-11-02) — reasoned · **Affects:** the signed-in route table in `App.tsx`

**Reproduce.** While signed in, open `/reset-password#token`, `/verify-email#token`, or `/forgot-password`. Those routes
exist only in the signed-out branch; the signed-in wildcard redirects to Home without rendering the form/redeeming
the link. This also affects an email link for a second account opened in a browser signed in to the first.

**Future fix / acceptance.** Make emailed-link/recovery routes reachable regardless of current session, with an explicit
account-switch choice when needed. Test a valid reset link while signed in and a verification link for another account.

**✅ FIXED** (T58, 2026-11-02). `/verify-email`, `/forgot-password` and `/reset-password` are matched before the signed-in and signed-out route tables (`App.tsx`, `EMAIL_LINK_PATHS`), so an emailed link opens its page whoever is signed in. Opening it signs in as the account it belongs to, as it does signed out. No separate account-switch prompt was added: the link is the owner's proof, and the page names what it is doing. `App.test` "BUG-161 signed in, an emailed link still opens its page"; mutant caught.

### BUG-170 🟡 Resending a verification email reports success even when the request fails

**Status:** ✅ fixed on `fix/security-audit` (T58, 2026-11-02) — reasoned · **Affects:** `SignInForm.sendAgain`, both "Send the link again" buttons

**Reproduce.** Sign up or try signing in to an unconfirmed account, then resend the link while offline or after the
endpoint returns 429/500. `sendAgain` sets `resent=true` in `finally`, has no error handler, and is called with `void`.
The screen says a new link is on its way even if no request reached the server. There is no in-flight guard, so rapid
taps can also send duplicate requests and consume the resend/sign-in IP quota.

**Impact.** A user waiting for an already-missing verification email gets a false success message and no way to tell
that they must reconnect or wait before retrying. The rejected promise can also become an unhandled browser error.

**Future fix / acceptance.** Show the generic queued message only after a successful response, handle failures without
revealing account existence, and disable/debounce while sending. Test network failure, 429, 500, and repeated taps.

**✅ FIXED** (T58, 2026-11-02). "Send the link again" says a link is on its way only after the server accepted the request. A refusal (429, 500, offline) shows the server's own wording as an alert, which never says whether the address has an account. While the request is out, the button reads "Sending…" and is disabled, so repeated taps send one request. `EmailLinks.test`: a 429 and repeated taps; two mutants caught. A redundant in-flight check that a mutant showed did nothing beside the disabled button was removed.

### BUG-172 🟡 Overlapping first saves of a drawing could still answer 404

**Status:** ✅ fixed on `fix/security-audit` (T58, 2026-11-02) — seen once (2026-11-02, `ChartDrawingApiTest.overlappingFirstSaves` in its class run; passed alone three times) · **Affects:** `ChartDrawingController.saveOnce`

**What.** BUG-135's fix retried a save that broke the key, but left a second gap. A save looks for the caller's row, then
asks whether the id exists at all; under READ COMMITTED each query sees the latest commit. An overlapping first save
that commits between the two makes the first answer "none of yours" and the second "it exists", so the owner was told
404, "not yours", for their own drawing.

**✅ FIXED** (T58, 2026-11-02). When the id exists, the owner-scoped lookup is asked again before refusing; the row is
committed by then, so the owner's own row is found and updated, and only a row still not theirs is a 404. Reasoned and
observed, not forced: the window is between two queries of one request, too narrow to make certain in a test.
`overlappingFirstSaves` passed four class runs after the change.

### BUG-164 🟡 An extreme news timestamp still reaches an unhandled server error

**Status:** ✅ fixed on `fix/security-audit` (T58, 2026-11-02) — verified (real HTTP, 500) · **Affects:** `NewsController.news`

**Reproduce.** Signed in, GET `/api/news?from=9223372036854775807`. `Instant.ofEpochSecond` throws before the 2000–2100
check runs; with `to` omitted an extreme-but-valid start can also overflow `start.plus(DEFAULT_SPAN)`. The shared
`Epochs.of` guard used by chart endpoints was not applied here. This is a different boundary from BUG-098's year-2100 fix.

**Future fix / acceptance.** Bound raw epoch values before constructing/adding times and return 400 for both parameters,
including omitted-to cases and Long.MIN/MAX. Do not log a full unhandled stack for ordinary rejected input.

**✅ FIXED** (T58, 2026-11-02). `NewsController` builds both times through the shared `Epochs.of` guard, so a value outside 1970–9999 is a 400 naming the parameter before any arithmetic, and `start.plus(14 days)` cannot overflow from there. `NewsApiTest.bounded`: Long.MAX/MIN for `from`, for `to`, and with `to` left out; mutant caught.

### BUG-165 🟡 A drawing with a null anchor bypasses field validation and crashes

**Status:** ✅ fixed on `fix/security-audit` (T58, 2026-11-02) — verified (unhandled NullPointerException through the controller) · **Affects:** `DrawingDto.anchors`,
`ChartDrawingController.saveOnce`

**Reproduce.** PUT a valid owned drawing id/kind with `"anchors":[null]`. The list is nonempty, and `@Valid` on its
elements does not reject null. The later loop calls `anchor.time()` and throws. The probe confirmed the unhandled
exception instead of a validation response. Authentication/CSRF still apply; this is malformed-input robustness, not data theft.

**Future fix / acceptance.** Require non-null elements before dereferencing them; test null list, null elements,
missing coordinates and valid anchors. Each malformed request should receive a safe 400.

**✅ FIXED** (T58, 2026-11-02). `DrawingDto.anchors` is `List<@NotNull @Valid AnchorDto>`, so a null element fails validation as a 400. `ChartDrawingApiTest.malformedAnchorsAreRejected`: a null list, `[null]`, a null after a good anchor, and an anchor missing either coordinate are all 400, and a good one still saves; mutant caught.

### BUG-147 🟠 The local database listens on every network interface with shared development credentials

**Status:** ✅ fixed on `fix/security-audit` (T58, 2026-11-02) — reasoned for remote exploitability; all-interface binding verified by `docker ps` · **Affects:** `docker-compose.yml`

**Attack.** The compose file publishes `5432:5432` and contains a fixed, well-known database login. The running container
reported `0.0.0.0:5432` and `[::]:5432`. Someone who can reach that port can attempt a direct database login, bypassing
all application authentication and ownership checks. Actual LAN reachability depends on the host firewall; no outside
host was probed and this is not a claim about a future managed production database.

**Impact.** Local account hashes, email-link hashes, journals, and drawings are exposed if the workstation's database
port is reachable from its network. The current compose file is explicitly the local-development setup.

**Future fix / acceptance.** Bind local PostgreSQL to loopback (or keep it on an internal container network), use a
machine-local secret, and keep deployment database access private. Verify the published listener and access from an
untrusted network after the change; retain local app connectivity.

**✅ FIXED** (T58, 2026-11-02). `docker-compose.yml` publishes the port as `127.0.0.1:5432:5432`. After the container was recreated (its volume and data kept), `docker ps` shows `127.0.0.1:5432->5432/tcp`; a connection to the machine's network address on 5432 fails and one to 127.0.0.1 succeeds. The development login stays fixed and well known, which is acceptable now that only this machine can reach it. A deployed database is the host's, with its own secret (DOCUMENTATION "Deploying").

### BUG-137 🔴 Production forwarded-header processing defeats both sign-in limits

**Status:** ✅ fixed on `fix/security-audit` (T58, 2026-11-02) — verified (2026-11-01, real HTTP) · **Affects:** `application-prod.yml`, `AuthRateLimitFilter`,
`ClientAddress`, `AuthService.login` · **Prerequisite:** incoming forwarded headers reach the app without trusted-edge sanitization.

**What and how to reproduce.** With production's `server.forward-headers-strategy=framework`, send wrong-password
POSTs to `/api/auth/login` for the same address from one connection source. Without spoofing, the probes hit 429.
Changing only `X-Forwarded-For` between requests produced **25 consecutive 401s, no 429**; changing RFC `Forwarded:
for=...` produced another 25. No account is needed. Spring's filter rewrites `getRemoteAddr()` and removes the header
before `ClientAddress` runs, so the latter's `trusted-proxies=0` check no longer protects the original connection address.
The account-and-address limit is keyed on that same forged address. Trusting one proxy in this helper does not undo
earlier rewriting by Spring.

**Impact.** An attacker can keep guessing or spending BCrypt work beyond both limits. The old direct-header fix
BUG-070 and warning BUG-131 do not cover this filter ordering. Whether a particular host strips/replaces both header
families must be verified on that host; the app's deployment configuration itself accepts the forgery.

**Future fix / acceptance.** Establish one trusted proxy boundary, strip untrusted `Forwarded` and `X-Forwarded-*`
headers there, preserve the connection peer for app-side trust checks, and avoid independently interpreting forwarded
addresses twice. Repeat this real-server test with both header families and the intended hosting proxy; spoofing must
not buy a new quota. [Spring's trust-boundary guidance](https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/filters.html#filters-forwarded-headers).

**✅ FIXED** (T58, 2026-11-02). The prod profile uses `forward-headers-strategy: native`, not `framework`. Tomcat believes `X-Forwarded-*` only from a proxy on a private network (`server.tomcat.remoteip.internal-proxies`), reads `X-Forwarded-For` from the right, so it gets what the proxy appended rather than what the caller wrote, and ignores RFC `Forwarded`. Spring's filter, which believed both from anyone, no longer runs. `tradeez.rate-limit.trusted-proxies` is fixed at 0 deployed, because Tomcat has already resolved the address. `DeployedServerTest.forwardedAddressesCannotBeMadeUp`: 25 attempts with a new made-up address each, in both header families, reach 429. `ProdProfileTest.forwardedHeadersOnlyFromTheProxy` keeps the profile saying so. If a host's proxy connects from a public address, every visitor would share one quota: set `SERVER_TOMCAT_REMOTEIP_INTERNAL_PROXIES` to it (DOCUMENTATION "Deploying").

### BUG-138 🔴 A rotated refresh token can undo sign-out and a password change

**Status:** ✅ fixed on `fix/security-audit` (T58, 2026-11-02) — verified (three independent service/API probes) · **Affects:** `AuthService.refresh`, `withinReuseGrace`,
`RefreshTokenRepository.revokeAllForUser`

**Attack.** Obtain refresh token R, rotate it to R2, then have the owner sign out of every device or change their
password. Replay R within 20 seconds of its original rotation. `isAlreadyRotated()` enters the grace branch before
checking activity or the account's revocation state and calls `issueFor` with the user's **current** generation.
The new access token reads `/api/me` with **200**. The same bypass works after ordinary logout of R2.

**Impact.** A thief holding a recently rotated cookie regains a fresh 30-day session after the recovery action
intended to remove them. This is distinct from simply accepting a concurrent duplicate before any sign-out.

**Future fix / acceptance.** Bind rotation families to a revocation/session generation and refuse grace after any
family or account revocation. A grace duplicate should resolve to a still-live successor, not mint an unrelated family.
Keep legitimate simultaneous-tab refresh working. All three probes must reject the old ancestor after revocation.

**✅ FIXED** (T58, 2026-11-02). The grace for a duplicate refresh now follows the token's rotation chain to its newest member and serves the duplicate only while that member is still active. After a sign-out, a sign-out everywhere or a password change, the old token is refused (401) and starts nothing. `SessionRecoveryTest`: the three probes turned round, plus `graceStillServesDuplicates` (two tabs refreshing at once, BUG-001, still both signed in).

### BUG-139 🟠 Detecting refresh-token theft leaves already-issued access tokens working

**Status:** ✅ fixed on `fix/security-audit` (T58, 2026-11-02) — verified · **Affects:** the reuse-detection branch of `AuthService.refresh`

**Reproduce.** Rotate a token; replay its ancestor outside the grace period. The probe moved its test database timestamp
back 30 seconds rather than sleeping. Reuse is detected and active refresh tokens are revoked, but the successor's
access token still reads `/api/me` with **200**. This branch never increments `AppUser.tokenGeneration`.

**Impact.** The log says every session was invalidated while a stolen access token retains data access and authenticated
writes until its remaining 15-minute lifetime ends. The password-change and logout-all paths already do invalidate it.

**Future fix / acceptance.** Commit access-generation revocation together with refresh-family revocation on detected
reuse, including the exception path. Every previously issued access and refresh token must then be rejected.

**✅ FIXED** (T58, 2026-11-02). Detected reuse now also raises the account's token generation, as a password change does, so every access token already handed out is refused along with the refresh tokens. It commits with them (`noRollbackFor`). `SessionRecoveryTest.theftEndsAccessTokens`: the successor's access token gets 401.

### BUG-140 🔴 Downloading account data exposes answers and practice outcomes before they are revealed

**Status:** ✅ fixed on `fix/security-audit` (T58, 2026-11-02) — verified (authenticated export and correct answer submission) · **Affects:** `AccountExport.SECTIONS`,
`MeController.export`, the no-lookahead boundaries

**Attack.** Start a risk question and GET `/api/me/export`. `riskDrills` contains its exact `answer`, `tolerance`, and
`question.working` while the ordinary drill result is still absent; submitting that exported answer was graded correct.
Start real-data practice, place a bracket, and export before advancing: the normal response withholds the result, but
`tradeResults` includes its future outcome/exit and `practiceSessions` its real hidden start date. The probe reproduced
both. Raw `drills`, `drillMarks`, and prediction `question`/`outcome` columns are exported through the same unrestricted
table queries too; these additional drill modes were traced in code rather than individually exercised.

**Impact.** The account menu provides a normal, discoverable way to bypass the training rules, recover hidden dates,
and corrupt progress/calibration. This exposes the caller's own training secrets; no cross-account export was found.

**Future fix / acceptance.** Define a safe export schema: user-provided answers and already-revealed results only;
withhold server answer keys and hidden replay metadata until their exercise/session is over. Test every drill mode,
an active real replay, early-close counterfactuals, and completed sessions. Do not remove the ability to export personal data.

**✅ FIXED** (T58, 2026-11-02). Each export section's query marks a row still open (`open_`: a drill or risk question not answered, a practice session not COMPLETE), and `AccountExport.HIDDEN_WHILE_OPEN` drops that row's answer, outcome, seed and real dates. A drill's marked setups (`drillMarks`) are left out until it is answered. A trade's result is left out until the replay passes its exit (the score's rule), and comes without its real times until the session ends. Everything comes once revealed; the export says so in `about`. `ExportRevealTest`: a mark drill, a risk question, and a real session before its exit, after it, and ended; three mutants caught.

### BUG-141 🟠 A single-use reset link can reset the password twice in a race

**Status:** ✅ fixed on `fix/security-audit` (T58, 2026-11-02) — verified (two concurrent transactions in disposable PostgreSQL) · **Affects:** `AccountEmails.use`,
`EmailTokenRepository.spendAll`, `AuthService.resetPassword`

**Reproduce.** Submit the same valid reset token concurrently with two different valid new passwords. The probe held
the token's row briefly until both calls had read it unused, then released it. **Both calls succeeded and issued
credentials.** The second change invalidated the first call's access token (observed access statuses 401 and 200).
`use` reads usability first, then bulk-spends links; it ignores whether its spending update changed any row.

**Impact.** A competing holder of the link can overwrite the owner's successful reset. Serial second use is rejected;
the defect is specifically the concurrent check-and-spend gap. Verification uses the same redemption helper.

**Future fix / acceptance.** Atomically claim the particular unspent, unexpired token, or lock and recheck it; exactly
one caller may continue. Invalidate sibling links in the same transaction. Test concurrent VERIFY and RESET calls.

**✅ FIXED** (T58, 2026-11-02). `AccountEmails.use` claims the one link with `UPDATE … WHERE id = ? AND used_at IS NULL` and goes on only when that changed a row. Postgres makes a second claim wait for the first and re-check `used_at`, so exactly one caller continues. `SessionRecoveryTest.resetLinkRace` holds the row until both calls have read it unused (Codex's probe): one reset, one refused.

### BUG-142 🟠 Changing the password does not invalidate an outstanding password-reset link

**Status:** ✅ fixed on `fix/security-audit` (T58, 2026-11-02) — verified · **Affects:** `AuthService.changePassword`, `AccountEmails`

**Attack.** Request a reset link, then change the password normally with the current password. Redeem the previously
issued reset link: it still changes the password and obtains authenticated access. The probe performed exactly this.
Password changes revoke sign-in tokens but never spend pending RESET tokens.

**Impact.** Someone holding an earlier reset email can regain the account after its owner changes the password to
secure it, for up to the link's one-hour lifetime.

**Future fix / acceptance.** Invalidate outstanding recovery tokens on successful authenticated password changes and
other credential-recovery events. The earlier link must fail after a change, while a newly requested link still works.

**✅ FIXED** (T58, 2026-11-02). A password change spends every unused reset link of the account (`AccountEmails.spendResets`), in the same transaction. `SessionRecoveryTest.passwordChangeSpendsResetLinks`: the link from before the change is refused, and one asked for after works.

### BUG-144 🟠 Three unsolicited sign-up notices can prevent the owner receiving a reset email

**Status:** ✅ fixed on `fix/security-audit` (T58, 2026-11-02) — verified (shared-mail-budget probe; public caller path traced in code) · **Affects:** `AccountEmails.allowed`,
`sendAlreadyRegistered`, `sendReset`, `AuthService.signUpByEmail`

**Attack.** Submit three sign-ups for an existing verified address using any policy-valid chosen password. Each sends
an "already registered" notice and consumes the shared `mail:<email>` bucket. The owner's forgot-password request then
returns its usual success without a reset email for the rest of that 15-minute window. The probe sent the three notices
through the mail service and confirmed that password recovery produced no additional message.

**Impact.** Anyone who knows the address can impede account recovery without knowing its password. The victim is told
to check an inbox that will not contain a recovery link.

**Future fix / acceptance.** Separate recovery and unsolicited-notification budgets, suppress repetitive sign-up
notices, and retain an aggregate anti-spam ceiling. Test that notice spam cannot consume all legitimate recovery capacity.

**✅ FIXED** (T58, 2026-11-02). The notes nobody asked for ("someone tried to sign up with your address", forgot password on an unconfirmed account) have their own budget, one per address per 15 minutes, apart from the three links (`AccountEmails.NOTICES_PER_ADDRESS`). Repeated sign-ups with an address send its owner one note, and their reset link still comes. `SessionRecoveryTest.noticesLeaveResetBudget`, `noticesAreRare`.

### BUG-133 🟡 The local test account's address and password are in git

**Status:** ✅ fixed on `fix/security-audit` (T57, 2026-11-02) — verified (2026-10-30) · **Affects:** `backend/.env.example` (comments)

**What.** The local test account's email and password sit in comments in `.env.example`, committed. The repository is
private and nothing creates the account automatically.

**Why it matters.** Anyone given access to the repository knows them; if that account is ever registered on a deployed
instance with the same password, it is open to them.

**How to fix.** Move them to the untracked `backend/.env`, rewrite the scripts to read them from there, and never
register that account on a deployed instance (write it in "Deploying").

**✅ FIXED** (T57, 2026-11-02). The two lines moved to the gitignored `backend/.env`; `.env.example` now only says where
they live. The session scripts read them from `.env` and never print them. Because the old password stays in git
history, the account's password was changed through `/api/auth/password` to a new random one, stored only in `.env`.
`EnvExampleTest.noTestAccount` fails the build if a `DEV_TEST_*` value comes back to the example, even in a comment.

### BUG-128 🟠 Free, unverified accounts make the database fillable by one script

**Status:** ✅ fixed on `fix/security-audit` (T56, 2026-11-01) — reasoned (2026-10-30) · **Affects:** registration, `DrillService`, `PracticeService`, the drawings

**What.** Each account has hourly caps (600 drills, 60 sessions, 500 drawings a chart), but accounts are free: 20
sign-ups per 5 minutes per address is 5,760 accounts a day from one machine, each able to write to its caps.

**Why it matters.** A free database is small (Render's free Postgres: 1 GB). Filling it stops every real user's writes;
on a paid plan it costs money.

**How to fix.** T56 first (no writes before the address is verified; unverified accounts deleted after 7 days); then a
global sign-up ceiling per hour, a size watch on the database, and old finished drills pruned.

**✅ FIXED** (T56, 2026-11-01). T56: an account cannot sign in, and so cannot write anything, until its address is confirmed; one never confirmed is
deleted after seven days (`SessionHousekeeping`, `EmailSignUpTest.unconfirmedDeleted`); the whole site takes at most 300
sign-ups an hour (`AuthService.SIGN_UPS_PER_HOUR`), and an address at most three emails per 15 minutes. Not done: a watch
on the database's size.

---

### BUG-119 🔴 Anyone can sign up as anyone: an email address is never shown to belong to the person

**Status:** ✅ fixed on `fix/security-audit` (T56, 2026-11-01) — verified (2026-10-30) · **Affects:** `AuthService.register`, `AppUser`, the sign-up and sign-in pages ·
**Task:** T56

**What.** Registration takes any address and signs the account straight in. `elon@tesla.com`, a classmate's address,
a jury member's, `admin@` anything — all accepted (201), and the account is fully usable at once.

**Why it matters.** (1) Impersonation: the address is the account's identity, shown in the account menu and the only
thing tying a person to their record. (2) The real owner of the address can never sign up — it is "already
registered" (409). (3) Nobody can recover a forgotten password: there is no reset, because there is no proven address
to send one to. (4) Every other abuse below (BUG-128 mass accounts, BUG-121's forged sessions needing an account)
gets cheaper when accounts are free and anonymous.

**How to fix (T56).** An account is *unverified* until its owner opens a single-use link sent to the address:
- On sign-up: store the account unverified, send a link with a random token (stored only as its SHA-256, like refresh
  tokens), valid 24 hours, single use. Until it is used: no sign-in, or a signed-in screen that says only "check your
  inbox" with a throttled "send again".
- The sign-up answer is the same whether the address is new or taken: "Check your inbox". A taken address gets an email
  saying someone tried to sign up with it (with a reset link) — so the sign-up form no longer tells anyone which addresses have accounts (today it answers 409, verified).
- Unverified accounts are deleted after 7 days (closes most of BUG-128).
- With proven addresses, add "Forgot password" (same token machinery, 1 hour, signs out every session after) and a
  verified email change.
- Sending email needs a provider with a free tier (e.g. Resend or Brevo — no money, Fabio's sign-up) and, for mail that
  is not marked spam, a domain with SPF/DKIM records: **this waits on Fabio's domain** (T51). Until then the links can
  be sent from the provider's test sender to Fabio's own address only, enough to build and test.

**✅ FIXED** (T56, 2026-11-01). T56 on `fix/security-audit`. Sign-up signs nobody in: it answers 202 "check your inbox" and emails a single-use link
(24 hours; only its SHA-256 is stored, in `email_token`, V34) whose token sits after a `#`, so no server, proxy or
`Referer` ever sees it. Opening it (`/verify-email`) confirms the address and signs in. Until then the right password
gets 403 `EMAIL_NOT_VERIFIED`, and the page offers the link again — which needs the password. The answer to a sign-up is
the same whether the address was new or taken, in the same time (a password is hashed on both paths): a taken,
confirmed address only gets an email telling its owner. So sign-up no longer reveals which addresses have accounts.
**Pre-hijacking** — signing up first with someone else's address and a password of one's own, waiting for the owner to
confirm — is closed: each sign-up's link carries the password chosen at that sign-up and sets it when opened, and the
other links die. "Forgot password" (`/forgot-password`, always 202) emails a one-hour reset link to a confirmed account
(`/reset-password`: a new password, every session signed out) and only a note, no link, to an unconfirmed one. Each
address gets at most three emails per 15 minutes. Existing accounts were marked confirmed by V34 (the local test account
signs in as before). Mail goes through `Mailer`: `outbox` locally (files in `backend/outbox/`, gitignored; the log names
the file and subject, never the address or the link) and `resend` deployed (Resend's HTTP API, the key only in the
Authorization header). The `prod` profile needs `MAIL_PROVIDER` and `APP_URL`, and `MailGuard` refuses to start with
confirmation off, with the outbox, or with an `http://` app address. `EmailSignUpTest` (7), `MailTest` (3),
`EmailLinks.test.tsx` (7); checked live — sign-up, the link from the outbox, sign-in on Home with the token gone from
the address bar; forgot, reset, signed in. Not done: changing the address of an existing account (there is no such
feature yet — when there is, it must confirm the new address the same way).

---

### BUG-135 🟡 Two saves of a new drawing at once answer 500

**Status:** ✅ fixed on `fix/security-audit` (T57, 2026-10-31) — reasoned (2026-10-30) · **Affects:** `ChartDrawingController.save`

**What.** A new drawing's id is chosen by the page, and a drag can send more than one save before the first commits.
Both find no row (owner-scoped lookup and `existsById` both see nothing uncommitted), both insert, the second breaks
the primary key: 500, and the page shows a save error for a drawing that was saved.

**How to fix.** Catch the duplicate-key failure on create and retry as an update for the owner (the row now exists and
is theirs), or insert with `ON CONFLICT DO NOTHING` and then update. Test with two concurrent PUTs of a new id.

**✅ FIXED** (T57, 2026-10-31). The save runs in its own transaction; when two first saves of one drawing collide, the loser's duplicate-key failure is
caught and the save runs once more — the row is there now, so it is an update for its owner and still a 404 for anyone
else. `ChartDrawingApiTest.overlappingFirstSaves` (five rounds of four concurrent saves).

---

### BUG-132 🟡 Small leaks that help an attacker map the server

**Status:** ✅ fixed on `fix/security-audit` (T57, 2026-10-31) — verified (2026-10-30, the image) · **Affects:** `HealthController`, Spring's auto-configuration, springdoc,
`SecurityConfig.passwordEncoder`

- `/api/health` tells anyone the application's name and when it was started and built — enough to time deploys and
  restarts. Signed out it should say only UP or DOWN.
- Spring logs "Using generated security password: …" at every start: an unused default user, harmless (form and basic
  sign-in are off) but it puts a password in the logs. Exclude `UserDetailsServiceAutoConfiguration`.
- springdoc still builds the API description in production; security refuses it, but it should be switched off there
  (`springdoc.api-docs.enabled=false` in the `prod` profile).
- BCrypt runs at cost 10. Fine today; 12 is the current advice for new hashes (rehash on sign-in).

**✅ FIXED** (T57, 2026-10-31). Deployed, `/api/health` answers only status, database and time (`DeployedServerTest.apiHealthSaysLittle`);
`UserDetailsServiceAutoConfiguration` is excluded, so no generated password is logged (checked on the image); springdoc
is off in the prod profile; BCrypt runs at cost 12 (`BCRYPT_STRENGTH`), and a hash made at a lower cost is redone at the
owner's next sign-in (`PasswordRehashTest`).

---

### BUG-131 🟡 Rate limits forget on restart, and behind a proxy can lock everyone out at once

**Status:** ✅ fixed on `fix/security-audit` (T57, 2026-10-31) — reasoned (2026-10-30) · **Affects:** `RateLimiter`, `ClientAddress`, the deployment's `TRUSTED_PROXIES`

**What.** Limits live in memory: a free host that sleeps and wakes, or any restart, gives every guesser a fresh quota.
And if `TRUSTED_PROXIES` is left at 0 behind a host's proxy, every visitor arrives from the proxy's address — the
whole site shares one quota of 20 sign-ins per 5 minutes, so one attacker (or a class signing in together) locks
everyone out.

**How to fix.** Start-up warning (or refusal in the `prod` profile, BUG-121) when requests carry `X-Forwarded-For` but
no proxy is trusted; keep the per-account limit as the main guard; a shared store if ever more than one instance.

**✅ FIXED** (T57, 2026-10-31). `ClientAddress` logs one warning per process when requests carry `X-Forwarded-For` but no proxy is trusted, naming
`TRUSTED_PROXIES`. Not refused: a client can send the header itself. The limits themselves are still in memory, as
`RateLimiter` documents; the per-account limit is the guard that matters.

---

### BUG-130 🟡 Every sign-in record is kept forever, with the browser's name

**Status:** ✅ fixed on `fix/security-audit` (T57, 2026-10-31) — verified by reading (2026-10-30): nothing deletes `refresh_token` rows · **Affects:** `RefreshToken`,
`AuthService`

**What.** Each refresh (every 15 minutes of use) adds a row, with the browser's user-agent string; rotated and expired
rows are never deleted.

**Why it matters.** Personal data kept with no purpose and no limit (GDPR data minimisation, BUG-120), and a table that
only grows.

**How to fix.** A scheduled job deleting rows expired or revoked more than 30 days ago (keeping recent ones for reuse
detection); store a coarse browser name ("Chrome on Windows") rather than the full string.

**✅ FIXED** (T57, 2026-10-31). `SessionHousekeeping`, hourly: refresh tokens expired or revoked more than 30 days ago are deleted (reuse detection
needs minutes), and signed-out access tokens past their expiry. Only the browser in two words ("Chrome on Windows",
`Devices`) is stored now; V33 cleared the full strings already kept — nothing ever read them. `SignOutTest.housekeeping`,
`IdentitiesTest.devices`.

---

### BUG-127 🟠 Names and addresses accept look-alikes and invisible characters

**Status:** ✅ fixed on `fix/security-audit` (T57, 2026-10-31) — verified (2026-10-30): all registered (201) · **Affects:** `RegisterRequest`, `AppUser`

**What.** A display name may contain a right-to-left override (`Admin‮gnp.exe` shows as "Adminexe.png"),
zero-width spaces (`Tra​deez Support`), newlines and markup. An email may use Cyrillic letters that look Latin
(`fаbio@…` with a Cyrillic "а") and may have no domain ending (`x@localhost`).

**Why it matters.** Impersonation of staff or other users, confusing displays, and — once T56 sends email — text
injected into messages (a newline in a name inside an email). React escapes markup today, so no XSS, but the name is
stored as typed for whatever renders it next.

**How to fix.** Display name: Unicode-normalise (NFC), refuse control and format characters (`\p{C}`) and bidi
overrides, collapse spaces, 1–40 characters, refuse reserved names ("Tradeez", "admin", "support", "moderator").
Email: lower-case and NFC; require a dot in the domain; refuse mixed-script addresses (or IDN-encode and show the
punycode). T56's verification covers the address being real.

**✅ FIXED** (T57, 2026-10-31). `Identities`: a name is normalised (NFC), refused with any control or format character (\p{C}: bidi overrides,
zero-width spaces, newlines), limited to letters of any script, digits, spaces and `. ' - _`, 1–40 characters, and refused
when a word of it is the app's own (tradeez, admin, support, staff…). An address is trimmed, lower-cased and must be plain
ASCII with a real domain ending — look-alike letters from other scripts cannot be used. The messages say what to change.
The sign-up field allows 40 characters. `IdentitiesTest` (21), `SignOutTest.namesAtSignUp`.

---

### BUG-126 🟠 A copied session cookie keeps working for up to 15 minutes after signing out

**Status:** ✅ fixed on `fix/security-audit` (T57, 2026-10-31) — verified (2026-10-30): the access cookie copied before sign-out still read `/api/me` (200) after it ·
**Affects:** `AuthController.logout`, `JwtAuthenticationFilter`

**What.** Signing out revokes the refresh token and clears the cookies in *this* browser, but the access token itself
stays valid until it expires (15 minutes). "Sign out everywhere" and a password change do end it (BUG-077); plain sign
out does not.

**Why it matters.** On a shared or school computer, or if the cookie was stolen, "Sign out" does not end access at
once — the person believes they are out and are not, for a quarter of an hour.

**How to fix.** On sign-out, record the token's id (`jti`) in a short-lived deny list until its expiry (in memory with
the rate limiter's eviction, or a table), or give each refresh family its own generation checked with the access
token. Test: the copied cookie gets 401 after sign-out.

**✅ FIXED** (T57, 2026-10-31). Every access token carries an id (`jti`). Signing out writes it to `revoked_access_token` (V33) until the token would
have expired, and the filter refuses a token found there; the rows are deleted once past their expiry. A database table,
not memory, so a restart does not forget a sign-out. `SignOutTest.copiedCookieEndsAtSignOut`.

---

### BUG-125 🟠 Deployed, the security headers a browser needs are missing

**Status:** ✅ fixed on `fix/security-audit` (T57, 2026-10-31) — verified (2026-10-30, the image; `X-Forwarded-Proto: https` sent as a host's proxy would) ·
**Affects:** `SecurityConfig`, `application.yml`

**What.** No `Strict-Transport-Security` (the app does not read the proxy's `X-Forwarded-Proto`, so it believes every
request is plain HTTP — `server.forward-headers-strategy` is not set), no `Content-Security-Policy`, no
`Referrer-Policy`, no `Permissions-Policy`. Present: `nosniff`, `X-Frame-Options: DENY`.

**Why it matters.** Without HSTS the first visit can be downgraded to HTTP on a hostile network. Without a CSP, any
future XSS (none found today) could load scripts from anywhere and send data out. A CSP is the safety net behind
React's escaping.

**How to fix.** `server.forward-headers-strategy: framework` (with the proxy trusted, as BUG-070's `TRUSTED_PROXIES`),
HSTS one year; CSP `default-src 'self'; img-src 'self' data:; style-src 'self' 'unsafe-inline'; connect-src 'self';
frame-ancestors 'none'; base-uri 'none'; form-action 'self'` (check the chart library and fonts against it);
`Referrer-Policy: strict-origin-when-cross-origin`; `Permissions-Policy` denying camera, microphone, geolocation.

**✅ FIXED** (T57, 2026-10-31). HSTS (a year, subdomains) on HTTPS requests — the prod profile makes the proxy's HTTPS visible; a CSP (`default-src
'self'`, `script-src 'self'`, styles also inline, images and fonts also `data:`, `object-src 'none'`, `base-uri 'none'`,
`frame-ancestors 'none'`); `Referrer-Policy: strict-origin-when-cross-origin`; a `Permissions-Policy` denying camera,
microphone, location, payment and USB. Checked live on the image: the CSP first blocked the small font subsets the build
inlines as `data:` URIs — caught, `font-src` widened, then every font loaded and no violation fired across Home, Chart, a
lesson and a drill. `DeployedServerTest.securityHeaders`.

---

### BUG-124 🟠 The cookies are cross-site cookies though the app is now one site

**Status:** ✅ fixed on `fix/security-audit` (T57, 2026-10-31) — verified (2026-10-30, `CookieService` and the image's headers) · **Affects:** `CookieService`,
`SecurityConfig` (CSRF cookie)

**What.** Deployed, the session cookies are `SameSite=None` — written for the old plan of the page and the API on two
sites (DEC-002, superseded by DEC-084). The CSRF cookie (`XSRF-TOKEN`) is sent with no `Secure` and no `SameSite` at
all.

**Why it matters.** `SameSite=None` sends the session with requests started by any other site. The CSRF token still
blocks writes, but `None` gives up the browser's own first line of defence for nothing: a hostile page can, for
example, make the browser call `/api/auth/refresh` (exempt from CSRF) and rotate the session. Safari's tracking
protection also treats such cookies as third-party. A CSRF cookie without `Secure` can travel over plain HTTP.

**How to fix.** `SameSite=Lax` (or `Strict` for the refresh cookie, scoped to `/api/auth`) in both environments;
CSRF cookie Secure and SameSite=Lax when deployed (`CookieCsrfTokenRepository.setCookieCustomizer`). Cookie names
with the `__Host-` prefix when deployed, so a sibling subdomain can never set them.

**✅ FIXED** (T57, 2026-10-31). Session cookies `SameSite=Strict` everywhere (one site, DEC-084); the CSRF cookie `Secure` when deployed and
`SameSite=Strict` (`CookieCsrfTokenRepository.setCookieCustomizer`). Not done: the `__Host-` prefix — the refresh cookie's
`/api/auth` path cannot carry it, and with one host and no subdomains it would add little. `DeployedServerTest
.cookiesAreFirstPartyOnly`.

---

### BUG-123 🟠 A request body of any size is read into memory, signed in or not

**Status:** ✅ fixed on `fix/security-audit` (T57, 2026-10-31) — verified in part (2026-10-30): an 8 MB sign-in body was read whole and answered 400 in 167 ms; larger not
tried · **Affects:** every `@RequestBody` endpoint, Tomcat configuration

**What.** No limit on a JSON body's size. Field limits (`@Size`) are checked only after the whole body has been read
and turned into strings.

**Why it matters.** A free host gives the app about 512 MB. A handful of concurrent 100 MB requests to `/api/auth/login`
— no account needed, and the per-address limit allows 20 per 5 minutes per address — can exhaust the memory and take
the app down for everyone (denial of service).

**How to fix.** Refuse any request whose body is over a small limit before reading it: a filter checking
`Content-Length` and capping the stream (e.g. 32 KB everywhere; the largest real body, a drawing, is under 2 KB), plus
Tomcat's `max-swallow-size`. Test: a 1 MB body gets 413 without being read.

**✅ FIXED** (T57, 2026-10-31). `RequestSizeLimitFilter`, first of all filters: a declared body over 64 KB is answered 413 before anything reads it; a
body sent without a length is cut off at 64 KB and fails as broken JSON. The largest real body is under 4 KB.
`RequestSizeLimitTest`; `ChartDrawingApiTest.styleBounded` now sees 413 for its 100,000-character style and the style
rule's 400 under the limit.

---

### BUG-122 🔴 After a redeploy, returning visitors keep the old page and get a blank screen

**Status:** ✅ fixed on `fix/security-audit` (T57, 2026-10-31) — verified (2026-10-30, the built image) · **Affects:** `SpaConfig`, the Docker build

**What.** Every file in the jar carries the zip format's date, 1 February 1980, as `Last-Modified`. `index.html` is
served with `no-cache`, so the browser asks again each visit with `If-Modified-Since: 1980` — and the server answers
**304 Not Modified, forever**, also after a new version is deployed (checked: 304 for `/` and `/learn/ICT-20`).

**Why it matters.** After any deploy, everyone who has visited before keeps the old `index.html`. It points at the old
build's scripts (`/assets/index-<old hash>.js`), which the new image no longer has: 404, and the app does not start —
a blank page until they clear their cache. Every release would break every returning user.

**How to fix.** Serve `index.html` (and the fallback) with an ETag from its content, or without `Last-Modified`, and
`Cache-Control: no-cache`; or set the files' times at build. A test: request `/` with `If-Modified-Since` far in the
past and an old ETag, expect 200 when the content differs.

**✅ FIXED** (T57, 2026-10-31). The page and every file outside `/assets` carry an ETag of their content (SHA-256, computed once per process) and no
`Last-Modified`; a browser holding an older build sends its old ETag and gets the new page, 200. Built assets keep their
year-long cache (their names change with their content). `SpaConfigTest.aNewDeployIsSeen`; the image answers `ETag` and
no `Last-Modified`.

---

### BUG-121 🔴 A deployment that forgets `COOKIES_SECURE=true` runs on the public development secrets

**Status:** ✅ fixed on `fix/security-audit` (T57, 2026-10-31) — reasoned (2026-10-30) · **Affects:** `application.yml`, `JwtSecretGuard`, `SecurityConfig`

**What.** `JWT_SECRET` and `DATABASE_PASSWORD` fall back to development values written in `application.yml`, which is
in the repository. `JwtSecretGuard` refuses the development JWT secret only when `cookies.secure` is true. Deploy
without that one variable and the app starts happily with the known secret — and also with SameSite=Lax cookies that
are not Secure, the API description open, and CORS accepting any localhost port.

**Why it matters.** With the development secret anyone who has read the repository can sign a session token for any
account whose id they learn — no password needed. One forgotten variable on a host's settings page is all it takes.

**How to fix.** Make "deployed" a single explicit switch that fails closed: e.g. a `prod` profile set in the
Dockerfile, in which `JWT_SECRET`, `DATABASE_URL` and `DATABASE_PASSWORD` have **no defaults** (start-up fails without
them), the development secret is refused, cookies are Secure, the API docs are off. Local development keeps its
defaults outside that profile. A test starts the app with the profile and no secret and expects it to refuse.

**✅ FIXED** (T57, 2026-10-31). The Docker image now always runs the `prod` profile (`ENV SPRING_PROFILES_ACTIVE=prod`), and `application-prod.yml`
gives `JWT_SECRET`, `DATABASE_URL`, `DATABASE_USER` and `DATABASE_PASSWORD` **no default**: started without one, the app
stops before it opens a port ("Could not resolve placeholder 'JWT_SECRET'" — checked on the image). The profile also
fixes `tradeez.cookies.secure=true`, believes the host's HTTPS proxy (`forward-headers-strategy: framework`) and turns
springdoc off. `ProdProfileTest`; the image started with and without the secrets.

---

### BUG-120 🔴 No way to delete an account or take its data out, and no privacy policy

**Status:** ✅ fixed on `fix/security-audit` (T57, 2026-10-31) — verified by reading (2026-10-30): `/api/me` has only GET; no delete or export endpoint exists ·
**Affects:** identity, every user-owned table, the landing page

**What.** A user cannot delete their account, cannot download what the app holds about them, and nothing tells them
what is kept, why, or for how long.

**Why it matters.** Fabio and his users are in the EU: under the GDPR a person may ask for their data (Art. 15, 20)
and for it to be erased (Art. 17), and must be told what is processed (Art. 13). FRED's terms also ask for a privacy
policy from an app serving other people (DEC-079). The data is small — an email, a name, a password hash, sign-in
records with browser names (BUG-130), practice trades and drill answers — but it is personal.

**How to fix.** `DELETE /api/me` behind the current password (and the CSRF token), deleting the user and every row
that hangs off it (the schema already cascades in 15 places — check every user-owned table does), signing out
everywhere; `GET /api/me/export` returning everything as JSON; a short privacy page (what, why, how long, how to
delete) linked from sign-up and the footer; a retention rule for sign-in records (BUG-130).

**✅ FIXED** (T57, 2026-10-31). `DELETE /api/me` (current password, CSRF token, throttled as a password change) deletes the account; every table that
belongs to an account cascades from it (checked through every foreign key). `GET /api/me/export` returns everything kept
about the account as a JSON file — account, sign-ins, lessons read, drills and their marks, risk drills, drawings,
settings, sessions, plans, closes and results — leaving out only the password's and the tokens' hashes, `no-store`.
`AccountDataTest` proves the export holds one account's data and none of another's, that **every** table hanging off
`app_user` is in it (a new table fails the test until it is added), and that deletion needs the password and leaves
nothing. In the app: "Your data" in the account menu (`/account`: download, delete with the password and a tick), and a
public `/privacy` page linked from sign-in, sign-up and the footer. Not done here: a host's backups still hold a deleted
account until they expire (BUG-129).

---

### BUG-073 🟠 Deployed on two sites, the page could not send the CSRF token, and Safari would drop the session

**Status:** ✅ fixed, merged 2026-10-29 (master 7818ad3, DEC-084) — reasoned in session 017's security pass; seen for real
when Fabio deployed the page alone to Vercel ("its not communicating with server now")
**Affects:** the planned deployment (DEC-002: the SPA on Vercel, the API on its own host); `client.ts`,
`SecurityConfig`, `CookieService`

DEC-032 puts the session in HttpOnly cookies and protects writes with Spring's CSRF cookie, which the
page reads and echoes as a header. That works locally because Vite proxies `/api`, so page and API are
one origin. Deployed on two sites, the `XSRF-TOKEN` cookie belongs to the API's site, and
`document.cookie` on the page's site cannot see it: no header is sent, and every POST, PUT and DELETE —
plans, drawings, drills, sign-out — would be refused with 403, the refresh too, signing the user out.
And the session cookies themselves are then third-party cookies (`SameSite=None`), which Safari blocks
by default and Chrome is restricting. Nothing in the code is wrong for one site; the plan of two is.

**Fix, for Fabio to choose with the host:** (a) serve both from one site — the page's host proxies
`/api` to the backend (a Vercel rewrite does this), so the cookies are first-party, `SameSite=Lax`
suffices and CORS is not needed; or (b) sibling subdomains of one domain (`app.` and `api.`), with the
cookies' domain set to the parent. (a) is the smaller change and the one recommended. Left open: it is
a hosting decision, not a code fix.

**Fixed** by a third way, stronger than (a): the backend serves the page itself (DEC-084), so there is one site and
nothing to proxy. Checked in the built image at http://localhost:8090 with `COOKIES_SECURE=true`: register 201, a
signed-in read 200, the CSRF cookie visible to the page, sign-out 204, then 401. `SpaConfigTest` (4).


---

### BUG-118 🟡 The entrance animations were over before anyone could see them

**Status:** verified (2026-10-28). Fabio: "animations load too early, you cant even see them". Measured on the built
app at 1366, sampling every painted frame from navigation: Home's step cards and the Drills cards were fully in about
0.23 s after they first showed, over 23–26 painted frames; the landing page's hero started while the browser was still
stalling 70–110 ms at a time finishing the page. **Affects:** every entrance of the motion pass (R-5).

**Why.** The entrances started at the first paint, lasted 0.2–0.25 s and moved 8 px: they ran while the page was still
being finished and the eye was still landing, so what a person saw was the end.

**✅ FIXED — 2026-10-28, `feat/mobile`.** One timing for every entrance, `lib/motion.ts`: a 0.15 s beat after the paint
(`SETTLE`), half a second each, 14 px of rise, a stagger capped for long lists; the landing page's hero 0.7 s a line,
its replay drawing once the hero is in. Measured again: 46–71 painted frames per entrance, every one starting after the
load's stalls. Test: the timing itself (`motion.test.ts`).
**Lesson:** measure an animation in painted frames on a cold load, not by its duration in the code.

### BUG-111 🟠 On a touch screen a tap could not mark a candle in a drill

**Status:** verified (the mobile audit, 2026-10-28): at 390×844 with touch, three taps on a swing-high drill's chart left
"0 marked"; a mouse click on the same spot marked one. **Affects:** every spot drill on a phone or tablet — the
drill could not be answered at all.

**What.** A tap on the chart did nothing. **Why.** `DrillOverlay` marked a candle on `click`. A finger's tap fires
`pointerdown` and `pointerup`, but the chart library takes the touch for its own panning and no `click` follows. T46
checked the drill on a phone-sized window with a mouse, which does click, so it was never caught.

**✅ FIXED — 2026-10-28, `feat/mobile`.** A candle is marked on `pointerup` when its `pointerdown` (the main button) was within the drag
tolerance; a click alone, a release whose press was elsewhere, and a right button mark nothing. Tests: a tap marks with
no click; a click alone, a stray release and a right button do not; the existing click-and-drag tests now press and
release. Two mutants, both caught. Live with touch: three taps, three marked; a fourth on the same candle unmarks.
**Lesson:** test touch with touch. A phone-sized window driven by a mouse proves the layout, not the gestures.

### BUG-112 🟡 Home scrolled sideways on a phone

**Status:** verified (2026-10-28): the page was 60px wider than a 390 screen, 90px wider than a 360 one. **Affects:**
Home; also the landing page's header at 360, 11px past the edge.

**What / why.** The News today card is a grid item, and a grid item is as wide as its content unless told otherwise
(`min-width: auto`): its longest title, "PCE — Personal Income and Outlays", could not truncate and pushed the
column, the page and the header with it. On the landing page the logo, "Sign in" and "Create an account" needed 368px
with its 24px sides.

**✅ FIXED — 2026-10-28, `feat/mobile`.** `min-w-0` on the card, so the title truncates; the landing page's sides are 16px below 640px.
Test: the card carries `min-w-0` (a mutant removing it is caught). Live: no sideways scroll on any screen at 360, 390
or 768.
**Lesson:** `truncate` inside a grid or flex item needs `min-w-0` on the item, or it never gets the chance.

### BUG-113 🟡 The practice replay bar covered a third of a phone's screen

**Status:** verified (2026-10-28): the sticky Replay panel was 235px tall on an 844px screen. **Affects:** the
practice session below 1024px.

**What / why.** T46 holds the replay at the screen's foot as you scroll, which is right; but it kept the desktop panel —
its title, a row of labelled buttons and speeds that wrapped to two, and the "End session…" row under a rule.
**✅ FIXED — 2026-10-28, `feat/mobile`.** Below 1024px: no title (still named for a screen reader), "Next bar" and "Play" as icons with
their names kept, and the controls and "End session…" flowing together into two rows. 126px. The desktop panel is as
it was.

### BUG-114 🟡 The Chart tab's controls took a third of a phone's screen

**Status:** verified (2026-10-28): at 390 the timeframes, the jumps, the date and Layers stacked into four rows, about
270px, leaving the chart about 260px. **Affects:** the Chart tab below 768px.

**✅ FIXED — 2026-10-28, `feat/mobile`.** Below 768px the timeframes, jumps, date and clock share one row that scrolls sideways, beside
Layers; from a tablet up the wrapper steps aside (`display: contents`) and nothing changes. The chart gains about
130px. A first try let the toggle groups shrink to a sliver inside that row (seen on the screenshot); its children
are now kept at their width.

### BUG-115 🟡 Touch targets under 44px across the app

**Status:** verified (2026-10-28, the audit measuring every visible control at 390, 360 and 768 with touch).
**Affects:** the drawing bar (tools squeezed to 27×36), every toggle (options about 35px wide), every button (32–36px),
the phone menu button, back links (20px), the news sources (16px), "More statistics", the sign-in switch link, the
Layers button and its rows (a 36×20 switch was the only way to turn a layer on), the chips' remove buttons (16px),
fields (40px), the market picker.

**✅ FIXED — 2026-10-28, `feat/mobile`.** On a coarse pointer only — the mouse keeps the compact sizes: buttons, fields, toggles and the
drawing tools at least 44px (`pointer-coarse:`), the drawing bar scrolling sideways instead of squeezing, a toggle
group that is wider than its row scrolling inside itself, the back links and other small links at 44px, a `tap`
utility giving inline links ("Read more", the FRED terms, "Clear all", a chip's ×) an invisible 44px area without moving
the text, and a whole layer row turning its layer on. Test: a tap on a layer's name toggles it, a tap on its switch
toggles it once (two mutants, both caught). Left at 36–42px, above WCAG's 24px minimum: the cards' text links
("Continue lesson"), the layer presets.

### BUG-116 🟡 On a tablet the account name wrapped onto two lines

**Status:** verified (2026-10-28, 768): "Phone / Tester" beside the full nav, the header row 52px tall.
**✅ FIXED — 2026-10-28, `feat/mobile`.** The name shows from 1024px up, cut short at 10rem; below it the initials alone, as on a phone.

### BUG-102 🟡 The header jumps sideways between screens

**Status:** verified (design critique, 2026-10-28, screenshots in `docs/redesign/critique/`). **Affects:**
`AppShell.tsx`, the Chart tab, a drill, a practice session.

**What.** On Home, Learn, Drills, Practice and News the header's content sits in the 1232px column (the logo at
x=344 on a 1920 screen). On the Chart tab and a drill it switches to the full width, so the logo and the section links
jump about 320px to the left as you move between them, and back.
**Why it matters.** The header is the one thing that should never move. A jumping nav reads as two apps stitched
together, and it is the most visible inconsistency because it happens on every click between those screens.
**How to fix.** Give the header one width on every screen. Either always full width (the logo at the left edge, the
account at the right, like most trading tools), or always the 1232px column while the chart pages' content alone goes
wide. Recommendation: always full width — the chart pages are the product, and the reading pages lose nothing.
Check at 1920 and 1366 on every section.

**✅ FIXED — 2026-10-28, `fix/t50-cleanup`.** The header is the full width on every screen: the logo at the left edge, the account at the
right, the same on Home and on the Chart tab. Checked at 1920 and 1366 on every section.
**Lesson:** the frame decides its own width; tying it to the page's makes it move with every page.

### BUG-103 🟡 The landing page's drawn candles are white and purple; every real chart is green and red

**Status:** verified (2026-10-28). **Affects:** `ReplayPreview` in `LandingPage.tsx`.

**What.** Up candles are `bg-ink/85` (white) and down candles `bg-accent` (purple). Every chart inside the app draws
up green and down red.
**Why it matters.** The picture is there to show what the product looks like, and it does not look like the product.
R-3 says the accent is "never used for prices or results", and M6 agreed "up and down stay green and red"; purple candles also add to the purple everywhere
(T50 D-8).
**How to fix.** Use the `up` and `down` tokens for the bodies and wicks; keep the FVG box and the session band
neutral or very faint.

**✖ NOT A BUG — 2026-10-28.** Fabio: "thats design decision". The drawn replay keeps its white and purple
candles: it is an illustration, said so beside it, not a chart of the app. Left as it is.

### BUG-104 🟡 A drill's chart leaves about a third of its width empty on the right

**Status:** verified by screenshot (a swing-high drill at 15m, `docs/redesign/critique/drill.jpg`: the candles end at
about 65% of the width); the cause read from the code. **Affects:** every drill chart; less visibly the lesson
examples.

**What.** About 31 candles fill the left two thirds; the right third is empty grid.
**Why it happens.** `lightweightAdapter.ts` keeps `RIGHT_OFFSET_BARS = 12` bars of space after the last candle, so the
position tool has room to draw its zones to the right of an entry. Twelve bars is fine on a chart of hundreds; on a
drill of about 30 it is 12 of 43 slots — nearly 30% of the width, plus the price axis.
**Why it matters.** The drill is about reading those candles; a third of the screen is wasted and the candles are
smaller than they need to be.
**How to fix.** Let a chart ask for its own right offset (an option on the adapter): the Chart tab and practice keep
12 bars (the position tool needs them), drills and lesson examples use a small one (2–3 bars). Check that marking
the last candle still works and that BUG-097's "draw ahead of the last candle" is unaffected on the Chart tab.

**✅ FIXED — 2026-10-28, `fix/t50-cleanup`.** `ChartMountOptions.rightOffset`: the Chart tab and practice keep 12 bars (the position
tool), a drill and a lesson's example ask for 2. Tests: the adapter takes 12 by default and 2 when asked; the drill
asks for 2; two mutants, both caught. Live: the drill's candles reach the price axis (`after/t50/drill.jpg`).
**Lesson:** a margin in bars is a different share of every chart; size it for the chart, not once for all.

### BUG-105 🟡 Internal ids and rule versions are shown to learners

**Status:** verified (2026-10-28). **Affects:** Learn's cards ("ICT-01" over every title), a lesson's eyebrow
("Structure · ICT-01"), Home's Due for review, a drill ("The rule (swing-1.0.0)" next to a chip "Graded against
swing-1.0.0" — the version twice), the evidence footnote ("Counted by swing-1.0.0 under evidence-1.0.0"), and a
"Card" chip on eight lessons that nothing explains.

**What.** Catalogue numbers and version strings from `ICT_CONCEPTS.md` sit in the first line a learner reads.
**Why it matters.** They are noise to a learner and make the screens look like a database view. The versions are
worth keeping — they are how Tradeez proves which rule judged an answer — but as a detail for the curious and the
jury, not the headline. "Card" means "a reference page without a drill", which no learner can guess.
**How to fix.** Drop "ICT-xx" from cards, eyebrows and Home (keep it in URLs and `data-*` attributes). Say the version
once, inside "The rule Tradeez uses" on a lesson and in a drill's result ("Graded by swing-1.0.0"). Rename "Card" to
"Read only", with a title saying reading it counts as done. Update the tests that look for the ids.

**✅ FIXED — 2026-10-28, `fix/t50-cleanup`.** No "ICT-xx" on Learn's tiles, Home's Due for review or a lesson's header (the topic alone);
a drill says "The rule Tradeez uses" with the version once ("Graded against swing-1.0.0"; a prediction "… predict-1.0.0,
on liquidity-1.0.0", so its rule's version is still said); "Card" became "Read only" with a title. The ids stay in the
addresses. Tests updated and one added: no tile carries an id.
**Lesson:** an id is for the code and the audit trail; a learner needs the name.

### BUG-106 🟡 Locked lessons are hard to read

**Status:** reasoned from the screenshot (2026-10-28) — the contrast is an estimate (about 2.5:1); measure the
computed colours before fixing. **Affects:** `LearnPage.tsx`, a locked lesson card.

**What.** A locked card dims its title and its "Pass the … drill to unlock" line together, well below the normal
subtle text.
**Why it matters.** The unlock line is the one thing a learner needs on a locked card — it says what to do next — and
it is the hardest text on the page to read. WCAG AA asks 4.5:1 for small text.
**How to fix.** Keep the title at its normal colour (or one step down), the unlock line at `text-subtle`, and let the
lock icon carry "locked". No opacity on text.

**✅ FIXED — 2026-10-28, `fix/t50-cleanup`.** No opacity on a locked tile: the title keeps its colour, the unlock line `text-subtle`,
the lock icon says the rest. Test: no tile dimmed; a mutant dimming them again is caught.
**Lesson:** never dim the text that tells someone what to do.

### BUG-107 🟡 Empty progress rings look like unchecked radio buttons

**Status:** verified (2026-10-28). **Affects:** Learn — every lesson card and the "0 / 33 lessons" ring.

**What.** A lesson with no progress shows an empty grey ring at its left; 33 of them in a column look like a form of
radio buttons, and invite a click on the ring.
**How to fix.** Show the ring only once there is progress; before that, nothing (or the lock for locked lessons). The
page header's ring can read "0 of 33" as text until the first lesson is done.

**✅ FIXED — 2026-10-28, `fix/t50-cleanup`.** A tile's ring appears once there is progress; the page's own ring became the line "0 of 33
lessons done". Test: no ring on a newcomer's tiles; a mutant drawing them is caught.

### BUG-108 🟡 Segmented controls are small targets

**Status:** reasoned from the screenshots (2026-10-28): NQ/ES on Drills, High / High + medium on News and the
lesson's 1m/5m/15m look about 20–24px tall at 1920; measure before fixing. **Affects:** `Segmented` in `ui.tsx`.

**What / why.** They are fiddly with a mouse and below the 44px touch guidance (WCAG 2.5.8 AA asks at least 24px).
T46 sized other controls for touch; these were missed.
**How to fix.** A minimum height of 32px on desktop and 44px on touch (`pointer: coarse`), the same text size. Check
that the chart toolbar's timeframe row still fits at 1366.

**✅ FIXED — 2026-10-28, `fix/t50-cleanup`.** `Segmented` is 32px tall, 44px on a touch screen (`pointer-coarse`), in the sans rather
than mono; its padding kept, so the Chart tab's toolbar still fits on one row at 1366 (checked: a wider padding pushed
Layers onto a second row).

### BUG-109 🟡 Practice's risk limit reads as a broken sentence

**Status:** verified (2026-10-28). **Affects:** the "Start a session" panel in `PracticeView.tsx`.

**What.** "Your risk limit per trade: $ [none]" and, on a separate line, "— your rule: each scorecard says whether a
trade kept it. It never blocks one." The dash makes the second line look like the end of a sentence that lost its
start, and "none" in the field looks like a value rather than a hint.
**How to fix.** Label "Risk limit per trade ($)", placeholder "No limit", and one hint under the field: "Each
scorecard says whether a trade kept it. It never blocks one."

**✅ FIXED — 2026-10-28, `fix/t50-cleanup`.** A real label "Risk limit per trade ($)", the placeholder "No limit", and one hint under the
field, tied to it by `aria-describedby`. The existing test finds the field by its label.

### BUG-110 🟡 A drill's answer bar is a full-width box around three small controls

**Status:** verified (2026-10-28). **Affects:** `DrillView.tsx` on desktop.

**What.** "0 marked", Clear and Check answer sit at the left of a 1600px bordered panel; the rest is empty box.
**Why it matters.** Card around everything (T50 D-6) at its most visible: the box adds weight and nothing else, and
the main action is far from the chart's right side where the eye ends.
**How to fix.** No box: a plain row under the chart, the count at the left and Clear + Check answer at the right. Keep
the sticky bar on phones (T46).

**✅ FIXED — 2026-10-28, `fix/t50-cleanup`.** On a desktop the marking bar has no box: the count at the left, Clear and Check answer at
the right under the chart. On a phone it is still the sticky panel (T46).

### BUG-099 🟠 The volume imbalance rule finds almost nothing on ES

**Status:** found by the post-redesign audit (2026-10-27): 17 of 18 volume-imbalance drills on ES answered 503 "Could
not find a suitable stretch of chart"; every one on NQ was made. **Affects:** the ICT-25 drill on ES, the Chart tab's
VI layer on ES, and the rule's evidence there.

`vi-1.0.0` asks for a gap of at least 4 ticks between the two bodies — one point, measured on NQ (session 017: about
1.6 per 30 candles on every timeframe). ES moves about a fifth as many points as NQ, so its body gaps are smaller.
Measured on 2025 at 5m, 15m and 1h, per 30 candles:

| Minimum gap | NQ | ES |
|---|---|---|
| 1 tick | 6.2–6.7 | 3.8–4.0 |
| 2 ticks | 4.0–4.3 | 0.15–0.21 |
| 4 ticks (the rule) | 1.41–1.45 | 0.01 |

Offered a minimum per market or ES leaving the drill and layer, Fabio chose the first (DEC-083).

**✅ FIXED — 2026-10-27, `fix/post-redesign-audit`.** `vi-1.1.0`: at least a point on NQ as before, at least a tick on
ES. A detector is now told which market it reads; the scanner and the evidence pass it along, and each hit keeps the
minimum it was found under, so its explanation reads "1 tick … on this market" on ES. Tests: the rule on both markets,
the scanner and the evidence passing the market (a 2-tick gap made in the ES fixture, whose simulated minutes have
none); five mutants, all caught. Live: 36 of 36 volume imbalance drills made on NQ and ES at 1m, 5m and 15m (ES was 1
in 18); the Chart tab's ES layer finds 96 over three days at 5m.
**Lesson:** a size measured on one market is a fact about that market. When a second one arrives, every fixed size in
ticks or points is a question to ask again.

### BUG-100 🟢 The guide, sign-up and Learn still said "real NQ" only

**Status:** found by the post-redesign audit (2026-10-27). **Affects:** the Guide's lead, its first step and its
sources; the sign-up line; Learn's "Drill what you've learned".

T47 made ES a full market, but copy written before it still named NQ alone — the guide even said the candles came
from Massive for NQ only.

**✅ FIXED — 2026-10-27, `fix/post-redesign-audit`.** Each now names NQ and ES. Lesson examples and the practice day
stay as they are: those are drawn from the market they name.
**Lesson:** a new market is a copy change too; search the words, not just the code.

### BUG-098 🟡 A news window past the year 2100 answered 500

**Status:** found by the post-redesign audit (2026-10-27): `GET /api/news?from=99999999999999` → 500.
**Affects:** `/api/news` only — every other endpoint that takes a moment bounds it already.

The controller turned `from` and `to` into instants and checked only their order and span. A moment that far out is
a valid instant, but the calendar's New York date arithmetic overflows on it and threw a `DateTimeException`, which
no handler maps.

**✅ FIXED — 2026-10-27, `fix/post-redesign-audit`.** Both bounds must lie between 2000 and 2100 (as a drawing's
points must), or the request is refused with 400. Test: `NewsApiTest.bounded`, with moments far past and before;
the mutant without the bound answers 500 and is caught.
**Lesson:** "valid as a number" and "valid as a date the code can do arithmetic on" are different checks.

### BUG-094 🟠 Switching the Chart tab to ES shows no chart

**Status:** reported by Fabio (2026-10-23), not yet reproduced by Claude. **Affects:** `ChartWorkspace`, ES

Choosing ES as the instrument on the Chart tab leaves the chart empty. ES leaves the Chart tab in T38 (NQ first);
the cause is to be found when ES returns as a full instrument (T47).

**2026-10-23, T38:** ES left the Chart tab (there is no instrument choice any more), so this cannot happen there now;
left open for T47.

**✅ FIXED — 2026-10-27, `feat/t47-es`.** Reproduced when T47 brought the market switch back: after NQ, ES's chart kept NQ's candles
and only its newest one took ES's price, so the price scale ran from ES's 7,700 to NQ's 31,000 and ES lay squashed along
the bottom — a chart that reads as empty. The cause is in `PriceChart`: to let a replay step forward without resetting
the view, it treats new candles as the shown ones *carried on* when the first and last times match, and then only updates
the newest. Two markets over the same minutes have exactly the same times. Now the first candle's prices must match too
(a closed candle never changes); a lone first candle, which may still be forming, is exempt. The API had always served ES
correctly. Test: `PriceChart.test` — ES over NQ's minutes replaces the series and refits; red before the fix.
**Lesson:** "the same times" is not "the same series"; an identity check must include something only that series has.

### BUG-097 🟠 Nothing can be drawn ahead of the last candle

**Status:** found by Fabio (2026-10-26) while trading in practice: "you cant use instruments in front of the last
candle, so you can mark fvg for example only from past until now".
**Affects:** every drawing tool, in practice and on the Chart tab — wherever the newest candle is on screen.

The drawing layer turned the pointer into a time with the chart library's `coordinateToTime`, and a drawing's time
back into a position with `timeToCoordinate`. Both know the candles only: the empty space right of the last one has
no time in them, so a click there was "not on the chart", and a point already beyond it could not be drawn. In
practice that empty space is exactly where the next move is planned — a gap's zone projected forward, a target.

**✅ FIXED — 2026-10-26, `fix/bug-097-draw-ahead`.** The adapter's projection converts through the candles' logical
positions (`logical.ts`, DEC-075), which run past either end a candle's step at a time; on the candles it lands
exactly where it did before. The magnet leaves a point beyond the candles alone — it would have pulled it back onto
the last candle. Tests: the projection ahead of and behind the candles, and the magnet; three mutants that bring the
old behaviour back, all caught. Checked live: a rectangle dragged from the last candles into the empty space ahead.
**Lesson:** a chart library's time conversions cover its data, not its canvas; the space around the data needs its
own arithmetic.

### BUG-096 🟠 Undo and delete saved the drawings as they were before

**Status:** found by Claude (2026-10-23) while checking T37 in a browser: two drawings undone, one still on the server.
**Affects:** `useDrawings`, the drawing toolbar and shortcuts on the Chart tab and in practice. Older than T37 —
`master` does the same.

The toolbar's undo, redo and delete dispatch and then call `commit()` in the same handler. `commit()` diffed the
server against a ref of the drawings that is updated only after React renders, so it saved the state from before the
change: each undo saved the step before it, and a deleted drawing stayed on the server until the next change — and
came back on reload.

**✅ FIXED — Session 018 (2026-10-23), `feat/t37-overlays`.** `useDrawings`' `dispatch` works the change out at
once — the reducer is pure — and puts it where `commit()` reads. The tests had dispatched and committed in separate
renders, which is why they never saw it. Two tests, red before the fix; the live check now ends with nothing left.
**Lesson:** a ref kept in step by an effect is a render behind inside an event handler.

### BUG-095 🟠 The chart's overlays lag behind the candles when it is moved or resized

**Status:** reported by Fabio (2026-10-23); the cause is known from the code. **Affects:** `ClockOverlay`,
`SetupOverlay`, `DrawingOverlay`

The clock, setups and drawings are drawn on a canvas of their own, redrawn when the chart reports that its view
changed — after the chart has painted. Panning or zooming, they trail the candles, and the chart feels broken.

**Fix (T37):** draw them as Lightweight Charts primitives, in the chart's own paint step.

**✅ FIXED — Session 018 (2026-10-23), T37, `feat/t37-overlays`.** Every overlay is painted by the chart now: the
adapter's `addLayer` wraps a paint function as a series primitive (DEC-004 kept — nothing else imports the library),
and `useChartLayer` asks for a repaint when the layer's data changes. The clock and a lesson example's band sit under
the candles, setups over them, drawings and a drill's marks over everything; the drawing canvas only takes the pointer.
Checked in a browser at 1920, photographed mid-pan and straight after a zoom: shading, setups and drawings on their
candles in every frame (`docs/redesign/after/t37/`).
Two slips caught on the way, worth knowing: a layer attached after a chart's first paint waits for the next one, and a
chart that never moves — a lesson's example — never had one, so the layer asks for a paint when it is attached; and a
`<canvas>` is not stretched by `inset-0` (it keeps its own 300 × 150), so the pointer canvas, no longer sized by the
old hook, needs `size-full`. Adapter tests (attach, re-attach after a remount, detach, the paint requests), the
layers' tests with a recording context; 17 mutations, all caught.

### BUG-092 🟡 The chart beside can be panned on its own and drift out of line

**Status:** reasoned (2026-10-21). **Affects:** `CompanionChart` (`FollowRange` is one-way)

The lower chart (DEC-075) keeps to the upper one, but it is a full chart of its own: dragging or zooming it moves it
alone, and the two show different minutes until the upper one is moved and the lower snaps back. A trader comparing
two swings can be reading a pair that is not the same moment.

**Fix, when asked:** sync both ways with a guard against feedback, or let the lower chart follow only (no own time
scrolling).

**✅ FIXED — Session 017 (2026-10-22), `fix/bug-hunt`.** The lower chart follows only: its time neither scrolls nor zooms on its own (`followsOnly`; its price axis
still scales), and its caption says to move the chart above. A two-way sync was weighed and not taken: the lower chart
framing its own new candles cannot be told from a learner dragging it, and would have moved the chart above.
CompanionChart and adapter tests.

### BUG-091 🟡 Discarding a draft keeps its partial and its intent for the next one

**Status:** reasoned (2026-10-21). **Affects:** `PracticeSession` (`discard`)

Discard removes the drawing only. The partial and the plan's intent — model, bias, draw on liquidity, confidence —
stay, so a new draft an hour of replay later starts with an old partial price (often no longer between the price and
the target) and an old draw. Placing a trade clears them; discarding should too.

**Fix, when asked:** reset the partial and the intent on discard.

**✅ FIXED — Session 017 (2026-10-22), `fix/bug-hunt`.** Discarding clears the partial and the intent — model, bias, draw, confidence — as placing does. PracticeView
test.

### BUG-090 🟡 Flipping a draft leaves its partial on the wrong side

**Status:** reasoned (2026-10-21). **Affects:** `PracticeSession` (`flip`, the partial's state)

"Flip to short" mirrors the stop and the target through the price, but the partial (T28) is kept apart from the
drawing and is not mirrored: a long's partial 40 ticks above becomes a short's partial 40 ticks above, the dashed line
is drawn on the wrong side, and the plan is refused ("must lie between the current price and the target") until the
learner retypes it.

**Fix, when asked:** mirror the partial with the bracket.

**✅ FIXED — Session 017 (2026-10-22), `fix/bug-hunt`.** Flipping mirrors the partial through the entry with the stop and the target. PracticeView test: 20011.50
above a 20001.50 price flips to 19991.50, and back.

### BUG-089 🟡 A decimal comma is read as a thousands separator, or silently thrown away

**Status:** reasoned (2026-10-21, from the code). **Affects:** `RiskDrills`, `PracticeView` (risk limit, fees), the plan
panel's price boxes (stop, target, partial, draw)

Croatian traders — the author and the jury among them — write 1,5 for one and a half. The risk drill strips commas,
so **"1,5" is read as 15** and graded wrong; the risk limit "250,50" becomes **$25,050** and is accepted; fees "4,50"
become $450 and are refused. The price boxes turn "20011,5" into no number and put the old price back without a word.

**Fix, when asked:** read a comma followed by one or two digits (and no dot) as the decimal point, and say so when an
input is not understood instead of reverting it.

**✅ FIXED — Session 017 (2026-10-22), `fix/bug-hunt`.** One reading of a typed number, `lib/decimal.ts`: a comma before one or two last digits, with no dot, is the
decimal point; groups of three are thousands either way round ("1,234.50", "1.234,50"); anything else is not guessed
at. The risk drill, the risk limit, the fees and the stop, target, partial and draw boxes read with it, and a box that
cannot read its text says so and keeps it to be put right. `decimal.test.ts`, RiskDrills, PlanPanel and PracticeView
tests. Live: "7718,75" became 7718.75; "abc" was answered "Not a price: write it like 20011.25 or 20011,25."

### BUG-088 🟡 The risk-limit check ignores the costs the app itself charges

**Status:** reasoned (2026-10-21). **Affects:** `Scorecard` (risk line), the plan panel's risk readout

The risk compared with the learner's limit is the distance from the price to the stop, times the tick value and the
contracts. A stop-out also pays the entry's market slippage, the stop's slippage and the fees — under the standard
costs a one-contract trade "within your $400 limit" loses $414.50 at its stop ($10.00 of
slippage, $4.50 of fees). The rule the scorecard judges ("risk limit:
kept") is not the loss the learner would take.

**Fix, when asked:** compare the limit with the loss at the stop under the plan's own costs, or show both numbers.

**✅ FIXED — Session 017 (2026-10-22), `fix/bug-hunt`.** DEC-076, `scorecard-1.1.0`: the limit is judged by the loss at the stop under the trade's own fill model —
price risk, the entry's and the stop's slippage, the fees — and both are shown: "Risked $200.00; $207.50 at the stop
with slippage and fees, $7.50 over your $200.00 limit." A fill model it cannot read: the price risk alone. The plan
panel reads the same number before the trade is placed. `ScorecardTest.riskLimit`, `ScorecardApiTest.scored`,
`PlanPanel.test`. Live: ES, 40 ticks, the standard costs → $529.50 at the stop.

### BUG-087 🟡 A trade that loses money after fees is counted as a win

**Status:** reasoned (2026-10-21). **Affects:** the replay's score, journal statistics, the trade log's R

Wins, losses, R, expectancy and the win rate are counted from ticks *before* commission (`totalTicks`), while the money
shown beside them is net. With fees the learner sets — up to $50 a round trip (DEC-072) — one NQ contract out a tick
ahead (+$5) nets −$45 and is still counted a win at +0.03R. The score can say "1 win" over a negative total.

**Fix, when asked:** decide gross or net once: count wins and losses by net money and report R net of costs, or show
both.

**✅ FIXED — Session 017 (2026-10-22), `fix/bug-hunt`.** DEC-076: R is the net result — costs paid, the money shown beside it — over the planned price risk; a win
made money, a loss lost it. A stop-out now reads a little worse than −1R, by its costs. Two old fixtures it touched said
so: one had recorded −1 tick as −$1.00. `PracticeApiTest.netOfCosts` (in and out at one price under $40 of fees: a loss
of 0.02R, not a 0R scratch), `totalMatchesTheColumn` (BUG-039's case, net: −1.09R and +1.96R make +0.87R).

### BUG-086 🟡 A practice session's hidden date can be recovered from its prices

**Status:** reasoned (2026-10-21). **Affects:** practice on real data (DEC-025), the Chart tab

The replay hides the date, but every price is real NQ to the tick and the time of day is shown. Searching the Chart
tab's two years for the same few candles finds the day — and with it everything the replay has not shown yet. Only a
learner cheating themselves gains, but the record (calibration, statistics) then describes trades made knowing the
future.

**Fix, when asked:** offset every price of a session by a hidden constant (as the clock is offset), or say plainly that
the date is hidden from casual recall, not from a search.

**✅ FIXED — Session 017 (2026-10-22), `fix/bug-hunt`.** DEC-077: the session the learner is in — the open one touched last — is held back from its cursor to a day
past its replay; what the replay has shown stays, since finding the day by it shows nothing new. The prices were not
offset: they are real NQ to the tick, which is the point of the app. Only the session in hand: the test account had 35
left open, and hiding all of them would have taken some 140 days out of the chart. A learner set on cheating themselves
still can, by resuming an older session after reading its days. `HiddenStretchesTest` (hidden, and back once ended; only
the session in hand; lessons, drills and new sessions keep clear of it), the App test for the notice. Live on the test
account: NQ and ES both without exactly 27 Aug 03:40 to 31 Aug 03:40 UTC, the notice under the chart.

### BUG-085 🟡 Prediction and management drills can be answered by looking up what came next on the Chart tab

**Status:** reasoned (2026-10-21). **Affects:** `DrillService.view` (candles with their real times), the Chart tab

A drill's candles carry their real times (the SMT drill read "Mon 22:45 CDT"), and the Chart tab's date picker opens
any minute of the same history and everything after it. A prediction or a management choice — "graded by what real
price did" — can therefore be read off before answering, and counts toward the learning state like an honest one.

**Fix, when asked:** hide the date in drills as practice does (DEC-025, the shifted clock); at least, keep a drill
whose real future was opened from counting.

**✅ FIXED — Session 017 (2026-10-22), `fix/bug-hunt`.** With BUG-086 (DEC-077, `HiddenStretches`): the learner's newest drill, while unanswered, is held back from
the Chart tab from its first candle to the last its answer is decided by — candles, setups and clock prices, on every
real market — and the chart says why and what brings it back; no lesson example, new drill or new session is drawn
from it. A drill left behind by "Next drill" is not hidden: the app never goes back to it. The date was not hidden in
drills: a drill's question is about the candles, and the clock is part of reading them. `HiddenStretchesTest.drillHidden`.

### BUG-084 🟡 A drawing accepts any time, any price and any style value

**Status:** verified live (2026-10-21). **Affects:** `ChartDrawingController.save`

A horizontal line at time 9.2 × 10¹⁸ and price −9.2 × 10¹⁸ ticks, styled `{"color":"<img src=x onerror=alert(1)>"}`,
was saved and read back. The canvas ignores a colour it cannot parse, so nothing runs today — but the server stores
whatever it is sent: style keys and values are only counted (BUG-072), and anchors are any 64-bit numbers.

**Fix, when asked:** bound anchors to plausible times and prices; validate the style's keys and values (a colour as
`#rrggbb` or `rgba(…)`, a line width of 1–4).

**✅ FIXED — Session 017 (2026-10-22), `fix/bug-hunt`.** A style holds only `color` and `fill` (`#rgb` to `#rrggbbaa`, `rgb(…)` or `rgba(…)`) and `lineWidth`
(above 0, at most 8); a point lies between 2000 and 2100 and within a billion ticks of zero, both sides spelled out —
`Math.abs(Long.MIN_VALUE)` is still negative, caught while writing the check. It replaces BUG-072's count of entries and
characters. `ChartDrawingApiTest.styleIsTheChartsOwn`, `anchorsBounded`.

### BUG-083 🟡 Error messages name the server's Java types

**Status:** verified live (2026-10-21). **Affects:** the shared validation error handler (`common/`)

`GET /api/practice/sessions/not-a-uuid` answers *"Method parameter 'id': Failed to convert value of type
'java.lang.String' to required type 'java.util.UUID'; Invalid UUID string: not-a-uuid"* — the framework's own words,
naming its types. Harmless alone; part of what makes the server easy to fingerprint.

**Fix, when asked:** answer a type mismatch with the parameter's name and what it should be.

**✅ FIXED — Session 017 (2026-10-22), `fix/bug-hunt`.** With BUG-078: `RequestErrorHandler` names the parameter and what it should have been — "'from' must be a
whole number.", "'id' must be an id." — never a Java type. `RequestErrorsTest.parameters`, `expected`.

### BUG-082 🟡 The whole API description and Swagger UI are open without signing in

**Status:** verified live (2026-10-21). **Affects:** `SecurityConfig` (`/v3/api-docs/**`, `/swagger-ui/**` permitted),
springdoc defaults

`/v3/api-docs` (68 KB) and `/swagger-ui.html` answer 200 to anyone. Useful locally; deployed, it is a map of every
endpoint and parameter for whoever probes the API. Spring warns about it at start-up.

**Fix, when asked:** turn springdoc off where `cookies.secure` is true, or put it behind sign-in.

**✅ FIXED — Session 017 (2026-10-22), `fix/bug-hunt`.** Deployed (`tradeez.cookies.secure=true`), `/v3/api-docs/**` and Swagger UI are refused to everyone;
locally they stay open, the frontend's contract (DEC-007). `DeployedServerTest.docsClosed`.

### BUG-081 🟡 Any signed-in user sees the server's health details, its folder path included

**Status:** verified live (2026-10-21). **Affects:** `management.endpoint.health.show-details: when-authorized`

Signed in as an ordinary learner, `GET /actuator/health` lists the database (PostgreSQL), the disk's total and free
space, and `"path":"C:\\Users\\Fabio\\Desktop\\…"`. *When authorized* with no roles named means any authenticated user.

**Fix, when asked:** `show-details: never` (or a role nobody but an operator has).

**✅ FIXED — Session 017 (2026-10-22), `fix/bug-hunt`.** `management.endpoint.health.show-details: never`. `DeployedServerTest.healthWithoutDetails`: up or down,
signed in or not, nothing about the database, the disk or its path.

### BUG-080 🟠 One signed-in user can keep the server busy: nothing limits the expensive reads

**Status:** verified live (2026-10-21), timings on this machine. **Affects:** `GET …/setups`, `…/bars`, drills, lessons

Every rule on hourly candles over 399 days took **12 s** the first time and **2.5 s** each time the window moved by an
hour — a new window is a new cache key, so it is scanned again — and returned 5.3 MB. The daily chart took 5.6 s;
fourteen days of the simulated market's five-minute candles 0.9 s. No read endpoint has a per-user or per-address
limit; drills allow 600 an hour, each searching windows; a lesson searches random stretches every time it is asked. A
few parallel requests from one account occupy the CPU for everyone.

**Fix, when asked:** a per-user rate limit on the scanning endpoints, a cost cap (range × rules × timeframe), windows
snapped to whole days so a moved window reuses its scan, and a bound on concurrent scans.

**✅ FIXED — Session 017 (2026-10-22), `fix/bug-hunt`.** Measured again first, and the entry's explanation was wrong: the public chart already scans whole UTC days,
and an identical repeat took the same 2.7 s as a moved one — no rescan. The time was the re-check: two counts of a
year of minutes per rule on every request. Now the minutes are counted once a call per market, over the widest reach
any rule has (warm, every rule over the chart's 160 days of hours: 0.6 s); a chart's setups cover at most 6,000 candles,
above the widest chart the app draws (four days of minutes, 5,760), so a year of hours is refused in 20 ms; at most two
scans run at once server-wide and the rest wait; and candles, setups, the clock and lessons are limited to 240 a minute
per account (`ReadRateLimit`, a 429). A cold scan of the widest chart is still about 5 s. `SetupScannerTest.scansBounded`,
`SetupApiTest.costCapped`, `ReadRateLimitTest`. *Lesson:* measure the cause before fixing it — the bug's own account of
it was a guess.

### BUG-079 🟡 Text with a NUL character makes the database refuse the write

**Status:** verified live (2026-10-21). **Affects:** every free-text field stored as `text` or `jsonb` — a drawing's text
and style, the display name and email at sign-up

A drawing label `"ab\u0000c"` (under the 80-character limit) reached PostgreSQL, which refused it — `invalid byte
sequence for encoding "UTF8": 0x00` — and the `DataIntegrityViolationException` became a 500 (shown as a 401 through
BUG-078). No data is damaged; the request simply fails in the most confusing way.

**Fix, when asked:** refuse control characters in text input (one shared constraint), and answer a constraint
violation with 400.

**✅ FIXED — Session 017 (2026-10-22), `fix/bug-hunt`.** `NoNulStrings`, a Jackson module: a string or map key in any request body holding U+0000 is refused while
the body is read — a 400 that says why, nothing stored. `RequestErrorsTest.nul` (a label's text, a style key, a
display name).

### BUG-078 🟠 Every error Spring forwards to `/error` comes back as an empty 401

**Status:** verified live (2026-10-21). **Affects:** `SecurityConfig`, `JwtAuthenticationFilter`; every client of the
API

A request without its CSRF token (should be 403), a body that is not JSON (400), a number too large for its field
(400), the wrong HTTP method (405), a `text/plain` body (415) and a database failure (500, BUG-079) all came back as
`401` with an empty body. Spring answers these by forwarding to `/error`; that forward passes through the security
chain again, `JwtAuthenticationFilter` — a `OncePerRequestFilter` — does not run on the error dispatch, so the
request is anonymous there, and `anyRequest().authenticated()` refuses it.

What it does to the app: the client reads 401 as an expired session, refreshes it and **sends the request again** (a
POST that failed for another reason is sent twice), then shows a bare "401" — the real reason, which the server wrote,
never arrives. A server failure looks like a sign-in problem.

**Fix, when asked:** permit the `ERROR` dispatcher type (or `/error`) in the security chain, or answer these exceptions
in an `@RestControllerAdvice` so nothing is forwarded.

**✅ FIXED — Session 017 (2026-10-22), `fix/bug-hunt`.** The error dispatch is let through the security chain (`DispatcherType.ERROR` permitted), so a refusal keeps
its own status, and `server.error.include-message: never` keeps whatever an exception says to the log. Requests refused
before any controller — not JSON, the wrong content type or method, a parameter of the wrong type or missing — are
answered first by `RequestErrorHandler`, in words about the request. `DeployedServerTest` (a real server on a real
port: a CSRF refusal is a 403 with a body, a bad body a 400) and `RequestErrorsTest`. *Lesson:* MockMvc never forwards
to `/error`, so every test passed while the real server answered 401: a layer only a real server exercises.

### BUG-077 🟠 "Sign out everywhere" and a password change leave a stolen access token working

**Status:** verified live (2026-10-21). **Affects:** `JwtAuthenticationFilter`, `AuthService.logoutEverywhere`,
`changePassword`

`POST /api/auth/logout-all` revokes every refresh token, and the UI says every device is signed out. The access token
those sessions already hold is a stateless JWT valid for up to fifteen minutes, and nothing checks it against the
sign-out: after `logout-all`, the old access cookie still read `/api/me` and the practice journal (200). A password
change — usually made because someone else may know the password — has the same gap. The filter already reads the
user's row on every request, so the check would cost nothing.

**Fix, when asked:** keep `tokens_valid_after` on `app_user`, set it on `logout-all` and on a password change, and
refuse a JWT issued before it.

**✅ FIXED — Session 017 (2026-10-22), `fix/bug-hunt`.** Every access token carries its account's token generation (`gen`); "sign out everywhere" and a password
change move the account to the next (V27, `token_generation`), and the filter refuses a token of an earlier one — or
with none. A first version compared issue times, which a JWT keeps to the second: a token stolen in the same second as
the sign-out would have outlived it, and the tests' own tokens did. `AuthHardeningTest`: logout-all and a password
change end this device's token and the other's, the new one works; a correctly signed token without a generation is
refused. *Lesson:* a revocation by time inherits the clock's resolution; a counter has none.

### BUG-076 🟠 Anyone can keep an account from signing in

**Status:** verified live (2026-10-21). **Affects:** `AuthService.login`

After ten wrong passwords for one address, the eleventh attempt is refused with 429 *before* its password is looked
at — the right password included. The window is fifteen minutes and a correct sign-in is the only thing that clears
it, which the lock prevents; ten more guesses every fifteen minutes keep a known address locked out for as long as
someone cares to (from any number of addresses, or from one through BUG-075). Verified with a made-up address:
`401` ten times, then `429`.

**Fix, when asked:** let a correct password through a lock it did not cause — count failures per account *and*
caller, or slow the caller rather than the account, or check the password first and throttle only failures.

**✅ FIXED — Session 017 (2026-10-22), `fix/bug-hunt`.** The account's ten are counted per account *and* address (`ClientAddress`, the per-address limit's own
trusted-proxy rule): ten wrong guesses stop that address for that account, and the owner, elsewhere, signs in. Guessing
from many addresses still costs ten tries an address a window, against password-1.0.0 and the breach check.
`AuthHardeningTest.AccountLimit`. *Lesson:* a limit checked before the password stops whoever it is keyed on — key it
on the guesser too, not on the account alone.

### BUG-075 🔴 The per-address limit on sign-in and sign-up is skipped by escaping one letter of the path

**Status:** verified live (2026-10-21, local backend). **Found by:** the bug hunt Fabio asked for — logged, not fixed
**Affects:** `AuthRateLimitFilter.shouldNotFilter`; `/api/auth/login`, `/api/auth/register`

Twenty-four sign-in attempts in a row at `POST /api/auth/log%69n`, each with a different unknown address, all came back
`401 Invalid email or password.` — the login handler answering — and never 429. Twenty-two sign-ups at
`/api/auth/regist%65r` all reached validation (400). On the plain paths the limit (20 a window of five minutes per
address, BUG-002, BUG-070) refuses the 21st.

The filter compares `request.getRequestURI()` — the raw path, still percent-encoded — with three exact strings, while
Spring MVC and Spring Security both match the decoded path. So the escaped request is routed to the login handler,
permitted, and exempt from CSRF like the plain one, but the filter does not recognise it. The per-account limit (ten
in fifteen minutes) still holds for each address; what is gone is everything that limit does not cover: spraying one
password over many accounts, stuffing leaked credentials, and walking registration to learn which addresses have
accounts — the reason BUG-002 added the per-address limit.

**Fix, when asked:** match the decoded, normalised path (`ServletRequestPathUtils` / `getServletPath()`), or apply the
limit through a Spring Security request matcher, so it sees the request exactly as the router does.

**✅ FIXED — Session 017 (2026-10-22), `fix/bug-hunt`.** The filter compares the path as the router reads it — decoded, `;` parameters removed
(`UrlPathHelper`) — for the check and the key alike, so `/api/auth/log%69n` counts against `/api/auth/login`'s twenty.
`AuthHardeningTest.EncodedPaths`: twenty escaped attempts, then the plain path is refused; escaped letters and `;x=1`
read away. *Lesson:* a check on a path must read it as the router does, or a spelling the router accepts walks past it.

### BUG-074 🟡 Trades placed before T22 counted as "outside the killzones"

**Status:** verified — on the test account's 236 trades (session 017, 2026-10-19)
**Affects:** the T23 scorecard's time line and the T25 statistics' killzone grouping, for plans placed before
V19 (T22)

The killzone a plan was placed in has been kept with it since T22. Plans from before have none stored, and the
scorecard read that as "Placed outside the killzones", and the first statistics put all 235 of those trades
outside — while 126 of them were inside one. A missing record is not a fact about the market.

**✅ FIXED — Session 017.** `TradePlan.killzoneWhenPlaced()`: the stored killzone when there is one, otherwise the
one its moment falls in (`Killzone.at`, now also what the plan context uses) — a killzone is a matter of the clock.
`TradePlanTest` (a pre-T22 plan in New York AM, one at noon, a stored one kept).

### BUG-072 🟡 A drawing's style was any JSON of any size

**Status:** verified — a 100,000-character style saved (session 017's bug hunt, 2026-10-17)
**Affects:** `ChartDrawingController` — `PUT /api/charts/{symbol}/drawings/{id}`

The app sends a colour, a width and a fill; the server stored whatever `style` held, per drawing, up to
500 drawings per chart — room for one account to fill the database with its own junk.

**✅ FIXED — Session 017.** At most 8 entries and 512 characters, else a 400.
`ChartDrawingApiTest.styleBounded`; the 500-drawing cap, never tested before, is `capped`. *Since BUG-084:* the count
gave way to the three keys the chart reads, each in the form it draws; `styleBounded` still passes.

### BUG-071 🟡 A stop far enough below the price slipped past the 20,000-tick check

**Status:** verified (session 017's bug hunt)
**Affects:** `PracticeService.validateBracket`

The check was `Math.abs(stop - reference) > MAX_BRACKET_TICKS`. For a stop of `Long.MIN_VALUE +
reference` the subtraction is exactly `Long.MIN_VALUE`, whose `Math.abs` is itself — negative — so the
plan was accepted, and its reward:risk came out negative. Harmless to anyone else; wrong all the same.

**✅ FIXED — Session 017.** The bounds are compared without subtracting. `RobustnessTest.bracketOverflow`.

### BUG-070 🔴 Writing a new X-Forwarded-For on each request was a new sign-in quota

**Status:** verified — 21 sign-ins with 21 different headers were all answered (session 017's security pass)
**Affects:** `AuthRateLimitFilter.clientIp`

Listed until now under Known limitations as "trusted as a best effort". The filter read the header's
**first** entry — which the client writes — so the per-IP limit on sign-in and sign-up could be walked
around entirely. A test even relied on it, using the header to pose as a second address. Fabio asked for
the login limit to be made real, so it is a bug, and fixed.

**✅ FIXED — Session 017 (DEC-064).** Each proxy appends the address it saw, so only entries our own
proxies add can be believed, counted from the right: `tradeez.rate-limit.trusted-proxies` of them
(`TRUSTED_PROXIES`, default 0 — the header is ignored and the connection's address is used). Set it to 1
behind one proxy that appends. `AuthHardeningTest.ClientAddress`; BugFixTest now sets the remote address.

### BUG-069 🟡 Two sign-ups for one address at the same moment: one got a 500

**Status:** reasoned, then shown with a repository that loses the race on cue (session 017's bug hunt)
**Affects:** `AuthService.register`

Both requests found the address free; the unique index on `lower(email)` let one row in, and the other's
`DataIntegrityViolationException` reached no handler.

**✅ FIXED — Session 017.** Caught at the save: the same 409 a taken address gets. `RegisterRaceTest`.

### BUG-068 🟡 A time past what Java's Instant holds answered 500

**Status:** verified — `to=9223372036854775807` on the bars, clock and setups endpoints (session 017's bug hunt)
**Affects:** `MarketDataController`, `ClockController`, `SetupController`

`Instant.ofEpochSecond` throws `DateTimeException` past its range, and nothing mapped it to a client
error.

**✅ FIXED — Session 017.** `Epochs.of`: an epoch second must lie in 1970–9999, else a 400 naming the
parameter. `RobustnessTest.market`.

### BUG-067 🟡 A practice filter the market never meets searched ten thousand times, then answered 500

**Status:** verified (session 017's bug hunt)
**Affects:** `PracticeService.create` and `pickStart`; the practice picker

Saturday, or the Asia killzone on a Friday (the market shuts at 17:00, before Asia's 20:00), on a
simulated market: `pickStart` drew 10,000 random moments, found none open, and threw an
`IllegalStateException` — a 500, after a busy loop. On real data the same request was a 400 with a vague
message. Sunday was accepted, though the API documents Monday to Friday. The picker offered Asia and
Friday together.

**✅ FIXED — Session 017.** Weekend days and Asia-on-Friday are refused up front with a reason; a search
that finds nothing is a 400; the picker disables the impossible pair. `RobustnessTest.practice`,
`PracticeView.test` (BUG-067).

### BUG-066 🔴 A password longer than BCrypt reads: a 400 that told accounts apart, and a match on its first 72 bytes

**Status:** verified (session 017's security pass, 2026-10-17)
**Affects:** `AuthService.login` and `register`; `RegisterRequest`

BCrypt hashes at most 72 bytes. Spring Security's encoder throws for more and, when checking, compares
only the first 72. The form allowed 200 characters. So:

- **sign-up** with 73 to 200 characters was refused with BCrypt's own message, "password cannot be more
  than 72 bytes", through the global `IllegalArgumentException` handler;
- **sign-in** hashed the typed password for an unknown address too, to keep the timing even — which
  threw for a long one: a **400** for an address with no account, a **401** for one with an account.
  An enumeration oracle, the thing the login's vague message exists to prevent;
- an account whose password was 72 bytes accepted it with **anything typed after it**.

**✅ FIXED — Session 017 (DEC-064).** password-1.0.0 refuses more than 72 bytes at sign-up, in words. A
longer password at sign-in is wrong for everyone alike, after hashing a stand-in of the same cost.
`AuthHardeningTest.LongPasswords`, `RobustnessTest.auth`.

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
