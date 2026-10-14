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
0. **`git pull --ff-only`.** The repo has a shared remote (see `CURRENT STATE`). The other LLM
   may have pushed since your last session, so the copy of this file on disk may be stale until
   you pull. Pulling first is what prevents two divergent histories.
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
4b. **Record any bug you found but did not fix in `BUGS.md`.** If you fixed one, move it to that
   file's Fixed section with the lesson — never delete it.
5. **Commit and push.** Leaving work uncommitted strands it on one machine and guarantees a
   divergence the next session has to untangle. If the push is rejected, run
   `git pull --rebase` and resolve the conflict — **never `git push --force`**, which would
   destroy the other collaborator's work.

**Standing instructions from Fabio (2026-09-17) — permanent, do not ask permission:**
- **Log bugs automatically** in `docs/BUGS.md` after every session and every change. Mark each
  entry *verified* or *reasoned*.
- **Do not fix bugs as you find them.** They are batched into one larger patch later. Log and move on.
- **Write a test for every new feature automatically.** Not optional, never a question.
- **Codex is away from ~2026-09-17 for about a month.** Claude is the only collaborator meanwhile.
  Keep these files current regardless: Codex must be able to resume, and `DOCUMENTATION.md` is read
  by the thesis jury. File claiming stays in force — Codex may return without warning.

**Conventions:**
- Session IDs are sequential: `001`, `002`, ...
- Author is `CLAUDE` or `CODEX`.
- Decision IDs are `DEC-NNN` and are never reused or renumbered.
- Status values: `PROPOSED` → `AGREED` → `IMPLEMENTED` → `SUPERSEDED`.

---

## CURRENT STATE

```yaml
last_session: 017   # in progress — T1–T7 merged; T8 (ICT drawing tools) built on feat/ict-drawings
last_author: CLAUDE
last_updated: 2026-10-14
phase: REAL DATA + ICT CURRICULUM — DEC-041 (ICT trainer, paper trading on real history), DEC-042 (Massive free plan first)
code_written: true
repo_initialized: true
git: true
remote: https://github.com/FabioZetovic/Tradeez   # PRIVATE, created session 007
branch: feat/ict-drawings                         # T8, not merged. master: feat/ny-clock (T7 + BUG-040–046) merged 2026-10-14 as 6d09763; feat/replay-trading (T5) 2026-10-14 as 9ab699a; feat/practice-real-data (T4) 2026-10-14 as c128237; feat/real-nq-data 2026-10-13 as 80014ad (no PR); feat/fvg-drill 2026-09-30 as 2280d37; PR #1 2026-09-29 as d6747e4
head: see git log                                 # updated every session
build_passing: true  # feat/ict-drawings: backend 264/264 (Testcontainers), frontend 215/215 + typecheck + build; knip clean; lint: zero warnings, enforced (--deny-warnings). Fresh-clone check still to do before the merge.
deployed: false      # local only, €0

stack_locked:        # DEC-036 — do NOT bump to Boot 4, it breaks springdoc/DEC-007
  backend:  Spring Boot 3.5.16 / Java 21 (JDK 26 installed, targets 21)
  frontend: React 19 / Vite 8 / TypeScript 6 / Tailwind CSS 4 (DEC-044)
  database: PostgreSQL 17 via docker-compose

files_in_repo:
  # Module map, not a file list. BUG-026: a hand-maintained file list sat six sessions out of
  # date because nothing fails when it is wrong. For individual files run `git ls-files`.
  backend/src/main/java/com/tradeez/:
    common/      # health endpoint
    config/      # SecurityConfig (CSRF, CORS, filter chain), OpenApiConfig
    identity/    # users, JWT cookies, refresh rotation, rate limiting          (sessions 008, 011)
    marketdata/  # instrument registry, DEMO-NQ generator, aggregation, day cache, bars API (009, 015)
      importing/ # shared: ImportLedger, ImportCommand (CLI), ImportReport, ImportChunks     (017)
      databento/ # Databento client + cost-capped importer (DEC-040)                          (017)
      massive/   # Massive client, request pacer, quarterly contracts, volume roll, importer (017, DEC-042)
    charting/    # saved drawings                                              (session 012)
    practice/    # fill engine, sessions, plans, results, replay API          (session 014)
    clock/       # New York clock layer: sessions/opens/levels rules, chart endpoint (017, T7)
    analysis/    # FVG detector, detected_setup, on-demand scanner (DEC-026)  (session 016)
    drills/      # FVG identification drill API — fixtures on simulated data  (session 016)
  backend/src/main/resources/db/migration/: V1 baseline · V2 instruments · V3 token hardening · V4 drawings · V5 practice · V6 setups + drills · V7 real market data · V8 simulated markets withdrawn from the list · V9 practice on real data · V10 several trades per replay · V11 ICT drawing tools (kinds, text, anchor counts)
  frontend/src/:
    lib/api/      # fetch client, auth, market data, drawings, practice
    lib/chart/    # ChartAdapter + Lightweight Charts impl, tick<->price, time axis
    lib/drawing/  # geometry, hit testing, snapping, undo store, renderer; ict.ts = Fibonacci/CE arithmetic (DEC-048)
    lib/chart/labelSpace.ts # places canvas labels without overlap (BUG-048)
    lib/clock/    # New York clock: layer switches, renderer (time placed inside bars)
    lib/practice/ # plan helpers: bracket from the position tool, validation, money/R formatting
    lib/drills/   # candle picking, marks, explanations generated from detector evidence
    components/   # app shell + tabs, sign-in, chart workspace, drawing overlay + toolbar; ui.tsx = shared Button/Segmented/Panel/Chip/Notice
    lib/styles.ts # cx() and shared Tailwind class strings (DEC-044); tokens live in src/index.css @theme
    components/practice/  # journal, replay screen, plan panel, trade result card, trade log + score
    components/drills/    # drill screen and click overlay
    hooks/        # useSession, useDrawings, useDrawingShortcuts, usePracticeSession
  docs/:
    - ANALYSIS_CLAUDE.md    # Claude's deep analysis      (Session 001-CLAUDE)
    - CHATGPT_RESEARCH.md   # Codex's deep analysis       (Session 001-CODEX)
    - DECISIONS.md          # ⭐ MERGED ADR — authoritative (Session 002)
    - DATA_STRATEGY.md      # Zero-cost demo/import data plan (Session 003)
    - ICT_CONCEPTS.md       # ⭐ ICT curriculum spec: every concept, draft rule, teaching mode, data need (Session 017)
    - BUGS.md               # Known defects, limitations, and fixed-bug lessons (Session 009)
    - DOCUMENTATION.md      # Human-readable project history
    - PATH.md               # This file

not_built_do_not_assume:
  - indicators, graded drills (drills exist only as DEC-034 fixtures), any aggregate score or rating

blocked_on: nothing — T8 is built on feat/ict-drawings; next is Fabio's call (merge it; T9 detector framework is the roadmap's next item).

read_order_for_new_session:
  1. docs/PATH.md          # this file — state, claims, next task
  2. docs/DECISIONS.md     # the locked decisions; do not re-litigate
  3. docs/DATA_STRATEGY.md # REQUIRED before touching market data or the drill engine
  3b. docs/ICT_CONCEPTS.md # REQUIRED before writing any detector, drill or lesson (DEC-041)
  4. docs/BUGS.md          # known defects — check before debugging, and before claiming something is broken
  5. docs/ANALYSIS_CLAUDE.md + docs/CHATGPT_RESEARCH.md   # only if you need the reasoning behind a decision
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
5. **Claim in the same commit as your first real change**, with your session id and the date. A
   claim with no work behind it is what an interrupted session leaves lying around (ENV-004). A
   claim older than 24 hours with no matching commit on the remote is stale by definition.

```yaml
claimed_files:      # session 017 (CLAUDE) — T8 ICT drawing tools, on branch feat/ict-drawings (2026-10-14)
  - backend/**
  - frontend/src/**
  - docs/**
  - README.md
```

---

## NEXT UP

Ordered. Take the topmost unblocked item. Roadmap shape is **vertical slice** (DEC-018): thin and
end-to-end first, then thicken. Nothing here is "build a whole layer."

**Done so far** (details in the session log): 1 reconcile the analyses (002) · 2 scaffold (006) ·
2b auth (008) · 3 data slice (009) · 4 chart (010) · 5 drawings, DEC-037 (012) · 6 practice slice
(014) · 6b bug batch (015) · 7 first drill, `fvg-1.0.0` (016) · T1–T4 and T6: real NQ, Tailwind,
practice on real NQ (017, merged) · T5 replay with several trades (017, merged) · T7 New York clock layer (017, merged).

### Unified roadmap — Session 017, 2026-10-04 (DEC-041)

Fabio: the product is **learning ICT by paper trading on real historical charts**. Codex is away, so
Claude builds every task, its own and Codex's (DEC-028 — no fixed ownership). This list merges
Claude's roadmap (`ANALYSIS_CLAUDE.md` §5–§8), Codex's (`CHATGPT_RESEARCH.md` §4–§5, §10), the open
DECISIONS and the ICT curriculum (`ICT_CONCEPTS.md`), ordered by what each task unblocks. Every
milestone ends with something ICT-specific a learner can see (DEC-041).

**Source key:** F = Fabio · C = Claude's analysis · X = Codex's research · D = a decision ·
I = `ICT_CONCEPTS.md`. Old R2–R5 map to T2, T3, T4 and T13.

**M0 — Real charts: paper trading on real NQ history**

| # | Task | Blocked by | Source | Notes |
|---|---|---|---|---|
| T1 | ~~**Massive importer**~~ | — | F, D-042 | ✅ Session 017: `marketdata/massive/` — client (Bearer header, same-host `next_url` only, BigDecimal prices), 5/min pacer with 429 back-off, quarterly contracts, front by previous-session volume, monthly chunks, `NO_DATA` never marked covered. Shared `importing/` package; `--tradeez.import.source=massive` (default). Found and fixed BUG-036. |
| T2 | ~~**Import 2 years of NQ**~~ | — | F | ✅ Session 017: 697,003 real 1m bars, 2024-10-08 → 2026-10-02, 25 months, 9 contracts, $0, ~8 min. Rolls the week before each expiry; prices checked against known events (April 2025 crash low 16,460). Local DB only (`IMPORTED_PRIVATE`). ES moved to T20. |
| T3 | ~~**Real NQ in the app**~~ | — | F, X §4 | ✅ Session 017: V8 withdraws DEMO from the list (still resolves by symbol for Practice/Drills); instruments carry `dataFrom`/`dataTo`; series carry their real `source`; chart opens on the newest data, date picker + Latest/1W/1M/3M/1Y/2Y; New York time axis by default (toggle NY → exchange → UTC). Session separators moved to T7. |
| T4 | ~~**Practice on real data**~~ | — | F, D-025 | ✅ Session 017, **merged 2026-10-14 as `c128237`** (DEC-045): real NQ by default; hidden-date clock (fictional week, NY wall clock, date revealed with the result); moment picker by killzone and weekday; `fill-1.1.0` on minute bars, no gap-means-closed rule; V9 provenance. Played end to end on the real import. |
| T5 | ~~**Replay paper-trading mode**~~ | T4 | F, C §5, X §5 | ✅ Session 017, **merged 2026-10-14 as `9ab699a`** (DEC-046): step / play at 1–8 bars a second / pause; a bracket at any moment, several trades per replay, one at a time, each locked; finished trades drawn on the chart; score in R; End session reveals the seed or the date (V10). |
| T6 | ~~**Bug batch, then merge `feat/real-nq-data`**~~ | — | process | ✅ Session 017, 2026-10-13: BUG-035 and BUG-038 fixed, fresh clone green, **merged into `master` as `80014ad`** (no PR). No open bugs. |

**M1 — ICT on the chart**

| # | Task | Blocked by | Source | Notes |
|---|---|---|---|---|
| T7 | ~~**New York clock layer**~~ | T3 | I-11, I-50–55, X §4 | ✅ Session 017, **merged 2026-10-14 as `6d09763`** (DEC-047): trading-day separators, killzone shading with a labelled strip, Silver Bullet and macro marks, midnight/08:30/09:30/weekly opens, PDH/PDL, PWH/PWL, killzone highs/lows — on the Chart tab and in every replay, switchable. Rules `sessions-1.0.0`, `opens-1.0.0`, `levels-1.0.0`, from minutes, never ahead of the cursor, not across rolls. |
| T8 | ~~**ICT drawing tools**~~ | T3 | I-21, I-42–43, X §4 | ✅ Session 017, 2026-10-14, on `feat/ict-drawings` (**not merged yet**, DEC-048): Fibonacci with ICT's levels (OTE shaded, targets −0.27 to −2), zone with its CE in half-ticks, labels; words typed on the chart; server checks anchor counts and text (V11). |
| T9 | **Detector framework, concept registry, overlay renderer** | T3 | I S-1–S-3, D-026 | Confirmation time on every rule; nothing across session breaks or rolls; overlays never past the replay cursor. |
| T10 | **Imbalances and gaps** | T9 | I-20–28 | Re-measure `fvg` on real NQ (→ `fvg-1.1.0` if it changes); FVG states, IFVG, BPR, volume imbalance, voids, NWOG/NDOG, opening range gap. |
| T11 | **Market structure** | T9 | I-01–06 | Swings, STH/ITH/LTH, structure labels, displacement, MSS, BOS/CHoCH aliases. |
| T12 | **Liquidity** | T11 | I-10–14 | Resting/taken levels, equal highs/lows, sweep vs run. |

**M2 — The learning loop**

| # | Task | Blocked by | Source | Notes |
|---|---|---|---|---|
| T13 | **Drill engine for every detector** | T10–T12 | C §6, D-023, D-034 | Modes A and B, graded on real data; explanations from evidence. Generalises the FVG drill. |
| T14 | **Lesson cards** | T13 | I Part 0 | Text from `ICT_CONCEPTS.md` plus detected real examples. |
| T15 | **Prediction drills** | T13 | C §6.1, I-15–17 | Mode C ("which liquidity is taken first?"); ERL/IRL labels, DOL questions, obstacle counts. |
| T16 | **Learning state and curriculum map** | T13 | C §6.3, I Part 12 | Per-concept rating, spaced repetition, unlocks; real-data attempts only (INVARIANT 8b). |

**M3 — Blocks, ranges, time models**

| # | Task | Blocked by | Source | Notes |
|---|---|---|---|---|
| T17 | **Blocks** | T11, T12 | I-30–37 | Order block, breaker, mitigation, rejection, unicorn. |
| T18 | **Ranges and Fibonacci** | T11 | I-40–44 | Dealing range, premium/discount, OTE, standard deviations. |
| T19 | **Time models and profiles** | T7, T11 | I-53–57, I-62–65 | Silver Bullet and macro windows, IPDA ranges, power of three, Judas swing, daily and weekly profiles. |
| T20 | **SMT divergence** | T2, T11 | I-66 | NQ vs ES. First an ES instrument row (migration) and `ES` in `MassiveImporter.ROOTS`, then import ES like NQ. |
| T21 | **Evidence views** | T10–T19 | I S-6 | How often each concept's claim held on real NQ, with sample size — never framed as an edge. |

**M4 — Models and the exam**

| # | Task | Blocked by | Source | Notes |
|---|---|---|---|---|
| T22 | **ICT plans and model checklists** | T5, T17–T19 | I-60–61, I-70–78, D-027 | Plan fields (model, DOL, bias, killzone); the chosen model's checklist checked at the decision moment. |
| T23 | **Scorecard** | T5 | C §5.5, X §5, D-027 | R, MAE/MFE, process and outcome separately, calibration (confidence vs hit rate). |
| T24 | **Review overlay** | T22 | D-026 | After the trade: what the rules saw at each moment, explained in their own numbers. |
| T25 | **Journal statistics** | T23 | C §1.4, X §5 | By model, killzone, weekday; sample sizes on every figure. |
| T26 | **Management and risk drills** | T23 | D-023 | Modes E and F. |
| T27 | **Fees, commission, slippage** | T5 | X §5 | New fill-model version (DEC-012). |
| T28 | **Partial targets** | T5 | X §5, I-81 | More than one target per plan. |

**M5 — Presentation, and later**

| # | Task | Blocked by | Source | Notes |
|---|---|---|---|---|
| T29 | **Disclaimers, onboarding, UI language** | — | D-015, X, Q-08 | Visible in the app and on a slide. |
| T30 | **BUG-035** | — | BUGS.md | The seven older lint warnings. |
| T31 | **€0 deployment smoke test; jury data rights** | M0 | X, C §4.1, Q-09 | Was task 8. No purchase without Fabio. |
| T32 | **Deeper history and one-second bars** | Fabio's budget decision | D-040 | Databento importer is built; or a month of Massive's paid plan. |
| T33 | **Indicators (VWAP, ATR, EMA)** | T3 | X §4 | Low priority for ICT. |

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
| DEC-038 ⚠️ | A simulated market **is** its seed. The public bars endpoint serves one fixed seed and rejects `seed`; practice seeds are `SecureRandom`, never the public one, and **never leave the server before COMPLETE** — and then as a string (BUG-028). |
| DEC-041 ⚠️ | **Tradeez is an ICT trainer: paper trading on real historical charts.** `ICT_CONCEPTS.md` is the curriculum spec; ICT is the only v1 track; every milestone ships visible ICT content. |
| DEC-045 ⚠️ | **Practice on real data:** times shown on a fictional week (same weekday, NY wall clock, `timeBasis = NEW_YORK_WALL_CLOCK`) — the real date never reaches the client before COMPLETE; fills on the finest bars stored (`fill-1.1.0`); picker by killzone/weekday; `data_source` instead of a seed (V9). |
| DEC-048 | **Three ICT drawing tools** extend DEC-037: Fibonacci (1 at the leg's start, 0 at its end, ICT's levels, OTE shaded), zone (CE in half-ticks), label (≤ 80 chars, own column). Server checks each kind's anchor count; a never-dragged two-point creation is not kept. |
| DEC-047 | **The New York clock layer:** `sessions-1.0.0`, `opens-1.0.0`, `levels-1.0.0` (CME day 18:00–17:00), computed server-side from 1m bars, never past the cursor (a replay re-asks at `nextChange`), no level from a partly seen period or across a roll; drawn on its own canvas under the drawings. |
| DEC-046 ⚠️ | **A session is a replay with several trades, one at a time.** Flat → in trade → flat; each trade numbered and immutable (V10); its result revealed when the replay passes its exit; the seed or date only when the session **ends** (learner ends it while flat, or a flat replay hits its limit); score over closed trades only. |
| DEC-044 | **Tailwind CSS 4 for all styling.** Tokens in `src/index.css` (`@theme`), shared with the chart; `components/ui.tsx` + `lib/styles.ts`; no other CSS. Never give one element two utilities for the same property. |
| DEC-043 | **Default ICT reading = the 2022 mentorship** (FVG entries after sweep + displacement MSS, midnight open); thin topics (blocks, PD matrix, IPDA, weekly profiles) from 2016–17, labelled; `sessions-1.0.0` = index-futures killzones. Every rule names its source. |
| DEC-042 | **Massive's free plan first** (2 years of CME 1m, 5 req/min, per-contract tickers — Tradeez picks the front contract by previous-session volume); Databento later for deep history and 1s. Key `MASSIVE_API_KEY` only in `backend/.env`. |
| DEC-040 ⚠️ | **Real NQ from Databento; the simulated market leaves the app.** Every download priced first, capped ($5/request, $100 total). Key only in `backend/.env`. `IMPORTED_PRIVATE` until display is licensed. |
| DEC-039 | FVG rule `fvg-1.0.0`: consecutive candles, gap ≥ 25% of the prior 14 candles' average range. **Never tune the generator to produce patterns for drills** — refuse the drill instead (1m). |
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
| Q-09 | Before real NQ is deployed, select a provider and obtain **written** confirmation covering classroom use, jury demo, authenticated display, caching and replay. Not a Phase 0 blocker. *(017: Massive's free plan says "individual use".)* | 003 | Fabio + either LLM |
| ~~Q-13~~ | ~~Default ICT reading?~~ **Resolved → DEC-043:** the 2022 mentorship. | 017 | closed |
| ~~Q-14~~ | ~~Killzone table for `sessions-1.0.0`?~~ **Resolved → DEC-043:** index-futures version. | 017 | closed |
| Q-15 | Source of historical economic-calendar dates (news-linked concepts ICT-35, ICT-56). | 017 | either LLM, at Level 6 |
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

### SESSION 007

```yaml
id: 007
author: CLAUDE
date: 2026-09-17
type: INFRASTRUCTURE
code_changed: false
claimed_files: []   # released
```

**Published to GitHub.** The Phase 0 scaffold is now in a **private** repository at
`https://github.com/FabioZetovic/Tradeez`, branch `master`, one commit (`91ac9c9`), 46 files.

Audited before publishing: no `node_modules`, no `target/`, no `dist/`, no `.jar`, and no real
secrets. `backend/.env.example` ships deliberately as an empty template (`JWT_SECRET=` blank).

GitHub CLI 2.101.0 was installed via winget to make this possible. **Fabio authenticated himself**
through the browser device flow — assistants do not handle credentials.

**Protocol changed — this is the part that matters for the next session.** Until now the project
existed in one directory on one machine, so "where is the current version" was trivial. With a
shared remote, two collaborators who never see each other's sessions can each hold a different idea
of the project's state — and `PATH.md`, the file whose whole job is preventing that, is itself one
of the files that can go stale. A stale `PATH.md` is worse than none, because it looks
authoritative.

So the protocol now brackets every session:

- **Step 0: `git pull --ff-only` before reading anything.**
- **Step 5: commit and push before finishing.** A rejected push is rebased. **Never force-push** —
  it would silently destroy the other collaborator's work.

`CURRENT STATE` now records `remote`, `branch` and `head` so a fresh session knows the repository
is shared before touching anything.

**Files modified:** `docs/PATH.md` (protocol steps 0 and 5, remote in CURRENT STATE),
`docs/DOCUMENTATION.md` (collision rule + Session 007 narrative), `README.md` (Repository section
with clone command). No application code, dependency, schema or API changed.

**Handoff:** unchanged — `NEXT UP` task 2b (auth endpoints) or task 3 (`DEMO-NQ` generator),
**Fabio's call.** Pull first.

---

### SESSION 008

```yaml
id: 008
author: CLAUDE
date: 2026-09-17
type: FEATURE — NEXT UP task 2b
code_changed: true
claimed_files: []   # released
```

**Auth endpoints built. DEC-032 is now implemented, not just structured.** 19/19 backend tests
pass against a real PostgreSQL container.

**Endpoints:** `POST /api/auth/{register,login,refresh,logout,logout-all}`, `GET /api/me`.

**New package `com.tradeez.identity`:** `AppUser`, `RefreshToken`, two repositories,
`JwtService`, `JwtAuthenticationFilter`, `CookieService`, `AuthService`,
`AuthController`, `MeController`, `AuthExceptionHandler`, `JwtSecretGuard`.

**Design points worth not re-litigating:**
- Access tokens are short-lived JWTs (15m); **refresh tokens are opaque random strings, not JWTs** —
  they hit the database anyway, so self-describing tokens would leak claims for no benefit.
- Refresh tokens are stored as **SHA-256 hashes**, never plaintext. Not BCrypt: the value is
  high-entropy random rather than human-chosen, so it is not brute-forceable, and it must be
  deterministic to look up by hash in one indexed query.
- **Both cookies carry the refresh lifetime**, not the access lifetime. Looks wrong, is deliberate:
  the JWT inside expires in minutes but the cookie must outlive it so the browser keeps sending it
  and the SPA can call `/refresh`. A cookie expiring with its JWT makes silent refresh impossible.
- Login hashes a throwaway password when the account does not exist, so **timing** does not leak
  what the identical error message withholds.
- `logout` is **not** CSRF-exempt. Only `login`, `register` and `refresh` are.

**⚠️ Two real bugs found by the tests — read this before writing similar code:**

1. **The reuse revocation was being rolled back.** `refresh()` is `@Transactional`; it revoked
   every token for the account and then threw `TokenReuseDetectedException`. The throw rolled the
   transaction back and **undid the revocation** — the attacker got a 401 while the victim's
   sessions quietly stayed alive. The security response was a no-op that looked correct from
   outside. Fixed with `noRollbackFor`, which is load-bearing rather than a workaround.
   **Any future "record something, then reject the request" path has this same trap.**
2. `deleteAll()` trips the self-referencing `replaced_by` foreign key, because it issues one
   DELETE per row. `deleteAllInBatch()` defers the constraint check to statement end.

**`JwtSecretGuard`** refuses to start the app when `cookies.secure=true` and the committed
development JWT secret is still in use. The default secret is public — it is in this repository.

**Frontend:** `src/lib/api/auth.ts` — `register`, `login`, `logout`, `getMe`,
`restoreSession`. Exports no `getToken`/`setToken` and never touches `localStorage`;
"am I signed in?" is answered by asking the server. No UI yet — that comes with the first screens.

**Verification:** backend 19/19 (Testcontainers) · frontend 3/3 · build · typecheck. All green.

**Not built:** login/register UI, rate limiting on auth endpoints (noted in
`AuthExceptionHandler` — registration inevitably reveals that an address is taken, and rate
limiting is the mitigation, not vagueness), email verification, password reset.

**Handoff:** `NEXT UP` task 3 — the `DEMO-NQ` generator. Read `DATA_STRATEGY.md` first.
Pull before starting.

---

### SESSION 009

```yaml
id: 009
author: CLAUDE
date: 2026-09-17
type: FEATURE — NEXT UP task 3
code_changed: true
claimed_files: []   # released
```

**DEMO-NQ generator + bars API. 51/51 backend tests pass.**

**New:** `V2__instruments.sql` (instrument registry + `data_class` enum, seeds DEMO-NQ/DEMO-MNQ),
package `com.tradeez.marketdata` — `DataClass`, `Timeframe`, `Bar`, `BarSeries`,
`SessionCalendar`, `BarAggregator`, `DemoSeriesGenerator`, `MarketDataProvider`,
`GeneratedMarketDataProvider`, `Instrument`, `MarketDataService`, `MarketDataController`.

**Endpoints:** `GET /api/market/instruments`, `/instruments/{symbol}`,
`/instruments/{symbol}/bars?timeframe=&from=&to=&seed=`. Authenticated (deny-by-default).

**Design points — do not undo these:**
- **Nothing is persisted.** The series is a pure function of `(seed, range)`, so storage would
  only be a cache that could disagree with the generator after a version bump. A full day is 86,400
  one-second bars; regenerating is cheap.
- **Always generate at 1s and aggregate up**, never generate the target timeframe directly. This is
  what guarantees a 5m candle agrees with the 1s bars inside it — and the fill engine walks those 1s
  bars (DEC-008), so disagreement would produce outcomes contradicting the user's chart.
- **Each second is seeded from its absolute timestamp**, not from running state. Asking for
  14:10–14:15 must not depend on whether 14:00 was requested first, or a replay resumed after a
  reconnect would diverge. There is a test.
- **Aggregation buckets align to the epoch**, not to the first bar, so two clients requesting
  different start times draw the same candles.
- **Gaps produce no bar.** A closed market is real information; a flat filler candle would be
  invented price action a detector could then "find structure" in.
- DEMO-NQ carries **real NQ economics** ($20/pt, 0.25 tick = $5) with exchange `DEMO`, never `CME`.
- `MAX_BARS_PER_REQUEST = 20_000`: without it a year of 1s data is ~31M bars in one response.

**Bug found and fixed (BUG-011):** regimes were selected with
`new Random(seed*7919 + block).nextInt(4)`. Blocks are consecutive by construction and
consecutive LCG seeds give correlated first outputs, so volatility barely varied — the busiest block
was only 1.6x the quietest. Replaced with a splitmix64 avalanche. **Generator version bumped to
`demo-gen-1.1.0`** because output changed (DEC-012).

**Also logged:** BUG-009 (demo volume is meaningless), BUG-010 (no caching), BUG-012 (a test that
failed against correct code — statistical assertions need an adequate sample).

**Verification:** backend 51/51 (Testcontainers). 21 new tests: determinism, bar integrity,
sessions, regime shape, aggregation, and the HTTP API.

**Not built:** no chart yet — this is the data behind one. No CSV import provider. No persistence
for imported data.

**Handoff:** `NEXT UP` task 4 — render these bars through `ChartAdapter` with Lightweight
Charts. Pull first.

---

### SESSION 010

```yaml
id: 010
author: CLAUDE
date: 2026-09-17
type: FEATURE — NEXT UP task 4
code_changed: true
claimed_files: []   # released
```

**The chart works. Verified in a real browser, not just in tests.**

**New (frontend):** `lib/chart/types.ts` (`ChartAdapter`), `lib/chart/priceFormat.ts`,
`lib/chart/lightweightAdapter.ts`, `lib/api/marketData.ts`, `hooks/useSession.ts`,
`components/{PriceChart,SignInForm,ChartWorkspace}.tsx`, `test/fakeChartAdapter.ts`.
Lightweight Charts v5 added. Auth UI built because the market API is authenticated — the chart
could not fetch anything without it.

**Design points:**
- `lightweightAdapter.ts` is the **only** file importing the charting library. Everything else
  talks to `ChartAdapter` (DEC-004).
- That abstraction paid off immediately, not eventually: jsdom has no working canvas, so
  `FakeChartAdapter` is the only reason the components are testable at all.
- `priceFormat.ts` is the single tick→price boundary (INVARIANT 3). Nothing downstream of the
  adapter sees a tick count.
- The demo window is anchored to a **fixed** epoch, not `Date.now()`. The generator is a pure
  function of time, so anchoring to "now" would show different data every reload and no two people
  could discuss the same candle.

**⚠️ BUG-015 — the one that matters.** The chart opened at **minus 417,000 index points**. The
generator's linear drift multiplied `days`, and `days` counted from the **Unix epoch** —
~19,800 by 2024 — not from the start of the series. **Fourteen passing tests did not catch it**,
because every one compared prices *to each other* (high ≥ low, open in range) and all of those hold
fine on a series centred on −417,000. Fixed with a bounded seasonal sine; generator bumped to
`demo-gen-1.2.0`; a price-sanity test added across four dates and three seeds.

**Also fixed:** BUG-016 (`PriceChart` remounted on every parent render — `adapterFactory` was
in the effect deps; during replay that would discard zoom/pan on every bar).

**Also logged:** BUG-013 (Vite's `changeOrigin` forwards the browser Origin, so a non-5173 dev
port needs a matching `CORS_ALLOWED_ORIGINS` or every call 403s), BUG-014 (chart axis is
UTC-only), ENV-002 (Docker Desktop occupies 8080 **and** 5173 on this machine).

**Running it locally here:** `PORT=8081` for the backend and a matching
`CORS_ALLOWED_ORIGINS`; Vite now reads `process.env.PORT`. See ENV-002.

**Verification:** backend 52/52, frontend 22/22, build + typecheck clean. Driven in a real browser:
registered, signed in, chart rendered, timeframe switching refetches.

**Not built:** no drawing tools (task 5 — the decision gate), no replay, no indicators. The chart is
read-only.

**Handoff:** `NEXT UP` task 5 — the drawing proof. **Five tools only** (HLine, trendline,
rectangle, R:R position) plus select/drag/snap/persist/undo, then stop and decide whether to
continue custom or switch to KLineChart. DEC-004 calls this the largest schedule risk in the
project. Pull first.

---

### SESSION 011

```yaml
id: 011
author: CLAUDE
date: 2026-09-18
type: BUG-FIX BATCH
code_changed: true
claimed_files: []   # released
```

**Eight of nine open bugs fixed. Backend 60/60, frontend 27/27.**

| Bug | Fix |
|---|---|
| BUG-001 🔴 | 20s grace window on refresh reuse + `@Version` optimistic lock |
| BUG-002 🟠 | Per-IP filter (20/5min) + per-account throttle (10/15min), reset on success |
| BUG-003 🟡 | Health check logs the cause; body still leaks nothing |
| BUG-004 🟡 | `V3` adds the `replaced_by` index |
| BUG-005 🟡 | JSONB `settings` now has a round-trip test |
| BUG-009 🟡 | **Not a defect** — moved to Known limitations. There is no real volume to generate |
| BUG-013 🟡 | Dev CORS accepts any localhost port; deployed keeps the explicit list |
| BUG-014 🟡 | Axis renders exchange-local with a UTC toggle, DST-correct via `Intl` |

**Still open — BUG-010 (no caching), left alone deliberately.** A cache must be keyed on
`(seed, range, timeframe, generatorVersion)` or it serves stale prices after a version bump,
and that version has already changed twice. Measure when the replay engine actually requests
overlapping ranges; do not add the machinery on a guess.

**⚠️ Five NEW bugs, all found by doing the work. Four were bad tests.**
- **BUG-017** — the new rate limiter broke *eleven* unrelated tests. It is an application
  singleton keyed by IP, every MockMvc request shares an address, so auth tests spent the quota
  and later classes got 429. Fixed with a reset in `AbstractIntegrationTest`.
- **BUG-018** — fixing BUG-001 turned `reuseOfRotatedTokenRevokesTheWholeFamily` red. **The
  test had encoded the bug**: it replayed a token immediately and demanded a mass logout. It now
  ages the rotation past the grace window.
- **BUG-019** — `forgedTokenIsRejected` was silently flaky. It flipped the JWT's last
  character, but a 32-byte HMAC is 43 base64url chars whose last one carries 4 significant bits
  plus 2 of padding — so `A` and `B` **decode identically**. It passed on luck.
- **BUG-020** — the eviction test failed against correct code: Windows' ~1ms clock granularity
  meant 500 windows with a 1ms expiry had not expired yet.
- **BUG-021** — toggling the timezone left the chart **blank**. The remount created a fresh chart
  and the data effect did not re-run. Caught by clicking, not by the suite.

**Verification:** backend 60/60, frontend 27/27, build + typecheck clean. Driven in a browser on
port 49900 **with no CORS override**, which is the real proof of the BUG-013 fix; timezone toggle
switches CDT↔UTC and the chart stays populated.

**Handoff:** `NEXT UP` task 5 — the drawing proof. **Five tools only** (HLine, trendline,
rectangle, R:R position) plus select/drag/snap/persist/undo, then a decision gate on continuing
custom versus switching to KLineChart. DEC-004 calls it the largest schedule risk here. Pull first.

---

### SESSION 012

```yaml
id: 012
author: CLAUDE
date: 2026-09-18
type: FEATURE — NEXT UP task 5
code_changed: true
claimed_files: []   # released
```

**The drawing layer works, and the DEC-004 gate is passed → DEC-037: keep building by hand.**
Backend 71/71, frontend 67/67. Verified in a browser end to end.

**Four tools + the machinery:** horizontal line, trend line, rectangle, position (risk/reward).
Select, drag, reshape by handle, magnet snap to OHLC, undo/redo, delete, persistence.

**New (frontend):** `lib/drawing/{types,geometry,store,snap,render}.ts`,
`components/{DrawingOverlay,DrawingToolbar}.tsx`, `hooks/useDrawings.ts`,
`lib/api/drawings.ts`. **New (backend):** `V4__drawings.sql`, `com.tradeez.charting`.

**Design points — do not undo these:**
- **Anchors are (time, priceTicks), never pixels.** Pixels depend on zoom and window size; a
  drawing stored in them slides off its level the moment either changes. DEC-024 adds the rest:
  raw prices, so a later contract roll cannot shift every line the user drew.
- **The overlay is transparent to the pointer unless there is something to interact with.** This
  is the crux of putting a canvas over a chart — if it always captured events, panning and zooming
  would stop working. It hit-tests via events bubbling to the shared parent while staying
  transparent itself.
- **An overlay canvas, not a chart plugin.** A plugin would tie every tool to Lightweight Charts
  and make DEC-004's escape hatch theoretical. The whole layer needs only four projection
  functions from `ChartAdapter`.
- **Undo is snapshots, not inverse operations.** Inverse operations are where undo implementations
  rot: every new tool needs its own, and the one nobody wrote corrupts the document.
- **Drag emits `preview` (no history); pointerup emits `update` (one entry).** Otherwise a
  single gesture takes fifty presses of undo to reverse.

**⚠️ BUG-022 — a real security hole, caught by its own test.** Drawing ids are client-assigned
UUIDs in the URL. The repository lookup was owner-scoped, which looked sufficient and was not:
because the id is assigned, `save()` routes through `merge()`, which matches on the
**primary key alone** and rewrote the existing row including `user_id`. One user could take
ownership of any drawing whose id they could name. Fixed; refused with 404 rather than 403 so the
endpoint cannot be used to probe which ids exist.

**Also fixed:** BUG-024 (`created_at` null — same merge root cause), BUG-025 (unknown kind was
a 500). **Logged:** BUG-023 (`useDrawings.commit` swallows save failures — it hid a
completely broken save path for this entire session), ENV-003 (a failed `spring-boot:run`
leaves the OLD backend serving, which cost ~20 minutes chasing a 401 in code that had just passed
71 tests).

**Verification:** drew a horizontal line in a browser, confirmed `priceTicks: 78832` in
PostgreSQL, reloaded the page and watched it come back at 19708.00 — which is exactly
78832 × 0.25. The position tool reads **"long · 2.00R · risk $200.00 · reward $400.00"** from the
instrument's real tick value.

**Not built:** no text tool, no Fibonacci, no style editing, no multi-select. Deliberate — DEC-037
keeps the scope at four shapes.

**Handoff:** `NEXT UP` task 6 — **the Practice slice, which is the actual product.** Blind
replay → immutable plan → one bracket trade → deterministic or `AMBIGUOUS` result → journal
row. Read DEC-008, DEC-009, DEC-011 and DEC-031 before starting. The position tool is already the
trade-entry control; reuse it rather than building a second one. Pull first.

---

### SESSION 013

```yaml
id: 013
author: CLAUDE
date: 2026-09-18 → 2026-09-28
type: INTERRUPTED — no code written
code_changed: false
claimed_files: []   # the claim this session made was stale and has been released
```

**Started task 6 and was cut off.** Claimed files for the Practice slice, then hit Fabio's usage
limit before writing any code. Nothing from this session reached the repository.

**The stale claim** stayed behind in the working tree and was reverted during a later exchange
— without being noted, against rule 4 of the claiming protocol. Recorded now as **ENV-004**.

**A PR was requested and could not be opened.** Every commit from Sessions 001–012 was pushed
straight to `master` as the protocol directs, so there was no branch holding anything
`master` lacked. Consequence for the next session: **task 6 goes on a branch**
(`feat/practice-slice`) so it can be reviewed as a pull request.

**Audit, at Fabio's request.** BUGS.md and DOCUMENTATION.md were accurate for all the code but
missed this session entirely, and `CURRENT STATE.files_in_repo` had been wrong since
Session 006. Fixed as **BUG-026** — replaced with a module map.

**Handoff:** task 6, on `feat/practice-slice`.

---

### SESSION 014

```yaml
id: 014
author: CLAUDE
date: 2026-09-28
type: FEATURE — task 6, the Practice slice
branch: feat/practice-slice
code_changed: true
tests: backend 71 → 118, frontend 67 → 92
claimed_files: []   # released
```

**Built task 6 — the loop the product exists for.** A session opens at a random, hidden moment in
a simulated market with four days of history. The learner places a bracket with the position tool
(entry pinned to the market price, stop and target dragged on the chart or typed in), locks it,
and steps the replay forward — one bar, ten, play, or skip to the result. The result card and a
journal row record what happened, and neither can be changed.

**Before any practice code: BUG-027 → DEC-038.** The public bars endpoint accepted a `seed`. A
simulated market is a pure function of its seed, so once practice sessions existed, anyone holding
a session's seed could read its future from outside the practice API. The endpoint now serves one
fixed market and rejects `seed` with 400; practice seeds are `SecureRandom`, never the public one,
and withheld until COMPLETE.

**Backend (`com.tradeez.practice`, `V5__practice.sql`).**
- `FillEngine` (DEC-008): walks one-second bars; gaps fill at the open; stops slip one tick;
  commission per contract; flat at the session close; `AMBIGUOUS` when one second spans both
  levels. Checked against an **independent naive oracle** on 2,000 random brackets over generated
  data — 1,925 decided, 29 ambiguous even at one-second resolution. That ratio is the evidence for
  DEC-008's claim that finer data narrows ambiguity without removing it.
- No lookahead, **by construction**: visible bars are generated only up to the cursor, so nothing
  past it exists in memory to leak. The one place future data is generated — resolving a locked
  plan — produces a result row and nothing else.
- Outcome written once (INVARIANT 2): computed when the plan locks, withheld until the cursor
  passes the exit. The replay is purely a reveal.
- Plans and results are immutable in three layers: no setters, `updatable = false`, and a
  database trigger that refuses UPDATE. Tested by going around the API with raw SQL.
- Owner-scoped everywhere (another user's session is a 404 identical to a missing one),
  optimistic-locked, 60 sessions/user/hour, replay bounded to 3 days past its start. Advancing
  steps over closed market; a plan is refused while the market is closed.

**Frontend.** Chart | Practice tabs; `components/practice/*`; `usePracticeSession` (server owns
everything, one request at a time so a double click cannot earn a 409); the chart now **extends the
series in place** during a replay instead of refitting, and frames the last 120 bars with room on the
right for the bracket. Keyboard shortcuts moved into `useDrawingShortcuts`, shared with the chart tab.

**Found in the browser, not by the suites:**
- **BUG-028 🔴 (fixed)** — the revealed seed was a JSON number; JavaScript rounded it
  (`…440752` → `…440600`), so the reproduction key on the result card regenerated a *different*
  market. Every backend test passed because Java reads a long back exactly. Now a string, with
  tests on both sides using a seed above 2^53.
- **BUG-029 🟠 (open)** — the time axis draws UTC while the crosshair draws exchange time. BUG-014's
  fix set only the crosshair formatter.
- BUG-030–032 🟡 (open) — test canvas stub never installs; title nearly invisible; 1px horizontal
  scroll. BUG-033 🟡 — practice regenerates data inside a transaction (measure before fixing).

**Verified in a browser:** registered a throwaway local account (recorded in `backend/.env.example`),
started a session, confirmed over the wire that no seed, no result and no bar at or past the cursor
was sent, locked a long, stepped forward, and was stopped out: −41 ticks (stop minus one tick of
slippage), −$207.50 net including $2.50 commission, −1.02R — all checked by hand.

**`.claude/launch.json`** gained a `tradeez-backend` entry (port 8081 — see ENV-002).

**Handoff:** merge the `feat/practice-slice` PR. Then Fabio chooses: the **bug batch** (6b —
BUG-029 first) or task 7, the first drill. Pull first.

---

### SESSION 015

```yaml
id: 015
author: CLAUDE
date: 2026-09-29
type: BUG BATCH — task 6b, at Fabio's request before the practice PR is pushed
branch: feat/practice-slice
code_changed: true
tests: backend 118 → 132, frontend 92 → 112
claimed_files: []   # released
```

**Every open bug closed** before the practice branch goes back up. Each entry moved to *Fixed* in
`BUGS.md` with its original text, a ✅ block, and the test that holds it.

| Bug | Fix | Proof |
|---|---|---|
| BUG-029 🟠 axis in UTC | `timeScale.tickMarkFormatter` in exchange time; day ticks read e.g. *Wed 18:00* (they sit at UTC midnight — see limitation) | adapter test with mocked renderer; real library in the browser |
| BUG-023 🟠 silent save failures | `syncError` + *Retry now*; retry saves before it reloads | 6 hook tests; forced failure in the browser |
| BUG-033 🟡 generation inside transactions | snapshot → generate with no tx → short write that 409s if the version moved | spy asserts no tx during any generation; injected race → 409, nothing written |
| BUG-010 🟡 no caching | `DayBarCache`, aligned ranges only, so it cannot leak past a cursor | bar-for-bar equality with generation; resume 27–33 ms |
| BUG-030 🟡 canvas stub | unconditional | warnings 55 → 0 |
| BUG-031 🟡 invisible title | app owns `--text-h` (also fixed invisible `<code>`, i.e. the seed) | computed colour in light mode |
| BUG-032 🟡 1px sideways scroll | `.shell` gets a definite width; `margin: auto` in a column flexbox had disabled stretch | reproduced at 961 px, then 946 = 946 |

**Environment.** ENV-004 → protocol rule 5 above. ENV-003 → `/api/health` reports `startedAt` and
`builtAt`. ENV-001 → integration tests fail with "Docker is not reachable … not a code failure"
instead of `ExceptionInInitializerError`. ENV-002 → `launch.json` carries the workaround. The Docker
behaviour itself is the machine's, and stays documented.

**Measured before caching (BUG-010's own condition):** four days of history = ~169,000 one-second
bars, ~105 ms to generate + ~22 ms to aggregate, on every create and resume.

**Two things worth knowing next time:**
- The browser pane only renders while it is displayed. Hidden, `requestAnimationFrame` and
  `ResizeObserver` stop, so the chart keeps stale sizes and cannot hit-test clicks — verify with
  DOM reads, or mount the adapter directly on the real library, as was done for BUG-029.
- Vitest runs with `css: false`, which turns even `?raw` CSS imports into empty strings. Read CSS
  from disk in tests (`styles.test.ts`).

**Double-check before merging (Fabio's request), and what it found.**
- **Verified from a fresh clone** of the branch, not the working copy: backend `clean verify`
  (tests + jar), frontend `npm ci` + typecheck + tests + build. The working copy had stale compiled
  classes from a deleted file; a clone cannot.
- **Lint had never been run.** `npm run lint` (oxlint) exists and nobody had used it. `master` has 7
  warnings; the PR had added 7 more — values copied into refs during render, state set inside
  effects, an effect with deliberately partial deps. All 7 fixed by removing the pattern, not by
  silencing it: the practice screen now *derives* the displayed bracket instead of syncing it into
  the drawing store with two effects; the price box keeps a draft only while typing; drawing errors
  are tagged with their symbol. The 7 pre-existing ones are **BUG-035**, deferred with the reason.
- **BUG-034 🟠 — found in the browser, fixed.** Reloading after the access token expired signed the
  user out: the app fired two refreshes of one token at once, and the second failed on the token's
  version column. BUG-001 had only covered the sequential case. The refresh endpoint now retries
  once after losing that race. Both new tests fail without the fix; in the browser, 9 simultaneous
  refreshes all returned 200.
- CodeRabbit (AI review app on the repo) reviewed the practice commits with no inline findings.

**Merged.** PR #1 went into `master` on 2026-09-29 as `d6747e4`, a merge commit that keeps the five
branch commits. It was pinned to the verified head (`--match-head-commit`), and the merged tree was
confirmed identical to that commit. CodeRabbit's review of the final commit was still waiting on
the free plan's hourly limit; paying to skip the wait was not an option (€0 rule). Then, on
`master`: this note and a README brought up to date. Its "home page shows backend status" line
dated from Phase 0, and its layout map predated the chart.

**Handoff:** work from `master`. Fabio chooses next: task 7, the first drill, is in line; BUG-035
is the only open bug. New task work goes on a branch and through a PR, as task 6 did. Pull first.

---

### SESSION 016

```yaml
id: 016
author: CLAUDE
date: 2026-09-29 → 2026-09-30   # interrupted twice by the usage limit; no work was lost
type: FEATURE — task 7, the first drill
branch: feat/fvg-drill           # pushed; NOT merged, no PR — Fabio: "just continue, don't PR"
code_changed: true
tests: backend 132 → 164, frontend 112 → 131
claimed_files: []   # released
```

**Built task 7: FVG detector → `detected_setup` → identification drill.** Decisions in **DEC-039**.

- **`com.tradeez.analysis`** — `FvgDetector` (pure, rule `fvg-1.0.0`, integer ticks, threshold
  relative to recent volatility), `DetectedSetup`, and `SetupScanner`, which scans a stretch on
  demand and stores every hit with its rule version, parameters, evidence, seed and generator
  version. The insert ignores duplicates, so rescanning and concurrent scans are safe.
- **`com.tradeez.drills`** — `POST /api/drills` picks a random 30-candle window of unbroken trading
  with 1–6 gaps; the answer key (`drill_setup`) is withheld until `POST /api/drills/{id}/answer`,
  which is accepted once and returns found / missed / extra with each gap's evidence.
- **V6** — `detected_setup`, `drill`, `drill_setup`. DEC-034 as a CHECK (a simulated drill can never
  be graded); an answered drill is immutable by trigger.
- **Frontend** — a **Drills** tab: fixture notice first, the versioned rule, the chart with a click
  overlay (clicks read from the frame, so panning still works), and explanations generated from the
  evidence. `SessionCalendar.isOpenThroughout` is now shared with practice.

**Measured before choosing.** A fixed 4-tick minimum flagged every fourth 5m candle and almost no 1m
ones; relative to volatility the rule gives 3–7 gaps per 30 candles at 5m, 1.5–3 at 15m. 1m drills
are refused rather than tuning the generator (DEC-034).

**Verified** — detector against an independent oracle on 5 days of 5m and 15m (exact agreement);
scanner stores exactly the detector's hits, once; live: drill created in 0.9 s cold / 0.1 s warm,
no answer key on the wire, an empty answer and a perfect answer (2 of 2 from the database's key)
both correct, second answer 409, and the live database refused both grading and editing an answered
drill.

**Next time:**
- The browser pane lays out at **zero width while hidden**, so clicks cannot be aimed at a candle
  there. The click-marking path is proven by component tests (fake adapter) and one live click;
  aiming at specific candles needs the pane displayed.
- A reload signed in with two 401 refreshes. The log showed no reuse detection and no error — the
  pane had been restarted between sessions and had no refresh cookie. Not a bug; BUG-034's fix was
  re-verified live the same day.
- ENV-001 recurred: the backend failed to start ("Connection to localhost:5432 refused") until
  Docker Desktop was restarted.
- A component test clicked before the chart had handed the overlay its adapter (a second render);
  the test now waits for the overlay to draw. A person cannot click that early.

**Handoff:** task 7 is on `feat/fvg-drill`, pushed, **not merged**. Fabio chooses: merge it (via a
PR or directly), task 8, or more drill types. BUG-035 is the only open bug. Pull first.

**Merged — 2026-09-30.** Fabio said "merge it". Merged locally with `--no-ff` as `2280d37` (no PR, as
asked), after a re-check from a fresh clone of the branch: backend 164/164, frontend 131/131, and a
live run with real clicks on the real chart through the adapter's projection (5 of 5 found, the
extra mark named). `master` had not moved since the branch point, and the merged tree was confirmed
identical to the verified branch. BUG-035 stays open, deferred for its stated reason. Next: Fabio
chooses — task 8 (€0 deployment smoke test) or more drill types. Pull first.

---

### SESSION 017 — IN PROGRESS

```yaml
id: 017
author: CLAUDE
date: 2026-09-30
type: FEATURE — real NQ data (DEC-040)
branch: feat/real-nq-data        # not merged
claimed_files: see IN PROGRESS above — held, the work continues
```

**Fabio rejected the simulated market**: the product needs real historical NQ, ten years back to
recent months. Options researched (Databento, FirstRate Data, Yahoo; Kaggle dump and Dukascopy
rejected); Fabio chose **Databento** and **removing the simulated market from the app** (DEC-040).

**Done (R1), none of it needing the key:** `backend/.env` is now actually loaded (it never was);
`DatabentoClient` (cost + CSV bars, key only in the auth header); `DatabentoImporter` (monthly /
daily chunks, priced before download, $5/request and $100 total caps, idempotent, off-grid prices
rejected); V7 (`market_bar`, `market_data_import`, instrument `NQ` as CME / `IMPORTED_PRIVATE`,
`instrument.listed`); `ImportedMarketDataProvider`; `ImportCommand` for the command line. Tests run
against `FakeDatabento` (JDK HTTP server) — never the real account. Backend 164 → 183.

A test caught a real bug before commit: the CSV parser wrapped the *consumer's* exceptions too, so an
off-grid price surfaced as "unreadable row". Fixed.

**Waiting on Fabio:** a Databento account and `DATABENTO_API_KEY=…` in `backend/.env` (not in
chat). If sign-up asks for a payment card, stop and decide first. Then: dry run → import → R3–R5.

**2026-10-04 — Databento sign-up asks for a card.** Their stated policy: the card verifies the
account and is billed only beyond the $125 credit. Fabio asked for card-free options first ("one step
at a time"). Checked: **Finnhub** (no CME futures; intraday candles not on the free plan),
**Marketstack** (stocks/indices, intraday is IEX stocks only), **Nasdaq Data Link** (CHRIS continuous
futures deprecated, daily only), **Alpha Vantage** (no futures), **Yahoo** (`NQ=F` minutes only
~7 days back, intraday ~60 days), **IEX Cloud** (shut down 2024-08-31). Only **Massive** (formerly
Polygon.io) fits: free Futures Basic, CME minute bars, 2 years back, 5 calls/min, per-contract tickers
(`NQZ5`, no continuous symbol), individual use. Second bars from $29/month; its history starts
2017-04-03 even on the top plan. Recommended: Massive free now for 2 years of real NQ, Databento later
for 5–10 years. Awaiting Fabio's choice.

**2026-10-04 — Fabio chose Massive and re-centred the product on ICT.** He signed up for Massive's free
plan (DEC-042). He also pasted the API key into the chat. Claude did **not** write it to
`backend/.env`: a key that has appeared in a chat transcript should be treated as exposed, so Fabio
was asked to regenerate it in Massive's dashboard and put the new one in `backend/.env` himself. The
key is recorded nowhere in the repository or these docs.

Fabio then said the project had drifted from its idea — *"an app that will help people trade ICT
concepts"* by *"paper trading charts from long time ago"* — and asked for (1) a detailed document of
every ICT concept, (2) one task list covering Claude's and Codex's work, sorted for viability, and (3)
a plan for teaching ICT. Delivered: `docs/ICT_CONCEPTS.md` (≈70 concepts in ten levels, each with
meaning, draft versioned rule, detectability class D/R/J, confirmation time, teaching modes and data
needs; Part 13 maps concepts to work), DEC-041 and DEC-042, and the unified roadmap T1–T33 in NEXT UP.
Time windows and gap definitions were cross-checked against community summaries; where versions
disagree, the doc says so and versions the choice (Q-13, Q-14). New open questions Q-13–Q-15.

No code changed in this part of the session. Next: T1 (Massive importer) on Fabio's go.

**Later the same day — Fabio approved the roadmap and the default reading.** Asked to elaborate on "the
2022 mentorship as the default", Claude compared the 2016–17 and 2022 material (market, style, entry
tool, access) and listed what changes per topic. Fabio: *"yeah this all sounds good now. The project
path is good."* Recorded as **DEC-043**; Q-13 and Q-14 closed. The roadmap T1–T33 stands as written.

**T1 — the Massive importer, built.** Fabio said "i pasted the key in there so we are good to go". The
key was not in `backend/.env` but in `backend/.env.example` — the committed template (ENV-005). Not
committed; the docs commit staged files by name. Claude read it into a shell variable, never printed,
for five probing requests that settled what the docs left open: Bearer-header auth works, `next_url`
is a cursor without the key, session bars carry per-contract volume, prices are quarter-point JSON
decimals, and **a date outside the 2-year window returns 200 with no results** — so an empty month must
never be marked done.

Built: a shared `importing/` package (ledger, CLI command choosing the source, report, chunks,
off-grid exception) that the Databento importer now uses too, and `marketdata/massive/`:
`MassiveClient`, `RequestPacer` (5 requests per 61-second window, 429 back-off), `QuarterlyContract`
(`NQZ4`-style tickers, third-Friday expiry, decade collisions refused), `RollSchedule` (front =
highest previous-session volume, ties to the nearer expiry), `MassiveImporter`. Tests: `FakeMassive`
(paging, opening-date session index, errors on demand), `MassiveClientTest` 11, `MassiveImporterTest`
9 (a March→June 2025 roll across a month boundary), `RollScheduleTest` 9, `RequestPacerTest` 4,
`ImportCommandTest` 2, `InstrumentSessionTimesTest` 2, `EnvExampleTest` 1. Backend 183 → 221.

**BUG-036, found by the new tests and fixed now** (it blocked T1's correctness, so it could not wait for
a batch): the JVM was never in UTC despite `application.yml` saying so, and Hibernate shifted `TIME`
columns by the host offset — NQ's 17:00 open read as 18:00. `TradeezApplication.useUtc()`.

**Waiting on Fabio:** move the key to `backend/.env` (regenerated, since the first one was in chat),
remove it from `.env.example`. Then T2: dry run, import, and the empty `MASSIVE_API_KEY=` line with a
comment goes into `.env.example`.

**T2 — two years of real NQ, imported.** Fabio said the regenerated key was in place; it was in
`backend/.env.example` again (byte-identical to before, so it was already the regenerated key), and
`backend/.env` did not exist. That key had never been in chat, so Claude moved the line into
`backend/.env` with a shell pipe (never displayed) and restored the template; the template now carries
an empty `MASSIVE_API_KEY=` with a comment saying where the real key goes. Dry run: 25 months, $0,
contract switches in the third month of each quarter. Import: **697,003 one-minute bars**,
2024-10-08 → 2026-10-02 16:59 ET, 9 contracts, ~8 minutes. Checks: no invalid OHLC, no zero volume,
sessions start 18:00 ET, every roll jumps +200 to +325 points (contango), the June-2025 contract's low
is the real April 2025 crash (16,460).

**T3 — the chart shows real NQ.** Backend: V8 sets `listed = false` on simulated instruments (list
only; they still resolve by symbol, Practice and Drills use them until T4/T13); `InstrumentResponse`
gains `dataFrom`/`dataTo`; `BarSeriesResponse` gains `source`, read from the import ledger instead of
the provider's hard-coded "databento" (INVARIANT 7). Frontend: real NQ by default; windows per
timeframe sized under the 20,000-bar cap (`dateNavigation.ts`); date picker (New York dates, a day
shows up to its 17:00 ET close, DST-correct) and jumps; New York axis by default
(`AxisTimezone = 'new-york' | 'exchange' | 'utc'`). Checked in the running app: Friday 2 Oct 2026's
08:30 ET jobs-report candle sits at 08:30 on the axis; 7 Apr 2025 shows the ~10:00 ET headline spike;
"2Y" first showed a single day — the window end was clamped one day after the first minute — so the
clamp now keeps a full window (found by clicking, test added). 1h reads ~220k minutes per request
(~0.4 s) — recorded as a limitation. Backend 221 → 223, frontend 131 → 146.

**2026-10-13 — Tailwind, and a cleanup of the whole repository (DEC-044, BUG-037).** Fabio sent a
screenshot of the sign-in page on a 1920px screen — boxed column, card stranded left — and asked for
Tailwind, the Vite starter styles deleted, and everything unneeded removed, file by file.

- **Cause of the look:** the Vite template's `index.css` had never been deleted (BUG-037; BUG-031 and
  BUG-032 were earlier symptoms of it). Deleted, with `App.css` and the template's assets, README and
  favicon. Page title is now "Tradeez", with a candlestick favicon.
- **Tailwind 4** (`@tailwindcss/vite`), tokens in `src/index.css` matching the chart's colours, Inter
  font from npm. Shared pieces: `components/ui.tsx` (Button, Segmented, Panel, Chip, Notice) and
  `lib/styles.ts`. Every component rewritten: top bar with section tabs, centred sign-in, a chart
  that fills the screen height, practice and drill panels. Texts, roles, aria labels and test ids
  unchanged — no test needed a selector change.
- **Caught before shipping:** a button carrying both its resting and active colour utilities (Tailwind
  resolves by stylesheet order, not class order) — now separate sets; `ui.tsx` exporting constants
  broke fast refresh (one new lint warning) — split into `lib/styles.ts`.
- **Checked in the browser** at 1600×900 and the pane's own size: sign-in card centred to the pixel
  (608px each side), chart, practice session, plan panel, drills. The console's 500s were Vite failing
  to resolve the deleted `App.css` for the few seconds before `App.tsx` was rewritten (its log shows
  it); the backend logged no errors.
- **Cleanup.** Frontend: knip found the unused `react-router-dom`, dead `getHealth` and `getDrill`,
  and 9 exports used only in their own file (un-exported); knip is now clean. Backend: a reference
  scanner found 64 lines of unused members — `DataClass.isTeachable/isDeployable`,
  `Instrument.ticksToPrice/pnlCents/isListed` and two mapped columns nothing read, `TradePlan.toEnginePlan`,
  unused getters on `TradePlan`, `JwtService`, `RefreshToken`, `Drill`. (A first attempt at the deletion
  matched doc comments greedily and took the `listed` field and the JPA constructor with it; reverted
  and redone line by line before anything ran.) Kept on purpose: test-observability hooks, `Bar.isUp`/
  `bodyTicks` (needed by T11/T17), the Databento importer (DEC-042). Every `application.yml` key is
  read. Stale text fixed: the OpenAPI description ("SIMULATED unless labelled"), the README (what
  works, stack, how to import, migrations V1–V8). Not removed: `npm run api:types` — DEC-007 requires
  generated types and they were never wired in; the README now says so instead of claiming otherwise.
  Stale build output (`target/` probe classes from session 016) deleted.
- Backend 223/223, frontend 146 → 148, typecheck, production build (CSS 24 KB), `npm audit` 0.

**2026-10-13 — T6: the bug batch and the merge.** Fabio: "merge it and then do T4". Only BUG-035 was
open (7 lint warnings). Fixed: refs written at commit in layout effects (the overlay's gesture and
state refs together, because a drag reads them as a pair), and ChartWorkspace's loading/error derived
from a request-tagged response instead of being reset inside the effect; lint now denies warnings.
The drag risk the entry named was tested in the browser with scripted drags — a burst of 10
pointermoves and the same drag paced — on the old and the new code: identical. That comparison
exposed **BUG-038**: drags re-snapped their last preview to the nearest bar on every event, so a slow
drag never moved sideways; now measured from the grab point (test with a snapping projection fails on
the old code). Fresh clone of the pushed branch: frontend lint/typecheck/149 tests/build, backend
223/223 without any `.env`. Merged with `--no-ff` as `80014ad`. The untracked
`gymnastics_puzzle_collage.jpg` in the repo root was deleted at Fabio's request. Next: T4 on a new
branch, `feat/practice-real-data`.

**2026-10-13 — T4: practice on real NQ (DEC-045).** Read the whole practice path first: the service
refused real instruments outright, and the fill engine both walked only one-second bars and read any
gap in them as the market closing — on real minute data a quiet minute has no bar, so real trades
would have been closed early. Built:
- **`PracticeClock`** — DEC-025 says a learner reading the network tab must not learn the date either,
  so a real session's times are mapped onto a fictional week in January 2000 keeping weekday and New
  York wall-clock time, sent with `timeBasis = NEW_YORK_WALL_CLOCK` and rendered by the client in UTC
  labelled "NY" (EST/EDT would leak the season). Per-session shift stored server-side
  (`clock_day_shift`, V9); `revealedStartTime` sent only at COMPLETE. DST changes happen while CME is
  closed, so wall-clock times cannot collide — tested across 9 March 2025.
- **`fill-1.1.0`** — resolves on the finest bars stored (S1, else M1, recorded on the result); the
  gap rule is gone because the service ends the bars at the session close. FillEngineTest's oracle
  now trims its generated data at the first closure, as the service does.
- **Picker** — random moment in the imported history, open by the calendar and with ≥30 bars in the
  hour before and ≥60 in the two after (holidays have none), optionally in a killzone
  (`marketdata.Killzone`, the sessions-1.0.0 table) and on a New York weekday; candidates are drawn
  inside the killzone rather than rejection-sampled.
- **V9** — seed and generator nullable, `data_source` on session and result, CHECK seed-or-source.
- Frontend: Session/Day pickers, "date hidden" chip, `wall-clock` axis mode, result card reveals
  "This was Tuesday 31 March 2026, 04:40" and names the source and resolution. (Node formats long
  dates as "Monday, 7 April 2025 at 10:05"; the reveal is assembled from parts so ICU versions agree.)
- Default instrument for practice is now NQ; tests that relied on the default name `DEMO-NQ`.
- **Played in the app on the real import:** London, Tuesday → "Tue 04:40 NY", 404 bars all dated
  2000, no real year in the payload, no seed; long → stopped out −1.15R on M1 by fill-1.1.0; reveal
  "Tuesday 31 March 2026, 04:40" = the stored start (08:40 UTC); the 04:45 minute's low 23,301.75
  broke the 23,304 stop, exit 23,303.75 with one tick of slippage.
- Backend 223 → 234, frontend 149 → 155; lint zero, knip clean.

**2026-10-14 — T4 merged.** Fabio: "continue with what you were doing". No open bugs; fresh clone of
`feat/practice-real-data`: frontend lint/typecheck/155/build, backend 234/234. Merged `--no-ff` as
`c128237`. Next: T5 on `feat/replay-trading`.

**2026-10-14 — T5: replay paper trading, several trades per session (DEC-046).** The practice slice
was one trade per session: `UNIQUE (session_id)` on plan and result, COMPLETE the moment the exit was
revealed, seed and date shown with that one result. Rebuilt as a replay:
- **States:** ANALYSING is *flat*, IN_TRADE as before, COMPLETE is *ended*. A revealed exit returns
  the session to flat (`settle`); `POST /{id}/end` ends it (only while flat; idempotent); a flat
  replay that reaches its 3-day limit ends by itself. Advancing over a closed market to the limit now
  moves the cursor with no bars instead of refusing — otherwise a replay whose last hours were a
  weekend could never end.
- **V10:** `seq` on `trade_plan` and `practice_result`, `UNIQUE (session_id, seq)` on both, composite
  FK result → plan. Existing rows became trade 1 without an UPDATE (constant default), so the
  immutability triggers were never touched; the local database migrated with its T4 session intact.
- **API:** `PracticeState.trades[]` (number, plan, entry once revealed, result once the exit is
  passed) and `score` (closed trades, wins, losses, total R, net) replace `plan/entry/result`; the
  journal row carries `trades` and `score`. Seed and `revealedStartTime` only at COMPLETE. Max 50
  trades per replay. New refusals: trade running (end), session ended (plan), too many trades.
- **Frontend:** speed control (1×/2×/4×/8× bars per second); play stops by itself when a trade
  closes; the locked draft is cleared so the next trade starts from a fresh bracket; finished trades
  drawn entry → exit (`Drawing.endTime`, `positionRight`) and never selectable; `TradeLog` (score,
  trades table, end reveal and provenance) and a per-trade `ResultCard`; End session asks once more.
- **Played on real NQ:** NY AM → "Fri 10:55 NY". Long stopped out a minute later (−1.07R), short hit
  its target in the next bar (+1.98R); play at 8× stopped on the close by itself; date hidden
  throughout; End session → "Friday 18 September 2026, 10:55", matching the stored start. Seq 1 and 2
  in the database. Layout checked at 1920px: centred, no horizontal overflow.
- **BUG-039** found there and fixed: the total read +0.90R under a column reading −1.07R and +1.98R.
- Backend 234 → 241, frontend 155 → 165; lint zero, knip clean. Pushed as `c0707a4`; fresh clone green
  (frontend lint/typecheck/165/build, backend 241/241 without `.env`).

**2026-10-14 — T5 merged.** Fabio: "yeah merge it and then do T7". Merged `--no-ff` as `9ab699a`.
Next: T7 (New York clock layer) on `feat/ny-clock`.

**2026-10-14 — T7: the New York clock layer (DEC-047).** Nothing of it existed: T3's "session
separators" were never built. Built:
- **Backend `clock/`:** `ClockLayerBuilder` — a pure function of one-minute bars and roll instants —
  implements `sessions-1.0.0` (killzones from `marketdata.Killzone`, Silver Bullet hours, macros,
  trading days), `opens-1.0.0` and `levels-1.0.0`. It throws on any bar at or after its end, so a
  caller cannot hand it the future. `ClockService` reads the minutes (14 days of lookback for PWH) and
  rolls (`MarketBarStore.contractChanges`, a `lag()` over `contract_id`). `GET
  /api/market/instruments/{symbol}/clock` (≤ 200 days, public market only) and `GET
  /api/practice/sessions/{id}/clock` (up to the cursor, on the hidden-date clock, with `nextChange`).
- **Found by a test before it shipped:** a line starting exactly at the layer's end was dropped, so a
  replay at 05:00 would have missed London's high and low until its next refresh at 08:31.
- **Frontend:** `lib/clock` (switches, colours, renderer); `ClockOverlay` on its own canvas under the
  drawings; `ClockToggles`, remembered in localStorage; `useOverlayCanvas` now shared with
  `DrawingOverlay`; `ChartAdapter.getPaneSize` so shading stops at the axes; `timeframeSeconds` moved
  to `lib/chart/dateNavigation`. A time between bars is placed by proportion inside its bar (the chart
  can only place bar times).
- **Checked on the real import:** a Friday in September: PDH 29,793.25, PDL 29,247.75, London
  29,966.75/29,845.00, 09:30 open 29,814.00 — all equal to SQL over the stored minutes; the roll
  (Mon 14 Sep 18:00) correctly removed that week's weekly open and PWH/PWL. A London replay at
  "Tue 04:00 NY": no London levels yet, next change 04:04; ten bars later one refresh, London H/L
  24,868.50/24,761.00 from 05:00 = the stored minutes of Tuesday 24 February 2026. Timing: 0.16 s on
  5m/14 days, 0.65 s on 1h/160 days. At 1920px the switches sit on their own line above the chart.
- ENV-006: Vite served a stale module after a `sed -i` edit; restarting the dev server fixed it.
- Backend 241 → 259, frontend 165 → 177; lint zero, knip clean. Pushed as `ca812ad`; fresh clone green
  (frontend lint/typecheck/177/build, backend 259/259 without `.env`). Not merged: waiting for Fabio.

**2026-10-14 — Bug batch: BUG-040 to BUG-043.** Fabio: "fix all the bugs you tracked until now".
None was open — BUG-001 to BUG-039 all carry a fix — so the code was audited for bugs never logged,
and four were found and fixed on `feat/ny-clock`, each with a test that fails on the old code:
- **BUG-040 🔴** the app stopped working fifteen minutes after it was opened: the client refreshed
  the access token only at page load. `apiFetch` now refreshes once on a 401 (one shared refresh for
  concurrent requests), retries with a fresh CSRF header, and returns to sign-in with a notice only
  when the refresh itself is refused (not on a network or server error). Hit live several times
  during T4–T7 checks before it was recognised as a bug rather than an expired test login.
- **BUG-041** undo after a trade brought its old bracket back: a store action `forget` now erases a
  locked plan from the present and all of history. The first test passed on the broken code (four
  undos walk past the draft); it now checks after every undo.
- **BUG-042** trade details opened only with a mouse: each trade number is a button.
- **BUG-043 🟠** one request could keep the server busy for 21 s (399 days of hourly DEMO-NQ bars;
  11 s for the new clock endpoint): simulated markets are served at most 14 days at a time.
- ENV-006 mitigated (no `sed -i` on sources while a dev server runs). Backend 259 → 260, frontend
  177 → 189. Pushed as `aa2b753`; fresh clone green (frontend 189 + build, backend 260 without `.env`).

**2026-10-14 — Review before the merge: BUG-044 to BUG-046.** Fabio: "check everything for bugs and
then merge, and then continue to t8". The branch diff (T7 + the batch, 42 files) was read through, and
the app driven end to end on real data — Chart with the clock, a drill, a two-trade replay to its end
with the date reveal — with no failed request. Three more, each with a test that fails on the old code:
- **BUG-044** clock opens and killzone levels could be taken from a later price where the data begins
  mid-day; now an open needs its minute within 15 min, a killzone level data from within 15 min of
  its open, a weekly open a week whose first minute opened a trading day (the test's first run caught
  the old 30-hour week allowance letting a Monday-10:00 start through).
- **BUG-045** an unanswered "End the session?" came back after a trade; **BUG-046** a newly closed
  trade stayed hidden behind an earlier pick. Both now remember when they were set and lapse.
- Docker Desktop had stopped again (ENV-001) and was restarted; it also brought back another project's
  container on port 5173, so the frontend preview moved to 5174.
- Backend 260 → 261, frontend 189 → 191. Fresh clone of `9b5e4dd` green (frontend 191 + build, backend
  261 without `.env`).

**2026-10-14 — T7 merged.** Merged `--no-ff` as `6d09763` with the bug fixes BUG-040 to BUG-046.
Next: T8 (ICT drawing tools) on `feat/ict-drawings`.

**2026-10-14 — T8: three ICT drawing tools (DEC-048).** Extends DEC-037's four shapes, as it said a
curriculum would have to:
- **Fibonacci** (`fibonacci`, 2 anchors): ICT's level set (ICT-43), 1 at the leg's start and 0 at its
  end; OTE 0.62–0.79 shaded with 0.705 heavier (ICT-42); targets −0.27/−0.62/−1/−2 green; levels to
  the right edge; prices on the tick grid. **Zone** (`zone`, 2): box with the CE dashed (ICT-21),
  kept in half-ticks and printed with an extra decimal; quarter lines; optional name. **Label**
  (`label`, 1): words at a point. Words are typed in a field over the chart (on placing a label,
  or double-clicking a label or zone); an empty new label is forgotten; saves skip a label still
  waiting for words. Toolbar: Φ ▤ T. Arithmetic in `lib/drawing/ict.ts`.
- **Backend V11:** the three kinds; `text` column (≤ 80, labels must have some, only labels and zones
  may); a CHECK on each kind's anchor count — the controller checks it too (400, not 500). Every
  existing row satisfied it, checked before writing the migration.
- **Found while building it:** BUG-047 (undo after a drag lost where the shape had been — the store
  recorded the last preview), BUG-048 (labels of lines ending together printed over each other —
  first seen on the real chart once screenshots worked, the 09:30 open under PDH), BUG-049 (a click
  with a two-point tool left an invisible shape; a zero-length Fibonacci printed ten labels at one
  price). Each with a test that fails on the old code.
- **Checked in the browser:** the clock layer at last seen with the eye (killzone strips, PDH/PDL and
  opens, labels no longer colliding); a Fibonacci dragged with the real mouse from a swing low to a
  high drew its levels and OTE and saved. The window kept going hidden, so the zone and label were
  checked by the overlay tests, not by eye. Test drawings removed from the test account afterwards.
- Docker Desktop had stopped (ENV-001) and was restarted.
- Backend 261 → 264, frontend 191 → 215.