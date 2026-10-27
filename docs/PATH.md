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
last_session: 017   # in progress — T1–T19, T34, T35, DEC-056–064, both bug hunts and the security pass merged; T21 (evidence views) next. Hosting and deployment deferred by Fabio until the app is finished
last_author: CLAUDE
last_updated: 2026-10-22
phase: REAL DATA + ICT CURRICULUM — DEC-041 (ICT trainer, paper trading on real history), DEC-042 (Massive free plan first)
code_written: true
repo_initialized: true
git: true
remote: https://github.com/FabioZetovic/Tradeez   # PRIVATE, created session 007
branch: master                                    # fix/bug-hunt (BUG-075–092 fixed, DEC-076, DEC-077) merged 2026-10-23 as 4f73512; feat/smt-chart (DEC-075, bug hunt BUG-075–092 logged) merged 2026-10-21 as 4e31dec; feat/smt, feat/partials, feat/onboarding (T20, T28, T29, DEC-073, DEC-074) merged 2026-10-21 in order as 5dade77, a8baa46, 9f332df; feat/evidence … feat/costs (T21–T27, DEC-065–072, BUG-074) merged 2026-10-21 in order as 815f7e3, ad43649, 85a60c0, cc02048, 75ab171, 9449a13, 6d6b340; fix/bugs-and-security (bug hunt + security pass, DEC-064, BUG-066–072) merged 2026-10-17 as 7c6ca81; feat/time-models (T19, DEC-063) merged 2026-10-17 as 2f476e0; feat/ranges (T18, DEC-062) merged 2026-10-17 as 8cf18bc; feat/blocks (T17, DEC-061) merged 2026-10-17 as 35b0de5; feat/session-candles (T35, DEC-060, BUG-063–065) merged 2026-10-16 as 25fc962; feat/learning-state (T16, DEC-059) merged 2026-10-16 as a16f0d1; test/practice-coverage (bug hunt, TP/SL tests, BUG-057–060) and with it feat/prediction-drills (T15, DEC-058) merged 2026-10-16 as 581b30a; fix/practice-trades (BUG-055, BUG-056, DEC-057) merged 2026-10-16 as 7f013a9; chore/upgrade-dependencies (T14 + DEC-056) merged 2026-10-16 as 37f8513; feat/drill-engine (T13) merged 2026-10-15 as e4f7cba; feat/liquidity (T12) merged 2026-10-15 as 1cb0c52; feat/market-structure (T11) merged 2026-10-15 as 57af061; feat/practice-timeframes (T34) merged 2026-10-15 as ec617ca; feat/imbalances (T10) merged 2026-10-15 as dcf699a; feat/detector-framework (T9) merged 2026-10-15 as a582b1a; feat/ict-drawings (T8 + BUG-047–049) merged 2026-10-14 as 4321ae6; feat/ny-clock (T7 + BUG-040–046) merged 2026-10-14 as 6d09763; feat/replay-trading (T5) 2026-10-14 as 9ab699a; feat/practice-real-data (T4) 2026-10-14 as c128237; feat/real-nq-data 2026-10-13 as 80014ad (no PR); feat/fvg-drill 2026-09-30 as 2280d37; PR #1 2026-09-29 as d6747e4
head: see git log                                 # updated every session
build_passing: true  # backend 706/706 (Testcontainers, 2026-10-22, fix/bug-hunt), frontend 390/390 + typecheck + build; coverage: backend 95.4% of lines (./mvnw -Pcoverage test), frontend 88.3% (npm run coverage); npm audit 0; knip clean; lint: zero warnings, enforced (--deny-warnings). Fresh clone of da5f361 (T12) green 2026-10-15; merged as 1cb0c52. Fresh clone of 778c306 (T13) green 2026-10-15; merged as e4f7cba.
deployed: false      # local only, €0

stack:               # DEC-056 — newest versions everywhere; exceptions written down with the reason
  backend:  Spring Boot 4.1.1 / Java 25 target (JDK 26 installed) / Maven 3.10.0 / springdoc 3.1.1 / Jackson 3
  frontend: Node 24.20 LTS / npm 12.2 / React 19.3 / Vite 8.3 / Vitest 5 / TypeScript 7 / Tailwind CSS 4 (DEC-044)
  coverage: JaCoCo 0.8.15 (Maven profile `coverage`, opt-in) / @vitest/coverage-v8 5.0.5 (`npm run coverage`)
  database: PostgreSQL 17 via docker-compose (17.11; Fabio kept 17 — 18 needs a dump and restore of the imported data, DEC-056)

files_in_repo:
  # Module map, not a file list. BUG-026: a hand-maintained file list sat six sessions out of
  # date because nothing fails when it is wrong. For individual files run `git ls-files`.
  backend/src/main/java/com/tradeez/:
    common/      # health endpoint; validation errors for every controller, never echoing or logging the rejected value (BUG-054); Epochs — a request's epoch second, bounded (BUG-068)
    config/      # SecurityConfig (CSRF, CORS, filter chain), OpenApiConfig
    identity/    # users, JWT cookies, refresh rotation, rate limiting (trusted proxies, BUG-070); PasswordPolicy = password-1.0.0, PwnedPasswords = the k-anonymous breach check, change password (sessions 008, 011, 017 — DEC-064)
    marketdata/  # instrument registry, DEMO-NQ generator, aggregation, day cache, bars API (009, 015); Timeframe.bucketStart = where a candle starts: 4h/1d from the 18:00 New York open (017, T35, DEC-060); TradingDayRange = one trading day's high and low, grouped in SQL (T19)
      importing/ # shared: ImportLedger, ImportCommand (CLI), ImportReport, ImportChunks     (017)
      databento/ # Databento client + cost-capped importer (DEC-040)                          (017)
      massive/   # Massive client, request pacer, quarterly contracts, volume roll, importer (017, DEC-042)
    charting/    # saved drawings                                              (session 012)
    practice/    # fill engine, sessions, plans, results, replay API          (session 014); close at market (DEC-057); TradingModel, ModelChecklist, DailyBias, PlanContextService — a plan's model, bias and checklist (T22, DEC-066); Scorecard, Calibration, PracticeSettingsStore, PracticeRecordController — the scorecard, calibration and the risk limit (T23, DEC-067); TradeReview, TradeReviewService — a finished trade told as it happened (T24, DEC-068); JournalStatistics — the journal's statistics (T25, DEC-069); partial targets in FillEngine (Partial, Leg; fill-1.4.0) and on plans, results and closes (T28, DEC-074)
    clock/       # New York clock layer: sessions/opens/levels rules, levels taken (liquidity-1.0.0), IPDA 20/40/60-day ranges within one contract (ipda-1.0.0), chart endpoint (017, T7, T12, T19)
    evidence/    # EvidenceService (counts every rule over the imported history, rule_evidence V18), EvidenceRefresher (after start-up), EvidenceText (the words) — T21, DEC-065
    analysis/    # Detector contract + registry (runsOn, T21), Segments, scanner, setups API (DEC-026, DEC-049); EvidenceCalculator (evidence-1.0.0); rules: FVG, FVG_STATE, IFVG, BPR, VI, VOID, OPENING_GAP, ORG (DEC-039, DEC-050); SWING, SWING_TIER, STRUCTURE, BOS_CHOCH, DISPLACEMENT, MSS on SwingPoints (DEC-052); LIQUIDITY, EQHL, SWEEP, TURTLE_SOUP on LiquidityLevels (DEC-053); OB, BREAKER, MITIGATION, REJECTION, UNICORN on Blocks (each block followed for its life) and Flips (DEC-061); DEALING_RANGE, PD, OTE, STDEV on Ranges — one leg, read four ways (DEC-062); PO3, DAILY_PROFILE, WEEKLY_PROFILE, JUDAS on Periods — read once the day, week or window is over (DEC-063); SMT reads a second market — `Detector.companionOf`, `Segments.beside` and `merged` (T20, DEC-073); RuleText = every rule in words and every hit explained (DEC-055)  (016, 017 T9–T14, T17–T19)
    curriculum/  # concept registry read from docs/ICT_CONCEPTS.md at startup; GET /api/concepts (017, T9); lesson cards with random real examples, GET /api/concepts/{id}/lesson (T14, DEC-055)
    learning/    # learning-1.0.0 (DEC-059): progress computed from graded drills (drills/DrillAttempts) and cards read — solid, SM-2 reviews, the ladder's unlocks; GET /api/learning, POST /api/learning/concepts/{id}/read  (017, T16)
    drills/      # drill engine (DEC-054): DrillKind catalogue (twelve drills, modes A, B and C), windows on real NQ, answer key = stored hits, excused candles; Prediction + PredictionText = predict-1.0.0 (mode C: two targets, the future withheld until the answer, DEC-058); FVG fixture on simulated data  (016, 017 T13, T15)
  backend/src/main/resources/db/migration/: V1 baseline · V2 instruments · V3 token hardening · V4 drawings · V5 practice · V6 setups + drills · V7 real market data · V8 simulated markets withdrawn from the list · V9 practice on real data · V10 several trades per replay · V11 ICT drawing tools (kinds, text, anchor counts) · V12 detector framework (shape, confirmation time) · V13 setup kinds · V14 drill engine (kinds, choice, excused candles) · V15 close at market (practice_close) · V16 prediction drills (question, outcome) · V17 cards read (concept_read) · V18 rule evidence · V19 a plan's model, bias and checklist · V20 scorecard and risk limit · V21 risk drills · V22 management drills · V23 trading costs · V24 ES (T20) · V25 partial targets (T28) · V26 the SMT drill (DEC-075) · V27 access tokens' generation (BUG-077)
  frontend/src/:
    lib/api/      # fetch client, auth, market data, drawings, practice, clock, setups, concepts, drills, lessons
    lib/chart/    # ChartAdapter + Lightweight Charts impl, tick<->price, time axis
    lib/drawing/  # geometry, hit testing, snapping, undo store, renderer; ict.ts = Fibonacci/CE arithmetic (DEC-048)
    lib/chart/labelSpace.ts # places canvas labels without overlap (BUG-048)
    lib/chart/timeToX.ts    # places any time on screen inside its bar — shared by the clock and setup overlays
    lib/clock/    # New York clock: layer switches, renderer
    lib/setups/   # detected setups: shapes.ts decides where each rule's hits start and end (T10), render.ts paints them
    lib/practice/ # plan helpers: bracket from the position tool, validation, money/R formatting
    lib/drills/   # candle picking and marks (explanations come from the server, DEC-054)
    components/   # app shell + tabs, sign-in, chart workspace, drawing overlay + toolbar; ui.tsx = shared Button/Segmented/Panel/Chip/Notice
    lib/styles.ts # cx() and shared Tailwind class strings (DEC-044); tokens live in src/index.css @theme
    components/practice/  # journal, replay screen, plan panel, trade result card, trade log + score
    components/drills/    # drill screen (spot and name), overlay: marks, the subject, the answer's shapes
    components/learn/     # Learn tab: curriculum ladder, lesson card, example charts (Setups renderer + a band on the hit)
    hooks/        # useSession, useDrawings, useDrawingShortcuts, usePracticeSession, useToggles (+ useClockLayers, useSetupLayers)
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
  - indicators, any aggregate score or rating; graded drills are stored (T13) but nothing reads them until the learning state (T16)

blocked_on: nothing — the redesign (M6, T36–T49, docs/REDESIGN.md). T36 theme and shell (feat/t36-shell, DEC-078) and T37 overlays in the chart's paint step (feat/t37-overlays, built on it; BUG-095 and BUG-096 fixed) built and merged 2026-10-23 (master 18399e5). T38 the Chart tab and T39 Home merged 2026-10-26 (master d0b84bd). T40 News merged 2026-10-26 (master 3bbaf28; DEC-079: our own calendar from FRED and the Fed — Forex Factory's terms forbid republishing its calendar). T41 the Practice home merged 2026-10-26 (master c3ea45f; DEC-080: costs fixed at the standard). T42 the practice session screen merged 2026-10-26 (master d4a21aa). T43 Drills merged 2026-10-26 (master 2366bca). BUG-097 (nothing could be drawn ahead of the last candle) fixed and merged 2026-10-26 (master 389f037). T44 the Learn audit decided 2026-10-26 (docs/redesign/learn-audit.md; DEC-081: Fabio accepted all 72 proposals — 33 pages in nine topics). T45 the Learn rebuild built on feat/t45-learn (from feat/t44-learn-audit; DEC-082): Learn by topic with locks, 12 new drills, the 33 lesson texts — texts approved by Fabio 2026-10-27; waiting for his "merge" (T44 merges with it). BUG-093 seen once and logged. Left after it: T31 (deployment, deferred by Fabio with BUG-073), T32 (deeper history: skipped for now, no budget — revisit at the end), T33 (indicators, low priority), and Q-09 (showing the data to the jury).

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
claimed_files:      # session 017 (CLAUDE) — T27 fees, commission, slippage, on branch feat/costs (from feat/risk, 2026-10-20)
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
practice on real NQ (017, merged) · T5 replay with several trades (017, merged) · T7 New York clock layer (017, merged) · T8 ICT drawing tools (017, merged) · T9 detector framework (017, merged) · T10 imbalances and gaps (017, merged) · T34 any timeframe in practice (017, merged) · T11 market structure (017, merged) · T12 liquidity (017, merged) · T13 drill engine (017, merged) · T14 lesson cards (017, merged) · DEC-056 newest versions everywhere (017, merged) · practice fix, DEC-057 (017, merged) · T15 prediction drills, DEC-058 (017, merged) · bug hunt and TP/SL tests, BUG-057–060 (017, merged) · T16 learning state, DEC-059 (017, merged) · T35 session-aligned 4h and daily candles, DEC-060 (017, merged) · T17 blocks, DEC-061 (017, merged) · T18 ranges, DEC-062 (017, merged) · T19 time models and profiles, DEC-063 (017, merged) · second bug hunt and security pass, DEC-064 (017, merged) · T21 evidence views, DEC-065 (017, on feat/evidence) · T22 ICT plans and model checklists, DEC-066 (017, on feat/plans) · T23 scorecard and calibration, DEC-067 (017, on feat/scorecard) · T24 review, DEC-068 (017, on feat/review) · T25 journal statistics, DEC-069 (017, on feat/stats).

### M6 — The redesign, Fabio, 2026-10-23

> **The plan is `docs/REDESIGN.md` — read it before any M6 work, and follow it.** It holds Fabio's words
> verbatim, the agreed decisions R-1 … R-20, Lovable measured screen by screen (images in
> `docs/redesign/lovable/`, the app before in `docs/redesign/before/`), and every task's steps, files, backend,
> tests and done checklist. This section is only the summary.

Fabio: "it is time that we change the damn design of the app, it looks extremely unpolished and vibe coded".
Reference: his Lovable mock-up, <https://github.com/FabioZetovic/futures-flow-studio> (anything may be taken from
it) — its layout, Home, tiles and chart-with-layers, **not** its lime colours. The before-screens:
`ui-screens/tradeez-ui-2026-10-23.pdf` on `feat/design`. About eight months to the jury.

**Agreed (his answers, 2026-10-23):**
- **NQ first, perfect; ES later** as a full instrument (some trade ES only). Until then ES appears only in the
  SMT lesson and drill.
- **Theme:** one accent token, indigo-violet `#7C6CFF` for now, changeable in one place; dark; animated
  (`motion` library). Up and down stay green and red.
- **Home** is the page after sign-in, like Lovable's; a public landing page before sign-in comes later. The
  "How it works" guide stays as a help page as well.
- **News:** Forex Factory's own weekly feed (`nfs.faireconomy.media/ff_calendar_thisweek.json`, terms to be
  checked first), upcoming events only, high (red) and medium (orange) impact. Today's and tomorrow's on Home,
  the week on a News page. In a replay only "high-impact USD news at 08:30", never the event's name — which
  needs past events, so it waits for a historical calendar.
- **Chart:** full screen, NQ only; the 45 toggles grouped by topic behind a glass menu opened by click; the
  drawing toolbar stays a slim bar on the left; overlays drawn in the chart's own paint step (BUG-095).
- **Learn:** rebuilt, grouped by topic, a lesson locked until the drill before it is passed, progress shown, one
  template per lesson (what it is, how to spot it, how it is traded, real NQ examples, practise it), the words
  rewritten in plain trader language with `ICT_CONCEPTS.md` updated and Fabio reviewing. Level 0 goes. Every
  page without a real example or a drill is listed first and decided together: kept with a drill, merged, an
  open reference card, or removed.
- **Practice:** no timeframe or weekday at the start; the session time and the risk limit stay; the fees and
  slippage settings go — one standard cost on every trade, "costs paid" shown; Lovable's stats tiles. The
  session: a full-screen chart, a side panel to plan the trade (risk in $, R:R, entry, stop, target) with the
  replay controls under it.
- **Drills:** no timeframe choice (the app picks); grouped into sections as cards.
- **Last of all:** an AI assistant that answers questions about the chart.

| # | Task | Blocked by | Notes |
|---|---|---|---|
| T36 | **Theme and shell** | — | Tokens (the accent in one place), Lovable's fonts, `motion`, the top bar (Home, Learn, Drills, Practice, Chart, News), page headers, cards, tiles, the glass surface; layouts built to reflow so T46 fits |
| T37 | **Chart overlays in the paint step** | — | Clock, setups, drawings as Lightweight Charts primitives; BUG-095 |
| T38 | **Chart tab** | T36, T37 | Full screen, NQ only, the grouped glass layers menu, the slim drawing bar |
| T39 | **Home** | T36 | Lovable's: steps with progress, up next, due for review, practice so far, a fixed NQ chart, today's high-impact news |
| T40 | **News** | T36 | The Forex Factory weekly feed, fetched and cached by the backend; the News page |
| T41 | **Practice home** | T36 | No timeframe, weekday or cost settings; one standard cost; stats tiles; the journal |
| T42 | **Practice session** | T37, T38 | Full-screen chart, the plan and replay panel, scorecard and review restyled |
| T43 | **Drills** | T36 | Sections of cards, no timeframe choice, the drill screens restyled |
| T44 | **Learn audit** | — | Every concept page: rule, real examples, drill — the list for Fabio to decide on. Can start any time |
| T45 | **Learn rebuild** | T36, T44 | Topics, locks, progress, the lesson template, the words rewritten |
| T46 | **Phones and tablets** | T36–T45 | Every screen below 1366px |
| T47 | **ES as a full instrument** | NQ done | Charts, practice, drills on ES; BUG-094 |
| T48 | **Public landing page** | T36 | Before sign-in |
| T49 | **AI chart assistant** | everything else | Last |

**Order:** T36 → T37 → T38 → T39 → T40 → T41 → T42 → T43 → T45, with T44 early (it needs Fabio's time),
then T46, T47, T48, T49. Each task its own branch, merged when Fabio says "merge".

### Fabio's request, 2026-10-17 — every bug, missing tests, security hardening

On `fix/bugs-and-security`, branched from `feat/time-models`; **merged 2026-10-17 as `7c6ca81`**, after
T19 as `2f476e0`. Fabio: "Priority is fixing all the bugs we have until now"; he allowed the work to be
split across sessions. **Order:**

1. ✅ **Bug hunt, all areas** — BUG-066 to BUG-073 logged; all but BUG-073 (a hosting decision) fixed,
   each with a test. A sweep (`RobustnessTest`) sends every endpoint bad input and expects a 4xx.
2. ✅ **Tests for the missing pieces** the coverage run showed (backend 96.0% of lines before): T19's
   words, logout-all, the 500-drawing cap, the health check with the database down, an expired refresh
   token, a candle with no stored minutes, the 400-day range.
3. ✅ **Security (Fabio's list):** (DEC-064)
   - Session token in localStorage? — *checked: no*; HttpOnly cookies since DEC-032. Add a
     Content-Security-Policy to the built page (defence in depth against XSS).
   - Admin check on the client? — *checked: there is no admin role*; every user-data endpoint checks
     ownership on the server. Add a test that every non-public endpoint refuses an anonymous call.
   - Rate limiting on login and the password endpoint — login and register are limited (BUG-002,
     session 011); `X-Forwarded-For` is trusted and forgeable, so the per-IP limit can be walked
     around — fix. There is no password endpoint yet: add change-password, limited.
   - Password leak check and real minimum requirements — HIBP range API (k-anonymity), a common-password
     list, not the email or name, BCrypt's 72-byte ceiling enforced.
4. ✅ Docs, mutation checks, full suites, fresh clone; merged. **Deferred by Fabio (2026-10-17):** BUG-073 and
   all hosting — "lets make the app perfect first". When it comes: page and API on one site (a Vercel
   rewrite of `/api`), or the cookies break.

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
| T8 | ~~**ICT drawing tools**~~ | T3 | I-21, I-42–43, X §4 | ✅ Session 017, **merged 2026-10-14 as `4321ae6`** (DEC-048): Fibonacci with ICT's levels (OTE shaded, targets −0.27 to −2), zone with its CE in half-ticks, labels; words typed on the chart; server checks anchor counts and text (V11). |
| T9 | ~~**Detector framework, concept registry, overlay renderer**~~ | T3 | I S-1–S-3, D-026 | ✅ Session 017, **merged 2026-10-15 as `a582b1a`** (DEC-049): a `Detector` contract (shape, confirmation time, lookback) checked for every detector — deterministic, causal, honest lookback, one per place; split at rolls; V12; lazy scans with a self-checking cache; setups API for the Chart tab and replays (never past the cursor, hidden date kept, no evidence sent); concept registry read from `ICT_CONCEPTS.md`; overlay with zones + CE, off by default. |
| T10 | ~~**Imbalances and gaps**~~ | T9 | I-20–28 | ✅ Session 017, **merged 2026-10-15 as `dcf699a`** (DEC-050): `fvg-1.0.0` re-measured on two years of real NQ and kept (~3 per 30 candles on every timeframe); `fvg-state-1.0.0` (OPEN→TAPPED→CE→FILLED→INVERTED, 500-candle memory), `ifvg-1.0.0`, `bpr-1.0.0`, `vi-1.0.0` (≥ 4 ticks, measured), `void-1.0.0` (gaps, voids), `opening-gap-1.0.0` (NDOG/NWOG from the data), `org-1.0.0`; V13 kinds; overlay with live zones, faded finished ones, stops at rolls. |
| T34 | ~~**Switchable timeframes in practice**~~ | T5 | F | ✅ Session 017, **merged 2026-10-15 as `ec617ca`** (DEC-051): any of 1m–1h mid-replay; the forming candle built only from minutes before the cursor (tested, and a leak mutation caught); steps and skip in viewed candles, never past the limit; chart updates the forming candle in place; clock, setups and finished trades follow the view. |
| T11 | ~~**Market structure**~~ | T9 | I-01–06 | ✅ Session 017, **merged 2026-10-15 as `57af061`** (DEC-052): measured first (swings ~13.5/30 candles, rank spans ≤ 160, displacement 2× = half of all gaps); `swing-1.0.0`, `swing-tier-1.0.0` (ITH/LTH/ITL/LTL), `structure-1.0.0` labels and its BOS/CHoCH breaks, `displacement-1.0.0` (the gap's own candles), `mss-1.0.0` (sweep → displacement → close, confirmed when both are known); every rule reads a bounded span (200/300). |
| T12 | ~~**Liquidity**~~ | T11 | I-10–14 | ✅ Session 017, **merged 2026-10-15 as `1cb0c52`** (DEC-053): measured first (half of swing levels taken within 5 candles, 95% within 500; 50% sweep / 29% run / 21% undecided; EQH/EQL 1.3–2.2 per 30); `liquidity-1.0.0` (RESTING → TAKEN/EXPIRED) on swings and on the clock's PDH/PWH/killzone levels (taken minute; a resting level makes the replay re-ask each step, revealing nothing); `eqhl-1.0.0`; `sweep-1.0.0` (sweep/run, judged on the most extreme level); turtle soup on equal pools. |
| T35 | ~~**Session-aligned 4h and daily candles**~~ | T3 | F | ✅ Session 017 (DEC-060), **merged 2026-10-16 as `25fc962`**: `Timeframe.bucketStart`, the one place a candle's start is decided — 4h at 18, 22, 02, 06, 10, 14 New York (the last 3h), daily 18:00 → 17:00 named by its trading day, safe across clock changes; the Chart tab and practice offer both (practice reaches back 30 / 120 days); the clock up to 4h, none on daily (BUG-063); detectors get their whole history on coarse charts and a request reads it once (BUG-064; nine rules on a cold daily chart 9.8 s → 3.0 s). |

**M2 — The learning loop**

| # | Task | Blocked by | Source | Notes |
|---|---|---|---|---|
| T13 | ~~**Drill engine for every detector**~~ | T10–T12 | C §6, D-023, D-034 | ✅ Session 017, **merged 2026-10-15 as `e4f7cba`** (DEC-054): ten drills — spot gaps, swing highs, swing lows, VIs, displacement, equal highs/lows, sweeps; name a swing's label, BOS or CHoCH, sweep or run — on real NQ 1m/5m/15m, graded against the rule; the key is the rule's stored hits shown whole, a hit the window cuts is excused (BUG-050); the subject is sent without its name; explanations rendered server-side from evidence; V14. The other rules are one constant each when wanted. |
| T14 | ~~**Lesson cards**~~ | T13 | I Part 0 | ✅ Session 017, **merged 2026-10-16 as `37f8513`** (DEC-055): a Learn tab with the curriculum ladder (72 concepts) and a card per concept — the document's What, prerequisites and what it builds toward, every rule in words (all 18 now, guarded by the contract test), the drills that practise it ("Practise it" opens the drill), and three real NQ examples found at random, each explained from its evidence; clock-drawn and undetected concepts say so. |
| T15 | ~~**Prediction drills**~~ | T13 | C §6.1, I-15–17 | ✅ Session 017 (DEC-058), `predict-1.0.0`: measured first (nearer level first 68–69%, fewer obstacles 65–67%, the gap after taking a level 39–47%); "which liquidity is taken first" — the nearest resting BSL and SSL, labelled ERL, open gaps between labelled IRL, each path's obstacles shown — and "external or internal next" after a level is taken; graded by the first candle within 100 to reach either; the future withheld until the answer, then revealed and ringed; V16; real data only. A B drill "ERL or IRL?" waits for T18's dealing range. |
| T16 | ~~**Learning state and curriculum map**~~ | T13 | C §6.3, I Part 12 | ✅ Session 017 (DEC-059), `learning-1.0.0`: progress computed from graded drills (real data only) and cards read, never stored; solid = ≥ 10 attempts and ≥ 80% over the last 20; predictions practice, unrated concepts done by reading; SM-2 reviews a day; Part 12's unlocks settled (suggest, never lock); the Learn tab shows it all, with *Mark as read*. V17. ELO and serving drills by what is due are not in it. |

**M3 — Blocks, ranges, time models**

| # | Task | Blocked by | Source | Notes |
|---|---|---|---|---|
| T17 | ~~**Blocks**~~ | T11, T12 | I-30–37 | ✅ Session 017 (DEC-061), **merged 2026-10-17 as `35b0de5`**: measured first (≈ 1 order block per 30 candles, breakers 0.4, mitigation blocks 0.5, three breakers in four unicorns; the draft's rejection block a third of all swings, so a long wick is now measured against the average candle too: 0.5 per 30); `ob-1.0.0`, `breaker-1.0.0`, `mitigation-1.0.0`, `rejection-1.0.0`, `unicorn-1.0.0`, each followed for its life (FORMED → TAPPED → MEAN_THRESHOLD → INVALIDATED, or EXPIRED after 500); five switches; the overlay draws a block on while it stands, its mean threshold dashed (`midHalfTicks` on the wire), zones named where they come into view. Propulsion, vacuum and the PD matrix are not rules; drills for blocks later. |
| T18 | ~~**Ranges and Fibonacci**~~ | T11 | I-40–44 | ✅ Session 017 (DEC-062), **merged 2026-10-17 as `8cf18bc`**: one leg read four ways — a displacement that breaks structure, from where it began to the first intermediate-term turn (measured: 0.7 per 30 candles; a one-candle turn left within 7 candles, the intermediate-term one within 10–11, kept); `dealing-range-1.0.0` (equilibrium line, broken high or low), `pd-1.0.0` (premium and discount halves), `ote-1.0.0` (62–79%, 70.5% line, tapped; 64% of legs retrace into it), `stdev-1.0.0` (−1, −2, −2.5, −4); four switches. ICT-43's Fibonacci tool was T8. |
| T19 | ~~**Time models and profiles**~~ | T7, T11 | I-53–57, I-62–65 | ✅ Session 017 (DEC-063), **merged 2026-10-17 as `2f476e0`**: every draft measured on two years of real NQ first, two changed — `po3-1.0.0` (the midnight-to-17:00 day; 52% fit), `daily-profile-1.0.0` (London 00:00–08:30 against New York; London low 33%, London high 25%, New York reversal 39%), `weekly-profile-1.0.0` (the weekday of the extreme against the close, when first; Monday 48%, Tuesday 20%), `judas-1.0.0` (a level still resting at the window's open taken, then a close back through the open; London 60% of days, New York 12% — the draft's any-swing reading fired in 90–95%); `ipda-1.0.0` on the clock (20/40/60-day highs and lows from one SQL row per trading day, within one contract: drawn on 70 / 39 / 7% of days). Four setup switches and an IPDA clock switch. The Silver Bullet and macro windows were T7's (`sessions-1.0.0`). |
| T20 | ~~**SMT divergence**~~ | T2, T11 | I-66 | ✅ Session 017 (DEC-073), **merged 2026-10-21 as `5dade77`**: ES as an instrument (V24) and imported like NQ (680,761 candles from 22 Oct 2024, $0); a rule may read a second market (`Detector.companionOf`), handed its candles over the same minutes and split at both markets' rolls — ES rolled a day before NQ twice; `smt-1.0.0`: each market's own swing highs (lows), the pairs at most 2 candles apart, one beyond its previous and the other not; 4.4 per 100 five-minute candles on NQ, 51% bearish. The second chart and the drill followed: DEC-075, **merged 2026-10-21 as `4e31dec`**. |
| T21 | ~~**Evidence views**~~ | T10–T19 | I S-6 | ✅ Session 017 (DEC-065), **merged 2026-10-21 as `815f7e3`**: `evidence-1.0.0` counts every rule over the whole history of real NQ on 1m, 5m, 15m and 1h — hits per 100 candles, how lives ended (a life counts once over; a leg's range by its end or start), kinds, and the time rules against days, weeks and windows — in the background after start-up (697,003 one-minute candles in 18 s), kept in `rule_evidence` (V18), shown on each lesson card with its sample size and "not a forecast". `Detector.runsOn`. Agrees with the earlier hand measurements (gaps 94% filled, OB 83% invalidated, liquidity 95% taken, ranges 59% past their end, OTE 64%). |

**M4 — Models and the exam**

| # | Task | Blocked by | Source | Notes |
|---|---|---|---|---|
| T22 | ~~**ICT plans and model checklists**~~ | T5, T17–T19 | I-60–61, I-70–78, D-027 | ✅ Session 017 (DEC-066), **merged 2026-10-21 as `ad43649`**: the plan carries a model (eight of Part 9's), the learner's bias and draw on liquidity, all optional, written once; with it the killzone, the `daily-bias-1.0.0` suggestion (real days only) and the model's checklist (`model-*-1.0.0`) checked against the rules confirmed by the cursor — read live while drafting (`POST …/context`), kept when placed. Adherence apart from the outcome: "4/5" in the trade log, the steps on the result card. Model and bias lessons point to Practice. Historical model instances later. |
| T23 | ~~**Scorecard**~~ | T5 | C §5.5, X §5, D-027 | ✅ Session 017 (DEC-067), **merged 2026-10-21 as `85a60c0`**: every finished trade scored on two sides of equal weight — outcome (money, R, worst and best point in R, the draw reached or how close) and process (risk against the learner's own limit, plan stated "3 of 4", the model's checklist, with or against their bias, the daily-bias reading, killzone, reward to risk, held or closed by hand, confidence) — a tick or a cross only on the learner's own rules, nothing added up (`scorecard-1.0.0`). A risk limit set before a session, copied onto each plan (V20). Calibration in the journal: confidence (10–90%, target before stop) against how often, per confidence and overall with a 95% Wilson interval, undecided trades counted apart (`calibration-1.0.0`). |
| T24 | ~~**Review overlay**~~ | T22 | D-026 | ✅ Session 017 (DEC-068), **merged 2026-10-21 as `cc02048`**: once the replay has passed a trade's exit, "Read the review" tells it as it happened (`review-1.0.0`) — the hits of liquidity, sweeps, MSS, FVG and the model's own rules confirmed in the 30 candles before the decision (three latest each) and while it ran (up to 24), each explained in its rule's numbers; the decision in the learner's words; the fill and the exit; whether price reached the draw; after a close by hand, what the bracket would have done, once the replay has passed it. Its hits drawn on the chart on request. Nothing past the cursor. |
| T25 | ~~**Journal statistics**~~ | T23 | C §1.4, X §5 | ✅ Session 017 (DEC-069), **merged 2026-10-21 as `75ab171`**: the journal's Statistics panel (`journal-stats-1.0.0`) — trades, win rate, average win and loss, expectancy with its 95% range read in words, profit factor, total R — overall and by model, killzone, weekday, side, model checks met and own rules kept (as the scorecard judges them); the count first on every row. |
| T26 | ~~**Management and risk drills**~~ | T23 | D-023 | ✅ Session 017, **merged 2026-10-21 as `9449a13`**: **mode F** (DEC-070) — risk drills against the clock (`risk-1.0.0`): position size on NQ/MNQ, a losing streak's compounded drawdown, the gain a drawdown needs; answered once, the working with the result, the record per kind. **Mode E** (DEC-071) — "Manage an open trade" (`manage-1.0.0`): a trade a whole R in profit on real NQ, hold / stop to breakeven / half off, each played out on the real candles after, the ones that paid most the answer, every choice told in R. |
| T27 | ~~**Fees, commission, slippage**~~ | T5 | X §5 | ✅ Session 017 (DEC-072), **merged 2026-10-21 as `6d6b340`**: fill-1.3.0 — every market fill slips (entry, close, the flat at the close), a target can need trading a tick through; the learner's own fees and slippage beside the risk limit (V23), the standard `mkt-1t/slip-1t/thru-1t/comm-450c` where unset; the plan resolved and a later close filled with the costs in force when placed; the plan panel's costs, the scorecard's Costs line. |
| T28 | ~~**Partial targets**~~ | T5 | X §5, I-81 | ✅ Session 017 (DEC-074), **merged 2026-10-21 as `a8baa46`**: one level for some of the contracts, filled like the target; optionally the stop on the rest to the entry from the next bar (fill-1.4.0); one walk for an exit or a close at market; kept on plan, result and close (V25); R and money over every contract; revealed while the rest runs; the chart, the scorecard's Partial line and costs, the review's PARTIAL moment. |

**M5 — Presentation, and later**

| # | Task | Blocked by | Source | Notes |
|---|---|---|---|---|
| T29 | ~~**Disclaimers, onboarding, UI language**~~ | — | D-015, X, Q-08 | ✅ Session 017 (DEC-015 note), **merged 2026-10-21 as `9f332df`**: the footer and sign-in page state the CFTC's three points and that practice never reaches a market; a first-visit guide to the four tabs and what the app is not, reopened from "How it works"; the slide's text in DOCUMENTATION.md; English only, `lang="en"` (Q-08). |
| T30 | ~~**BUG-035**~~ | — | BUGS.md | ✅ Already done in session 017, before the merge of `feat/real-nq-data`: the seven warnings fixed and `npm run lint` runs `oxlint --deny-warnings` (BUGS.md). The row was stale. |
| T31 | **€0 deployment smoke test; jury data rights** | M0 | X, C §4.1, Q-09 | Was task 8. No purchase without Fabio. A public deployment also needs a privacy policy: FRED's terms ask it of an app serving other users (DEC-079). |
| T32 | **Deeper history and one-second bars** | Fabio's budget decision | D-040 | Databento importer is built; or a month of Massive's paid plan. **Skipped for now (Fabio, 2026-10-21): no budget; to be revisited at the end.** |
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
| DEC-053 | **Liquidity:** swing and reference levels RESTING → TAKEN (a tick beyond) or EXPIRED (500 candles); a resting clock level makes `nextChange` the next minute (no leak); `eqhl-1.0.0`; `sweep-1.0.0` SWEEP/RUN on the most extreme level a candle takes; turtle soup = a sweep of equal highs/lows. Per-request rule cap 32, guarded by a test. |
| DEC-052 | **Market structure on one swing rule, each rule with a bounded memory** (200 candles; 300 for ranks): swings, ranks, HH/HL labels, BOS/CHoCH (SMC words, same rule), displacement = the gap's own candles, MSS confirmed at the later of break and displacement. |
| DEC-051 | **A replay can be looked at on any timeframe (1m–1h):** the forming candle only from what came before the cursor; a step is one viewed candle (completing the forming one first); fills unchanged. |
| DEC-050 | **Imbalances and gaps, measured on real NQ first:** `fvg-1.0.0` kept (~3 per 30 candles, every timeframe); `fvg-state` with a 500-candle memory, `ifvg`, `bpr`, `vi` (≥ 4 ticks, because body gaps do not scale with the timeframe), `void` (runs must progress), `opening-gap` (closures from the data), `org` (its own rule). `kind` joins a hit's identity (V13). |
| DEC-049 | **One contract for every detector** (S-1): shape ZONE/LEVEL/POINT, confirmation time, honest lookback, causal — checked for every registered detector; candles split at rolls; a replay served from one scan filtered by its cursor; evidence never sent. The concept registry **is** `ICT_CONCEPTS.md`, read at startup (S-2). Overlay off by default (S-3). |
| DEC-048 | **Three ICT drawing tools** extend DEC-037: Fibonacci (1 at the leg's start, 0 at its end, ICT's levels, OTE shaded), zone (CE in half-ticks), label (≤ 80 chars, own column). Server checks each kind's anchor count; a never-dragged two-point creation is not kept. |
| DEC-047 | **The New York clock layer:** `sessions-1.0.0`, `opens-1.0.0`, `levels-1.0.0` (CME day 18:00–17:00), computed server-side from 1m bars, never past the cursor (a replay re-asks at `nextChange`), no level from a partly seen period or across a roll; drawn on its own canvas under the drawings. |
| DEC-077 | **What a learner is still graded by stays off the Chart tab** (BUG-085, BUG-086): the session in hand (the open one touched last) from its cursor to a day past its replay, and the newest unanswered drill to the last candle its answer is decided by — candles, setups and clock prices left out on every real market, `hidden` on the bars and a notice under the chart; no lesson example, drill or session drawn from them. Prices not offset; sessions left behind not hidden. |
| DEC-076 | **R and the score net of costs; the risk limit judged by the loss at the stop** (BUG-087, BUG-088): R = net money ÷ planned price risk, a win made money after costs; `scorecard-1.1.0` judges the limit by price risk + the entry's and stop's slippage + fees under the trade's fill model, both shown; the plan panel the same. |
| DEC-072 | **Trading costs** (T27): fill-1.3.0 — FillModel(market slippage, stop slippage, limit-through ticks, fees per round trip); market fills (entry, close, session flat) slip; a target fills once traded `through` ticks beyond (an open beyond fills at the open); BASIC = fill-1.2.0's `slip-1t/comm-250c`, STANDARD = `mkt-1t/slip-1t/thru-1t/comm-450c`; the name parsed back for a close; V23 settings (fees 0–5,000¢, slippages 0–8, through 0–4) saved all at once; the Costs line in the scorecard. |
| DEC-071 | **Management drills** (T26, mode E): `OPEN_TRADE_MANAGEMENT`, `manage-1.0.0` — a constructed trade (stop 1.5 × ATR(14), ≥ 8 ticks; target 3R), the moment its first close ≥ 1R after ≥ 3 candles; HOLD / BREAKEVEN / HALF (half at the next open) played to the first level or 100 candles, gaps fill at the open, ties in a candle read on minutes or never asked; outcome the choices that paid most (ties all count); real data only (V22); practice, never rated. |
| DEC-070 | **Risk drills** (T26, mode F): `risk-1.0.0` — CONTRACTS (account × risk% ÷ (stop ticks × $5 NQ / $0.50 MNQ), rounded down, ≤ 30, exact, 30 s), STREAK (1 − (1 − r)^n, ± 0.5 pt, 45 s), RECOVERY (d ÷ (100 − d), ± 1 pt, 30 s); V21 `risk_drill` (answer, tolerance, given/answered_at/correct together), `POST /api/drills/risk`, `GET …/{id}`, `POST …/{id}/answer` once; the time from the database's clock; a clock on the page that never refuses a late answer. |
| DEC-069 | **Journal statistics** (T25): `journal-stats-1.0.0`, `GET /api/practice/statistics` — overall and by model, killzone (from the decision's time for pre-T22 trades, BUG-074), NY weekday (Sunday first), side, checks (every / some / none / no model), own rules (all kept / some broken / none) as the scorecard judges them; trades, wins, losses, win rate, avg win/loss R, expectancy ± 1.96 SE (≥ 2 trades) with a reading, profit factor, total R; only revealed exits. |
| DEC-068 | **Review** (T24): `review-1.0.0`, `GET …/trades/{n}/review` once the exit is revealed (409 before) — BEFORE (hits confirmed in the 30 candles to the decision, 3 latest per rule), TRADE (decision in the learner's words, fill, hits to the exit capped at 24, exit), AFTER (draw reached — at or beyond from the entry's side — and, after a close, the bracket's own exit once the cursor passes it); rules LIQUIDITY, SWEEP, MSS, FVG plus the model's; the told hits as a setup layer for the chart. |
| DEC-067 | **Scorecard** (T23): `scorecard-1.0.0` — outcome (R, money, MAE/MFE in R, draw reached) beside process (risk limit, plan stated, checklist, own bias, daily-bias reading, killzone, R:R, execution, confidence) at equal weight; verdicts only on the learner's own rules (risk limit, own bias), the rest stated; headline an unweighted count of those rules, never a score. Risk limit $1–$1,000,000 set before a session (`practice_settings`), copied onto the plan (V20), never blocking. `calibration-1.0.0`: confidence 10–90% that the target fills first, against how often — per confidence with counts, overall with a 95% Wilson interval; closed by hand, session close and one-candle ambiguity counted apart; only revealed exits. |
| DEC-066 | **ICT plans** (T22): plan fields model (MODEL_2022, SILVER_BULLET, OTE, UNICORN, TURTLE_SOUP, BREAKER, SESSION_REVERSAL, OPENING_RANGE_GAP), bias, dol_ticks — optional, immutable (V19); killzone, `daily-bias-1.0.0` suggestion (close beyond the day before's high/low → continuation; ran it and closed back → the other way; inside or both → nothing; real data only), and the model's checklist (`ModelChecklist`, `model-*-1.0.0`: each step read from hits confirmed by the cursor — shift within 30 candles, gap in its leg, stop beyond the sweep, target reaching resting liquidity; Silver Bullet ≥ 10 points; soup within 3 candles) — kept with the plan, scored apart from the outcome. |
| DEC-065 | **Evidence** (`evidence-1.0.0`, S-6): every rule counted over a real market's whole history on 1m–1h, split at rolls — hits per 100 candles; lives counted once over (final state or 500 candles inside the data), a leg's range read by end/start; kinds; time rules against days, weeks and windows. Cached in `rule_evidence` (V18), refreshed after start-up when data, rule or counting version changes; never simulated data; on each lesson card, ending on "not a forecast". |
| DEC-064 | **Passwords and sign-in** (extends DEC-032): `password-1.0.0` — 12 characters to 72 bytes, 5 different characters and no straight run, not the email name, display name or app name, not a common password, not in a known breach (Have I Been Pwned range API, k-anonymous, padded, fails open after 3 s); `POST /api/auth/password` (current password, 403 when wrong; signs every other session out; 5 per account per 15 min and the per-IP limit); per-IP limits believe only `TRUSTED_PROXIES` entries of X-Forwarded-For (default 0); a Content-Security-Policy on the built page. No admin role exists; ownership is checked on the server. |
| DEC-063 | **Time, measured first** (`Periods`): `po3-1.0.0` (midnight open to the 17:00 halt; extreme against the close first and ≥ 15% of the range beyond the open; ≤ 1h charts), `daily-profile-1.0.0` (London 00:00–08:30, New York 08:30–17:00; LONDON_LOW, LONDON_HIGH, NY_REVERSAL; ≤ 30m), `weekly-profile-1.0.0` (Sunday 18:00–Friday 17:00; the trading day of the extreme against the close, when first; every chart), `judas-1.0.0` (00:00–05:00 and 09:30–10:00; previous day, Asia and London levels resting at the window's open, taken a tick beyond, then a close back through the open in the window; ≤ 30m); `ipda-1.0.0` clock rule (20/40/60 trading days before each day, one contract, no hole > 4 days). All read after the fact. |
| DEC-062 | **Ranges — one leg, read four ways** (`Ranges`): a displacement that breaks the latest swing, from where it began to the first intermediate-term turn, known when that rank is, only while price is inside, one range per turn; `dealing-range-1.0.0`, `pd-1.0.0` (halves at equilibrium, in half-ticks), `ote-1.0.0` (62–79% rounded outward, 70.5% line), `stdev-1.0.0` (−1, −2, −2.5, −4, rounded away); each range followed until broken high or low, or 500 candles. |
| DEC-061 | **Blocks, measured first:** `ob-1.0.0` (the last opposite-close candle(s) before a displacement leg that itself breaks the latest swing; run ≤ 5, ≤ 5 back, unbroken), `breaker-1.0.0` / `mitigation-1.0.0` (one flip shape: the candles at the broken swing, swept or not), `rejection-1.0.0` (wick ≥ half its candle **and** ≥ the 14-candle average range), `unicorn-1.0.0` (a breaker overlapped by its own displacement's gap); each block followed for its life — FORMED, TAPPED, MEAN_THRESHOLD, INVALIDATED, EXPIRED (500). |
| DEC-060 | **4h and daily candles count from the trading day's 18:00 New York open** (`Timeframe.bucketStart`, one place): 4h at 18, 22, 02, 06, 10, 14 (the last 3h), daily 18:00 → 17:00 named by the next day (no Sunday candle); up to 1h from the epoch; never through the UTC day cache; practice's coarse views reach back 30 / 120 days on real data; daily labels name the trading day (weekday only on a hidden date); no New York clock on daily charts. |
| DEC-059 | **The learning state, `learning-1.0.0`:** computed on every request from graded drills (real data only) and cards read, never stored; rated where a marking or naming drill grades (solid = ≥ 10 attempts and ≥ 80% over the last 20; attempts scored as the drill's result scores them); predictions are practice; unrated concepts done by reading; SM-2 reviews, one a day; the ladder's rules settled — it suggests, never locks. |
| DEC-058 | **Prediction drills (mode C), `predict-1.0.0`:** the chart stops at a moment, two targets drawn (nearest resting BSL vs SSL with obstacle counts; or after a level was taken, the untapped gap behind vs the next level beyond — ERL/IRL); answered by the first candle within 100 to reach either (ties by minutes, else never asked; never across a roll); question and outcome written at creation (V16), nothing after the moment sent before the answer; real data only. |
| DEC-057 | **A draft is not a trade; a placed trade can be closed at market.** Plan long/short only draws a draft (titled so, discardable); Place trade sends it. Close at market fills at the next fine bar's open (`fill-1.2.0`, `CLOSED`), stored in `practice_close` (V15) beside the untouched planned result; the plan's own stop/target wins a tie. |
| DEC-046 ⚠️ | **A session is a replay with several trades, one at a time.** Flat → in trade → flat; each trade numbered and immutable (V10); its result revealed when the replay passes its exit; the seed or date only when the session **ends** (learner ends it while flat, or a flat replay hits its limit); score over closed trades only. |
| DEC-044 | **Tailwind CSS 4 for all styling.** Tokens in `src/index.css` (`@theme`), shared with the chart; `components/ui.tsx` + `lib/styles.ts`; no other CSS. Never give one element two utilities for the same property. |
| DEC-043 | **Default ICT reading = the 2022 mentorship** (FVG entries after sweep + displacement MSS, midnight open); thin topics (blocks, PD matrix, IPDA, weekly profiles) from 2016–17, labelled; `sessions-1.0.0` = index-futures killzones. Every rule names its source. |
| DEC-042 | **Massive's free plan first** (2 years of CME 1m, 5 req/min, per-contract tickers — Tradeez picks the front contract by previous-session volume); Databento later for deep history and 1s. Key `MASSIVE_API_KEY` only in `backend/.env`. |
| DEC-040 ⚠️ | **Real NQ from Databento; the simulated market leaves the app.** Every download priced first, capped ($5/request, $100 total). Key only in `backend/.env`. `IMPORTED_PRIVATE` until display is licensed. |
| DEC-039 | FVG rule `fvg-1.0.0`: consecutive candles, gap ≥ 25% of the prior 14 candles' average range. **Never tune the generator to produce patterns for drills** — refuse the drill instead (1m). |
| ~~DEC-036~~ | ~~Spring Boot 3.5.x + Java 21 — NOT Boot 4.~~ **Superseded by DEC-056** (2026-10-15): Boot 3.5's free support ended 2026-06-30 and springdoc 3 supports Boot 4. |
| DEC-056 ⚠️ | **Newest versions everywhere.** Spring Boot 4.1 / Java 25 / Node 24 LTS / TypeScript 7 / Vitest 5. Keep dependencies at their newest; a major upgrade is done when it lands and the tests pass. Exceptions are written down: `@types/node` follows the runtime, PostgreSQL 17 keeps its data. |
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
| ~~Q-08~~ | ~~Croatian or English UI for the presentation?~~ **Resolved (Fabio, 2026-10-21): English only for now.** | 001 | closed |
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
- Backend 261 → 264, frontend 191 → 215. Pushed as `0f72da6`; fresh clone green (frontend 215 + build,
  backend 264 without `.env`).

**2026-10-14 — T8 merged.** Fabio questioned the "all passing" claim; both suites were re-run from
scratch and shown raw (frontend 215/215 exit 0, no warnings; backend `Tests run: 264, Failures: 0,
Errors: 0, Skipped: 0`, BUILD SUCCESS, zero ERROR log lines) — the failures he may have seen were
intermediate runs during development, each fixed. Merged `--no-ff` as `4321ae6`. Next: T9 on
`feat/detector-framework`.
**2026-10-15 — T9: detector framework, concept registry, overlay renderer (DEC-049).** On
`feat/detector-framework`, not merged:
- **S-1, the contract.** `Detector` (id, rule version, parameters, source, `conceptId`,
  `lookbackBars`) and `Detection` (shape ZONE/LEVEL/POINT, direction, span, anchor,
  **confirmation time**, ticks, evidence). `Segments` splits candles at rolls and says whether a
  pattern's candles are consecutive. `DetectorRegistry` holds every `Detector` bean; the FVG is one.
  `DetectorContractTest` checks every registered detector: deterministic, one hit per anchor and
  direction, causal (found again from exactly the candles closed by its confirmation, nothing sooner
  at any cut), honest lookback. Mutations tried and caught: a lookback two candles short, a
  confirmation one second early, one candle late.
- **Found while building it:** the first definition of `lookbackBars` did not count the pattern's
  own candles, and a time-based history window missed Friday behind a weekend — what a scan found on
  Monday morning depended on where it began. The scanner now hands over exactly `lookbackBars()`
  candles, reaching back up to ten days when the market was shut. Caught by the lookback test before
  any commit; not logged as a bug.
- **Scanning.** V12 adds `shape` and `confirmed_at` (the 95 local FVG rows backfilled — checked in
  the database, and by `DetectorFrameworkMigrationTest` on a schema migrated to V11 first).
  `SetupScanner.ensureScanned` for any detector, real or simulated; the drill path unchanged. Its
  cache remembers hits and stored minutes per stretch, so deleted rows or a new import mean a rescan.
- **Endpoints.** Chart setups (same limits as the bars, refactored into
  `MarketDataService.requirePublicRange`; whole-day scans), practice setups (one scan of the
  replay's window, filtered by the cursor, on the session's clock), `GET /api/concepts`. No evidence
  is sent. `SetupApiTest`: the chart equals the rule run by hand, a narrower window is the wider one
  cut at its edge, nothing spans a mid-session roll (the test first proves the unsplit candles would
  make one), a replay equals the chart over the same real stretch moved onto its clock, before and
  after 30 steps; strangers get 404. Mutations caught: the replay filtered by its scan end instead of
  the cursor; the roll split removed.
- **S-2.** The build copies `ICT_CONCEPTS.md` onto the classpath; `ConceptRegistry` reads 72
  concepts, their **What.** paragraphs, prerequisites and the ladder, and refuses to start on a
  malformed document or a detector whose rule the document does not list. F-3 was the one concept
  without a **What.** line; one was added.
- **S-3.** `SetupOverlay` (zones over their candles with CE dashed in half-ticks, levels, points),
  `SetupToggles` named from the registry, off by default; `timeToX` moved to `lib/chart`; the clock's
  toggle hook generalised (`useToggles`). A replay asks again whenever the cursor moves, one request
  at a time — the test holds an answer back and checks no second request goes out until it arrives
  (it fails without the guard).
- **Checked in the browser** (real NQ, local): Chart tab 5m, two weeks — 301 FVGs, green and red
  boxes painted; toggles on one row at 1920 px, no horizontal scroll. A replay: 129 setups, all
  confirmed before the cursor, on the fictional January 2000 week, each on a bar the learner has; each
  step followed by exactly one setups request. First scans 0.2 s (14 days 5m) and 0.5 s (160 days
  1h), repeats 33–51 ms. Screenshots at 1920 px kept timing out (window hidden), so that width was
  checked through the DOM.
- Backend 264 → 285, frontend 215 → 221.

**2026-10-15 — T9 fresh-clone check.** `7830517` cloned clean, no `.env`: frontend 221/221, lint,
typecheck, knip and build clean; backend `tests=285 errors=0 skipped=0 failures=0`, no `[ERROR]` lines.
The T9 story in DOCUMENTATION.md had called the FVG threshold "too loose" on real data before any
measurement — DEC-039's own yardstick was 3–7 gaps per 30 five-minute candles and real NQ gives about
3.3 — so it now says the question is measured in T10. Fabio: "merge it and then do T10".

**2026-10-15 — T9 merged.** Merged `--no-ff` as `a582b1a`. Next: T10 (imbalances and gaps) on
`feat/imbalances`.
**2026-10-15 — T10: imbalances and gaps (DEC-050).** On `feat/imbalances`, not merged:
- **Measured first.** Two years of real NQ minutes exported from the local database to a scratch
  script (not committed), aggregated as the app does; the script's FVG port first reproduced the
  Chart tab's 301 exactly. `fvg-1.0.0`: ~3 gaps per 30 candles on every timeframe 1m–1h — inside
  DEC-039's accepted range, so **kept, no `fvg-1.1.0`**. Volume imbalances of any size: ~7 per 30 on
  every timeframe (they do not scale), so `vi-1.0.0` takes a fixed 4 ticks (~1.6 per 30). Voids: the
  draft's 3 candles / 20% give about one a day on 5m; the longest run in two years was 6 candles.
- **Seven new rules** under DEC-049: `FVG_STATE` (fvg-state-1.0.0), `IFVG`, `BPR`, `VI`, `VOID`
  (kinds GAP, VOID), `OPENING_GAP` (NDOG, NWOG), `ORG` (org-1.0.0, split from opening-gap-1.0.0 so a
  detector names one concept). Shared: `FvgLifecycle`, `RuleDetector`. V13 adds `kind` to a hit's
  identity; `Detector.lookbackBars` now takes the timeframe; a layer carries its rolls.
- **Found while building it** (none shipped): the void draft let up-closing candles that each gapped
  *lower* count as a run, producing an inverted zone — a run must now progress; the visible-setups
  query ordered only by start, which the several states of one gap share — now a total order; my
  first script for the curriculum table matched the wrong 📐 marks and rewrote the legend and ICT-01
  to 05 — `ConceptRegistryTest` caught it, the file was restored from git and the rows redone line by
  line, the diff checked to touch only the ten intended lines.
- **Tests.** `DetectorContractTest` now runs on `PatternMarket`, a seeded synthetic market dense in
  every pattern (the simulated market never makes a volume imbalance), on 1m, 5m and 15m, with every
  lookback cut many times; every rule passes. `ImbalanceDetectorsTest` (17) pins each rule on hand-
  built candles — including a bearish CE at 194.5 reached at 195 and not 194; floor-dividing the
  midpoint was tried and caught. `SetupApiTest` checks the replay equals the chart over the same real
  stretch for all eight rules (its fixture now opens every 35th minute two points away, so volume
  imbalances exist), and that the roll is sent. Frontend: `shapes.ts` (where each rule's hits start
  and end) with its own tests; an ORG's quarters in the render test.
- **Checked in the browser**, real NQ, 5m, two weeks: 301 FVG, 79 BPR, 163 VI, 2 gaps, 8 voids,
  8 NDOG + 2 NWOG, 10 ORG; lifecycle 301 open → 268 filled, 262 inverted; IFVG = INVERTED. First scan
  of all eight 1.2 s, again 0.18 s. A replay with all eight: 946 hits, all confirmed by the cursor,
  all on the fictional week, no real timestamp in the response. The window stayed hidden, so the
  picture was checked by compositing the chart's canvases in the page and viewing the image: live gaps
  run right, finished ones fade, ORG and NWOG drawn. Fresh reload: no failed request.
- Backend 285 → 302, frontend 221 → 228.

**2026-10-15 — T10 fresh-clone check.** `c504625` cloned clean, no `.env`: frontend 228/228, lint,
typecheck, knip and build clean; backend `tests=302 errors=0 skipped=0 failures=0`, no `[ERROR]` lines.
Waiting for Fabio to say merge.

**2026-10-15 — T10 merged.** Merged `--no-ff` as `dcf699a`. Fabio: two years of data is fine for
now; asked for switchable timeframes in practice — **T34**, taken next, before T11 — and why 4h and
daily are missing: UTC-midnight buckets match New York only up to 1h (a 4h candle would straddle the
17:00 halt, a daily one hold two hours of the next trading day) — **T35**, session-aligned buckets.

**2026-10-15 — T34: any timeframe in a replay (DEC-051).** On `feat/practice-timeframes`, not merged:
- **Server:** `get`, `advance`, `skip` and `setups` take an optional view timeframe (1m–1h; 400 for
  others). A step starts from the candle the cursor is in, so a forming candle completes first and
  comes back whole; the cursor is held at the replay's limit. No provider change: real data folds the
  stored minutes up to the requested end, and the simulated day cache is used only on candle bounds.
- **Client:** a "Chart timeframe" switch in the replay header; it takes effect when the candles
  arrive. The hook lays returned bars over those from the first one's time; `PriceChart` now treats
  "same series, newest candle updated, maybe more after it" as a continuation (updateBar), not a new
  series — so the zoom survives. Clock, setups, finished trades and the bracket anchor use the view.
- **Tests:** four in `RealPracticeApiTest` (forming candle = minutes up to the cursor; a 15m step
  completes it then moves in whole candles; skip on 1h stops at the end of the exit's hour; setups
  on the view; 4h/1s/1d refused). Leak mutation (visible candles built to the candle's end) caught.
  Frontend: the switch end to end, and a mutation appending instead of merging caught; a chart test
  for the in-place update. Existing practice tests updated: steps now name their timeframe, and the
  test server's route matcher ignores the query string.
- **Checked in the browser** on real NQ: a replay at Fri 09:55 on 5m, switched to 1m, two steps to
  09:57, switched to 15m — the forming 09:45 candle equalled the 12 minutes revealed (09:45–09:56)
  exactly; one step on 15m moved the cursor to 10:00 and completed that candle in place (369 candles
  before and after, 15 minutes in it, no duplicate time); setups re-asked on 15m; no failed request.
- Backend 302 → 306, frontend 228 → 230.

**2026-10-15 — T34 fresh-clone check.** `3417263` cloned clean, no `.env`: frontend 230/230, lint,
typecheck, knip and build clean; backend `tests=306 errors=0 skipped=0 failures=0`, no `[ERROR]` lines.

**2026-10-15 — T34 merged.** Merged `--no-ff` as `ec617ca`. Next: T11 (market structure) on
`feat/market-structure`.

**2026-10-15 — T11: market structure (DEC-052).** On `feat/market-structure`, not merged:
- **Measured first** (scratch scripts on the two years of real NQ, not committed): swings with one
  candle each side ~13.5 per 30 candles on every timeframe; an ITH's span ≤ 36 candles, an LTH's
  ≤ 160; displacement tied to its gap at 2× the recent range = about half of all gaps (1.5 per 30 on
  5m). The draft's numbers kept.
- **Six rules** on a shared `SwingPoints`: `SWING`, `SWING_TIER`, `STRUCTURE`, `BOS_CHOCH` (ICT-06,
  same `structure-1.0.0`), `DISPLACEMENT`, `MSS`. Each reads at most a fixed span to its
  confirmation (200, ranks 300): finite lookback for rules over swings any distance back. Lookbacks
  understated (MSS 50, ranks 100) were tried and caught by the contract test.
- **Found while building it:** (1) the drafted MSS required the displacement to be known by the
  breaking close, but the displacement candle is often the one that breaks and its gap is known a
  candle later — the commonest shift would never fire. Found by building that case by hand; now
  confirmed at the later of the two, and the old behaviour fails the test. (2) The first MSS code
  rescanned every swing at every candle: the contract test passed 10 minutes and was stopped (its
  stray Maven and test JVMs ended by hand). Rewritten — the contract test with fourteen rules takes
  ~12 s. (3) My first expectations for the hand-built tests were wrong twice — the ends of a zigzag
  cannot be swings, and the MSS candles held two extra gaps — corrected before the run.
- **Tests:** `StructureDetectorsTest` (9), the contract test for all fourteen rules, `SetupApiTest`'s
  replay-equals-chart for all fourteen (each found something in the replay's window). Frontend: points
  above highs and below lows, ranks and labels named, breaks as lines.
- **Checked in the browser**, real NQ 5m, two weeks (2,760 candles): 622 swing highs and 610 lows,
  168 ITH / 147 ITL / 46 LTH / 42 LTL, 312 HH / 308 LH / 325 HL / 282 LL, 145 BOS / 124 CHoCH,
  129 displacement legs, 43 MSS. First scan of the six 1.5 s, again 0.15 s. A real screenshot showed
  ranks over and under the swings, BOS/CHoCH and MSS lines, displacement boxes. The sign-in had
  lapsed during the long test runs; signed in again with the local test account.
- Backend 306 → 315, frontend 230 → 233.

**2026-10-15 — T11 fresh-clone check.** `a917c2d` cloned clean, no `.env`: frontend 233/233, lint,
typecheck, knip and build clean; backend `tests=315 errors=0 skipped=0 failures=0`, no `[ERROR]` lines.
Fabio: "merge it and then do T12".

**2026-10-15 — T11 merged.** Merged `--no-ff` as `57af061`. Next: T12 (liquidity) on `feat/liquidity`.

**2026-10-15 — T12: liquidity (DEC-053).** On `feat/liquidity`, not merged:
- **Measured first** (scratch script on the real minutes, not committed): half of all swing levels are
  taken within 5 candles of their confirmation, 95% within 500; of those taken, ~50% are swept (closed
  back the same candle), 29% run, 21% undecided — every timeframe alike. EQH/EQL at the draft's
  tolerance: 1.3–2.2 per 30 candles. The draft numbers kept; a 500-candle memory, as for gaps.
- **Four rules** on a shared `LiquidityLevels`: `LIQUIDITY` (RESTING / TAKEN / EXPIRED), `EQHL`,
  `SWEEP` (SWEEP / RUN; a candle taking several levels on a side is judged on the most extreme), and
  `TURTLE_SOUP` (ICT-14, `sweep-1.0.0`) on equal-highs/lows pools.
- **The clock's reference levels** (PDH/PDL, PWH/PWL, killzone highs and lows) now carry the minute that
  took them, from the minutes before the layer's end only; in a replay `nextChange` becomes the next
  minute while one rests — it says a level could be taken, never when. Clock lines end at the take
  with a small cross. Three clock tests: first minute beyond (a touch is not enough), no lookahead,
  the clock's own moments when nothing rests.
- **Found while building it:** (1) the setups endpoint refused more than 16 rules per request — T12
  makes 18, so every switch on would have broken the overlay. Raised to 32; the contract test now
  fails if the rules outnumber it. (2) The cross marking a taken clock level was centred half a pixel
  off its line; the new render test caught it.
- **Tests:** `LiquidityDetectorsTest` (8; inverting the most-extreme choice was tried and caught), the
  contract test for all eighteen rules, `SetupApiTest`'s replay-equals-chart for all eighteen.
- **Checked in the browser**, real NQ 5m, two weeks: 622 BSL / 610 SSL levels (= the swings), 1,149
  taken, 77 expired; 462 sweeps and 176 runs (fewer judged than taken: one candle often takes several
  levels and is judged once); 159 EQH/EQL; 68 turtle soups; 53 of 102 clock levels taken (PDH/PDL 10
  of 20), every take inside its level's span. A real screenshot showed resting BSL/SSL running right,
  taken ones short and faint, turtle soups labelled, and the PDH ending where price crossed it.
- Backend 315 → 326, frontend 233 → 235.

**2026-10-15 — T12 fresh-clone check.** `da5f361` cloned clean, no `.env`: frontend 235/235, lint,
typecheck, knip and build clean; backend `tests=326 errors=0 skipped=0 failures=0`, no `[ERROR]` lines.
Waiting for Fabio to say merge.

**2026-10-15 — T12 merged.** Merged `--no-ff` as `1cb0c52`. Fabio: "merge it and then do T13". Next: T13
(drill engine) on `feat/drill-engine`.
**2026-10-15 — T13: the drill engine (DEC-054).** On `feat/drill-engine`, not merged:
- **Ten drills, one engine.** `DrillKind` is the catalogue: seven *spot* drills (mode A, 30 candles —
  FVG, swing highs, swing lows, VI, displacement, EQH/EQL, sweeps) and three *name* drills (mode B,
  40 candles — structure label, BOS or CHoCH, sweep or run). A new drill is one constant (and a row in
  V14's `ck_drill_kind`); window, key, grading and explanations are shared.
- **Real NQ, graded.** Default NQ, 1m/5m/15m; a random stretch of the imported history, open
  throughout, every candle present, no roll inside; `graded` exactly when the data is real. DEMO-NQ
  keeps only the FVG fixture (DEC-034), and any other drill on it is refused with the reason.
- **Key = stored hits shown whole** (the scanner the overlay uses): from the candle before a swing
  start to the confirming candle. **Excused** hits — anchored in the window but cut by it — count
  neither way (V14 `excused`); before, a mark on one counted as extra (BUG-050, found by the
  fixture's partial test landing on one). **Name drills** send only side, level start, candle and
  price; BOS/CHoCH asks only breaks whose four structure swings are on screen (stored `STRUCTURE`
  hits). V14 `choice`; an answer is marks or a choice, never both.
- **Explanations moved server-side** (`DrillText`): every hit from its evidence, every rule's
  definition from its parameters, prices in ticks, no times. The FVG wording unchanged; its tests
  moved to Java. `GET /api/drills/kinds`. Removed as dead: `SetupScanner.scanFvgs`,
  `findSimulated`, the client's `explainFvg`.
- **Frontend:** a drill picker grouped Spot / Name, 1m/5m/15m, marks or name buttons, subject drawn
  (dashed level + arrow, or a ring on a swing), answers drawn by shape, recall and precision against
  the rule, excused marks named.
- **Tests:** `RealDrillApiTest` (16, on imported minutes: every kind, key completeness, excused marks,
  modes, immutability, timeframes, no history), `DrillTextTest` (7), the fixture test kept. Mutations
  tried and caught: dropping the excused list, never grading, skipping the BOS structure check,
  accepting any choice, letting a name drill take marks.
- **Checked on real NQ** (dev database, two years): every drill on every timeframe found a window,
  35–274 ms; 105 more at 5m: none failed, median 40–60 ms. Excused per drill: sweeps 2.1 (88% of
  drills), EQH/EQL 1.0, the rest 0–0.5. In the app at 1920 px: a BOS/CHoCH drill drawn and named
  (the explanation agrees with the chart), an FVG drill marked by real clicks and graded.
- Backend 326 → 350, frontend 235 → 236.

**2026-10-15 — T13 fresh-clone check.** `778c306` cloned clean, no `.env`: frontend 236/236, lint,
typecheck, knip and build clean; backend `tests=350 errors=0 skipped=0 failures=0`, no `[ERROR]` lines.
Waiting for Fabio to say merge.

**2026-10-15 — T13 merged.** Merged `--no-ff` as `e4f7cba`. Fabio: "Merge and go". Next: T14 (lesson
cards) on `feat/lesson-cards`.

**2026-10-15 — T14: lesson cards (DEC-055).** On `feat/lesson-cards`, not merged:
- **Learn tab:** the curriculum ladder of `ICT_CONCEPTS.md` (72 concepts by level, detected ones
  marked), opening on ICT-01; a card per concept — the document's What, level, detectability, status,
  "know first" and "builds toward" links, the rules in words, where the app draws it, "Practise it"
  (opens the Drills tab on that drill), and three real NQ examples. `GET /api/concepts/{id}/lesson`.
- **Examples at random, never picked:** each from its own random two-day stretch, a random hit (whole
  ones first), 32 candles up to the candle it is known by and 8 after, no roll, no overlap; drawn by
  the Setups renderer with a faint band on the hit; caption with the hit's candle, date and when it was
  known. On real NQ all 18 detected concepts gave three examples at 5m, 121–613 ms each.
- **Every rule in words:** `drills/DrillText` → `analysis/RuleText`, extended to all 18 rules
  (definitions, labels, explanations). `DetectorContractTest.words`: every numeric parameter appears in
  its rule's definition, and every hit on the pattern market renders. Lifecycle and liquidity events say
  "a later candle".
- **Tests:** `LessonApiTest` (24, incl. every detected concept), `RuleTextTest` (9), `DrillKindTest`,
  the contract's `words`, a `DrillView` start-on-a-drill test, `LearnView` (6), `formatSessionDate`.
  The three-week NQ import used by three tests is now one helper, `ThreeWeeksOfNq`. Mutations caught:
  a misspelt evidence key, a parameter dropped from a definition, deterministic examples, a dead
  "Practise it", a caption without when-known.
- **Environment:** ENV-007 — an npm update made outside the session (undici 8.12.2 needs Node ≥ 22.19)
  stops vitest here; left for Fabio, kept out of the commit, tests run on the committed lockfile in a
  scratch copy. ENV-008 — two mutation runs at once swapped two files; rebuilt and re-tested.
- Backend 350 → 378, frontend 236 → 244.

**2026-10-15 — T14 fresh-clone check, first run: one failure.** `6e33ebc` cloned clean: frontend
244/244, lint, typecheck, knip, build clean; backend 378 with 1 failure — `SetupApiTest.practice`, whose
random replay window held no MSS (BUG-051, a flaky assertion since T11–T12). Fixed: the replay must
cover at least 12 rules, and a new `everyRule()` checks all 18 over a fixed fortnight. Backend 379.

**2026-10-15 — T14 fresh-clone check.** `6e6d152` cloned clean, no `.env`: frontend 244/244, lint,
typecheck, knip and build clean; backend `tests=379 errors=0 skipped=0 failures=0`, no `[ERROR]` lines.
Waiting for Fabio to say merge. ENV-007 still open (his call).

**2026-10-15 — The upgrade to the newest versions (DEC-056).** Fabio: "update undici and everything to
the newest version, dont leave it lagging behind that leaves holes for hackers." On
`chore/upgrade-dependencies` (branched from `feat/lesson-cards`), not merged:
- **ENV-007 closed.** The outside npm update was discarded first (the project's Node was 22.14); that
  showed the old undici had five high-severity advisories. Node 24.20 LTS installed with winget (Fabio
  chose LTS over 26 Current), npm 12.2.
- **Frontend:** every package newest — React 19.3, Vite 8.3, Vitest 5, TypeScript 7, jsdom 30.1
  (undici 8.12.2), oxlint 1.88; `@types/node` 24 to match the runtime; `engines` Node ≥ 24.20;
  `npm audit` 0. `openapi-typescript` (needs TS 5's compiler API, gone in TS 7) runs through `npx`
  with its own TypeScript and left the dependencies (27 packages fewer).
- **Backend:** Spring Boot 3.5 was past its free support (2026-06-30). Now Boot 4.1.1 (Spring 7,
  Security 7.1, Hibernate 7.4, Jackson 3.1, Flyway 12.4, Testcontainers 2.0, JUnit 6), springdoc 3.1.1,
  jjwt 0.13, PostgreSQL driver 42.7.14, Lombok 1.18.48, Java 25 target, Maven 3.10.0. Changes: Boot 4's
  split starters, Testcontainers' new names, Jackson 3 imports and `asString`. All 379 tests passed.
- **PostgreSQL** container moved to the freshly built 17-alpine image (still 17.11, the newest 17.x),
  after a verified backup in the scratchpad; 697,003 bars intact.
- **Found and fixed:** BUG-054 (a rejected password went to the server log — now one validation
  handler for every controller, logging nothing and naming only field and rule), BUG-052 (the zone
  button named today's zone, not the chart's), BUG-053 (`api:types` pointed at another app's port).
- **Checked live** on the new stack: sign-in, chart bars, setups, clock, a lesson, a drill answered, a
  practice session advanced, a validation reply, the January chart reading EST.
- Backend 379 (one test reworked), frontend 244 → 245.

**2026-10-15 — Fresh-clone check of `chore/upgrade-dependencies`.** `e2ab13c` cloned clean, no `.env`,
on Node 24.20 / npm 12.2 / JDK 26 / Maven 3.10: backend `tests=379 errors=0 skipped=0 failures=0`, no
`[ERROR]` lines; frontend `npm audit` 0 vulnerabilities, lint, typecheck (TypeScript 7), Vitest 5
245/245 and build clean; knip clean — on its third run: the first two stopped allocating parser
memory (ENV-009, open, harmless). Waiting for Fabio to say merge; merging this branch brings T14 too.

**2026-10-15 — Fabio's follow-ups to DEC-056.** On `chore/upgrade-dependencies`:
- **Global npm tools** updated too ("u can change the global tools as well"): Bruno CLI 3.5 → 4.2.1,
  nodemon 2.0 → 3.1.14, TypeScript 5.1 → 7.0.2, Vite 4.4 → 8.3.4.
- **undici 8.12.2 is the newest** (its `latest` tag, published today); jsdom asks for `^8.12.1`.
- **openapi-typescript on TypeScript 5, properly:** its own tool package `frontend/tools/api-types`
  (TypeScript 5.9.3, openapi-typescript 7.13.0, own lockfile, 0 vulnerabilities) instead of an
  unlocked `npx` download. Run against the dev backend: the generated types include the lesson and
  drill DTOs and typecheck under the app's TypeScript 7 — so the app stays on 7.
- **ENV-009 fixed:** knip as a locked dev dependency, `npm run knip`, with
  `KNIP_DISABLE_RAW_TRANSFER=1` from `knip.env`; a planted unused export is still caught.
- **PostgreSQL:** asked; Fabio keeps 17 for now.

**2026-10-15 — Fresh-clone check of `49ca427`** (frontend only changed since `e2ab13c`, whose backend
was green): `npm audit` 0 in the app and in `tools/api-types`, lint, typecheck, `npm run knip`,
245/245 and build clean. Waiting for Fabio to say merge; merging this branch brings T14 too.

**2026-10-16 — T14 and DEC-056 merged.** `chore/upgrade-dependencies` merged `--no-ff` as `37f8513`.
Fabio: "merge it and then do T15, but i think we have a problem" — in practice, a stop or target being
hit does not close the trade, and a trade cannot be cancelled once placed. Those first (bugs before new
work, the working agreement), then T15.

**2026-10-16 — Fabio's practice report: BUG-055, BUG-056, DEC-057.** On `fix/practice-trades`, not merged.
Fabio: a stop or target being hit "doesnt close the trade", and "there is no way to cancel trade once
you press go long or go short".
- **Diagnosis first.** The server was right: his own session's trades exited at exactly the first
  1m bar touching the stop or target (checked in the DB), and a lock-then-step replay through the API
  and the UI settled at once. The fault was the screen: "Place long" only drew an unlocked draft that
  looked like a position; nothing traded until "Lock plan", and the draft had no discard (BUG-055).
  And a placed trade could not be left early (BUG-056).
- **Draft UX:** *Plan long / Plan short*, the panel titled *Draft · long at market* with a *not placed*
  chip, **Discard**, *Place trade* (was *Lock plan*); the running trade's panel says *placed*.
- **Close at market (DEC-057):** `POST /api/practice/sessions/{id}/close` — fills at the open of the
  first fine bar (1s or 1m) at or after the cursor, no slippage, commission charged
  (`FillEngine.closeAtMarket`, `fill-1.2.0`, `Outcome.CLOSED`); the cursor moves one fine bar past the
  fill. If the stop or target was touched before, or at that very open, the plan's exit is revealed
  instead (as Skip). Stored in `practice_close` (V15, immutable trigger) beside the untouched
  `practice_result`; score, journal and trade log read the close.
- The result card's "How this was decided" says how a close fills.
- Tests: FillEngineTest +5 (and an MFE check on the older gap-through-target path, which a misaimed
  mutation showed was unchecked), PracticeApiTest +4, PlanPanel.test +3, PracticeView.test +2 (old
  texts updated). Mutations, one at a time: 13 backend and 7 frontend, all caught.
- **Checked live** at 1920px on real NQ: draft, Discard, Place trade, Next bar, Close at market →
  "Closed by you", −$2.50 (filled at the 1m open, which happened to equal the entry; MAE 16 / MFE 26
  ticks match the stored bars), trade log and score updated, `/close?timeframe=5m` 200, V15 applied.
- Backend 379 → 388, frontend 245 → 250; tsc, lint, knip, build clean.

**2026-10-16 — Fresh-clone check of `60b666f`** (`fix/practice-trades`), no `.env`: backend
`tests=388 errors=0 skipped=0 failures=0`, no `[ERROR]` lines; frontend `npm audit` 0 vulnerabilities,
lint, typecheck, `npm run knip`, 250/250 and build clean. Waiting for Fabio to say merge; then T15.

**2026-10-16 — The practice fix merged.** Fabio: "merge". `fix/practice-trades` merged `--no-ff` into
`master` as `7f013a9` (BUG-055, BUG-056, DEC-057). Next: T15, prediction drills.

**2026-10-16 — T15: prediction drills (DEC-058).** On `feat/prediction-drills`, not merged.
- **ENV-001 recurred** first: Docker Desktop had quit entirely, taking the database and the backend
  with it. Started again (documented fix); the database came back intact.
- **Measured first**, on two years of real NQ exported from the dev database (scratchpad script, rules
  re-implemented): which of the nearest resting BSL/SSL is taken first — askable 77–83% of windows,
  ties ≈ 1%, neither within 100 candles ≤ 0.4%, buy-side 51–53%, nearer 68–69%, fewer obstacles 65–67%;
  after a level is taken, the untapped gap behind first 39–47% (≈ 5% of windows qualify).
- **Built:** mode C in the drill engine — `Prediction` (`predict-1.0.0`: targets from the moment's stored
  `liquidity-1.0.0` and `fvg-state-1.0.0` hits, ERL/IRL roles, obstacle counts, resolution by the first
  candle to reach either, ties on one-minute candles, else not asked; 100-candle horizon cut at a roll),
  `PredictionText` (rule, labels, explanation in prices), two drills (`LIQUIDITY_FIRST_PREDICTION` ICT-16,
  `ERL_IRL_PREDICTION` ICT-15), V16 (`question`, `outcome`, `outcome_at`, `reveal_to`; checks that a
  prediction has all of them, never before the window's end, and never on simulated data). Frontend:
  the Predict group, targets and context drawn and labelled, each target's distance and obstacles, the
  answer reveals the hidden candles with a "chart ended" line and the deciding candle ringed, the chart
  reframed around the whole story; lesson cards name prediction drills ("Predict: …") — found missing
  while building, fixed before commit.
- **Through the app** on the dev database: 240 drills, every one found a moment, median 95–145 ms (max
  536 ms); stored outcomes matched the measurement. Checked live at 1920px: both drills, answered, the
  reveal reframed.
- Tests: PredictionTest (11), RealPredictionApiTest (12: nothing after the moment before the answer,
  on create or reload; targets resting and on screen; the answer checked against stored candles and
  minutes independently of the engine; the roll cut; the database rules), DrillView (+3), LearnView
  (+1), catalogue and registry tests updated. Mutations, one at a time: 18 backend, all caught in the
  end — two first survived (a level whose swing is off the chart; gap states confirmed after the moment,
  a lookahead), so the selectors moved into `Prediction` over a plain `Hit` record and got direct tests;
  8 frontend, all caught.
- Backend 388 → 413, frontend 250 → 254; tsc, lint, knip, build clean.


**2026-10-16 — Fresh-clone check of `3749d91`** (`feat/prediction-drills`), no `.env`: backend
`tests=413 errors=0 skipped=0 failures=0`, no `[ERROR]` lines; frontend `npm audit` 0 vulnerabilities,
lint, typecheck, `npm run knip`, 254/254 and build clean. Waiting for Fabio to say merge; then T16.

**2026-10-16 — Bug hunt and missing tests, on Fabio's request.** On `test/practice-coverage` (from
`feat/prediction-drills`), not merged. Fabio: "check for bugs now. any sneaky bugs", "write new tests
for parts that dont have them", and tests "for tp sl and stuff like that in the practice section". He
had noticed the test count looked flat: the number of frontend test *files* had stayed 23–24 since T10,
and frontend tests grew by only 1–3 for T11–T13 (backend kept growing).
- **Coverage measured, then kept as tooling:** backend 95.3% of lines / 88.4% of branches (practice
  97%), frontend 82.6% / 73.9%. Weakest: the drill overlay's painting (18%, jsdom has no canvas), the
  keyboard shortcuts (33%), resuming a practice session, and — found this way — the per-address sign-in
  limit's refusal, which no test had ever made happen (it works: now tested).
- **TP/SL end to end on real NQ:** 175 random brackets through the dev API (long and short, 1–320
  ticks, 34 ambiguous) and 45 closes at market, each recomputed from the stored minutes by a separate
  implementation — outcome, entry, exit, P&L, net, commission, MAE, MFE, gap flag, reference: all agree.
- **Found and fixed:** BUG-057 (Close at market refused with Skip's message), BUG-058 (2.7 contracts
  printed "$553.50.000000000007276"), BUG-059 (a target gap explained as if it were a stop), BUG-060
  (a test that passed only by test-class order). Reviewed and kept, as designed: an ambiguous result's
  best point counts the target (price did reach it); an entry opening past the target is a 0-tick
  "target hit", as a real bracket would be.
- **New tests:** `BracketOutcomeApiTest` (10 — worked TP/SL examples through the API on hand-written
  minutes: long and short targets and stops, a tick short, gaps either way, entry at the next price
  with R from the plan, three contracts, ambiguity, the session close, a close at market; and 40 random
  brackets against an independent replay), FillEngineTest +7 (the open exactly at a level, entries past
  a level, a short's ambiguity and time exit, contracts), the per-address limit, ResultCard.test (8),
  PracticeView +8 (a short end to end, flip, Next 10, play stopping on an error, resume, review, a load
  failure), useDrawingShortcuts.test (5), DrillOverlay.test (3, with a recording canvas now shared in
  `src/test/canvasRecorder.ts`), plan.test +4. Mutations, one at a time: 13 backend on TP/SL (slippage the wrong way, a stop or target needing a tick
  beyond, a gapped stop not slipped, an exact open called a gap, ambiguity resolved for the trader, entry
  at the close, the time exit at the open, commission once per trade, R off its base, the session an hour
  long, the reference from an older minute, ambiguity unlabelled) and 11 frontend — all caught.
- Backend 413 → 433, frontend 254 → 283 (24 → 27 files). Coverage after: backend 95.4% of lines / 88.8% of
  branches (it was already high; the new backend tests check that the numbers are right), frontend
  88.3% / 79.6% (from 82.6% / 73.9%).


**2026-10-16 — Fresh-clone check of `53d7c3c`** (`test/practice-coverage`), no `.env`: backend 433 test
cases, 0 failures, 0 errors, no `[ERROR]` lines (counted as `<testcase>` elements — the reports' headers
undercount nested classes, ENV-010); frontend `npm audit` 0, lint, typecheck, knip, 283/283, coverage
88.3% of lines, build clean. Waiting for Fabio to say merge (it brings T15 too); then T16.

**2026-10-16 — T15 and the bug hunt merged.** Fabio: "merge it and do T16". `test/practice-coverage`
(which carries `feat/prediction-drills`) merged `--no-ff` as `581b30a`. Next: T16.

**2026-10-16 — T16: the learning state (DEC-059).** On `feat/learning-state`, not merged.
- **Design:** nothing about progress is stored — `learning-1.0.0` reads it afresh from graded drills
  (immutable once answered) and V17's `concept_read` (immutable; a learner's own "Mark as read").
  Rated concepts are the eight a marking or naming drill grades; each attempt is scored as the drill's
  own result scores it (`drills/DrillAttempts`). Solid: ≥ 10 attempts, ≥ 80% over the last 20.
  Predictions are practice. Unrated concepts are done when read or practised. SM-2 reviews, one per
  concept per UTC day. Part 12's placeholders settled in `ICT_CONCEPTS.md` (the F-5 risk drill waits for
  T26); the ladder suggests the first open, incomplete concept and never locks a card.
- **Frontend:** the Learn tab's summary (solid, complete, next, due), a mark per concept and a line per
  level in the ladder (now a named landmark), and a "Your progress" panel on every card with *Mark as
  read* and the rule in words.
- **Found and fixed:** BUG-061 — the ladder's buttons were named "F-1Candle anatomy", and an old test
  had been clicking a link on the card instead of the list.
- **Checked live** on the local test account (47 graded drills from earlier testing): all eight rated
  concepts learning and due, the two prediction concepts done by use, F-1 suggested; marking F-1 read
  (a permanent record on that test account) completed it.
- Tests: LearningRuleTest (17), LearningApiTest (8: the start, reads opening level 1, ten perfect drills
  making a concept solid, partial and excused marks scored as the result scores them, naming, simulated
  fixtures and unanswered drills never counting, predictions as practice, two learners apart),
  LearnView +4. Mutations, one at a time: 18 backend (solid without ten attempts or only above 80%,
  accuracy over every attempt, predictions rating, a lapse keeping its interval, every attempt a review,
  due a day late, a half passing, liquidity opening with the foundations, an empty level complete, entry
  models ignoring prerequisites, a read not completing, a complete concept suggested, a second read
  failing, simulated drills counting, excused marks counting against, extra marks free, naming always
  right) and 7 frontend — all caught; three first survived and got their tests (an empty level, entry
  models, the suggestion button).
- Backend 433 → 460, frontend 283 → 287.

**2026-10-16 — Fresh-clone check of `f6ad72a`: one failure, fixed.** `BracketOutcomeApiTest.randomBrackets`
failed its own sample check (no exit at a gapped open) — the sessions started at random moments, so the
sample changed between runs (BUG-062). Every bracket still agreed with the independent replay. The moments
are seeded now; green twice locally.

**2026-10-16 — Fresh-clone check of `673d86c`** (`feat/learning-state`), no `.env`: backend 460 test cases,
0 failures, 0 errors, no `[ERROR]` lines; frontend `npm audit` 0, lint, typecheck, knip, 287/287, build
clean. Waiting for Fabio to say merge.

**2026-10-16 — T16 merged.** Fabio: "sure then merge and continue". `feat/learning-state` merged `--no-ff` as
`a16f0d1`. Next: T35, session-aligned 4h and daily candles.

**2026-10-16 — T35: session-aligned 4h and daily candles (DEC-060).** On `feat/session-candles`, not merged.
- **One place decides where a candle starts:** `Timeframe.bucketStart`. Up to 1h from the epoch, as before;
  4h and 1d from the trading day's 18:00 New York open — 4h at 18, 22, 02, 06, 10, 14 (the last three
  hours long), a daily candle 18:00 → 17:00 and named by the next day. Aggregation, practice's steps and
  its forming candle all use it; `DayBarCache` keeps out of 4h/1d (they straddle UTC days). Safe across
  clock changes because New York changes clocks while CME is shut.
- **Offered on the Chart tab and in practice.** Practice's coarse views reach back 30 days (4h) and 120
  days (1d) before the hidden moment, real data only. Daily candles are labelled by trading day ("Mon, 9
  Jun 2025"; the weekday alone on a hidden date). The frontend reads where a candle starts from the
  chart's own candles (`candleAt`) — a finished trade's box, the running trade's anchor — instead of
  `time − time % step`, which is wrong for both new timeframes.
- **Found and fixed while checking it in the browser:** BUG-063 — the New York clock vanished on 4h/1d
  (window wider than the clock's 200 days; 4h window now 200 days, no clock on daily, the switches say
  why). BUG-064 — on coarse charts a detector got less history than its lookback (one ten-day margin for
  closures; a daily chart lost two days in seven), so results could depend on where a scan began; it
  now reaches back until it has the candles. Measuring it: each rule read two years of minutes and
  looked up their rolls for itself — now once per request; nine rules on a cold daily chart 9.8 s → 3.0 s,
  practice 1d 4.1 s → 1.1 s, 4h 1.2 s → 0.34 s, the same hits. ENV-011 — a mutation run counted a
  build that never started as kills; both runners now need a passing baseline and a failing test.
- **Checked live** (Chart tab at 1920 px, then a practice replay): 171 daily candles over eight months, all
  opening 18:00 New York on Sunday–Thursday evenings; 4h candles only at the six hours (four fewer at
  14:00 — early-close holidays); 400 days of daily candles in 1.2 s; the crosshair reads "Fri, 21 Aug
  2026"; on a hidden date "Wed"; the clock on 4h, "On intraday charts, up to 4h" on daily.
- Tests: TimeframeTest (6, including both sides of 9 March and 5,000 random moments), BarAggregatorTest
  +2, DayBarCacheTest, RealPracticeApiTest +1 (`sessionCandles`, a session planted after the clock
  change: hidden-clock hours, exactly 8 daily and 47 four-hour candles of history, the forming candle,
  a daily step returning the whole day and landing on the next 18:00), ScanHistoryTest (4),
  SetupScannerTest +1 (together = alone); frontend timeAxis +3, timeToX +2, plan +1, dateNavigation +1,
  ClockToggles (2).
- Mutations, one at a time after a passing baseline. 14 backend (the open at 17:00, counting from the
  coming open, 4h from the epoch or on epoch multiples, the open read in Chicago, aggregation ignoring
  the anchor, the UTC day cache, practice refusing 1d, no extra history, practice stepping from epoch
  starts, one widening only, rolls ignored, an unscanned stretch trusted): 13 caught, two of them only
  after `sessionCandles` was planted late and made to check the day a step returns. The 14th, every
  detector given the longest lookback, is equivalent: DetectorContractTest proves a detector finds the
  same with more history. 14 frontend (candle lookup, trading-day labels and zones, the hidden date,
  finished-trade boxes, window sizes, the timeframe list, the clock on daily): all caught.
- Backend 460 → 474, frontend 287 → 296.

**2026-10-16 — Fresh-clone check of `48bab07`: one failure, fixed.** `DrillApiTest.answerKeyIsTheRule` — its
independent recomputation had five candles of context for a drill window opening at 18:25, just after the
daily reopen, and missed a gap the app had found correctly (BUG-065, a test bug: recomputing that exact
window with days of context gives exactly the app's answer key). Everything else green: backend 473 of 474,
frontend npm audit 0, lint, typecheck, knip, 296/296, build.

**2026-10-16 — Fresh-clone check of `71c6e46`** (`feat/session-candles`), no `.env`: backend 474 test cases,
0 failures, 0 errors, no `[ERROR]` lines; frontend `npm audit` 0, lint, typecheck, knip, 296/296, build
clean. Waiting for Fabio to say merge.

**2026-10-16 — T35 merged.** Fabio: "merge it and do T17". `feat/session-candles` merged `--no-ff` as
`25fc962`. Next: T17, blocks.

**2026-10-16 / 17 — T17: blocks (DEC-061).** On `feat/blocks`, not merged.
- **Measured first** (scratchpad script on the two years of real NQ; swings, gaps and displacement
  re-implemented; 1m, 5m, 15m, 1h alike): ~1 order block per 30 candles; three displacement legs in
  four break a swing inside the leg (three candles more would add one in twenty); the block's candle
  right before the leg 70–78%, within 5 in 99%; runs ≤ 5 in 97%; 92% traded back into (median 8–9
  candles), 83% invalidated within 500. Breakers 0.4 / 30, mitigation blocks 0.5; unicorns 76% of
  breakers. The draft rejection block matched a third of all swings (4.5 / 30, traded back into the
  next candle more often than not) — a long wick is now also ≥ the 14-candle average range: 0.5 / 30.
- **Five rules** on two shared helpers: `Blocks` (the run, the break, the block, its life) and `Flips`
  (breaker and mitigation are one shape, swept or not; the unicorn reads the breaker's own leg and gap).
  Every block is followed for its life — FORMED, TAPPED, MEAN_THRESHOLD, INVALIDATED, EXPIRED — as a
  gap is. Breakers anchor on the broken swing (one per swing, no duplicates by construction); order
  blocks dedupe a candle two legs claim, which is why their lookback has 10 more candles than 200 + 500.
  MSS's `latestConfirmedBefore` moved into `SwingPoints` for the blocks to share.
- **On the chart:** five switches; a block's box runs on while it stands and fades once invalidated or
  expired; its dashed line is the mean threshold — the middle of its bodies, sent as `midHalfTicks`
  (a price, never a time). Zones are now named where they come into view, not off the left edge where
  a long-lived one began (found looking at it).
- **Found while building it:** the contract test's synthetic market had no long wicks, so a rejection
  block could never be found there (it now leaves one now and then); SetupApiTest's replay-equals-chart
  list had stopped at T12's 18 rules — the five blocks are in it now, all found in its fortnight.
- **Checked live** on real NQ 5m, two weeks (2,760 candles): 74 order blocks, 32 breakers (20
  unicorns), 46 mitigation and 38 rejection blocks — the measured rates; all 23 rules cold 3.8 s,
  warm 0.5 s. A daily chart finds almost none: 281 daily candles hold 12 gaps and 3 displacements,
  since fvg-1.0.0 asks for three consecutive candles and every weekend breaks a daily triple — a
  question for the gap rule, not this task.
- Tests: BlockDetectorsTest (17: forming, every state, expiry, an invalidated block not expiring, a
  break outside the leg, the block two candles back, not across a closure, a run stopping at one, a
  swing beyond the 200-candle reach, breaker / mitigation / equal lows / unicorn, rejection forming,
  too short, its life), the contract test for all 23,
  SetupApiTest +1 (midlines) and its list +5, ConceptRegistryTest updated; frontend shapes +2,
  render +2. Mutations, one at a time after a passing baseline: 25 backend — 22 caught at once; two more
  (a run merged across a closure, no bound on what a block reads) after the two tests named above were
  written for them; the last, the lookback without its 10-candle margin, survives: the bound needs the
  margin (a second leg claiming one candle is known up to 5 candles later), but no test market meets
  that case, and a longer lookback is never wrong. 7 frontend, all caught.
- Backend 474 → 492, frontend 296 → 300.

**2026-10-17 — Fresh-clone check of `a9c42a8`** (`feat/blocks`), no `.env`: backend 492 test cases, 0 failures,
0 errors, no `[ERROR]` lines; frontend `npm audit` 0, lint, typecheck, knip, 300/300, build clean. Waiting for
Fabio to say merge.

**2026-10-17 — T17 merged.** Fabio: "merge it and do T18". `feat/blocks` merged `--no-ff` as `35b0de5`.
Next: T18, ranges and Fibonacci.

**2026-10-17 — T18: ranges (DEC-062).** On `feat/ranges`, not merged.
- **One leg, read four ways.** The drafts each named a leg differently; `Ranges` reads it once: a
  displacement in which a candle is the first close beyond the latest swing (the break `ob-1.0.0`
  reads), from the extreme the move began from to the first intermediate-term turn at or after the
  break, known when that rank is, only while price is inside, one range per turn.
- **Measured first** (scratchpad script, two years of real NQ, 1m–1h alike): ~0.7 legs per 30 candles,
  5–6 average candles tall. A one-candle turn gave ranges left within a median of 7 candles; the
  draft's intermediate-term turn 10–11 — kept. Left beyond the end ~60%, beyond the start ~40%; 8%
  already left when ranked (not reported). Equilibrium reached in ~72%, the OTE in ~64% (59% before a
  new extreme); −1 / −2 / −2.5 / −4 reached 33 / 21 / 17 / 11%. An earlier measurement stopped
  following a range at its break and so showed the projections almost never reached — caught before
  any rule was written on it.
- **Four rules:** `dealing-range-1.0.0` (equilibrium line; BROKEN_HIGH / BROKEN_LOW / EXPIRED),
  `pd-1.0.0` (premium and discount halves; on an odd range equilibrium belongs to neither), `ote-1.0.0`
  (62–79% rounded outward, 70.5% line, TAPPED), `stdev-1.0.0` (levels rounded away from the leg).
  `swing-tier-1.0.0`'s ranking is shared (`SwingTierDetector.intermediate`). The API's `midHalfTicks`
  now carries a range's own line too.
- **Found while building it:** an outside candle ranked both high and low ended a bullish and a bearish
  range at once, so two premium halves shared a place — the contract test caught it; one range per turn
  now, the one known first.
- **Checked live** (real NQ 5m, two weeks): 59 ranges (34 broken high, 25 low), 32 OTE taps; all 27 rules
  cold 4.0 s, warm 0.56 s. The switches were put back to their defaults afterwards.
- Tests: RangeDetectorsTest (7: the range and its turn, the halves, the OTE's rounding and life — a tap
  on its edge, a close on the range's low that does not break it — the deviations, broken high and low,
  expiry, a range already left, a broken swing beyond the 300-candle reach), the contract test for all
  27, SetupApiTest's list +4 and its midline test extended to the OTE, ConceptRegistryTest; frontend
  shapes +2.
- Mutations, one at a time after a passing baseline: 17 backend — 16 caught at once, the 17th (the
  API ignoring a range's own line) after the midline test was extended to the OTE; 5 frontend, all
  caught. The edge cases (a tap on the OTE's edge, a close on the range's low, a swing beyond the
  reach) were written before the run, from T17's survivors.
- Backend 492 → 499, frontend 300 → 302.

**2026-10-17 — Fresh-clone check of `0f02e51`** (`feat/ranges`), no `.env`: backend 499 test cases, 0 failures,
0 errors, no `[ERROR]` lines; frontend `npm audit` 0, lint, typecheck, knip, 302/302, build clean. A first run was
cut off when the session ended; Docker Desktop had then quit again (ENV-001) and was started as documented
before the rerun. Waiting for Fabio to say merge.

**2026-10-17 — T18 merged.** Fabio: "merge it and do T19". `feat/ranges` merged `--no-ff` as `8cf18bc`.
Next: T19, time models and profiles.

**2026-10-17 — T19: time models and profiles (DEC-063).** On `feat/time-models`, not merged.
- **Measured first** (scratchpad scripts, two years of real NQ minutes, 507 days, 96 whole weeks on one
  contract). Power of three: 52% of midnight days fit (29% bullish, 23% bearish), 55% from 18:00, 47% from
  09:30 — no open is special, so the 2022 course's midnight. Daily profiles on the CME day: Asia makes an
  extreme on 44% of days and ICT's London shapes come to 21%; on the midnight day split at 08:30, London
  low 33%, London high 25%, New York reversal 39%. Weekly: Monday 48%, Tuesday 20%, Wednesday 14%,
  Thursday 6%, the other way round 15%. Judas against any one-candle swing: 90–95% of windows (noise);
  against levels still resting at the window's open: London 60%, New York 12%. IPDA within one contract:
  20-day 70% of days, 40-day 39%, 60-day 7% — kept within the contract, as the clock's roll rule
  (DEC-047); back-adjustment written down as a possible later task.
- **Four detectors and a clock rule:** `po3-1.0.0`, `daily-profile-1.0.0`, `weekly-profile-1.0.0`,
  `judas-1.0.0` on a shared `Periods` (midnight days, CME weeks, killzone ranges, the previous trading
  day; a period ends at the candle covering its end — on 4h/1d an hour late, never early). Each limited to
  the timeframes whose candles start where its periods do (Po3 ≤ 1h, daily profile and Judas ≤ 30m).
  `ipda-1.0.0` reads one row per trading day — SQL groups the minutes by `(New York time + 6 h)::date` —
  instead of months of minutes. The clock response's rules carry `ipda`; lessons know the rule.
- **Found while building it:** the Judas swing as drafted would have been on nearly every chart, every
  morning; the first daily-profile reading named mostly Asia; the weekly profile has no hit in
  SetupApiTest's first fortnight (its first week's low came after its high, the second has the roll), so
  that test now covers the three weeks.
- **On the chart:** four setup switches (labels "Po3", "London low", "NY reversal", "Tue low of week",
  "Judas London"), an IPDA clock switch (green dashed, labelled before PDH). Checked live at 1920 px
  (real NQ, 5m and 15m): a fortnight of the four rules in 68 ms, the clock in 0.2–0.4 s; IPDA lines in
  November 2025 and August 2026, none in early October 2026 (the September roll was under 20 trading days
  before). **Found in the browser:** Po3, the daily profile and the weekly profile all shade a whole day
  or week, so with them on the candles sat under three stacked fills — a day's or week's range is now
  framed, not shaded (the Judas zone, a few candles, keeps its fill). The switches were put back to their
  defaults afterwards.
- Tests: TimeRulesTest (13: Po3 both ways, shallow, the order, not on 4h; the three daily profiles, the
  close against London's extreme, the 08:30 candle; Tuesday low of the week, out of order; Judas in
  London, no return — at the open, after 05:00 — a touch, the New York window with levels already traded
  through), ClockLayerBuilderTest Ipda (4: levels, causal and taken — the day's own row never read —
  contracts, a roll day and a hole), ClockApiTest (trading days across DST, the rules), the contract test
  for all 31, SetupApiTest's list +4, ConceptRegistryTest; frontend: shape labels, a day framed and a
  Judas swing shaded, the IPDA switch and its label priority.
- Mutations, one at a time after a passing baseline: 8 frontend, all caught; 24 backend, 22 caught. The
  two left change nothing that can happen: grouping trading days from 17:00 instead of 18:00 (CME halts
  17:00–18:00, so no minute lies between; the 18:00 edge, from 19:00, is caught) and letting the Judas
  rule read five candles further back (on 1m the previous day starts at most ~2,310 candles back, inside a
  reach of 2,461 in London and 2,731 in New York — the bound only guards the lookback promise).
  The edge cases (the order, a close against London's extreme, the 08:30 candle, a touch, a level taken
  before the window, a return after it, a hole, a roll day, the day's own row) were written before the run.
- Backend 499 → 517, frontend 302 → 305.

**2026-10-17 — Fresh-clone check of `cad9c70`** (`feat/time-models`), no `.env`: backend 517 test cases, 0 failures,
0 errors, no `[ERROR]` lines; frontend `npm audit` 0, lint, typecheck, knip, 305/305, build clean. Waiting for
Fabio to say merge.

**2026-10-17 — Bug hunt and security pass (DEC-064, BUG-066–073).** Fabio: "check for all bugs, and write
tests to missing pieces", four security questions, "priority is fixing all the bugs". On
`fix/bugs-and-security`, branched from `feat/time-models` (T19 is not merged, so this brings it along).
- **The four questions.** Session token in localStorage — no (HttpOnly cookies, DEC-032; AuthFlowTest
  already pins it); a CSP on the built page added. Admin check on the client — there is no admin role;
  ownership is checked on the server, and `RobustnessTest.anonymous` calls every published endpoint
  without a session (compared with the OpenAPI paths, so none is forgotten). Rate limiting — login and
  sign-up were limited, but the per-IP key was the client-written X-Forwarded-For (BUG-070); now
  `TRUSTED_PROXIES`. A password endpoint did not exist; `POST /api/auth/password` does, limited.
  Leak check and requirements — `password-1.0.0` with Have I Been Pwned's k-anonymous range API.
- **Found by reading, coverage and a bad-input sweep:** BUG-066 (72-byte BCrypt ceiling: sign-up refused
  with the library's message, sign-in a 400-vs-401 enumeration oracle, a truncated match), BUG-067
  (impossible practice filters searched 10,000 times then answered 500), BUG-068 (an epoch past
  Instant's range answered 500), BUG-069 (a sign-up race answered 500), BUG-070, BUG-071 (a bracket
  check that overflowed), BUG-072 (a drawing's style unbounded), BUG-073 (open: two-site deployment
  breaks the CSRF header and third-party cookies — a hosting decision for Fabio).
- **Found while building it:** the password rules inside the field's label became its accessible name;
  moved out and linked with aria-describedby. The status text for screen readers lost its space.
- **Coverage-guided tests:** backend lines 96.0% before; untested were T19's words for a New York Judas
  swing and a bearish New York reversal, logout-all, the drawing cap, the health check with the database
  down, an expired refresh token, a candle with no stored minutes, the 400-day range.
- Tests: RobustnessTest (6), AuthHardeningTest (13), PasswordPolicyTest (6), PwnedPasswordsTest (4, a local
  stand-in server), RegisterRaceTest, HealthControllerTest, RuleTextTest +1, ChartDrawingApiTest +2,
  PredictionTest +1 case, ConceptRegistryTest; AuthFlowTest and BugFixTest updated. Frontend:
  password.test, AccountMenu.test (4), App.test +2, PracticeView.test +1.
- Mutations, one at a time after a passing baseline: 30 backend, all caught (each fix undone in turn, and the
  policy's every rule); 12 frontend, all caught. Three edge cases were written first, from what would have
  survived: the password endpoint in the per-IP limit, exactly four different characters, a three-letter name.
- Checked live at 1920 px: the account menu; a wrong current password is a 403 with no refresh-and-retry; a
  sign-up with a breached password refused by the real range API (2,298 breaches), no account made; the built
  page under its CSP signs in, draws the chart, Learn, Practice and Drills, and an injected inline script is
  blocked. A launch configuration serves the built page (tradeez-frontend-built, port 4173).
- Backend 517 → 553, frontend 305 → 316.

**2026-10-17 — Fresh-clone check of `c5aa35c`** (`fix/bugs-and-security`), no `.env`: backend 553 test cases, 0
failures, 0 errors, no `[ERROR]` lines; frontend `npm audit` 0, lint, typecheck, knip, 316/316, build clean. Waiting
for Fabio to say merge — `feat/time-models` (T19) first, or this branch alone, which contains it. BUG-073 waits for
the hosting decision.

**2026-10-17 — T19 and the security pass merged.** Fabio: "lets push back on hosting and stuff like that lets
make the app perfect first. merge whats not merged and then continue with tasks". `feat/time-models` merged
`--no-ff` as `2f476e0`, then `fix/bugs-and-security` as `7c6ca81`. Hosting, deployment (T31) and BUG-073
wait until the app is finished. Next: T21, evidence views. T20 (SMT) needs ES imported from Massive's free plan
first — asked of Fabio, not done unasked.

**2026-10-17 — T21: evidence views (DEC-065).** On `feat/evidence`, not merged.
- **evidence-1.0.0** (`EvidenceCalculator`, a pure function of a rule and the candles split at rolls): every
  rule's hits and hits per 100 candles; lives (fvg-state, the five blocks, dealing-range, pd, ote,
  liquidity) counted once over — a final state or 500 candles inside the data — with how they ended, a
  leg's range read as past its end or its start; kinds; and the time rules against days, weeks and
  windows. `Detector.runsOn` says which timeframes a rule reads.
- **EvidenceService** counts a real market's whole history on 1m, 5m, 15m and 1h and keeps it in
  `rule_evidence` (V18); `EvidenceRefresher` recounts what is stale after start-up, in the background
  (697,003 one-minute candles in 18 s, 5m in 5 s, 121 rule-timeframe counts). Never simulated data.
- **On the lesson card**, between the rule and the examples: the counts in words with their sample size
  and dates, a timeframe switch, and "not a forecast, and not a reason to take a trade".
- **Measured** (5m; 1m–1h within a few points): gaps ~11 per 100 candles, 94% filled, 91% inverted; liquidity
  95% taken; OB 83% invalidated; ranges 59% past their end; OTE reached in 64%; 74% of takes are sweeps;
  the time rules as T19 measured. Agrees with the hand measurements of DEC-050 to DEC-063.
- **Found while building it:** the first counting read a range's break as above its high or below its low,
  which mixes up and down legs — now past its end or its start; dealing ranges and halves never emit
  "tapped", so it is not listed for them; the time rules on a timeframe they do not read would have read
  "none in 507 days" — `runsOn`.
- Tests: EvidenceCalculatorTest (5, stand-in rules), EvidenceTextTest (4), EvidenceServiceTest (4, on
  ThreeWeeksOfNq), LessonApiTest +1, DetectorContractTest (runsOn); frontend LearnView +2.
- Mutations, one at a time after a passing baseline: 17 backend, all caught; 5 frontend, all caught after one
  survivor (the caveat shown as one more count) got its test. Three edge cases were written first: a life of
  exactly 500 candles, a final state near the end of the data, windows that differ between London and New York.
- Checked live at 1920 px: the ICT-22 card reads its gaps' lives, ICT-64 its days; the timeframe switch offers
  only what the rule reads.
- Backend 553 → 567, frontend 316 → 318.

**2026-10-17 — Fresh-clone check of `9e7067b`** (`feat/evidence`), no `.env`: backend 567 test cases, 0 failures,
0 errors, no `[ERROR]` lines; frontend `npm audit` 0, lint, typecheck, knip, 318/318, build clean. Waiting for Fabio
to say merge; T22 continues on `feat/plans`, branched from this.

**2026-10-17 — T22: ICT plans and model checklists (DEC-066).** On `feat/plans`, branched from `feat/evidence`, not merged.
- **The plan** carries, optional and written once (V19): the model (MODEL_2022, SILVER_BULLET, OTE, UNICORN,
  TURTLE_SOUP, BREAKER, SESSION_REVERSAL, OPENING_RANGE_GAP — the market maker models are judgement, left out),
  the learner's bias and the draw on liquidity (within 20,000 ticks, like the bracket).
- **What the app sees**, kept with it: the killzone; `daily-bias-1.0.0` (the last two whole trading days:
  continuation, the other way, or nothing — with its reasons, never graded, real days only); the model's
  checklist (`ModelChecklist`, `model-*-1.0.0`), each step in words from the hits of the model's rules confirmed
  by the cursor — the replay's own scan (`SetupService.visibleForReplay`), never a later candle.
- **Live while drafting:** `POST /api/practice/sessions/{id}/context`, a quarter of a second after the last drag;
  checked again and stored at lock. On real NQ: 0.1 s, the first model check 0.7 s (the scan).
- **On screen:** "Your reading" in the plan panel (model, bias, draw; the killzone and bias line; the checklist
  ✓/✗); "2/3" in the trade log's Model column; the steps on the result card — adherence apart from the
  outcome (DEC-027). Model and bias lesson cards point to Practice.
- **Found while building it:** the opening range gap's middle printed as "255.000" — `RuleText.halfPrice`; the bias
  reading on a simulated market would have taught from invented data and generated days of seconds per plan —
  refused for simulated markets.
- Tests: DailyBiasTest (3), ModelChecklistTest (9: every model met and missed, the 2022 model's edges),
  PlanContextApiTest (4), RobustnessTest (the new endpoint in the anonymous sweep and two bad bodies),
  LessonApiTest, ConceptRegistryTest; frontend PlanPanel +4, PracticeView +1, ResultCard +1.
- Mutations, one at a time after a passing baseline: 19 backend, all caught; 8 frontend, all caught. Edge cases
  written first: a gap from before the sweep, price on the gap's edge, a stop on the sweep's low, a target on
  the liquidity, no reading on a simulated market. The run was cut off by the session's end at the sixteenth,
  which it left in the source (ENV-012); restored, the last four run and caught, the tests green again.
- Checked live on real NQ (through the API: the browser pane was hidden): a New York AM session — the
  daily-bias reading "Yesterday closed at 20362.75, above the day before's high at 20248.50…", the 2022
  model's checklist 2 of 5 in words, Silver Bullet, OTE and turtle soup checklists.
- Backend 567 → 583, frontend 318 → 324.

**2026-10-19 — Fresh-clone check of `47155b9`** (`feat/plans`), no `.env`: backend 583 test cases, 0 failures,
0 errors, no `[ERROR]` lines; frontend `npm audit` 0, lint, typecheck, knip, 324/324, build clean. Waiting for Fabio
to say merge (it carries T21 too); T23 continues on `feat/scorecard`, branched from this.

**2026-10-19 — T23: the scorecard and calibration (DEC-067).** On `feat/scorecard`, branched from `feat/plans`, not merged.
- **The scorecard** (`scorecard-1.0.0`, `Scorecard`): worked out from the stored plan and result whenever a trade's
  exit is revealed, never before. Outcome — money, R, worst and best point in R, the draw reached or how close;
  process — the risk limit, the plan stated ("3 of 4"), the checklist, with or against the learner's bias, the
  daily-bias reading, killzone, reward to risk, held or closed by hand, the confidence. A tick or a cross only
  on the learner's own rules; the headline counts them ("Your rules: 1 of 2 broken"), never a score.
- **The risk limit:** `GET`/`PUT /api/practice/settings`, $1 to $1,000,000, set on the Practice page before a
  session; the plan panel reads the draft's risk against it; copied onto each plan at lock (V20), never blocking.
- **Calibration** (`calibration-1.0.0`, `GET /api/practice/calibration`): a plan's confidence, 10–90% that the
  target fills first; in the journal by confidence with counts, and the average against the hit rate's 95%
  Wilson interval. Hand closes, session closes and one-candle ambiguity counted apart.
- **On screen:** the result card has its own row now, two sides of equal width (715 px each at 1920); the plan
  panel's "Target before stop"; the journal's risk limit and Calibration panel.
- **Found while building it:** the edit scripts passed their replacement to `String.replace` as a string, which
  reads "$$" as "$" — the risk-limit error said "$1 to 1,000,000" (ENV-013); the scripts now pass a function.
- Checked live on real NQ (New York AM, a long of 2 contracts, $400 against a $300 limit, the 2022 model, bullish,
  60%): target hit +1.98R; "Your rules: 1 of 2 broken"; "60% sure … it did"; the journal's calibration read
  "your 60% is inside it" on one trade, with 21%–100%.
- Tests: ScorecardTest (6), CalibrationTest (7), ScorecardApiTest (3), RobustnessTest (the three endpoints in the
  anonymous sweep and four bad bodies); frontend ResultCard +2, PlanPanel +2, PracticeView +4.
- Mutations, one at a time after a passing baseline: 21 backend, all caught; 11 frontend, all caught. Edge cases
  written first: risk exactly at the limit, three contracts, a draw reached to the tick, a draw behind the entry,
  an average confidence on each edge of the interval (70% with 1 of 4, 30% with 3 of 4), a confidence of 100.
- Backend 583 → 599, frontend 324 → 332.

**2026-10-19 — Fresh-clone check of `81911d6`** (`feat/scorecard`), no `.env`: backend 599 test cases, 0 failures,
0 errors, no `[ERROR]` lines; frontend `npm audit` 0, lint, typecheck, knip, 332/332, build clean. Waiting for Fabio
to say merge (it carries T21 and T22 too); T24 continues on `feat/review`, branched from this.

**2026-10-19 — T24: the review (DEC-068).** On `feat/review`, branched from `feat/scorecard`, not merged.
- **`GET /api/practice/sessions/{id}/trades/{n}/review`** (`review-1.0.0`): 409 until the replay has passed the exit,
  404 for a trade or session that is not the learner's. `TradeReview` (pure) tells it; `TradeReviewService` gathers
  the hits — the replay's own scan, confirmed by the cursor — and the minute candles after the entry.
- **Told in three parts:** before the decision (30 candles, three latest per rule); while it ran (the decision in
  the learner's words, the fill, hits up to 24 and a count, the exit); after (the draw reached, and after a close by
  hand the bracket's own exit, once the cursor has passed it). Rules: LIQUIDITY, SWEEP, MSS, FVG and the model's own.
- **On screen:** a Review panel under the result card, read on request, grouped by part; "Show its rules on the
  chart" draws the told hits as a second setup layer, and takes them off.
- **Found while building it:** "reached" first meant a candle containing the draw, so a market that gapped through
  it would never have reached it; now it is a high at or above a draw above the entry, a low at or below one below it.
- Checked live on real NQ (the T23 session's trade): nine hits before the decision — gaps, two sweeps, liquidity
  resting and taken, each in its own numbers — the decision with the 2022 checklist, the fill, the target; drawn on
  the chart beside the trade.
- Tests: TradeReviewTest (7: order, the windows' edges, the caps, the draw both ways and through a gap, the held
  bracket before and after the cursor and in its own words, the decision in words), TradeReviewApiTest (2), RobustnessTest (the endpoint
  in the anonymous sweep and three bad requests); frontend TradeReview (3), PracticeView +1.
- Mutations, one at a time after a passing baseline: 18 backend, all caught — the held target's wording survived the
  first run and has its own test now; 7 frontend, all caught by tests — the trade number in the review's address was
  first caught only by the type checker, which does not count, and the panel's test now asks for trade 2.
- Backend 599 → 608, frontend 332 → 336.

**2026-10-19 — Fresh-clone check of `ebbf55d`** (`feat/review`), no `.env`: backend 608 test cases, 0 failures,
0 errors, no `[ERROR]` lines; frontend `npm audit` 0, lint, typecheck, knip, 336/336, build clean. Waiting for Fabio
to say merge (it carries T21–T23 too); T25 continues on `feat/stats`, branched from this.

**2026-10-19 — T25: journal statistics (DEC-069).** On `feat/stats`, branched from `feat/review`, not merged.
- **`GET /api/practice/statistics`** (`journal-stats-1.0.0`, `JournalStatistics`): every finished trade of the
  learner's, from all their sessions — overall and by model, killzone, weekday, side, model checks met and own
  rules kept (the scorecard's own verdicts). Trades, wins, losses, win rate, average win and loss, expectancy with
  its 95% range in words, profit factor, total R. Calibration now reads the same finished trades.
- **On screen:** a Statistics panel between the journal and the calibration: the whole in one line, then one
  grouping at a time, the trades column first.
- **Found while building it, on the test account's 236 trades:** all the trades placed before T22 counted as
  "outside the killzones", because the killzone was only stored from T22 on — and the T23 scorecard said the same
  of them (BUG-074, fixed: `TradePlan.killzoneWhenPlaced` reads it from the time, `Killzone.at`); and 25 trades
  on Sunday evening, where the futures week opens, were listed after Friday — Sunday comes first now.
- Live: 236 trades, expectancy −0.25R (−0.41R to −0.10R), "negative so far"; by killzone Asia 30, London 46,
  New York AM 38, New York PM 13, outside 109.
- Tests: JournalStatisticsTest (4: the arithmetic by hand, the readings, nothing yet, six groupings in order),
  TradePlanTest (2, BUG-074), ScorecardApiTest +1 (statistics through the API), RobustnessTest (the endpoint in the
  anonymous sweep); frontend StatisticsPanel (2), PracticeView +1.
- Mutations, one at a time after a passing baseline: 15 backend, all caught — the plan view's killzone survived the
  first run, because sessions start at random moments; the statistics test now starts in New York AM and checks the
  plan, the scorecard line and the group; 7 frontend, all caught.
- Backend 608 → 615, frontend 336 → 339.

**2026-10-19 — Fresh-clone check of `462eb6d`** (`feat/stats`), no `.env`: backend 615 test cases, 0 failures,
0 errors, no `[ERROR]` lines; frontend `npm audit` 0, lint, typecheck, knip, 339/339, build clean. Waiting for Fabio
to say merge (it carries T21–T24 too); T26 continues on `feat/risk`, branched from this.

**2026-10-20 — T26, part 1: risk drills, mode F (DEC-070).** On `feat/risk`, branched from `feat/stats`, not merged.
- **`RiskQuestion`** (`risk-1.0.0`, pure): position size on NQ or MNQ, a losing streak's compounded drawdown, the gain
  a drawdown needs — each with its answer, tolerance, time limit and working. **`RiskDrillService`** stores it (V21
  `risk_drill`), withholds the answer, takes one answer and times it by the database's clock.
- **On screen:** "Risk, against the clock" under the chart drills: a kind, a question, a box and a clock counting
  down ("Time is up — answer anyway"), then Right / Not quite, the time, the working step by step and the record.
- Live: "An account of $25,000, risking 2%… 55 ticks… on NQ": answered 1, right, in 9.7 s, with "$500 ÷ $275 = 1.82".
- Tests: RiskQuestionTest (6: the arithmetic by hand, rounding down, none, 2,000 draws against their givens, the
  tolerance edges), RiskDrillApiTest (2), RobustnessTest (three endpoints in the sweep, four bad requests); frontend
  RiskDrills (4, the clock with fake timers).
- Mutations, one at a time after a passing baseline: 14 backend, all caught — a record mixing the kinds survived the
  first run, since no test answered two kinds for one learner; one does now; 8 frontend, all caught.
- Backend 615 → 623, frontend 339 → 343.

**2026-10-20 — T26, part 2: management drills, mode E (DEC-071).** On `feat/risk`.
- **`Management`** (`manage-1.0.0`, pure): the trade opened at a candle's close (stop 1.5 × ATR(14), at least 8 ticks,
  target 3R), the moment it first closes a whole R up after three candles, and each choice played out on the candles
  after — the first level, a gap at the open, a candle reaching both read on its minutes, 100 candles at most.
- **In the drill engine:** `OPEN_TRADE_MANAGEMENT` in a new mode, MANAGE (V22 lets it carry a question and an
  outcome); the trade drawn as three lines through the existing overlay, the choices as options, the result as each
  choice's story; the catalogue gives it its own rule card; the learning state counts it as practice, never a rating.
- **Found while building it:** `/api/drills/kinds` answered 400 — it looked every kind's rule up among the detectors,
  and this one's is the drill's own; and the learning state's switch over the modes no longer compiled — it now
  scores a management drill by whether the choice was among those that paid most.
- Live on real NQ: a long from 19951.25, stop 19905.75, target 20087.75, at +2.53R; "Yes — Hold and Stop to
  breakeven paid most here": the target the next candle, +3.00R, half off +2.76R in all.
- Tests: ManagementTest (9, every number worked by hand), ManagementDrillApiTest (2: nothing after the moment, holding
  replayed independently on the candles); RealDrillApiTest (the catalogue); frontend DrillView +3, DrillOverlay +1.
- Mutations, one at a time after a passing baseline: 16 backend, all caught; 8 frontend, all caught.
- Backend 623 → 634, frontend 343 → 347.

**2026-10-20 — Fresh-clone check of `9ac95bb`** (`feat/risk`), no `.env`: backend 634 test cases, 0 failures,
0 errors, no `[ERROR]` lines; frontend `npm audit` 0, lint, typecheck, knip, 347/347, build clean. Waiting for Fabio
to say merge (it carries T21–T25 too); T27 continues on `feat/costs`, branched from this.

**2026-10-20 — T27: fees, commission, slippage (DEC-072).** On `feat/costs`, branched from `feat/risk`, not merged.
- **fill-1.3.0** (`FillEngine`): `FillModel` now has market slippage, stop slippage, the ticks a target must be traded
  through and the fees; every market fill slips — the entry, a close at market, the flat at the session's close — and a
  target can need trading through. `BASIC` is fill-1.2.0's model under its old name; `STANDARD` is the new default. A
  model is named on every result and read back from the name (`FillModel.parse`) to fill a later close the same way.
- **The learner's costs** (V23 on `practice_settings`): fees, market and stop slippage, how targets fill — set beside
  the risk limit, all at once; unset, the standard. The plan panel says the most a round trip can cost; the scorecard
  says what it did, in dollars and R.
- **Found while building it:** a close at market was filled with `FillModel.DEFAULT` whatever the plan had been
  resolved with — harmless while there was one model, wrong the moment there are two; it now uses the plan's own.
- The worked examples (PracticeApiTest, BracketOutcomeApiTest) keep fill-1.2.0's numbers by setting BASIC as the
  learner's costs; new worked examples show the standard ones and a close keeping its plan's costs.
- Live on real NQ: the draft "at most $14.50"; a trade stopped out — "Fees $4.50 and 2 ticks of slippage ($10.00):
  $14.50, 0.07R of what was risked", resolved by fill-1.3.0 with `mkt-1t/slip-1t/thru-1t/comm-450c`.
- Tests: FillEngineTest +6 (entry slippage both ways, through with its edges, an open at and beyond the target, the
  flat, the close, the names), BracketOutcomeApiTest +2, ScorecardTest +1, ScorecardApiTest (the costs in the settings);
  frontend PracticeView +1, PlanPanel +1.
- Mutations, one at a time after a passing baseline: 17 backend, all caught — a target exit counted as slipping
  survived the first run (no test had a standard-model target); one does now; 7 frontend, all caught.
- **Found by the full suite:** two tests store results under a made-up fill model ("test"); the new Costs line could not
  read it and the whole session failed to load. Real results always carry a readable name, but an unreadable one now
  costs only that line its slippage ("the slippage cannot be read from its fill model").
- Backend 634 → 643, frontend 347 → 349.

**2026-10-20 — Fresh-clone check of `8e4eb97`** (`feat/costs`), no `.env`: backend 643 test cases, 0 failures,
0 errors, no `[ERROR]` lines; frontend `npm audit` 0, lint, typecheck, knip, 349/349, build clean. Waiting for Fabio
to say merge (it carries T21–T26 too). Paused here for his input: T28 reshapes the stored result (one trade, several
exits), T29 needs Q-08, T20 an ES import.

**2026-10-21 — T21–T27 merged.** Fabio: "merge, you have my go ahead, app should be english only for now, and yes you can
import ES". Merged into `master` with `--no-ff`, in the order they were built, each on the one before: `feat/evidence`
(815f7e3), `feat/plans` (ad43649), `feat/scorecard` (85a60c0), `feat/review` (cc02048), `feat/stats` (75ab171),
`feat/risk` (9449a13), `feat/costs` (6d6b340). Master's tree after the last is exactly `feat/costs`'s, which passed its
fresh-clone check (8e4eb97: backend 643, frontend 349). Q-08 resolved: English only for now. Go-ahead for T28 and for
importing ES (T20).

**2026-10-21 — T20: SMT divergence** (`feat/smt`, DEC-073).
- ES imported from Massive's free plan (Fabio's go-ahead): V24 adds the instrument and `MassiveImporter.ROOTS` the root.
  The CLI's dry run is its default; the real run needs `--tradeez.import.dry-run=false`. 680,761 one-minute candles,
  25 chunks, nine contracts, $0. ES starts 22 October 2024 — the free plan's two years back from today — two weeks
  after NQ, so there are no divergences in NQ's first two weeks.
- **A second market for a rule.** `Detector.companionOf` and a three-argument `detect` with `Detector.Companion`
  (a symbol and candles; `NONE` for one market). The scanner reads the other market from the rule's first candle,
  splits at both markets' rolls and hands each segment the other's candles over its minutes; both markets' stored
  minutes go into what a remembered scan is checked against, so an import of either rescans. The evidence service the
  same. ES rolled a day before NQ in Dec 2024 and Sep 2025 — exactly what splitting at both rolls is for.
- `smt-1.0.0` (`SmtDetector`, ICT-66 ✅): see DEC-073. RuleText's definition, label ("Bearish SMT divergence") and three
  lines of explanation naming both markets' prices; `compared()` now shared with the structure labels. EvidenceText's noun.
  The overlay draws it as a point labelled SMT, above a high when bearish.
- Live on real data: the SMT switch on NQ, two weeks of 5m: 143 (82 bearish); on ES beside NQ: 138. The ICT-66 lesson
  card shows three real examples, e.g. "This market made a higher high and ES did not". Evidence: NQ 5m 6,180 (4.4 per
  100 candles), 15m 1,987, 1h 467; ES 5m 6,059. ES is listed in the Chart tab.
- Tests: SmtDetectorTest (6), SmtScanTest (5: stored data with ES rolling a day early — nothing spans either roll, and
  split at NQ's roll alone a hit would; ES beside NQ; a stretch scanned before ES was imported scanned again after;
  evidence beside ES; the pair's tick sizes), SegmentsTest +2, RuleTextTest +1, DetectorContractTest (every rule beside
  a second seeded market), shapes +1.
- **Found by the full suite:** MarketDataApiTest and MassiveImporterTest asserted NQ was the only market listed; ES is
  listed now (sorted first), and both say so.
- Mutations, one at a time after a passing baseline: 22 backend, 19 caught. `Segments.beside` dropping the segment's
  last minute survived — no test had the other market's candle exactly there; one does now, and it is caught. Sorting
  SMT's hits by time survived: nothing needs it, so it was removed. `>=` for `>` in the "higher/lower" wording is
  equivalent (equal prices are worded first). 2 frontend, both caught.
- Fixed in passing: the `blocked_on` line still carried the old "T20 needs an ES import" after the merge write-up.
- Backend 643 → 657, frontend 349 → 350.

**2026-10-21 — Fresh-clone check of `9def84f`** (`feat/smt`), no `.env`: backend 657 test cases, 0 failures,
0 errors, no `[ERROR]` lines; frontend `npm audit` 0, lint, typecheck, knip, 350/350, build clean. Waiting for Fabio
to say merge. Next: T28 (partial targets) on `feat/partials`, branched from this one.

**2026-10-21 — T28: partial targets** (`feat/partials`, from `feat/smt`, DEC-074).
- **The engine (fill-1.4.0):** `FillEngine.Partial` on the plan, `FillEngine.Leg` on the fill; one private `walk` for
  `resolve` and `closeAtMarket` (the close is a walk with a moment to close at), so the two cannot drift. A partial is
  a limit like the target (trading through, gaps); target-and-partial in one bar fills both; stop-and-partial is
  ambiguous, the stop for every contract; the breakeven stop applies from the next bar and slips like a stop.
  `Fill.totalTicks` and `FillEngine.totalTicks` count every contract. Without a partial, nothing changes but the
  version name.
- **Storage (V25):** `trade_plan.partial_ticks / partial_contracts / breakeven_after_partial` (checked: some contracts,
  between the price and the target, the move only with a partial); the leg's five columns on `practice_result` and
  `practice_close`, all null together.
- **The service:** `PlanRequest`'s three fields, checked (`validatePartial`); `TradePlan.enginePlan()`; R, wins and the
  journal's statistics over `totalTicks`; `TradeView.partial` revealed once the cursor passes it; the ambiguity note names
  the partial's level when it was still to be taken; `PlanView`'s planned reward : risk is the whole position's.
- **Words:** the scorecard's Partial line ("1 of 2 contracts taken off at …: +1.00R on each; the stop on the rest then
  moved to the entry"), costs counting slippage on the contracts that left by a stop or at market, Execution "Held to the
  plan: the stop, moved to the entry after the partial, closed the rest", Reward : risk for the whole position; the
  review's decision line and a PARTIAL moment.
- **Frontend:** the plan panel's Partial price (empty = none), "Take off n of N", "then the stop to the entry" (two
  contracts or more), Reward "40 then 80 ticks · $600.00" and the whole position's ratio; the partial dashed on the chart,
  the stop drawn at the entry once it has moved; the open trade's Partial and Stop rows; the result card's Partial row;
  PARTIAL among the learner's own review moments.
- **Found live and fixed:** a partial and its target filled in the same minute, and the scorecard and review still said
  the stop on the rest "moved to the entry" — the trade was already over. Both now say so only when the trade outlived the
  partial's minute (ScorecardTest, TradeReviewTest).
- Live on real NQ, 1920px: long 2 from 26,007.00, stop 25,997.00, one off at 26,017.00, the other to 26,027.00, stop to the
  entry ticked — the panel "40 then 80 ticks · $600.00", 1.50; placed, "1 at 26017.00, then the stop to the entry"; four bars
  on, the target: $591.00 net, +1.50R, "1 of 2 at 26017.00 · 40 ticks each", costs "$9.00 and slippage of 2 ticks across the
  contracts ($10.00)".
- Tests: FillEngineTest +11 (Partials), BracketOutcomeApiTest +5 (worked examples on stored minutes: the breakeven, held and
  unreached, ambiguous with the stop, revealed then closed, refused with the reason), ScorecardTest +1, TradeReviewTest +1; frontend PlanPanel +4, plan +3, drawing +1,
  ResultCard +2, PracticeView +1.
- Planning the mutations found three things no test checked, each tested now: the service's own partial check (the
  engine and the database refuse the same plan, so only its message tells — asserted), the ambiguity note naming the
  partial's level (a worked example), and a partial set aside when the contracts drop to one (PracticeView).
- Mutations, one at a time after a passing baseline: 22 backend, all caught; 9 frontend, all caught.
- Re-read live after the fix: the same-minute trade's Partial line now ends "+1.00R on each.", and the review's PARTIAL
  moment carries no line about the stop.
- Backend 657 → 675, frontend 350 → 361.

**2026-10-21 — Fresh-clone check of `5433063`** (`feat/partials`), no `.env`: backend 675 test cases, 0 failures,
0 errors, no `[ERROR]` lines; frontend `npm audit` 0, lint, typecheck, knip, 361/361, build clean. Waiting for Fabio
to say merge (it carries T20 too). Next: T29 on `feat/onboarding`, branched from this one.

**2026-10-21 — T29: disclaimers, the guide, English only** (`feat/onboarding`, from `feat/partials`).
- The disclaimer (`App.tsx`, footer of every screen and the sign-in page) now states DEC-015's three CFTC points — not
  advice and simulated; leveraged, most retail day traders lose; losses can exceed the money deposited — and that practice
  trades never reach a market. The README's matches it.
- `Guide.tsx`: a first visit opens "How Tradeez works" above the tab — Learn, Drills, Practice, Chart, each a link to its
  tab, then what the app is not (ICT's claims taught as claims, nothing advice). Closing it, or following a step, is
  remembered in `localStorage` (`tradeez.guideSeen`, read and written in try/catch; a refused store just shows it again);
  "How it works" in the header reopens it.
- Q-08: the UI was already English (`lang="en"`; no other language in any string). Nothing to change but to record it.
- The presentation slide's text is in DOCUMENTATION.md ("For the presentation").
- At 1920px the guide spans the content width (1,488px), its four steps in one row; no horizontal scroll.
- Tests: App +3 (the guide shown, closed, remembered, reopened; a step opens its tab; the three points in the footer).
  Mutations: 5, all caught. Frontend 361 → 364; backend unchanged at 675.

**2026-10-21 — Fresh-clone check of `7b6a62d`** (`feat/onboarding`), no `.env`: backend 675 test cases, 0 failures,
0 errors, no `[ERROR]` lines; frontend `npm audit` 0, lint, typecheck, knip, 364/364, build clean. Waiting for Fabio
to say merge: `feat/smt` (T20), `feat/partials` (T28) and `feat/onboarding` (T29), in that order, each on the one
before. Paused for his input: everything left needs a decision of his (T31 deployment, T32 budget, Q-09 data rights)
or is optional (T33 indicators, the SMT second chart and drill).

**2026-10-21 — Fabio on what is left.** T32: "i dont have any money skip it i guess for now and we will circle to it in
the end" — skipped, nothing bought, revisited at the end. He asked what T31 (deployment, deferred by him on 2026-10-17)
and Q-09 (showing the real data to the jury) mean; both explained, neither decided yet.

**2026-10-21 — T20, T28 and T29 merged.** Fabio: "merge, then do the SMT second chart and drill." Merged into `master`
with `--no-ff`, in the order they were built: `feat/smt` (5dade77), `feat/partials` (a8baa46), `feat/onboarding`
(9f332df). Master's tree after the last is exactly `feat/onboarding`'s, which passed its fresh-clone check (7b6a62d:
backend 675, frontend 364). Next: the SMT second chart and drill.

**2026-10-21 — The SMT second chart and drill** (`feat/smt-chart`, from master 6aceb13, DEC-075).
- **Backend:** `SMT_IDENTIFICATION` (mode A, V26); `DrillService.besideWhole` — a window only where the companion is one
  contract with every candle; the drill's and the lesson example's `companion` (`CompanionBars`: instrument, tick size,
  candles over the same minutes); a lesson example only where the companion does not roll.
- **Frontend:** `ChartAdapter.getVisibleTimeRange` / `setVisibleTimeRange`, by logical position through
  `lib/chart/logical.ts` (empty space beside the candles included); `CompanionChart` (the other market under a chart,
  keeping to its minutes) and `AdapterOut`; the Chart tab's "Beside" choice with the companion's own SMT hits; under the
  drill's chart and each lesson example.
- **Found live:** the drill's ES chart filled its width while NQ's showed space before its candles — Lightweight Charts'
  visible time range covers only candles. Synced by logical position instead; a test for each direction.
- Live at 1920px: NQ with ES beside on 5m, SMT on — the two aligned, both marked, and kept aligned through a zoom; the drill
  "Found 0 of 1 · Bearish SMT divergence · Mon 22:45 CDT missed" with both markets' prices; the ICT-66 lesson's three
  examples each with ES under them and "Spot: SMT divergence".
- Tests: SmtDrillApiTest (4: the drill with ES candle for candle, graded and read back; `besideWhole` at the fixture's roll and
  with candles missing; no drill where ES misses candles; the lesson's examples), RealDrillApiTest (one market, no companion);
  frontend CompanionChart +3, ExampleChart +2, logical +4, DrillView +2, App +1, the adapter +1. `ThreeWeeksOfEs` shared by the
  SMT tests.
- **Found by the full suite:** LearningApiTest counted eight concepts rated by a marking or naming drill; ICT-66 is the
  ninth now, and the test says so.
- Mutations, one at a time after a passing baseline: 7 backend, 6 caught. The survivor is the lesson's guard against an
  example spanning ES's roll: examples are picked at random from the whole history, so no test can force one onto the roll
  day; the drill's guard — the same check — is caught both directly and where it is called (with ES missing its first five
  minutes of every hour, no drill is asked). 11 frontend, all caught.
- Backend 675 → 679, frontend 364 → 377.

**2026-10-21 — Fresh-clone check of `4b07dc6`** (`feat/smt-chart`), no `.env`: backend 679 test cases, 0 failures,
0 errors, no `[ERROR]` lines; frontend `npm audit` 0, lint, typecheck, knip, 377/377, build clean.

**2026-10-21 — Bug hunt, logged only.** Fabio: "go through the app and mark every single bug you can find, both on
frontend and backend, when you find them dont touch them yet just write them in the bugs md file … think both like a
hacker for backend and trader for frontend". Read the security chain, identity, every controller's ownership and
limits, the practice and drill logic and the frontend's plan, chart and input handling; probed the running backend
(scripts in the scratchpad, the local test account, made-up addresses — no account created). Eighteen logged in
BUGS.md, none fixed:
- verified live: BUG-075 🔴 the per-address sign-in/sign-up limit skipped by `/api/auth/log%69n`; BUG-076 🟠 anyone can
  lock an account out (10 wrong, then 429 for the right password); BUG-077 🟠 an access token keeps working after
  "sign out everywhere"; BUG-078 🟠 every error forwarded to `/error` comes back as an empty 401 (CSRF, bad JSON, 405,
  415, 500 — the client then refreshes and re-sends); BUG-079 NUL in text → database 500; BUG-080 🟠 wide scans
  unthrottled (12 s cold, 2.5 s per shifted window, 5.3 MB); BUG-081 health details with the server's path to any
  user; BUG-082 API docs public; BUG-083 Java types in error messages; BUG-084 drawings accept any time, price, style;
- reasoned: BUG-085 drills' real times let the Chart tab show the future; BUG-086 practice's hidden date found from
  prices; BUG-087 wins counted before fees; BUG-088 risk limit ignores costs; BUG-089 decimal comma ("1,5" read as 15);
  BUG-090 flip leaves the partial behind; BUG-091 discard keeps the partial and intent; BUG-092 the chart beside drifts
  when panned on its own.
- Checked and found sound: ownership on every user-scoped endpoint (sessions, drills, risk drills, drawings, reviews),
  SQL built only with parameters, JWT verification, CSRF on state changes, the keyboard shortcuts' input guard, no
  HTML injection on the frontend, sign-up field limits.

**2026-10-21 — `feat/smt-chart` merged.** Fabio: "merge, then fix all the bugs". Merged into `master` with `--no-ff` as
4e31dec; master's tree is exactly the branch's, which passed its fresh-clone check (4b07dc6: backend 679, frontend 377).
Next: BUG-075 to BUG-092 on `fix/bug-hunt`.

**2026-10-22 — The bug hunt's eighteen, fixed** (`fix/bug-hunt`, from master 7043160, DEC-076, DEC-077). Fabio: "merge,
then fix all the bugs".
- **Security (BUG-075–079, 081–084):** the per-address limit reads the path as the router does (`UrlPathHelper`); the
  account limit is per account and address (`ClientAddress`, shared with the filter); access tokens carry their
  account's generation, moved on by "sign out everywhere" and a password change (V27 `token_generation`); the error
  dispatch is let through the chain, `server.error.include-message: never`, and `RequestErrorHandler` answers what
  Spring refuses before a controller in words; `NoNulStrings` refuses U+0000 while a body is read; health without
  details; API docs closed when deployed; a drawing's style is the chart's three keys in their forms, its points within
  2000–2100 and a billion ticks.
- **Load (BUG-080):** measured first — an identical repeat took the same 2.7 s as a moved window, so the cause was the
  per-rule re-check, not a rescan (the entry was wrong). Minutes counted once a call per market over the widest reach;
  setups over at most 6,000 candles; two scans at a time server-wide; 240 costly reads a minute per account
  (`ReadRateLimit`). Live: every rule over the chart's 160 days of hours, warm, 2.7 s → 0.6 s; a year of hours refused
  in 20 ms.
- **Honest numbers (BUG-087, BUG-088, DEC-076):** R net of costs over the planned risk, wins and losses by money;
  `scorecard-1.1.0` judges the risk limit by the loss at the stop with slippage and fees, and the plan panel shows it.
- **No peeking (BUG-085, BUG-086, DEC-077):** `HiddenStretches` — the session in hand from its cursor to a day past its
  replay, the newest unanswered drill to the last candle that decides it — left out of the Chart tab's candles, setups
  and clock prices on every real market, `hidden` on the bars and a notice under the chart; lessons, new drills and new
  sessions keep clear. First hid every open session: the test account had 35, some 140 days of holes — narrowed to the
  one in hand.
- **Frontend (BUG-089–092):** `lib/decimal.ts` reads a decimal comma ("1,5", "250,50", "20011,25") and groups of three,
  and a box that cannot read its text says so; flip mirrors the partial; discard clears the partial and the intent; the
  chart beside follows only (`followsOnly`).
- **Found on the way:** `Math.abs(Long.MIN_VALUE)` is negative (the anchor check); a JWT's issue time is to the second, so
  revocation by time let a same-second token live (hence the generation); `AppUser`'s new field landed under the
  settings' `@JdbcTypeCode(JSON)` — Hibernate's validation caught it; a practice fixture recorded −1 tick as −$1.00; a
  setup confirmed at a replay's cursor counted as hidden by a second (`SetupService.knownBy`); tests that opened
  sessions without ending them (RealPracticeApiTest's filters, BracketOutcomeApiTest's 40 brackets) now end each;
  BUG-093, an SMT lesson example's ES an hour before its NQ, seen once in a full run and not again.
- **Live at 1920px** (the window hidden, so read from the page, no screenshots): the notice and NQ/ES both missing
  exactly 27 Aug 03:40 – 31 Aug 03:40 for the test account's ES session; ES's plan panel "$500.00 · $529.50 at the stop
  with costs"; "7718,75" → 7718.75, "abc" → the message, kept, `aria-invalid`; the caption under the chart beside; the
  journal's R net ("−1.10R −$219.50"). The draft discarded after; nothing else changed on the account.
- Tests: AuthHardeningTest +7, RequestErrorsTest (4), DeployedServerTest (3, a real server as deployed),
  ChartDrawingApiTest +2, SetupScannerTest +2, SetupApiTest +1, ReadRateLimitTest (1), HiddenStretchesTest (6),
  PracticeApiTest +1, RealPracticeApiTest +1 (R in ES's money), ScorecardTest (rewritten risk line); BracketOutcomeApiTest and others in net R; frontend
  decimal (5), App +1, PlanPanel +1 (and one rewritten), PracticeView +3, RiskDrills +1, CompanionChart, adapter +2.
- Mutations, one at a time after a passing baseline: 34 backend, all caught — three only once the tests were
  strengthened: R in a $12.50 market (every practice test had traded $5 ticks), a rescan after an import (never
  tested, though older than this branch), lessons keeping clear (a replay planted mid-fixture, eight lessons); frontend 17, all caught.
- Backend 679 → 706, frontend 377 → 390.

**2026-10-22 — Fresh-clone check of `fe16cfb`** (`fix/bug-hunt`), no `.env`: backend 706 test cases, 0 failures,
0 errors, no `[ERROR]` lines; frontend `npm audit` 0, lint, typecheck, knip, 390/390, build clean. Waiting for Fabio's
"merge".

**2026-10-23 — `fix/bug-hunt` merged.** Fabio: "merge it". Merged into `master` with `--no-ff` as 4f73512; master's
tree is exactly the branch's, which passed its fresh-clone check (fe16cfb: backend 706, frontend 390). Next: the
redesign — Fabio: "it is time that we change the damn design of the app, it looks extremely unpolished and vibe coded";
a critique given, the direction waiting on his references; `feat/design` holds every screen before it as a PDF.
