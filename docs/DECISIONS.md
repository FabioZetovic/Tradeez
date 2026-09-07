# DECISIONS.md — Architecture Decision Record

> **Authoritative.** This file merges Claude's analysis (`ANALYSIS_CLAUDE.md`) and ChatGPT Codex's
> analysis (`CHATGPT_RESEARCH.md`) into one agreed set of decisions, with Fabio's product calls
> applied on top.
>
> These decisions are **settled**. Do not re-litigate them in a working session. If one appears
> wrong, raise it with Fabio explicitly and record a `SUPERSEDED` entry — never silently deviate.
>
> Decision IDs are stable and are never reused or renumbered.

**Reconciled:** 2026-08-27 (Session 002, Codex reconciliation audit)
**Status key:** `AGREED` (locked, not yet built) · `IMPLEMENTED` · `SUPERSEDED`

---

## How the two analyses compared

Both LLMs researched independently, without seeing each other's work. They converged on
**ten** structural decisions: PostgreSQL over MongoDB, split Vercel/container deployment,
Lightweight Charts with a custom drawing layer, server-authoritative replay, historical-only data
in v1, real-contract storage with continuous series as a view, process-score separated from
outcome-score, modular monolith, an instrument registry rather than hardcoded NQ, and — most
importantly — **both independently identified the TP/SL same-bar ambiguity as a first-class
correctness problem.**

Independent convergence on that last point is the strongest signal in the whole exercise, and it
is worth presenting to the jury as a validation of the architecture rather than a coincidence.

Where they differed, the merge rule was: **Codex won on engineering rigour and delivery shape;
Claude won on the learning engine and on unblocking development.**

| Contributed by Codex | Contributed by Claude |
|---|---|
| Integer-tick pricing (DEC-011) | Sub-resolution fill engine (DEC-008) |
| Reproducibility versioning (DEC-012) | `detected_setup` → drill generation (DEC-026) |
| Immutable plan + revision log (DEC-013) | Free-data-first, no procurement block (superseded DEC-005/020 → DEC-033) |
| WebSocket sequence numbers (DEC-014) | Drawing anchoring against roll adjustment (DEC-024) |
| CFTC/educational disclaimers (DEC-015) | Session date obfuscation (DEC-025) |
| FX futures, not spot FX (DEC-016) | |
| Audit / immutable domain events (DEC-017) | |
| Vertical-slice roadmap (DEC-018) | |
| MVP acceptance criteria (DEC-019) | |

### Round two — Codex's corrections to Claude's claims

After reading the first draft of this document, Codex assessed Claude's analysis as "strong and
roughly 85% aligned" but flagged eight overclaims. **Seven were accepted outright and one was
refined.** All are marked inline with ⚠️:

| # | Claude's claim | Correction | Decision |
|---|---|---|---|
| 1 | Sub-resolution replay is "correct" | More accurate ≠ conclusive. `AMBIGUOUS` is valid even at 1s. | DEC-008 |
| 2 | Historical CME data is "far less restricted"; credits → production | Credits buy data, not redistribution rights. "Derived" does not launder it. | DEC-020 |
| 3 | Detectors give objective ground truth; explanations "always correct" | Only FVG is deterministic. Say *reproducible under a versioned rule*. | DEC-026 |
| 4 | Aggregate 0–100 process score | Several judgments are strategy-dependent. Transparent scorecard first. | DEC-027 |
| 5 | "Vercel cannot host Spring Boot" | Too absolute — not *officially supported*. Split still correct. | DEC-002 |
| 6 | Replay integrity via WebSocket | State must persist in Postgres; REST must drive the domain. | DEC-031 |
| 7 | Build the full drawing suite | Largest schedule risk. Five-tool proof, then a decision gate. | DEC-004 |
| 8 | "JWT auth from Phase 0" | Separate schema ownership from token security; Fabio then selected JWT and DEC-032 defines secure handling. | DEC-022/032 |

Corrections 1, 3 and 4 are **epistemic**: in each case Claude stated something as objectively true
that is actually true only relative to a chosen definition. That pattern is worth naming in the
presentation, because a jury will probe exactly those claims.

---

## Decisions

### Stack and infrastructure

**DEC-001 — Database is PostgreSQL** · `AGREED` · both analyses
The domain has strong relational invariants (user → session → immutable plan → orders → fills →
result → review) and needs constraints, transactions, joins and window functions for analytics.
`JSONB` covers the one genuinely schemaless case — drawing geometry — without giving up any of
that. OHLC bars get a natural composite key that prevents duplicate-bar corruption on re-ingest.
At ~10M rows this is comfortably within plain Postgres. Add TimescaleDB only if measurement later
justifies it; do not adopt it preemptively.

**DEC-002 — Frontend on Vercel, backend on a container host** · `AGREED` · both analyses
Java is **not an officially supported Vercel Functions runtime** (Node, Bun, Python, Rust, Go,
Ruby, Wasm, Edge). Community custom runtimes technically exist, so the defensible statement is not
"Vercel cannot host Spring Boot" but: *Vercel is not an appropriate officially supported host for
this Spring Boot application, particularly given persistent WebSocket behaviour and a long-lived
JVM process.* **The split remains correct.**

The static Vite build can deploy to Vercel; Spring Boot will deploy to a Java/container-capable
host selected from the no-cost options available at deployment time. **Phase 0 runs locally and
requires no hosting purchase.** Render and Railway are candidates, not locked dependencies. When
deployed, use stable `app` and `api` domains with restrictive CORS and secure token handling.

**DEC-036 — Spring Boot 3.5.x on Java 21, not Spring Boot 4.x** · `AGREED` · Claude, Session 006
Discovered during Phase 0 scaffolding. Spring Initializr now defaults to **Boot 4.1.1** and offers
Java 26 (the JDK installed on this machine). Boot 4 was scaffolded, evaluated, and **rejected**:

1. **springdoc has no Boot 4 release.** Its latest artifact across every module is `2.8.6`, which
   targets Spring Framework 6 / Boot 3. Boot 4 moves to Spring Framework 7. **DEC-007 makes the
   OpenAPI document the contract between the two AI collaborators** — breaking it to gain a major
   version is a bad trade.
2. **Boot 4 renamed the starters.** `spring-boot-starter-webmvc` instead of `-web`, a new
   `spring-boot-starter-flyway`, per-starter `-test` artifacts instead of one
   `spring-boot-starter-test`, and `testcontainers-junit-jupiter` instead of `junit-jupiter`. Both
   LLMs' training data, and effectively every tutorial and Stack Overflow answer, is Boot 3. Codex
   would write Boot 3 patterns by default and they would silently fail to resolve.
3. **Ecosystem maturity beats novelty for a thesis.** Boot 4.1.1 shipped 2026-08-20; 3.5.16 is the
   current 3.5.x and still receiving patches.

**Java 21 (LTS)**, not 26, for the same class of reason: it compiles and runs fine on the installed
JDK 26, and it stays deployable on hosting platforms whose Java buildpacks track LTS releases.

Revisit once springdoc ships Boot 4 support. Until then, **do not bump the major version.**

**DEC-007 — OpenAPI is the frontend/backend contract** · `AGREED`
The backend publishes an OpenAPI document via springdoc-openapi; the frontend generates its
TypeScript types with `openapi-typescript`. API shapes are never hand-written on the client and
never invented unilaterally. This is what keeps two LLMs from silently drifting.

**DEC-022 — User ownership exists in the schema from Phase 0**
`AGREED` · Fabio, refined by Codex
Every table is user-scoped from the first migration. Retrofitting `user_id` onto sessions,
drawings, journals and results later is expensive and error-prone. It also lets the jury create an
account and use the app themselves.

Early user ownership is the schema decision. Fabio separately selected JWT authentication; its
secure transport/storage policy is recorded in DEC-032 rather than inferred from the word “JWT.”

**DEC-032 — JWT authentication starts in Phase 0 with secure cookie handling** · `AGREED` · Fabio

- Spring Security owns password hashing, authentication and authorization.
- Access JWTs are short-lived. Refresh tokens rotate and their server-side records can be revoked.
- Authentication tokens use `Secure`, `HttpOnly` cookies in deployed environments; never persist
  long-lived bearer tokens in `localStorage` or `sessionStorage`.
- Cookie-based authenticated mutations use CSRF protection.
- CORS permits only the configured frontend origin and credentials; preview/development origins
  are explicit, not wildcards.
- Secrets come from environment configuration and never enter the repository or documentation.
- Authentication integration tests cover login, refresh rotation, logout/revocation, expired and
  invalid tokens, CSRF rejection, and cross-user resource isolation.

---

### Market data

**DEC-020 — ~~Dukascopy as the development/v1 feed~~** · `SUPERSEDED` by DEC-033
This was proposed because it appeared to provide free sub-minute Nasdaq-100 data. Verification
showed that it is a broker-derived CFD/index feed, not NQ futures: it has no CME contract, expiry,
roll, basis, or exchange volume. Dukascopy's official data terms also place conditions on public
use and redistribution. A third-party downloader's MIT software license does **not** grant a
license to the market data it downloads. Tradeez will not teach futures by silently relabeling a
CFD feed as NQ.

**DEC-034 — Graded drills require real data; `SIMULATED` drills are developer fixtures only**
`AGREED` · Claude, Session 004 · extends DEC-033 and DEC-026
Detectors may run on `DEMO-NQ` — injected ground truth is exactly what unit-tests a detector. But a
generator authored to contain FVGs, sweeps and equal highs is **circular as training material**: a
learner drilled on it learns the generator's artifacts, not the market's. Generated data reproduces
the *shape* of a setup, never the *reason* it matters.

| Data class | Detector may run? | May back a **graded** drill? |
|---|---|---|
| `SIMULATED` | ✅ | ❌ |
| `IMPORTED_PRIVATE` | ✅ | ✅ (local/single-user) |
| `LICENSED_DEPLOYABLE` | ✅ | ✅ |

A `SIMULATED` drill is labelled a developer fixture, is never served as a graded exercise, and
never feeds a concept rating or the spaced-repetition scheduler. Detector work is **not** blocked
by this; only the curriculum claim is. Full rationale in `DATA_STRATEGY.md`.

**DEC-035 — Licensing exposure is a property of deployment, not of the application**
`AGREED` · Claude, Session 004
The public deployment serves `DEMO-NQ` only and therefore carries no third-party market-data
licensing exposure. Real data may live locally as `IMPORTED_PRIVATE` **only within the source
licence**. A jury/classroom presentation is not assumed to be personal use; imported data may
appear there only when the broker/provider terms explicitly permit that display. Same binary,
different dataset — the `data_class` enum and environment policy encode the boundary. This makes
`CsvMarketDataProvider` the primary €0 route to real data when Fabio's broker permits both export
and the intended use.

**DEC-033 — Zero-cost development uses explicitly simulated futures data plus permitted import**
· `AGREED` · Fabio's €0/no-crypto constraint, Codex decision

Phase 0 and the first vertical slice use deterministic datasets named `DEMO-NQ` and `DEMO-MNQ`.
They are generated on the real 0.25-point tick grid with the real NQ/MNQ multiplier, exchange
timezone/session shape, maintenance breaks, and seeded volatility/jump regimes. Hand-authored
golden sequences cover gaps, TP/SL collisions, FVG geometry, sweeps, trend/range regimes, and DST
boundaries. They are always labeled **SIMULATED DATA — NOT MARKET HISTORY**.

This is the honest zero-cost starting point:

1. **€0 and no crypto/CFD proxy.** It models the instruments Fabio wants without pretending that
   a different venue is CME futures.
2. **Unblocks engineering.** Charting, replay, authentication, drawings, fills, journaling and
   drills can be implemented and tested immediately.
3. **Reproducible.** Seed and generator version are stored under DEC-012, which makes correctness
   tests deterministic.
4. **No training-performance claim.** Generated bars validate software, not a trading edge. The
   product cannot claim real NQ training efficacy while it contains only generated history.

A `CsvMarketDataProvider` is built alongside the generator so Fabio can locally import NQ/MNQ
history exported from a broker/platform **only when his license permits that use**. Imported data
stays local and is never deployed or shared automatically. A licensed real-NQ provider remains a
later release gate, not a coding blocker. Free credits, academic programs, trials, or vendor feeds
may be evaluated later, but no card, subscription, or paid data is part of Phase 0.

Credits pay for obtaining data; they do not automatically grant public redistribution rights.
Calling vendor bars a "derived practice session" also does not erase licensing obligations. Before
any real NQ/MNQ dataset is deployed, its classroom, jury-demo, authenticated-display, caching and
replay rights must be confirmed in writing. *This is a technical risk assessment, not legal advice.*

**DEC-003 — No real-time market data in v1** · `AGREED` · both analyses
Publishing live CME prices publicly constitutes redistribution and requires a formal exchange
licence; even delayed distribution carries fees under some configurations of the January 2026 fee
schedule. The Practice tab needs only historical data. This is a deliberate scope decision, stated
as such in the presentation.

**DEC-021 — Provider-neutral `MarketDataProvider` interface** · `AGREED` · both analyses
Instrument search, contract metadata, historical bars, optional historical events, live
subscription. Provider symbols live in a mapping table, never in the domain model. Never scrape
TradingView or any other site.

**DEC-010 — Store raw per-contract bars; continuous series is a view** · `AGREED` · both analyses
Every bar carries its real contract symbol. Continuous series are built at query time from a
`roll_calendar` recording roll date, front/back contract and gap offset. Document whether a
continuous series is volume/OI-rolled or calendar-rolled, and unadjusted / difference-adjusted /
ratio-adjusted. Practice sessions stay inside a single contract so they never cross a roll.
The demo generator emits explicit simulated contract IDs so contract and rollover behavior can be
tested without claiming its prices are historical exchange prices.

**DEC-024 — Drawings anchor to raw prices, never adjusted prices** · `AGREED` · Claude
A drawing stores `(contract_id, timestamp, raw_price)`. If drawings anchored to back-adjusted
prices, every future roll re-adjustment would silently shift every line the user ever drew.

**DEC-016 — FX futures, not spot forex** · `AGREED` · Codex
Spot FX has no consolidated tape; price and volume vary by venue, which does not fit the data
model. "Forex" enters the app as **FX futures** (6E, 6B, 6J) once appropriately licensed CME data
is available. Phase 0 does not use a spot-FX or crypto proxy.

---

### The replay and simulation engine

**DEC-009 — Replay is server-authoritative; no lookahead, ever** · `AGREED` · both analyses ⚠️ critical
The server owns the cursor. The API must never return a bar timestamped after it. Future bars are
never sent-and-hidden with CSS. Indicators compute from visible history only. Autoscaling,
tooltips, network responses and cached API data must not leak future values. Outcomes are written
once, transactionally, on first resolution — re-requests return the stored result, so a user cannot
retry until they win.

**DEC-025 — Obfuscate the session's real date until reveal** · `AGREED` · Claude
Show "USA 100, unknown date" during analysis. A user who recognises or looks up the date has
lookahead just as surely as one reading the network tab.

**DEC-008 — The fill engine simulates at sub-resolution** · `AGREED` · both analyses ⚠️ critical
**The problem:** a replayed 5m bar with `high` above the take-profit and `low` below the stop-loss
does not reveal which was touched first. At realistic day-trading stop distances this is common,
not rare.

**The resolution:** the **display timeframe and the simulation timeframe are different things.**
The user watches 5m candles; the fill engine walks the underlying **tick or 1-second** data inside
each candle in true chronological order and finds the genuine first touch.

**⚠️ Corrected in Session 002 (Codex).** Claude's original wording called sub-resolution replay
"correct." That was an overclaim. Finer data makes the answer *more accurate*, never *conclusive*:
if TP and SL both fall inside a single 1-second candle's range, the order is still unknown; and
even ordered trade ticks show trade sequence without modelling bid/ask spread, limit-order queue
position, or whether the learner's order would actually have filled.

**The resolution hierarchy is therefore:**

| Rank | Source | Status |
|---|---|---|
| 1 | Ordered quotes/trades, when available | Best available fidelity — still not a perfect fill model |
| 2 | 1-second OHLC | **The MVP approximation** |
| 3 | 1-minute OHLC | Fallback |
| 4 | Still ambiguous at the finest available interval | Return **`AMBIGUOUS`** |

`AMBIGUOUS` is a legitimate terminal outcome **at every level of the ladder, including
1-second** — it is not merely the bottom rung. Never silently resolve in the user's favour, and
never hide the uncertainty. Where a pessimistic (stop-first) assumption is applied for a usable
result, it must be labelled as an assumption, not reported as fact.

*This distinction belongs in the jury presentation: it demonstrates honest simulation engineering
and an understanding of what candle data can and cannot prove.*

**Additional fill rules, all versioned (DEC-012):**
- Touch = fill (`low <= stop` triggers), documented explicitly.
- Gaps fill at the **open**, not at the order price.
- Slippage: configurable, default 1 tick against the user on stop-loss (market) fills, 0 on
  take-profit (limit) fills.
- Commission: default ~$2.50 round-turn per contract.
- Session boundaries: auto-flat at session close with an explicit `TIME_EXIT` outcome.
- Partial fills and queue position are **explicitly unsupported** without book data — never
  simulated from candles.

**DEC-011 — Prices are integer ticks; never binary floating point** · `AGREED` · Codex
Store prices as integer tick counts and convert only at the presentation edge. P&L becomes
`pnl_ticks × tick_value` — exact. Use `NUMERIC` where a decimal is genuinely required. Never
`float`/`double` for price or money.

**DEC-013 — The trade plan is immutable; revisions are separate events** · `AGREED` · Codex
The original plan is locked on submission. Later modifications append to a revision/event history
rather than mutating the plan, so review cannot rewrite the past — and so that moving a stop
becomes *measurable behaviour to study* rather than merely forbidden.

**DEC-012 — Every result stores full reproducibility metadata** · `AGREED` · Codex
Data source + version, contract, replay seed, engine version, fill-model version, curriculum/rubric
version, and the original plan. Without this, improving the fill engine silently invalidates every
historical result. Same seed + same data + same engine version must produce an identical result,
and there is a test asserting it.

**DEC-014 — WebSocket events carry schema version, session ID and a monotonic sequence number**
`AGREED` · Codex
Enables reconnect/resume and makes dropped events detectable. Event types: `BAR_OPENED`,
`BAR_UPDATED`, `ORDER_ACCEPTED`, `ORDER_FILLED`, `POSITION_UPDATED`, `REPLAY_FINISHED`.

**DEC-031 — Replay state persists in PostgreSQL; WebSocket is transport, not authority**
`AGREED` · Codex
**Server authority does not mean in-memory WebSocket authority.** Claude's design implicitly kept
session state in the live WS process, which breaks badly on exactly the infrastructure we chose:
Render free instances sleep, connections drop, and a restart mid-session would corrupt or lose the
result.

Therefore:
- The authoritative **cursor, plan and result live in PostgreSQL**, not in process memory.
- **REST commands** (`POST /replays/{id}/commands` — pause, speed, next event) are a first-class
  path, not a fallback. The domain must be fully drivable without a socket.
- WebSocket handles **smooth playback only**, and clients recover by **sequence number** after a
  drop.
- A session survives a backend restart with its integrity intact. There is a test for this.

**DEC-017 — Immutable domain-event audit log** · `AGREED` · Codex
An `audit` module recording domain events and reproducibility metadata.

---

### Product and learning

**DEC-023 — Practice sessions and micro-drills are co-equal features** · `AGREED` · Fabio
Fabio chose the ambitious scope deliberately. Both are first-class:

- **Sessions** — the full loop: analyse, commit a plan, replay, score, review. ~5 minutes per rep.
- **Drills** — short, single-concept, auto-generated exercises. ~15 seconds per rep, hundreds per
  sitting.

Drill taxonomy: **A Identification** ("click every FVG here") · **B Classification** ("BOS or
CHoCH?") · **C Prediction** ("which way does this resolve?") · **D Execution** (a full session) ·
**E Management** ("+1.5R and stalling — hold, breakeven, or partials?") · **F Risk** (position
sizing under time pressure).

*Sequencing note: co-equal in priority does not mean simultaneous in time. The Phase 1 vertical
slice still ships first because drills depend on the detector suite, and the detectors depend on
the bar pipeline. Drills begin in Phase 2 as a first-class track, not as a deferred nice-to-have.*

**DEC-026 — `detected_setup` is the keystone table** · `AGREED` · Claude
Batch-scan history once with deterministic detectors (FVG, order block, BOS/CHoCH, liquidity pool,
sweep, ORB, session levels) and store every hit. One table then powers **four** features: drill
generation with ground truth, practice-session selection ("give me a session containing a liquidity
sweep"), structural stop-placement scoring, and review annotations.

This is the genuinely novel part of the project and the thing to defend to a jury.

**Explanations are generated from the detector's own reasoning**, not hand-written. If the FVG
detector fired because `bar[i-1].high = 20140 < bar[i+1].low = 20155`, the explanation renders
those exact numbers against those exact bars — specific, and scaling to thousands of drills.

**⚠️ Corrected in Session 002 (Codex).** Claude described detector output as objective "ground
truth" and its explanations as "always correct." **That is wrong and must not survive into the
presentation.** Only some detectors are genuinely deterministic: FVG geometry is, once its
definition is fixed. Order blocks, BOS, CHoCH, liquidity pools, sweeps, displacement thresholds and
"structural" stop placement are **substantially subjective** and shift with parameter choices.

The correct claim is:

> The explanation is **reproducible and consistent with the selected, versioned rule definition** —
> not proof that the pattern is real or that ICT is objectively true.

Every `detected_setup` row therefore stores: **detector name and rule version**; **parameters**
(swing strength, displacement threshold, mitigation rules, lookback); **confidence / qualification
metadata**; **methodology source**; and an optional **human validation** flag.

The app grades whether the learner **followed a defined, versioned curriculum** — never whether a
methodology is objectively correct. This is both the honest position and the defensible one in
front of an academic jury.

**DEC-027 — Scoring is a transparent multidimensional scorecard, not a single number** · `AGREED`
Outcome: R-multiple, P&L in ticks and currency, MAE, MFE. Process: risk-limit compliance, plan
completeness, rule adherence, execution quality, and **calibration** (does stated confidence match
actual hit rate — Codex's contribution). A profitable rule violation scores badly on process; a
well-planned loss scores well. Both are displayed with equal visual weight.

**⚠️ Corrected in Session 002 (Codex).** Claude proposed an aggregate 0–100 "process score." It
presents well, but several of the judgments feeding it are **strategy-dependent, not universal**:

- A target below 1:1 R:R is **not automatically bad** — high-win-rate scalping lives there.
- MAE of 0.2R does **not** prove the stop was too wide; the stop simply was not tested.
- Low MFE capture does **not** necessarily mean the exit was poor; it may be correct trade
  management.
- What counts as a "structural" stop depends entirely on the chosen methodology's definitions.

**Therefore:** ship a **transparent scorecard** with each dimension shown separately and its
reasoning visible. Introduce a composite score only once its **weights are visible, versioned and
user-adjustable per methodology**. Never present a strategy-dependent judgment as a universal
verdict on the trade.

**DEC-004 — Lightweight Charts v5 behind a `ChartAdapter` interface** · `AGREED` · both analyses
Apache 2.0, canvas-based, fast at large bar counts, and it is TradingView's own engine so the feel
matches. Drawing tools are absent from core and **we build them** — hand-built hit-testing, magnet
snapping and persistence is the most demonstrable engineering in the project, and a jury can
evaluate it in a way they cannot evaluate an installed dependency.

Codex's addition: isolate it behind `ChartAdapter`, `DrawingRepository` and `MarketDataClient` so
TradingView Advanced Charts could replace the renderer later without touching feature code.

**⚠️ Scope-limited in Session 002 (Codex).** The custom drawing layer is **the single largest
schedule risk in the project**, and Claude's "build the full suite" framing was cavalier. A
graduation project benefits far more from a finished Practice loop than from 68 half-finished
drawing tools.

**Build a tightly scoped proof first — five tools only:**

1. Horizontal line
2. Trend line
3. Rectangle *(the ICT workhorse — order blocks and FVGs are rectangles)*
4. Risk/reward position tool *(doubles as the Practice tab's TP/SL entry UI)*
5. …plus the shared machinery: selection, dragging, magnet snapping, persistence, undo/redo

**Decision gate:** if that proof runs long, stop and evaluate **KLineChart** (drawing tools built
in) or a carefully audited community plugin. Do not expand the tool catalogue until the Practice
vertical slice is complete.

**DEC-015 — Educational disclaimers are a product requirement** · `AGREED` · Codex
The app states plainly that it is educational, not financial advice, and that simulated results do
not guarantee live results. The CFTC warns that futures are leveraged and complex, that many retail
participants lose their funds, and that losses can exceed the deposit. Visible in the UI and on a
presentation slide.

**DEC-006 — ~~Practice tab before the ICT engine~~** · `SUPERSEDED` by DEC-023 and DEC-018
Fabio elected co-equal scope. The delivery-order concern it expressed is now handled by the
vertical-slice roadmap instead.

---

### Process

**DEC-018 — Vertical-slice roadmap** · `AGREED` · Codex's shape, adopted over Claude's
Claude proposed horizontal layers (data spine → chart → drawings → practice), which leaves nothing
demoable until the fourth phase. Codex's vertical slice is better engineering and much better for a
project that must be shown at arbitrary times.

**Phase 1 exit criterion: a user can complete one honest, reproducible practice trade end-to-end.**
Thin, then thicken.

**DEC-019 — MVP acceptance criteria** · `AGREED` · Codex §11, adopted wholesale
The first release is credible when: the browser never receives future replay bars; the same
seed + data + engine version returns the same result; P&L and tick rounding are correct under
automated tests; TP/SL ambiguity is surfaced or conservatively resolved by documented policy;
drawings and plans survive reload; original plans and revisions are distinguishable; every
displayed dataset has recorded source and permitted use; risk/simulation disclosures are visible;
and a student can explain the product and architecture from `DOCUMENTATION.md`.

**DEC-028 — No fixed module ownership; `PATH.md` claiming is mandatory** · `AGREED` · Fabio
Either LLM may work anywhere in the codebase. This makes the `IN PROGRESS / DO NOT TOUCH` block in
`PATH.md` **load-bearing rather than advisory**:

1. Before editing, add the files to `claimed_files` in `PATH.md` with your session ID.
2. Never edit a file claimed by another session.
3. Release the claim in your end-of-session update.
4. If you find a stale claim from a session that clearly ended, note it and take it — do not just
   ignore the mechanism.

**DEC-029 — Testing standard** · `AGREED` · both analyses
Backend: JUnit + Testcontainers. Frontend: Vitest + React Testing Library. Priorities, in order:
property tests for tick rounding, long/short P&L, gaps, commissions and TP/SL conflicts; a
**golden-file fixture suite for the fill engine** with hand-verified expected outcomes; replay
determinism tests; golden-data tests for indicators; a test asserting the API never returns a bar
past the cursor; and browser tests for drawing persistence and future-data leakage.

**DEC-030 — Schema changes only via Flyway migrations** · `AGREED`
Never hand-edit the database. Seed only synthetic or permitted demo data.

---

## Still open

| ID | Question | Owner |
|---|---|---|
| Q-03 | Is order flow / footprint in scope at all? Both analyses say it is impossible without licensed trade/quote/depth data. **Recommendation: cut it from v1 and say why** rather than build a fake footprint chart. | Fabio |
| Q-08 | Croatian or English UI for the presentation? | Fabio |
| Q-09 | Select and legally verify the real NQ/MNQ historical-data source before Tradeez claims real-market training or deploys vendor data. This is not a Phase 0 blocker. | Fabio + either LLM |
