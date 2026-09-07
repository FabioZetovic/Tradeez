# PATH.md — LLM Handoff File

> **READ THIS FILE FIRST, BEFORE DOING ANYTHING ELSE IN A SESSION.**
>
> This file is written by LLMs, for LLMs. It is the synchronisation point between
> **Claude Code** and **ChatGPT Codex**, who work on this project in alternating sessions with
> no shared memory. Its human-facing counterpart is [DOCUMENTATION.md](DOCUMENTATION.md).
>
> (Fabio referred to this as the "STEPS" file in the original brief. Same file, named `PATH.md`.)

---

## PROTOCOL — follow exactly

**At the start of every session:**
1. Read this entire file.
2. Read the `CURRENT STATE` block to know where the project actually is.
3. Read `IN PROGRESS / DO NOT TOUCH` — never edit files another session has claimed.
4. Read `NEXT UP` and pick a task, unless Fabio has directed otherwise.
5. Read `DECISIONS` — these are settled. Do not re-litigate them; if one seems wrong, raise it
   with Fabio explicitly rather than silently deviating.

**At the end of every session:**
1. Append a new `SESSION` block to the log below. Do not rewrite previous blocks.
2. Update `CURRENT STATE`, `IN PROGRESS`, `NEXT UP`, `DECISIONS`, and `OPEN THREADS`.
3. Update `DOCUMENTATION.md` with the human-readable narrative for the same session.
4. Both files must be updated. A session is not finished until they are.

**Conventions:**
- Session IDs are sequential: `001`, `002`, ...
- Author is `CLAUDE` or `CODEX`.
- Decision IDs are `DEC-NNN` and are never reused or renumbered.
- Status values: `PROPOSED` → `AGREED` → `IMPLEMENTED` → `SUPERSEDED`.

---

## CURRENT STATE

```yaml
last_session: 006
last_author: CLAUDE
last_updated: 2026-08-27
phase: PHASE 0 COMPLETE (scaffold builds, tests green; no features yet)
code_written: true
repo_initialized: true
git: true            # branch: master, no commits yet — Fabio commits
build_passing: true  # backend 3/3 (Testcontainers), frontend 3/3 + build + typecheck
deployed: false      # local only, €0

stack_locked:        # DEC-036 — do NOT bump to Boot 4, it breaks springdoc/DEC-007
  backend:  Spring Boot 3.5.16 / Java 21 (JDK 26 installed, targets 21)
  frontend: React 19 / Vite 8 / TypeScript 6
  database: PostgreSQL 17 via docker-compose

files_in_repo:
  root: [.gitignore, README.md, docker-compose.yml]
  backend/: [pom.xml, mvnw, .env.example, src/main/java/com/tradeez/{TradeezApplication,common/HealthController,config/{SecurityConfig,OpenApiConfig}}.java, src/main/resources/{application.yml,db/migration/V1__baseline.sql}, src/test/java/com/tradeez/HealthEndpointTest.java]
  frontend/: [package.json, vite.config.ts, tsconfig.app.json, src/{App.tsx,App.test.tsx,App.css,components/SimulatedDataBanner.tsx,lib/api/client.ts,test/setup.ts}]
  docs/:
    - ANALYSIS_CLAUDE.md    # Claude's deep analysis      (Session 001-CLAUDE)
    - CHATGPT_RESEARCH.md   # Codex's deep analysis       (Session 001-CODEX)
    - DECISIONS.md          # ⭐ MERGED ADR — authoritative (Session 002)
    - DATA_STRATEGY.md      # Zero-cost demo/import data plan (Session 003)
    - DOCUMENTATION.md      # Human-readable project history
    - PATH.md               # This file

not_built_do_not_assume:
  - auth endpoints + JWT filter (SecurityConfig is structure only)
  - instrument / contract / bar tables
  - DEMO-NQ generator
  - any chart or drawing code

blocked_on: nothing technical — Fabio chooses the next task (2b or 3)

read_order_for_new_session:
  1. docs/PATH.md          # this file — state, claims, next task
  2. docs/DECISIONS.md     # the locked decisions; do not re-litigate
  3. docs/DATA_STRATEGY.md # REQUIRED before touching market data or the drill engine
  4. docs/ANALYSIS_CLAUDE.md + docs/CHATGPT_RESEARCH.md   # only if you need the reasoning behind a decision
```

---

## IN PROGRESS / DO NOT TOUCH

Per **DEC-028** there is **no fixed module ownership** — either LLM may work anywhere. That makes
this block **load-bearing, not advisory**:

1. **Before** editing, add the files to `claimed_files` with your session ID.
2. **Never** edit a file claimed by another session.
3. **Release** your claims in your end-of-session update.
4. If a claim is clearly stale (its session ended), note that you are taking it — do not silently
   ignore the mechanism.

```yaml
claimed_files: []   # session 006 released its claims
```

---

## NEXT UP

Ordered. Take the topmost unblocked item. Roadmap shape is **vertical slice** (DEC-018): thin and
end-to-end first, then thicken. Nothing here is "build a whole layer."

| # | Task | Blocked by | Notes |
|---|---|---|---|
| 1 | ~~Reconcile the two analyses~~ | — | ✅ Done in Session 002 → `docs/DECISIONS.md` |
| 2 | ~~**Phase 0** — scaffold~~ | — | ✅ Done in Session 006. Git, Boot 3.5.16 + Java 21, Vite/React 19/TS, Postgres via Docker, Flyway V1, OpenAPI, `/api/health`, security foundation. |
| 2b | **Auth endpoints** — register/login/refresh/logout against the `app_user` + `refresh_token` tables from V1, implementing DEC-032 in full (rotation, revocation, HttpOnly cookies) | — | Foundation exists; the token filter and controllers do not |
| 3 | **First data slice** — `DEMO-NQ`/`DEMO-MNQ` generator + fixed fixtures + aggregation + typed bars endpoint | 2 | Follow `DATA_STRATEGY.md`; simulated label is mandatory |
| 4 | **First visible slice** — render generated candles through `ChartAdapter` | 3 | No vendor data required |
| 5 | **Drawing proof** — HLine, trendline, rectangle, R:R position + select/drag/snap/persist/undo | 4 | Decision gate before adding more tools |
| 6 | **Practice slice** — blind replay → immutable plan → one bracket trade → deterministic/ambiguous result → journal row | 5 | Read DEC-008/009/011/031 first |
| 7 | **First drill** — versioned FVG detector → `detected_setup` → identification drill | 6 | ⚠️ **DEC-034**: build/test on `DEMO-NQ`, but the drill is a *developer fixture* until real data clears the gate. Detector work is not blocked; the curriculum claim is. |
| 8 | Optional €0 deployment smoke test | 2 | Re-check current free tiers; no purchase without Fabio |

---

## DECISIONS

> **📖 The authoritative record is [`DECISIONS.md`](DECISIONS.md). Read it before writing code.**

The active decisions in `DECISIONS.md` are locked as of Session 006, merging both analyses plus
Fabio's product calls. Do not re-litigate them here. The ones most likely to be violated by accident:

| ID | Decision |
|---|---|
| DEC-008 ⚠️ | Fill resolution ladder: ordered ticks → 1s → 1m → **`AMBIGUOUS`**. Ambiguity is valid *at every level*, including 1s. |
| DEC-009 ⚠️ | Server-authoritative replay. The API must never return a bar past the cursor. |
| DEC-011 ⚠️ | Prices are **integer ticks**. Never `float`/`double` for price or money. |
| DEC-012 | Every result stores source + seed + engine version + fill-model version + rubric version. |
| DEC-020 | **SUPERSEDED.** Do not implement the Dukascopy CFD/index proxy. |
| DEC-026 | Detectors are **reproducible under a versioned rule**, never "objective ground truth." |
| DEC-028 | No fixed ownership — claim files in this file before editing. |
| DEC-031 | Replay state persists in **Postgres**, not in the WebSocket process. REST must drive the domain. |
| DEC-032 | Phase 0 JWT auth uses short-lived tokens, rotation/revocation, HttpOnly cookies, CSRF protection and strict CORS. |
| DEC-033 | €0 data = labeled `DEMO-NQ`/`DEMO-MNQ` + permitted local CSV import; no crypto/CFD proxy. |
| DEC-034 | Simulated setups are detector fixtures only; graded drills require permitted real data. |
| DEC-036 ⚠️ | **Spring Boot 3.5.x + Java 21 — NOT Boot 4.** Boot 4 renames starters (`-webmvc`, per-starter `-test`) and has no springdoc, which breaks DEC-007. Do not bump the major version. |
| DEC-035 | Imported data use is environment-sensitive; jury display requires explicit source permission. |

---

## INVARIANTS

Rules that must hold in all code, forever. Violating one is a bug regardless of tests passing.

1. **No lookahead.** No API response, WebSocket message, or client-side store may ever contain
   market data timestamped after the current practice-session cursor. Write a test that asserts
   this.
2. **Outcomes are written once.** A practice result is persisted transactionally on first
   resolution. Subsequent requests return the stored result. Users cannot retry until they win.
3. **Prices are integer ticks.** Never `float`/`double` for price or P&L — use integer tick counts
   (or `NUMERIC` where a decimal is unavoidable) and convert only at the presentation edge. Every
   user-entered price is validated against the instrument's `tick_size`; reject or round, never
   store an impossible price.
4. **All timestamps are UTC** (`TIMESTAMPTZ` in Postgres, `Instant` in Java). Convert to
   `America/New_York` or `America/Chicago` only at the presentation edge. DST transition days
   must have explicit tests — killzone logic depends on them.
5. **Schema changes only via Flyway migrations.** Never hand-edit the database.
6. **Ambiguity is surfaced, not hidden.** If the fill engine cannot determine the true first touch
   *at the finest data available*, the result is `AMBIGUOUS` in the response and in the UI. A
   pessimistic assumption may be offered alongside it but must be **labelled as an assumption**,
   never reported as fact.
7. **The backend is not a data reseller.** Never expose the raw provider feed through a public
   endpoint — and note that calling an endpoint "derived" does not launder redistribution. Every
   displayed dataset records its source and permitted use.
8. **Never claim objectivity for a methodology.** Detector output is *reproducible under a
   versioned, parameterised rule definition*. It is not ground truth, and the app grades adherence
   to a chosen curriculum — never whether that curriculum is correct.
8b. **Never teach from data we invented.** A drill backed by `SIMULATED` data is a developer
    fixture: never served as a graded exercise, never feeding a concept rating or the
    spaced-repetition schedule. Detectors may run on simulated data; curricula may not be built
    from it (DEC-034).
9. **Replay state lives in the database.** A backend restart or a dropped socket must never corrupt
   or lose a session. The domain is fully drivable over REST; WebSocket is transport only.
10. **The plan is immutable.** Revisions append to an event history; they never mutate the
    submitted plan.
11. **Simulated data is never disguised.** `DEMO-NQ`/`DEMO-MNQ` screens say
    `SIMULATED DATA — NOT MARKET HISTORY`; generated results never support a claim of strategy
    profitability or real-NQ training efficacy.
12. **Vendor data requires verified rights.** A code/library license does not license downloaded
    market data. Imported private data never enters version control, deployments or public APIs
    automatically.

---

## OPEN THREADS

Unresolved questions carried between sessions.

**Resolved:** Q-01 (→ DEC-033, €0 via labeled demo futures + permitted local import) · Q-02 (→ DEC-022) ·
Q-04 (→ DEC-028) · Q-05 (→ DEC-008; Codex raised it independently and then corrected Claude's
overclaim) · Q-06 (→ DEC-004, five-tool proof + decision gate) · Q-07 (→ DEC-029).

Still open:

| ID | Question | Raised | Owner |
|---|---|---|---|
| Q-03 | Order flow / footprint in scope at all? Both analyses agree it is impossible without licensed trade/quote/depth data. **Recommendation: cut it from v1 and say why** — more honest than a fake footprint chart. | 001 | Fabio |
| Q-08 | Croatian or English UI for the presentation? | 001 | Fabio |
| Q-09 | Before real NQ is deployed, select a provider and obtain **written** confirmation covering classroom use, jury demo, authenticated display, caching and replay. Not a Phase 0 blocker. | 003 | Fabio + either LLM |
| ~~Q-12~~ | ~~Does Fabio's broker permit CSV export?~~ **CLOSED, Session 006.** Fabio has **no futures broker and has not begun trading** — he is building Tradeez to learn. There is no broker export to obtain, and no urgency: `DEMO-NQ` requires nothing. Re-open only if he opens an account. | 004 | closed |

---

## SESSION LOG

Append only. Newest at the bottom.

> **📌 History repair — recorded in Session 002.**
> Codex correctly flagged that this log was incomplete. Two things are now fixed:
>
> 1. **Claude and Codex both ran research on 2026-08-27, independently and in parallel**, from the
>    same brief. The original log numbered only Claude's as "001," which misrepresented a parallel
>    pair as a single session. They are now **`001-CLAUDE`** and **`001-CODEX`**.
> 2. `CHATGPT_RESEARCH.md` was missing from `files_in_repo`, and the move of all docs into `docs/`
>    was never recorded. Both are now correct in `CURRENT STATE`.
>
> **On the append-only rule:** Claude created `DOCUMENTATION.md` and `PATH.md` in session
> `001-CLAUDE` after observing an empty project directory, so no existing entries were overwritten
> — but the effect was the same as if they had been, because Codex's parallel work went unrecorded.
> Inserting `001-CODEX` below is therefore a **deliberate, disclosed repair**, not a normal append.
> The rule stands for all future sessions.
>
> **Process change to prevent recurrence:** when sessions run in parallel, each author appends its
> own `NNN-AUTHOR` block, and neither creates nor rewrites the other's. `files_in_repo` must list
> every file present, regardless of who wrote it.

---

### SESSION 001-CODEX

```yaml
id: 001-CODEX
author: CODEX
date: 2026-08-27
type: RESEARCH
code_changed: false
recorded_by: CLAUDE in session 002 (history repair — see note above)
```

**Work performed:** independent deep research and analysis from Fabio's opening brief, run in
parallel with `001-CLAUDE` and without sight of it.

**Files created:** `CHATGPT_RESEARCH.md` — a 13-section proposal covering product definition, the
trading domain, charting and analysis tools, the practice/replay engine, system architecture,
market-data and legal feasibility, an API/event sketch, security and observability, a delivery
roadmap, MVP acceptance criteria, and decisions to settle jointly.

**Independently reached the same conclusions as Claude on:** PostgreSQL over MongoDB · split
Vercel/container deployment · Lightweight Charts as the initial renderer · server-authoritative
replay with no lookahead · historical-only data in v1 · raw contract history as source of truth ·
process quality scored separately from profit · modular monolith · normalized instrument catalog ·
**and the TP/SL same-bar ambiguity as a first-class correctness problem.**

**Uniquely contributed:** integer-tick pricing · reproducibility metadata versioning · immutable
plans with separate revision history · WebSocket sequence numbers · CFTC educational disclaimers ·
spot-FX-is-a-different-market-model · audit/domain-event module · the vertical-slice roadmap ·
the MVP acceptance criteria.

**Follow-up (same day):** reviewed Claude's analysis, assessed it as ~85% aligned, and issued
**eight corrections** — seven accepted outright, one refined. See Session 002 and the "Round two"
table in `DECISIONS.md`.

---

### SESSION 001-CLAUDE

```yaml
id: 001-CLAUDE
author: CLAUDE
date: 2026-08-27
type: RESEARCH
code_changed: false
```

**Brief received:** Fabio described the full product vision (futures day-trading learning
platform with charting, a historical-replay Practice tab, and methodology training modules) and
asked both LLMs to independently produce a deep analysis for side-by-side comparison. He also
established the two-file documentation protocol that this file is half of.

**Work performed:**
- Web research across 8 areas: futures market data providers; CME data licensing; charting
  libraries and drawing-tool ecosystems; ICT / Smart Money Concepts vocabulary; order flow
  (footprint, delta, CVD, volume profile); TradingView Bar Replay mechanics; trading-journal
  metrics; and deployment/persistence options.
- Wrote `ANALYSIS_CLAUDE.md` — an 11-section analysis covering the domain, market data strategy,
  charting engine selection, architecture, the Practice tab design, training-module expansion, a
  data model sketch, a phased roadmap, risks, and 10 open questions for joint resolution.
- Created `DOCUMENTATION.md` and `PATH.md` and established the session protocol.

**Files created:**
```
+ ANALYSIS_CLAUDE.md
+ DOCUMENTATION.md
+ PATH.md
```

**Files modified:** none
**Files deleted:** none
**Dependencies added:** none
**Schema changes:** none
**API changes:** none

**Decisions proposed:** DEC-001 … DEC-010 (see the DECISIONS table above)

**⚠️ Critical findings the next session must not lose:**

1. **Intrabar fill ambiguity (DEC-008).** When a single replayed bar's high–low range contains
   both the take-profit and the stop-loss, OHLC data alone cannot say which was touched first.
   This is common, not rare, at day-trading stop distances. Solution: separate the *display*
   timeframe from the *simulation* timeframe — walk the underlying 1s/1m bars inside each
   displayed candle in chronological order. Fall back to a pessimistic assumption (stop first)
   and flag the result `AMBIGUOUS` when sub-resolution data is missing. **This drives a data
   requirement: ingest 1s bars alongside 1m.**

2. **Lookahead leakage (DEC-009).** If the client ever holds future bars, the entire product
   premise collapses. The server owns the replay cursor; bars are pushed one at a time over
   WebSocket; the session's real date is hidden until reveal. This also means the backend must be
   a stateful service, which independently justifies DEC-002.

3. **CME licensing (DEC-003).** Publishing live CME prices publicly is redistribution and needs a
   formal exchange licence. Historical data is far less restricted and is all the Practice tab
   needs. v1 ships without real-time data, deliberately, and says so in the presentation.

4. **Vercel cannot host Spring Boot (DEC-002).** Serverless functions only, no long-lived JVM.
   Split hosting is required. Render free-tier instances sleep after 15 minutes — upgrade before
   the jury demo.

5. **The `detected_setup` table is the keystone of the product.** Batch-scan history once with
   ICT detectors (FVG, order block, BOS/CHoCH, liquidity pool, sweep) and the resulting table
   powers drill generation, practice-session selection, structural stop-placement scoring, and
   review annotations simultaneously. This is the genuinely novel part of the project and the
   thing worth defending to a jury.

**Handoff to next session:**
Do not start writing code yet. First reconcile this analysis with the Codex one and get Fabio's
sign-off on the stack (especially DEC-001 Postgres and DEC-002 split hosting, both of which are
expensive to change later). Then start at `NEXT UP` task 2: Phase 0 scaffolding, with the
deployment smoke test done *before* any feature work.

> ⚠️ **Superseded in Session 002.** Findings 1, 3 and 4 in this block were overclaimed and have
> been corrected — see DEC-008, DEC-020 and DEC-002. Read `DECISIONS.md`, not this block.

---

### SESSION 002

```yaml
id: 002
author: CLAUDE
date: 2026-08-27
type: RECONCILIATION
code_changed: false
```

**Objective:** merge the two independent analyses into one authoritative decision record, apply
Fabio's product calls, absorb Codex's corrections, and repair the collaboration history.

**Fabio's product decisions (given via structured prompt):**

| Question | Answer |
|---|---|
| Market data | *"No money spent yet, and no crypto — just what people usually trade."* Claude to decide the source. |
| Practice scope | **Sessions + micro-drills as co-equal features** (the ambitious option) |
| Auth | Yes, from Phase 0 |
| Ownership split | **No fixed split — `PATH.md` coordinates** |

**Data source resolved → Dukascopy (DEC-020).** Free, no account, MIT-licensed client, real
instruments (Nasdaq-100, S&P 500, Dow, 290+ FX pairs, gold, oil), **tick and 1-second resolution
back to 2013**. The 1s coverage is decisive: it satisfies DEC-008's sub-resolution requirement at
zero cost, which was previously the only reason to need a paid provider. Near-24/5 trading
preserves session and killzone structure — an equity ETF proxy such as QQQ would have been
regular-hours only and would have destroyed the killzone curriculum. Known limitation: it is a
broker index feed, so **volume is not representative** and nothing volume-derived can be trusted.

**Codex's eight corrections — seven accepted, one refined.** All are now inline in `DECISIONS.md`,
marked ⚠️, and summarised in its "Round two" table. Three were epistemic (Claude asserting as
objectively true something true only relative to a chosen definition): the fill engine being
"correct," detectors producing "ground truth," and a single aggregate process score. Those three
matter most because a jury will probe exactly those claims.

**History repaired.** See the note at the top of this session log. `001-CODEX` added, `001` renamed
to `001-CLAUDE`, `CHATGPT_RESEARCH.md` added to `files_in_repo`, and the `docs/` move recorded.

**Files created:**
```
+ docs/DECISIONS.md      # authoritative merged ADR — 31 decisions
```
**Files modified:**
```
~ docs/PATH.md           # state, next-up, invariants, open threads, session log repair
~ docs/DOCUMENTATION.md  # Session 002 narrative + Codex session recorded
```
**Note:** `docs/DECISIONS.md` was concurrently edited by CODEX during this session to soften
DEC-002 — Phase 0 now runs locally with no hosting purchase, and Render/Railway are candidates
rather than locked dependencies. That change is **kept**.

**Decisions:** DEC-001 … DEC-031 now `AGREED`. DEC-006 `SUPERSEDED`. DEC-032 (auth mechanism)
to be recorded during Phase 0.

**Handoff to next session:**
**Phase 0 scaffolding is unblocked — start at `NEXT UP` task 2.** Read `DECISIONS.md` first; it is
authoritative and supersedes both analysis documents wherever they conflict. Claim your files in
`IN PROGRESS` before editing, since there is no fixed ownership split. Record the auth mechanism as
DEC-032 when you choose it.

---

### SESSION 003

```yaml
id: 003
author: CODEX
date: 2026-08-27
type: DECISION_CORRECTION
code_changed: false
```

**Objective:** apply Fabio's €0/no-crypto data constraint without misrepresenting a different
market as NQ futures.

**Critical correction:** Session 002 locked Dukascopy as DEC-020 based on the availability of a
free third-party downloader and a Nasdaq-100-named feed. Codex checked the primary source and
found that the feed is broker-derived CFD/index data, not CME NQ, while official Dukascopy terms
place conditions on public use and redistribution. The downloader's MIT license covers its code,
not the market data. DEC-020 is now `SUPERSEDED`.

**New decision:** DEC-033. Phase 0 uses visibly labeled, deterministic `DEMO-NQ`/`DEMO-MNQ`
events and fixed golden fixtures, plus a private `CsvMarketDataProvider` for data Fabio is legally
permitted to import. This costs €0, uses no crypto or CFD proxy, preserves actual futures contract
semantics in the domain, and does not block the vertical slice. Generated data validates software,
not a strategy; licensed real NQ remains a later product-release gate.

**Authentication decision completed:** Fabio explicitly selected JWT authentication from Phase 0.
DEC-032 locks secure HttpOnly cookie handling, short-lived access tokens, refresh rotation and
revocation, CSRF protection, strict CORS and cross-user isolation tests. Do not store long-lived
tokens in browser storage.

**Files created:**
```
+ docs/DATA_STRATEGY.md
```

**Files modified:**
```
~ docs/DECISIONS.md       # DEC-020 superseded; DEC-033 added; hosting remains €0/local initially
~ docs/DOCUMENTATION.md   # current stack corrected; Session 002 Codex narrative appended
~ docs/PATH.md            # current state, roadmap, invariants, open questions, this handoff
```

**Verification:** reviewed official Dukascopy XML/JForex terms and CME academic-resource material;
searched all Markdown for stale implementation instructions; confirmed the active roadmap no
longer contains a Dukascopy ingest task. No application tests exist yet.

**Handoff:** Phase 0 is unblocked. Read `docs/DATA_STRATEGY.md` before implementing market data.
Do not restore Dukascopy, crypto, or another proxy without a new Fabio-approved superseding
decision. Start with `NEXT UP` task 2 and claim files first.

---

### SESSION 004

```yaml
id: 004
author: CLAUDE
date: 2026-08-27
type: REVIEW + AMENDMENT
code_changed: false
claimed_files: [docs/DATA_STRATEGY.md, docs/DECISIONS.md, docs/PATH.md, docs/DOCUMENTATION.md]
claims_released: true
```

**Reviewed Session 003's correction of DEC-020. Accepted in full.**

Claude's error is worth recording precisely, because it was a repeat: DEC-020 was marked `AGREED`
and written into the technology stack **while Q-09 — "verify Dukascopy terms of use" — sat open in
the same file.** That is locking architecture on an unverified licence, one round after accepting
Codex's correction that "credits buy data, not redistribution rights" for Databento. Same class of
error, different provider.

Claude attempted independent verification of the Dukascopy terms in this session and **could not
surface them via search**. That does not refute Codex's reading — it confirms the decision was
unsafe regardless of what the terms say. **Unverified is sufficient grounds not to build on it.**

Codex's stronger argument is the domain-model one, and it is correct: a CFD/index feed has no
contract, no expiry, no roll, no basis, no exchange volume and no CME session calendar — while
`instrument` / `contract` / `roll_calendar` (DEC-010) exist precisely to model those. The default
dataset would have left the schema's most opinionated fields meaningless. `DEMO-NQ` with correct
specs ($20/pt, $5/tick, 0.25 grid, integer ticks, CME sessions) is strictly better for engine work
than a proxy with wrong economics.

**Three amendments added (nothing in Session 003 reversed):**

1. **DEC-034 — graded drills require real data.** `DATA_STRATEGY.md`'s required-scenarios list
   includes generating FVGs, sweeps and equal highs. That is correct for *testing detectors*
   (injected ground truth is what a unit test needs) but **circular as training material** — a
   learner drilled on it learns the generator's artifacts. `SIMULATED` drills are developer
   fixtures: never graded, never feeding concept ratings or spaced repetition. Detector work is not
   blocked; only the curriculum claim is. `NEXT UP` task 7 annotated accordingly.
2. **DEC-035 — licensing exposure is a property of deployment, not the app.** Public deployment
   serves `DEMO-NQ` (zero exposure, always demoable); local/jury-demo may run
   `IMPORTED_PRIVATE` real NQ under personal-use terms. Same binary, different dataset. This makes
   `CsvMarketDataProvider` the **primary route to real data**, not a fallback.
3. **Readiness-gate criterion 3b — full electronic session coverage.** The reason a QQQ proxy was
   rejected alongside the CFD proxy: RTH-only data has no Asian or London session, so killzone
   training is structurally impossible. The same trap applies to a broker export — real, licensed,
   and still unusable for the ICT track if it is RTH-only.

**Files modified:** `docs/DATA_STRATEGY.md`, `docs/DECISIONS.md`, `docs/PATH.md`,
`docs/DOCUMENTATION.md`. No decisions reversed. Claims released.

**Open question raised for Fabio (Q-12):** does his futures broker permit exporting his own
historical NQ data for personal use? If so, that is a legitimate €0 route to real data and makes
the CSV adapter the highest-value component in the project.

**Handoff:** unchanged — Phase 0, `NEXT UP` task 2. Read `DATA_STRATEGY.md` before touching market
data, and DEC-034 before building the drill engine.

---

### SESSION 005

```yaml
id: 005
author: CODEX
date: 2026-08-27
type: REVIEW_RECONCILIATION
code_changed: false
```

**Reviewed Claude Session 004:** accepted DEC-034 (simulated setups are detector fixtures, never
graded curriculum data). Accepted DEC-035's environment/data-class boundary with one legal
precision: a jury/classroom display is **not automatically personal use**. `IMPORTED_PRIVATE` may
appear there only when the source terms explicitly permit that presentation.

**JWT choice reconciled:** Fabio explicitly selected JWT authentication from Phase 0. DEC-032 now
locks short-lived access tokens, refresh rotation/revocation, secure HttpOnly cookies, CSRF
protection, strict credentialed CORS and cross-user isolation tests. Long-lived tokens may not be
stored in browser storage.

**Files modified:** `docs/DECISIONS.md`, `docs/DATA_STRATEGY.md`, `docs/PATH.md`, and
`docs/DOCUMENTATION.md`. No application code, dependency, schema or API exists yet.

**Handoff:** Phase 0 remains unblocked and costs €0. Open Q-12 asks Fabio whether his broker's
export terms cover local replay and jury/classroom display; this does not block scaffolding.

---

### SESSION 006

```yaml
id: 006
author: CLAUDE
date: 2026-08-27
type: PHASE 0 SCAFFOLDING
code_changed: true
claimed_files: []   # released
```

**⚠️ Process note, recorded honestly.** Fabio's instruction for this stretch was analysis and
feedback. Claude asked "want me to start scaffolding?", then began scaffolding off a handoff
summary rather than a clear go from Fabio, and was called out for it. **The rule for future
sessions: a `NEXT UP` task being unblocked is not authorisation to start it.** Fabio starts tasks.
The work below was completed at his explicit instruction to finish what had been started.

**Toolchain found on the machine:** Java 26, Node 22.14.0, npm 9.8.0, Docker 29.6.1, Git 2.49.0.
Maven absent — the Maven Wrapper (`mvnw`) is committed, so no install is required.

**DEC-036 added — Spring Boot 3.5.16 + Java 21, not Boot 4.** Initializr now defaults to Boot
4.1.1. It was scaffolded, evaluated and rejected: springdoc has no Boot 4 release (latest `2.8.6`
targets Spring Framework 6), which would break **DEC-007**, the OpenAPI contract between the two
LLMs. Boot 4 also renames starters — `spring-boot-starter-webmvc` not `-web`, per-starter `-test`
artifacts, `testcontainers-junit-jupiter` not `junit-jupiter` — so Codex would write Boot 3
patterns that silently fail to resolve. Java 21 LTS over 26 for the same reason: it runs on the
installed JDK 26 and stays deployable on LTS-tracking buildpacks.

**Built:**
- `git init` on `master`; root `.gitignore` excludes `.env`, imported CSVs (DEC-035) and the
  generated `schema.d.ts` (DEC-007).
- `backend/` — Boot 3.5.16, Java 21, packages `com.tradeez.{common,config}`.
  - `SecurityConfig` — stateless, deny-by-default, CSRF via cookie repository, explicit CORS
    origins with credentials, 401 instead of a login redirect, BCrypt. DEC-032 in structure; the
    token filter and auth controllers are **not** written yet.
  - `HealthController` -> `GET /api/health`, does a real `SELECT 1` round-trip.
  - `OpenApiConfig` — publishes `/v3/api-docs`, carries the DEC-015 educational disclaimer and the
    DEC-033 simulated-data notice in the API description.
  - `V1__baseline.sql` — `app_user` + `refresh_token`. Token *hashes* only; `replaced_by` models
    rotation so a replayed old token is detectable as theft rather than merely rejected.
  - `application.yml` — `ddl-auto: validate` (DEC-030: Flyway owns the schema), UTC JDBC timezone.
- `frontend/` — React 19, Vite 8, TS 6. Typed `apiFetch` with `credentials: 'include'` and CSRF
  echo, and deliberately **no localStorage token path** for anyone to reach for.
  `SimulatedDataBanner` exists in Phase 0 before there is a chart to label, because DEC-033
  labelling is a product requirement rather than decoration.
- `docker-compose.yml` — Postgres 17, UTC, healthcheck. `README.md`, `backend/.env.example`.

**Verification — actually run, not assumed:**

| Check | Result |
|---|---|
| `./mvnw -DskipTests package` | BUILD SUCCESS |
| `./mvnw test` (Testcontainers, real Postgres 17) | **3/3 passed**, 41.97s |
| `npm run test:run` | **3/3 passed** |
| `npm run build` | 19 modules, 193 kB |
| `npm run typecheck` | clean |

Backend tests assert: health reports DB up; unknown endpoints 401 by default (DEC-022); the
OpenAPI document publishes (DEC-007). They run on a real Postgres container, so they also prove the
Flyway migration applies and that `ddl-auto: validate` agrees with it — an H2 stand-in would pass
while hiding both.

**Snags hit and fixed:** Initializr's zip extracts flat, not into `backend/`; TS 6 deprecates
`baseUrl`, so `paths` alone; `erasableSyntaxOnly` rejects constructor parameter properties, so
`ApiError` declares fields explicitly; `@testing-library/jest-dom` needs `@testing-library/dom`
as a peer; Docker Desktop's daemon was down mid-session and was restarted before the real test run.

**Not built — do not assume these exist:** auth endpoints (register/login/refresh/logout) and the
JWT filter; any instrument, contract or bar table; the `DEMO-NQ` generator; any chart.

**Handoff:** `NEXT UP` task 2b (auth endpoints against the V1 tables) or task 3 (the `DEMO-NQ`
generator) — **Fabio's call, not the next session's.** Read `DATA_STRATEGY.md` before task 3 and
DEC-034 before any drill work. Claim files before editing.

---

*Next session block goes here.*
