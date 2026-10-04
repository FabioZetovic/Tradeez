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

**DEC-042 — Massive's free plan first; Databento later for deep history**
`AGREED` · Fabio, Session 017 (2026-10-04) · amends the *source order* of DEC-040, nothing else

Databento's sign-up asks for a payment card (billed only beyond its $125 credit). Fabio asked for a
card-free start — *"for now lets take it one step at a time"* — and, after a comparison, signed up
for **Massive** (formerly Polygon.io). Finnhub (no CME futures), Marketstack (stocks only), Nasdaq
Data Link (continuous futures deprecated, daily only), Alpha Vantage (no futures), Yahoo (minutes only
days back) and IEX Cloud (shut down 2024) were checked and rejected.

- **What the free plan gives.** CME futures one-minute aggregates, about **two years back**, 5
  requests a minute, up to 50,000 bars a request, "individual use". No card.
- **What it does not.** No one-second bars (they start at $29/month), so practice fills run on
  minute bars and report `AMBIGUOUS` more often — honestly, per DEC-008 and INVARIANT 6. History on
  Massive starts in April 2017 even on its top plan, so "10 years back" still needs Databento.
- **Contracts.** Massive serves each quarterly contract separately (`NQZ5`, `NQH6`); there is no
  continuous symbol. Tradeez builds the continuous series itself: for each session, the front
  contract is the one with the **highest volume in the previous session** — no lookahead, the same
  idea as Databento's `NQ.v.0`. Bars keep their contract id (`market_bar.contract_id`), raw prices,
  and the roll stays visible (DEC-010, DEC-024).
- **Everything else in DEC-040 stands:** the key lives only in `backend/.env` (`MASSIVE_API_KEY`), the
  data class is `IMPORTED_PRIVATE`, the simulated market leaves the app, and every import is
  recorded in `market_data_import`. Massive's free plan costs nothing, so the cost cap has nothing to
  price; the importer instead respects the request rate limit.
- **The Databento importer stays** — built and tested in Session 017 — for when Fabio decides to pay
  for deeper history or one-second bars. That decision is his.

**DEC-040 — Real historical NQ from Databento; the simulated market leaves the app**
`AGREED` · Fabio, Session 017 · ⚠️ **supersedes DEC-033 for everything a learner sees** · source
order amended by **DEC-042** (Massive's free plan first)

Fabio, session 017: *"you can not mimic price action on your own, it needs to be a real historic
chart data … from NQ from 10 years ago, 5 years ago, 3 months ago."* The premise of DEC-033 — that a
generated market is an acceptable stand-in until real data was affordable — is rejected by the
person the product is for.

- **Source.** Databento's Historical API, dataset `GLBX.MDP3` (CME Globex), continuous symbol
  `NQ.v.0` — the front contract by volume — with **raw, unadjusted prices**: the real jump at each
  quarterly roll is kept, so drawings anchored to prices (DEC-024) stay valid. Chosen over FirstRate
  Data (paid, one-minute only) and Yahoo (intraday history only weeks to two years back). A
  TradingView-derived Kaggle dump (doubtful licence) and Dukascopy (a CFD, DEC-020) were rejected.
- **Resolution.** One-minute bars for the whole history, stored in PostgreSQL (`market_bar`, V7);
  every coarser timeframe is aggregated from them. One-second bars — what the fill engine walks
  (DEC-008) — are fetched a trading day at a time, for the days practice actually uses.
- **Money.** The €0 rule stands. New Databento accounts carry a $125 credit; every download is
  priced with `metadata.get_cost` **before** it is fetched and refused above $5 per request or $100
  in total (`application.yml`), and every attempt is recorded in `market_data_import`. Any large
  import starts with a dry run. Raising a cap is Fabio's decision, not code's.
- **The key.** `DATABENTO_API_KEY` lives in `backend/.env` (gitignored, loaded through
  `spring.config.import`) — never in the repository, in chat, in a log line or in an error message.
- **Data class.** `IMPORTED_PRIVATE` — local, one user — until written permission to show the data
  to others (the jury) exists (DEC-035; Q-09 stays open). Under DEC-034 this class **may** back
  graded drills.
- **The simulated market** is withdrawn from the app once real data is in (`instrument.listed`),
  not deleted; the generator stays in the test suite, where injected ground truth is exactly what a
  test needs.

Before the switch, three things real data forces: practice must **hide the date** until the reveal
(DEC-025 — a learner who knows the date knows the outcome); the fill engine must not read a quiet
second as a market closure (real one-second bars are sparse: no trade, no bar); and drills on real
data become gradable exercises rather than fixtures.

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

> ⚠️ **Superseded by DEC-040 (session 017)** for everything a learner sees: Fabio requires real
> historical NQ. The generated market remains a test fixture only.

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

*Refined by DEC-038:* on **simulated** data the date reveals nothing — there is no real history to
look up. The secret that matters there is the **seed**. Date obfuscation still applies in full the
moment real data (`IMPORTED_PRIVATE` or `LICENSED_DEPLOYABLE`) is used in practice.

**DEC-038 — A simulated market's seed is a secret; the public endpoint serves one fixed market**
`AGREED` · Claude, Session 014, at Fabio's instruction ⚠️ critical

A simulated market is a pure function of `(seed, time)`. Whoever holds the seed can generate any
range of it — including the part of a practice replay that has not been revealed yet. So for
simulated data, **the seed is the lookahead**, in exactly the sense DEC-009 forbids.

Until Session 014, `GET /api/market/instruments/{symbol}/bars` accepted any seed and any time
range. That made it a generator anyone could drive: a practice session's seed, leaked through any
channel, would have let a client read the whole future of that session from *outside* the practice
API, never touching the cursor that DEC-009 relies on. Nothing exploited it only because the
Practice slice did not exist yet.

The rules:

1. **The public bars endpoint serves exactly one market**, `MarketDataService.PUBLIC_CHART_SEED`
   (42). A request that supplies a seed is rejected with 400 — loudly, so a client that believes it
   is selecting a market learns that it is not. Kept at 42 because drawings saved before this
   decision are anchored to prices on that market.
2. **Practice seeds come from `SecureRandom`**, as full 64-bit values, and are **never equal to the
   public seed**.
3. **A practice seed is withheld from every response until its session is complete.** After that it
   is shown, because DEC-012 reproducibility requires it and there is nothing left to reveal.
4. **A practice market is read only through the practice API**, which clips every response to the
   session cursor.

Tested at both ends: the backend rejects a seed parameter, and the frontend is asserted never to send
one.

*Refined in the same session (BUG-028):* when the seed is revealed, it travels as a **decimal
string**. A full 64-bit value does not survive being a JSON number in a browser — JavaScript rounds
anything above 2^53 — and a rounded seed regenerates a different market, which would make rule 3's
reproducibility promise false for nearly every session.

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

**DEC-041 — Tradeez is an ICT trainer: paper trading on real historical charts**
`AGREED` · Fabio, Session 017 (2026-10-04) · sharpens DEC-023, narrows the methodology tracks

Fabio, session 017: *"i feel like we stirred too far away from the original idea of the app … my idea
was to make an app that will help people trade ICT concepts"*, and *"the whole idea would be paper
trading charts from long time ago. Since you can learn on historical charts based off of candle
movements to select trades."*

- **The product is ICT learning by paper trading on real history.** The practice replay (DEC-023's
  sessions) is where ICT is *applied*; drills are where each ICT concept is *learned*. Both stay
  co-equal.
- **[`ICT_CONCEPTS.md`](ICT_CONCEPTS.md) is the curriculum specification**: every concept with its
  meaning, draft rule, detectability, teaching modes and data needs. New detectors and drills are
  specified there first.
- **ICT is the only methodology track in v1.** The orderflow, generic price-action and session-
  statistics tracks of Claude's analysis (§6.2) are dropped; price-action basics appear only as the
  foundations ICT builds on (Level 0–1). Order flow (Q-03) is therefore out of scope.
- **Every milestone must ship visible ICT content.** The project spent its first sixteen sessions on
  foundations (auth, data, chart, fill engine, immutability) with one ICT concept to show; Fabio
  noticed. The roadmap in `PATH.md` now interleaves engine work with concepts a learner can see.
- **Nothing in the honesty decisions changes.** INVARIANT 8, DEC-026 and DEC-034 apply to every
  concept: Tradeez grades adherence to its versioned reading of ICT, never that ICT works; a new
  Evidence mode shows how often each concept's claims held in the imported history, with sample sizes.

**DEC-043 — The 2022 mentorship is the default ICT reading; killzones are the index-futures version**
`AGREED` · Fabio, Session 017 (2026-10-04) · resolves Q-13 and Q-14

ICT's definitions shift between teaching periods, so every rule needs a stated source. Fabio approved
(*"yeah this all sounds good now. The project path is good"*):

- **Where periods disagree, version 1.0.0 of a rule follows the 2022 mentorship.** It was taught on
  index futures (ES/NQ), it is public (a learner or the jury can watch the same source), and its model
  is a sequence of checkable steps: sweep → displacement with a market structure shift → entry in the
  fair value gap → opposite liquidity. Concretely: an MSS requires a sweep and displacement; entries
  are FVG-based; the daily reference is the New York midnight open; stops sit beyond the sweep's swing;
  targets are opposing liquidity or standard deviations.
- **Topics the 2022 material treats thinly come from the 2016–17 mentorship**, labelled as such: the
  detailed block definitions (order, breaker, mitigation, rejection), the PD array matrix, IPDA data
  ranges, weekly profiles, market maker models.
- **`sessions-1.0.0` uses the index-futures killzones** of `ICT_CONCEPTS.md` ICT-51 (New York AM
  08:30–11:00, London 02:00–05:00, Asia 20:00–00:00, New York PM 13:30–16:00). Forex variants may
  become alternative versions, never silent edits.
- **Every implemented rule names the lecture or period it follows**, and definitions marked "verify" in
  `ICT_CONCEPTS.md` are checked against the source before they are locked.
- "Default" is a documented choice, not a claim that this reading is the correct one (INVARIANT 8).

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

**DEC-039 — FVG rule `fvg-1.0.0`, and drills that do not bend the data** · `AGREED` · Claude, Session 016
The first detector under DEC-026. The rule:

- **Bullish:** candle 1's high is below candle 3's low. **Bearish:** candle 1's low is above candle 3's high.
- The three candles are **consecutive** — no market closure between them. A jump across a session
  break is an opening gap, a different concept, and is never reported.
- The gap is at least **25% of the average high–low range of the 14 candles before candle 1**. Integer
  ticks throughout: the threshold is rounded up, never below one tick, and compared without floating
  point.

**Why relative, not a fixed size.** Measured on the simulated market: a fixed 4-tick minimum flagged
roughly one five-minute candle in four, and almost no one-minute candles. Measured against recent
volatility, the rule gives about 3–7 gaps per 30 candles at 5m and 1.5–3 at 15m across three seeds,
and the same parameters carry to other instruments. The 25% is a judgement call — exactly the kind
of parameter DEC-026 says shifts results — so it is stored on every hit, and changing it or any other
parameter means a new rule version.

Consequences:

1. **The generator is not tuned for drills.** At one minute the simulated market produces almost no
   qualifying gaps. One-minute drills are refused, with that reason, rather than manufactured:
   tuning the generator to produce gaps would be the circularity DEC-034 forbids.
2. **Hits are scanned on demand and stored idempotently.** A simulated market has no end, so "scan
   history once" becomes "scan a stretch the first time something needs it". The insert ignores
   duplicates, so concurrent scans of overlapping stretches are safe. Parameters are part of a hit's
   identity.
3. **Identification drills use the public chart's market (seed 42).** A static chart hides nothing in
   the future, so it needs no secret seed (contrast DEC-038), and one market means one shared set of
   stored hits.
4. **Explanations state what the rule checked, and nothing more.** They are rendered from the stored
   evidence. An early draft saying the gap "was crossed only by candle 2" was cut: the rule does not
   establish it.
5. **DEC-034 is in the schema.** `ck_drill_simulated_never_graded` makes a graded simulated drill
   impossible, and a trigger makes an answered drill immutable.

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

**DEC-037 — Continue with the hand-built drawing layer. Gate passed.** `AGREED` · Session 012

DEC-004 required a five-tool proof followed by an explicit decision: keep building drawings by
hand, or adopt KLineChart and inherit its toolset. The proof is built and the answer is **keep
building**. The reasoning, so it can be challenged later rather than assumed:

**What the proof cost.** One session for all four shapes plus the shared machinery — selection,
dragging, handle reshaping, magnet snapping, undo/redo, persistence, and 44 tests. That is
materially cheaper than feared, and the reason is that the hard part is not the shapes.

**The hard part is the machinery, and it is now done.** Hit testing, the projection between
market space and screen space, history, and the pointer-transparency trick that keeps chart
panning working are all tool-agnostic. A fifth shape — Fibonacci, a channel, a measured move — is
a hit-test function and a draw function against interfaces that already exist. The marginal cost
of tool number five is small in a way the marginal cost of tool number one was not.

**What adopting KLineChart would actually cost.** It would replace the renderer, not just add
tools: a different chart, a different data pipeline, a different plugin model, and the loss of
everything built on `ChartAdapter`. And its drawings would not know about integer ticks
(INVARIANT 3), the magnet would not snap to our bars, and none of it would anchor to raw prices
the way DEC-024 requires — so the Practice tab could not score stop placement against detected
structure without re-deriving all of it anyway.

**The decisive argument is the position tool.** It already renders a live risk/reward with real
money — "long · 2.00R · risk $200.00 · reward $400.00" — computed from the instrument's tick
value. That is not a drawing; it is the Practice tab's trade-entry control, built early. No
third-party library would give us that, because it depends on our contract economics and our
fill model.

**What this does not license.** The scope stays four shapes. A catalogue of sixty-eight tools is
still worth less than a finished Practice loop, and DEC-004's warning stands: this layer is where
the schedule goes to die if it is allowed to. New tools are added when a curriculum needs them,
not because they would look good in a toolbar.

**Reconsider if** a future tool needs something the overlay genuinely cannot express — text
editing in place, or a shape that must interleave with the candle rendering rather than sit above
it.

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
| Q-03 | Is order flow / footprint in scope at all? Both analyses say it is impossible without licensed trade/quote/depth data. **Recommendation: cut it from v1 and say why** rather than build a fake footprint chart. *Session 017: DEC-041 puts it out of scope for v1 (not part of ICT); close it if Fabio agrees.* | Fabio |
| Q-08 | Croatian or English UI for the presentation? | Fabio |
| ~~Q-13~~ | ~~Default ICT reading?~~ **Resolved → DEC-043** (2022 mentorship; thin topics from 2016–17, labelled). | closed |
| ~~Q-14~~ | ~~Which killzone table becomes `sessions-1.0.0`?~~ **Resolved → DEC-043** (index-futures version). | closed |
| Q-15 | Source of historical economic-calendar dates for news-linked concepts. | either LLM, when Level 6 is built |
| Q-09 | Select and legally verify the real NQ/MNQ historical-data source before Tradeez claims real-market training or deploys vendor data. This is not a Phase 0 blocker. | Fabio + either LLM |
