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

**DEC-036 — Spring Boot 3.5.x on Java 21, not Spring Boot 4.x** · `SUPERSEDED` by DEC-056 (2026-10-15) · Claude, Session 006
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

**DEC-056 — Newest versions everywhere: Spring Boot 4.1, Java 25, Node 24 LTS** `IMPLEMENTED` ·
Fabio, Session 017 (2026-10-15) — supersedes DEC-036

Fabio: "update undici and everything to the newest version, dont leave it lagging behind that leaves
holes for hackers." It started with an npm update made outside the session (ENV-007): the `undici`
that jsdom pulls in had five high-severity advisories, and the fixed one needs Node ≥ 22.19. Checking
the rest found worse: **Spring Boot 3.5's free security support ended on 2026-06-30** — the backend was
on a line that no longer gets public fixes.

- **Backend:** Spring Boot **4.1.1** (Spring Framework 7, Spring Security 7.1, Hibernate 7.4, Jackson
  3.1, Flyway 12.4, Testcontainers 2.0, JUnit 6), springdoc **3.1.1**, jjwt 0.13.0, the PostgreSQL
  driver 42.7.14 and Lombok 1.18.48 (newer patches than Boot manages), **Java 25** target (the newest
  LTS; the JDK installed is 26), Maven **3.10.0**. DEC-036's reason is gone — it said to revisit once
  springdoc supported Boot 4, and springdoc 3 does. The migration was the starters Boot 4 split up
  (`-webmvc`, `-flyway`, `-webmvc-test`, Testcontainers' new artifact names and `PostgreSQLContainer`)
  and Jackson 3 (`tools.jackson.*`; `asText` → `asString`). All 379 tests passed on it unchanged.
  Jackson 2 remains only inside `jjwt-jackson`.
- **Frontend:** Node **24.20 LTS** (the Node 22 line installed before is replaced; Node 26 is not yet
  LTS), npm **12.2**, and every package at its newest — React 19.3, Vite 8.3, **Vitest 5**,
  **TypeScript 7**, jsdom 30.1 (undici 8.12.2), oxlint 1.88. `@types/node` follows the runtime's major
  (24), not the newest types. `engines` records Node ≥ 24.20. **`npm audit`: 0 vulnerabilities.**
- **TypeScript 7 has no compiler API**, which `openapi-typescript` (newest 7.13) still needs. It lives
  in its own small tool package, `frontend/tools/api-types`, locked to TypeScript 5.9.3 by its own
  lockfile and audited like the rest; `npm run api:types` installs and runs it. The app keeps
  TypeScript 7 — it typechecks on it, generated types included — so neither has to be held back for
  the other. 27 packages fewer in the app's own tree.
- **knip** is a locked dev dependency too, run as `npm run knip` (ENV-009). The global npm tools
  were updated as well: Bruno CLI 4.2, nodemon 3.1, TypeScript 7, Vite 8.3. npm 12 no longer runs
  packages' install scripts unless allowed; none of ours needs one.
- **PostgreSQL stays on 17** (17.11, the newest 17.x, on a freshly built image) — Fabio's choice,
  asked: 18 cannot read 17's data files, so moving means a dump of the database holding the imported
  history and a restore into a fresh 18. 17 gets security fixes until November 2029.

Found on the way, all fixed (BUGS.md): a rejected **password was written to the server log** on every
failed registration validation (BUG-054); the time-zone button named today's
zone instead of the chart's (BUG-052); the `api:types` script pointed at another app's port (BUG-053).

**The rule from now on:** dependencies are kept at their newest versions; a major upgrade is done
when it is released and the tests pass on it, not deferred. Exceptions are written down with the
reason (here: `@types/node` follows the runtime, PostgreSQL 17 keeps its data).


**DEC-044 — Tailwind CSS for all styling** · `IMPLEMENTED` · Fabio, Session 017 (2026-10-13)

Fabio, on a screenshot of the sign-in page: *"this looks extremely ugly and nothing is positioned
correctly"*, then *"use tailwind"* and *"rewrite whole css and make it in tailwind … delete the damn
vite starter styles"*. (He recalled asking for Tailwind at the start; no record of that request
exists in these docs or the transcripts Claude can read — it is recorded here.)

- **Tailwind CSS 4** via `@tailwindcss/vite`. No hand-written stylesheets: `src/index.css` holds only
  the Tailwind import, the **design tokens** (`@theme`: canvas, surface, raised, line, ink, subtle,
  brand, up, down, warn) and two base rules (dark colour scheme, body defaults).
- **One palette for page and chart.** The tokens are the colours `lightweightAdapter.ts` already
  used, so the chart canvas and the UI around it cannot drift apart.
- **A few shared pieces, not a component library.** `components/ui.tsx` (Button, Segmented, Panel,
  Chip, Notice) and `lib/styles.ts` (`cx` and class strings for fields, labels, key–value lists).
  Utilities otherwise live at the call site.
- **No conflicting utilities.** Two classes for the same property are resolved by stylesheet order,
  not class order, so a button's resting and active looks are separate complete sets, and a
  `className` passed in is for layout only.
- Font: Inter (variable), bundled from npm — no external font request.

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

*Implemented in Session 017 (task T1): `marketdata/massive/`. Checked against the real API before
building: Massive answers a date outside the plan's window with "OK" and no results, so a month
with no data is reported and never recorded as imported — a wider plan later can still fill it.*

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
  test needs. *Implemented in Session 017 (T3, migration V8) for the chart; Practice and Drills keep
  using the labelled simulated market until T4 and T13.* Practice moved to real data in T4, Drills in
  T13 (DEC-054); on the simulated market only the FVG drill is left, as its labelled fixture.

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

*Implemented in Session 017 (T4) by DEC-045.*

**DEC-045 — Practice on real data: a hidden-date clock, minute fills, a filterable picker**
`IMPLEMENTED` · Claude, Session 017 (2026-10-13), implementing DEC-025 and DEC-040 for practice

- **The date never reaches the client before the result.** A real session's times — bars, cursor,
  plan, entry, exit — are shown on a fictional week in January 2000 (`PracticeClock`): same weekday,
  same New York wall-clock time, written as UTC (`timeBasis = NEW_YORK_WALL_CLOCK`). The offset is
  per session and server-side (`practice_session.clock_day_shift`). The client renders those times in
  UTC and labels them "NY" — not EST/EDT, which would reveal the season. Daylight-saving changes fall
  on Sunday 02:00 New York, when CME is closed, so wall-clock times never collide. The real start is
  sent once COMPLETE (`revealedStartTime`). Simulated sessions keep real epoch times (DEC-038).
- **Fills on the finest bars stored** — one-second where they exist, otherwise one-minute (Massive's
  free plan, DEC-042) — and the result records which (`resolution`). **`fill-1.1.0`** drops
  fill-1.0.0's rule that any gap in the bars meant the market had closed: real data is sparse, and a
  quiet minute with no trades has no bar. The service ends the bars at the session's close instead, so
  the close still ends the trade. An ambiguous result says "inside the same minute" on minute bars.
- **The moment is drawn from the imported history** where the market actually traded — open by the
  calendar *and* with bars around it (the calendar knows hours, not holidays) — optionally inside an
  ICT killzone (`sessions-1.0.0`, DEC-043) and on a chosen New York weekday.
- **Provenance without a seed:** real sessions and results record `data_source` (V9); a CHECK requires
  every session and result to carry a seed or a source.
- **Default instrument is real NQ.** Simulated `DEMO-NQ` remains available by symbol (Drills, tests).

Known limits, stated rather than hidden: the price level hints at the era (NQ at 20,000 vs 30,000),
and a determined learner could search the Chart tab for the same pattern. Neither gives the next
candle; both are in BUGS.md's known limitations.

*Refined by DEC-046:* "COMPLETE" now means the session has **ended**, not that a trade has. The date
is revealed then, and no sooner, because a second trade could use it.

**DEC-046 — A practice session is a replay with several trades, one at a time**
`IMPLEMENTED` · Claude, Session 017 (2026-10-14), T5 — Fabio's core idea (DEC-041): paper trading
historical charts, taking trades whenever the candles say so

- **The session is a replay.** It starts flat (`ANALYSING`) at a hidden moment and is played forward
  bar by bar — stepped, played at 1–8 bars a second, paused. Whenever the learner sees a setup they
  lock a bracket (`IN_TRADE`). When the replay passes its exit the result is revealed and they are
  flat again, free to take the next. **One trade runs at a time**: a second plan while one runs is
  refused, as before (DEC-013).
- **Each trade is written once, at its lock** (INVARIANT 2), numbered within the session
  (`trade_plan.seq`, V10); its result carries the same number, and a composite foreign key ties the
  two. Locked plans and written results stay immutable in the database (V5's triggers, untouched).
- **A trade's result is revealed when the replay passes its exit — the session's secrets when it
  ends.** The seed of a simulated market and the date of a real one (DEC-038, DEC-025) are the
  *whole* future, so they wait until no trade can use them: the learner ends the session (only while
  flat; ending twice is harmless), or a flat replay reaches its limit and ends by itself — moving over
  a closed market to get there if it has to.
- **The score counts closed trades only:** count, wins, losses, total R and net P&L. A running trade's
  outcome exists on the server and counts for nothing until its exit is shown, in the session and in
  the journal alike. The total R is the sum of the R column as displayed (BUG-039).
- **Finished trades stay on the chart,** drawn entry to exit at the real fill with the planned stop
  and target. They are the record: never selectable, never editable.
- **Bounds:** at most 50 trades a replay; the 3-day replay limit and the hourly session cap stand.

Not in this decision: limit entries, scaling, partial targets (T28), and moving a stop after the
lock — DEC-013's revision log is where that belongs.

**DEC-057 — A draft is not a trade; a placed trade can be closed at market**
`IMPLEMENTED` · Claude, Session 017 (2026-10-16) — Fabio's report: "hitting stop loss or take profit
doesnt close the trade", and "there is no way to cancel trade once you press go long or go short"
(BUG-055, BUG-056)

- **A draft says it is a draft.** "Plan long" / "Plan short" draws a bracket and nothing else: the panel
  is titled *Draft · long at market — not placed*, the bracket can be reshaped, flipped or
  **discarded**, and price crossing its lines does nothing because there is no trade. "Place trade"
  (was "Lock plan") is the only thing that sends the plan; from then on it is immutable (DEC-013).
- **A placed trade can be closed at market at any time** (`POST /api/practice/sessions/{id}/close`).
  It exits at the open of the first bar of the fill engine's resolution at or after the cursor —
  exactly how the entry fills — with no slippage and the normal commission (`fill-1.2.0`,
  `Outcome.CLOSED`). The replay moves to one such bar after the fill, so the result is shown at once.
- **The plan's own exit wins a tie.** If the stop or target was touched before the cursor, or is
  touched by that very open (a gap), the resting order fills first: the close reveals the plan's exit
  as Skip to result would, and nothing is written.
- **The close is a second immutable fact, not an edit.** `practice_close` (V15) sits beside the planned
  result, keyed by the same session and trade number, with its own engine and fill-model versions, and
  the database refuses an update (the same `forbid_update` trigger). `practice_result` stays exactly as
  written at the lock (INVARIANT 2): it is what the plan would have done, not shown to the learner yet.
  Score, journal and the trade log read the close when there is one.

Rejected: rewriting the stored result on close (breaks INVARIANT 2 and loses the planned outcome a
later review, T24, will want), and filling the close at the last close on screen (that price is
already gone — the next trade happens at the next open, which is also how the entry fills).

**DEC-051 — A replay can be looked at on any timeframe** `IMPLEMENTED` · Claude, Session 017
(2026-10-15), T34 — Fabio: "on practice chart you can switch time frames too"

- **A view, not a new session.** A replay starts on 1m, 5m or 15m as before, and can then be looked
  at on any of the Chart tab's timeframes, 1m to 1h, as often as the learner likes. The session's own
  timeframe is unchanged; each request names the one it wants (`?timeframe=`, or `timeframe` in a
  step) and the server builds the candles.
- **The candle still forming is built only from what came before the cursor.** Stepping a 5m replay
  to 10:05 and switching to 15m shows a 10:00 candle made of five minutes, exactly as a live chart
  would. That needed no new code — both market-data providers already cut at the requested end — and
  a test now holds them to it: the forming candle equals the stored minutes up to the cursor, and
  building it to the candle's end instead was tried and caught.
- **A step is a candle of the timeframe being viewed.** A candle still forming counts as the first:
  "Next bar" on 15m at 10:05 completes the 10:00 candle and moves the cursor to 10:15. What comes
  back starts with that whole candle, and the chart updates its newest candle in place rather than
  adding a second one at the same time, so the learner's zoom survives. "Skip to result" stops at
  the end of the viewed candle the trade ended in. The cursor never passes the replay's limit: a
  candle the limit cuts through stays forming.
- **Everything else follows the view:** detected setups are asked for on it, the New York clock and
  finished trades are drawn on it. Fills do not change — they are decided on the finest stored bars
  whatever is on screen (DEC-008).

Not in this decision: remembering the viewed timeframe when a session is resumed (it reopens on its
own), and 4h/daily, which need session-aligned candles first (T35).

**DEC-047 — The New York clock layer: three versioned rules, computed from minutes, never ahead of
the cursor**
`IMPLEMENTED` · Claude, Session 017 (2026-10-14), T7 — ICT-11 and ICT-50 to ICT-54

- **What it is.** An overlay on every chart — the Chart tab and every replay — that shows the day the
  way ICT reads it: trading-day separators at 18:00, the four killzones (DEC-043's index-futures
  times), the Silver Bullet hours, the macros; and the prices read off the clock — the midnight,
  08:30, 09:30 and weekly opens, PDH/PDL, PWH/PWL, and each killzone's high and low. Each part can be
  switched on and off; the choice is remembered in the browser.
- **Three versioned rules** (DEC-026), exactly as written in `ICT_CONCEPTS.md`: `sessions-1.0.0`,
  `opens-1.0.0`, `levels-1.0.0`. Every response names them. The day is the CME trading day, 18:00 to
  17:00 New York (`dayBoundary = CME`). The macro schedule is the commonly cited one and the spec
  flags it for checking; a correction will be a new version, not an edit.
- **Computed on the server from one-minute bars, not from the chart's candles.** On an hourly chart
  the 08:30 boundary falls inside the 08:00 candle, so a session high read off hourly candles would
  include half an hour that is not in the session. Nothing is stored: a layer is a cheap pure function
  of the minutes and the rule versions.
- **Never ahead of the cursor (DEC-009).** The builder is handed only bars before an instant and
  refuses any other. A level appears once its period has closed, an opening price once its minute has
  printed. Window and line *ends* may lie past the cursor — they are clock times, not prices. A replay
  is told when its layer can next change (`nextChange`) and asks again only then, not on every bar.
  On real data every time goes through the session's hidden-date clock (DEC-045).
- **Honest about missing and joined data.** No level is measured from a period the data did not see
  from its start (the start of the import, or of what was read). No level is drawn across a contract
  roll: one measured on the old contract is left out, and one whose span crosses a roll stops at it —
  NQ's next contract trades a few hundred points away.
- **Drawn in the frontend** on its own canvas under the drawings and above the candles, faintly, and
  never over the price or time scale. Times between bars are placed by proportion inside their bar.

Not yet: London close and lunch shading, overnight/RTH/monthly levels, CBDR (ICT-55), and whether a
level has been taken (`liquidity-1.0.0`, T12).

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
*Session 017 (DEC-045): on real data without one-second bars the ladder starts at one minute, and
`fill-1.1.0` no longer reads a gap in the bars as the market closing.*
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

*Session 017 (DEC-046):* a session now holds several plans, numbered, one running at a time. Each is
as immutable as the single plan was; the revision history is still to be built.

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

*Extended by DEC-049 (Session 017, T9):* the table now holds any detector's hits, each with the
moment it became knowable, and "scan once" is done lazily, stretch by stretch, as charts ask.

**DEC-049 — One contract for every detector; the curriculum read from its document** `IMPLEMENTED` ·
Claude, Session 017 (2026-10-15), T9 — ICT_CONCEPTS.md S-1, S-2, S-3

The FVG was written before there was a second detector. T10–T12 bring a dozen; each must be safe to
draw over a replay without leaking the future. So, before any of them:

**The detector contract (S-1).** A `Detector` has an id, a rule version, parameters, a methodology
source and the concept it reads (`conceptId`). Each `Detection` has a **shape** — `ZONE` (a price
band), `LEVEL` (one price over a span) or `POINT` (one price at one candle) — a direction (bullish,
bearish, neutral), its span and anchor, its evidence, and a **confirmation time**: the first moment
it could be known. Every detector keeps four promises, checked for every registered detector by
`DetectorContractTest`:

- **Deterministic** — same candles, same detections.
- **Causal** — a detection depends only on candles that had closed by its confirmation, and that is
  the first moment they had. The test finds each detection again from exactly the candles closed by
  its confirmation, and cuts the candles at every third bar to check nothing appears sooner. Both a
  confirmation one second early and one candle late were tried on the FVG and caught.
- **Honest lookback** — `lookbackBars()` is how many candles a detection may read, counting back from
  its confirmation (the FVG: its own three plus 14). Given only that many candles before a point, it
  finds the same from that point on.
- **Unbroken, one per place** — it is handed one contract at a time (the framework splits candles at
  every roll, `Segments.atRolls`), its pattern's own candles never span a session break
  (`Segments.consecutive`), and it reports at most one hit per anchor and direction — the row's
  identity.

Causality is what lets **a replay be served from one scan of its whole window**: the scanner stores
everything up to the end of the replay once, and the endpoint returns only rows confirmed by the
cursor. A row confirmed by the cursor is exactly what a scan up to the cursor would have found —
`SetupApiTest` checks the replay's answer equals the Chart tab's over the same real stretch, moved
onto the session's clock.

**Scanning (DEC-026's "once").** `detected_setup` gains `shape` and `confirmed_at` (V12; the 95
stored FVGs were backfilled: candle 3's close). `SetupScanner.ensureScanned` stores every hit
confirmed in a stretch, with exactly `lookbackBars()` candles of history before it — reaching back
up to ten more days when the market was shut, so a Monday-morning gap measures itself against Friday
whichever stretch was scanned first. Inserts ignore duplicates, so scans may overlap. A process-local
cache skips stretches already scanned, but remembers how many hits and how many stored minutes each
had: rows deleted, or minutes imported, since then mean a rescan rather than trust. Real data and
simulated markets are scanned alike (real rows have no seed).

**Reading.** `GET /api/market/instruments/{symbol}/setups?timeframe&from&to&detectors=FVG` for the
Chart tab — same limits as the bars (`requirePublicRange`), one minute and above, scans whole UTC
days so scrolling reuses a scan; `GET /api/practice/sessions/{id}/setups?detectors=FVG` for a replay,
on its hidden-date clock. Both send geometry and the rule (version, parameters, concept) — **never
the evidence**, whose candle timestamps are real and would give a hidden date away.

**The concept registry (S-2) is the document.** The build copies `docs/ICT_CONCEPTS.md` onto the
classpath; `ConceptRegistry` reads its master table, each concept's **What.** paragraph and the
curriculum ladder at startup. Prerequisites are the concepts a section refers to that come earlier in
the curriculum (a lower level, or the same level and earlier in the table). The application refuses
to start if the document loses a summary or a section, or if a detector runs a rule the document
does not list for its concept. `GET /api/concepts`. A generated JSON copy with a drift test was
considered and rejected: two sources and a test that they agree is worse than one source.

**The overlay (S-3).** Its own canvas over the clock and under the drawings: a zone is a box over the
candles that formed it, with its CE dashed in half-ticks; a level a line; a point a mark. A zone is
**not** extended to the right — whether price returned to it is another rule (`fvg-state`, T10).
One switch per detector, named from the registry (concept, rule version, summary), **off by default**:
a learner should find a gap before being shown one. In a replay, setups are asked for whenever the
cursor moves, one request at a time.

Not in this decision: the drill engine for any detector (S-4, T13) and re-measuring the FVG threshold
on real NQ — the Chart tab shows 301 `fvg-1.0.0` gaps in two weeks of real 5-minute NQ (2,760
candles), about one candle in nine, which is T10's question.

**Reconsider if** a detector genuinely needs to revise a past detection as later candles arrive (a
swing that stops being one): that is a new detection with its own confirmation, not an edit, or the
causal promise breaks.

**DEC-053 — Liquidity: resting and taken, equal highs and lows, sweep or run** `IMPLEMENTED` ·
Claude, Session 017 (2026-10-15), T12 — ICT-10 to ICT-14

Measured first on the two years of real NQ:

- **Swing levels** are taken fast: half within 5 candles of their confirmation, 95% within 500 — on
  every timeframe. A level is followed for **500 candles**, like a gap's lifecycle; the rest expire.
- **When one is taken**, about **50%** close back the same candle (sweep), **29%** close beyond and
  hold two more candles (run), **21%** neither — at the draft's parameters, on every timeframe. A
  description of the past, not an edge (INVARIANT 8).
- **Relative equal highs/lows** at the draft's tolerance: 1.3 to 2.2 per 30 candles.

The draft numbers are kept. The rules:

- **`liquidity-1.0.0` (ICT-10)** — every swing high is buy-side, every swing low sell-side liquidity:
  `RESTING` from the swing's confirmation, `TAKEN` at the close of the first candle trading a tick
  beyond, `EXPIRED` if still resting 500 candles on.
- **Reference levels (ICT-11) under the same rule**, in the New York clock layer: PDH/PDL, PWH/PWL and
  each killzone's high and low carry the minute that took them, found from the minutes before the
  layer's end only. A replay asks for the clock again only at the clock's own moments (DEC-047); while
  a level still rests, that moment is now the next minute — which says only that a level could be
  taken, never when, so nothing about the future leaks.
- **`eqhl-1.0.0` (ICT-12)** — a swing pairs with the latest earlier one of its kind at least 3 candles
  before it, within the larger of a tick and 10% of the 14-candle average range (rounded up, in
  ticks), with no candle between beyond the more extreme of the two. Read within 200 candles.
- **`sweep-1.0.0` (ICT-13)** — `SWEEP` (closed back in the taking candle) or `RUN` (closed beyond,
  and two more closes beyond); undecided is not reported. When one candle takes several levels on a
  side it is judged on the most extreme — a candle through 115 and 120 that closes at 118 swept 120;
  it did not run 115.
- **Turtle soup (ICT-14)**, under `sweep-1.0.0`: a sweep of an equal-highs or equal-lows pool, its
  level the more extreme of the two. Sweeps of the reference levels are visible as taken levels in the
  clock layer but are not reported as turtle soup.

Direction: liquidity and equal highs/lows point by their side, highs down and lows up, as the swings
they come from; a sweep points away from the level, a run through it.

**Found while building it:** the setups endpoint refused more than 16 rules in one request — "more
than the overlay toggles there are" when written. T12 makes 18, so a learner with every switch on
would have got an error. Raised to 32, and the contract test now fails if the registered rules ever
outnumber it.

**On the chart:** a liquidity level is one line at its latest state (BSL over a high, SSL under a
low), running on while it rests and stopping faint once taken; a taken clock level ends at the take
with a small cross. Four more switches, off by default.

Not in this decision: drills (T13), sweeps of reference levels as turtle soup, ERL/IRL and the draw
on liquidity (T15), and the Evidence views of the sweep and take statistics (T21).

**DEC-052 — Market structure: six rules on one swing, each with a bounded memory** `IMPLEMENTED` ·
Claude, Session 017 (2026-10-15), T11 — ICT-01 to ICT-06

Measured first on the two years of real NQ, aggregated as the app does:

- **Swings** (`swing-1.0.0`, one candle each side as ICT draws short-term structure): about 13.5 per
  30 candles, highs and lows together, on every timeframe (8 per 30 with two each side). Kept at one:
  it is ICT's definition, and the ranks below are how the chart is read without the noise.
- **Ranks** (`swing-tier-1.0.0`): an intermediate-term high's span — from the swing before it to the
  one that confirms it — never passed 36 candles; a long-term high's never passed 160.
- **Displacement** (`displacement-1.0.0`): at the draft's two times the recent range, about half of all
  gaps are left by displacement on every timeframe (1.5 per 30 five-minute candles). Kept.

**A bounded memory for every rule.** Structure is read off swings that can lie any distance back, but
DEC-049 needs a finite lookback. So each rule reads at most a fixed span of candles, from the first
it uses to the one that confirms it: 300 for ranks, 200 for labels, breaks and shifts. A rule that
would need more is not reported — never cut short. The bounds sit far above anything measured.

The rules — each checked by `DetectorContractTest`, and pinned by `StructureDetectorsTest`:

- **`swing-1.0.0` (ICT-01)** — strictly above the candle to the left, at least the one to the right
  (of two equal highs the first is the swing); `HIGH`/`LOW`; known at the next close.
- **`swing-tier-1.0.0` (ICT-02)** — `ITH`/`LTH` and `ITL`/`LTL`, known only when the next swing (or
  ITH) is: the lag the curriculum warned overlays must respect.
- **`structure-1.0.0` (ICT-03)** — each swing labelled `HH`/`LH`/`EH` or `HL`/`LL`/`EL` against the
  last of its kind.
- **The same rule's breaks (ICT-06)** — a second detector, `BOS_CHOCH`, under `structure-1.0.0` as the
  curriculum says: the first *close* beyond the latest swing, `BOS` with the trend (bullish = last high
  HH and last low HL), `CHOCH` against it; unnamed while structure is undefined. Labelled as SMC words.
- **`displacement-1.0.0` (ICT-04)** — the leg is the gap's middle candle with its first and third when
  they close the same way: 1–3 candles, always the ones that made the gap, one displacement per gap.
  The draft left open which candles and when a later gap counts; this settles both. Known with the gap.
- **`mss-1.0.0` (ICT-05)** — a swing sweeps an earlier, still-resting swing; the latest opposite swing
  before it is closed through within 20 candles, the first close since it formed; a displacement leg
  starts between the sweep and the break. **Confirmed at the later of the break and the leg's
  confirmation**, anchored on the breaking candle.

**Found while building it:**
- The first MSS rule confirmed a shift at the breaking close and required the displacement to be known
  by then. But the displacement candle is often the one that closes through the level, and its gap is
  known a candle later — so the commonest shift would never have been reported. A hand-built test of
  exactly that case found it; the old version fails it.
- The first MSS implementation rescanned every swing at every candle; the contract test ran past ten
  minutes and was stopped. Rewritten to walk only nearby swings, with a running extreme for "still
  resting" — the whole contract test now takes about 12 s.

**On the chart.** Swings and ranks are marks above highs and below lows; ranks and labels are named
(ITH, HH…), plain swings are not. A break or a shift is a line at the level that broke, from the swing
to the candle that broke it. Six more switches, off by default.

Not in this decision: drills (T13), liquidity resting on these swings (T12), and the Evidence counts.

**Reconsider if** a rule's span bound turns out to drop real cases on some timeframe — the bound
would rise, as a new version.

**DEC-050 — Imbalances and gaps: seven rules, measured on real NQ first** `IMPLEMENTED` · Claude,
Session 017 (2026-10-15), T10 — ICT-20 to ICT-28

Every number below was measured on the two years of real NQ one-minute bars (697,003, Oct 2024 →
Oct 2026), aggregated the way the app aggregates them, with a port of each rule checked against the
server first (it gave the Chart tab's 301 FVGs exactly).

**`fvg-1.0.0` re-measured, and kept.** Gaps per 30 candles at the rule's 25%: 1m 3.96, 3m 3.35,
5m 3.27, 15m 3.11, 30m 3.06, 1h 2.90. DEC-039 accepted 3–7 per 30 at 5m on simulated data; real NQ
lands inside it, and the rule now behaves the same on every timeframe, which is what a threshold
relative to recent range is for. With no outside criterion for a better number, changing it would be
tuning to taste, so there is no `fvg-1.1.0`. (One-minute gaps, which the simulated market hardly
made, are plentiful on real data — relevant to T13's drills.)

**One table, several kinds of hit (V13).** `detected_setup.kind` tells a detector's kinds apart, and
joins a hit's identity: one per anchor, direction and kind. The visible-setups query now has a total
order, since several states of one gap share a start. A layer also carries the contract rolls in its
window, so the overlay stops anything still in force at a roll. `Detector.lookbackBars` takes the
timeframe: the ORG spans clock time, not a fixed count of candles.

The rules — each a `Detector` under DEC-049, all checked by `DetectorContractTest` on a synthetic
market dense in every pattern:

- **`fvg-state-1.0.0` (ICT-22)** — each gap's lifecycle as hits of kind `OPEN`, `TAPPED`, `CE`,
  `FILLED`, `INVERTED`, each at the close of the candle that moved it; CE compared in half-ticks
  (a bearish gap's CE at 194.5 is reached at 195, not 194). A gap is followed for **500 candles**
  (about two trading days on 5m) and is `EXPIRED` if still unfilled: the bound that keeps the rule's
  lookback finite. Two weeks of real 5m: 301 gaps, 268 filled and 262 closed through within that span.
- **`ifvg-1.0.0` (ICT-23)** — the `INVERTED` event as its own hit, pointing the other way: ICT's
  claim that a failed gap flips, labelled rather than endorsed.
- **`bpr-1.0.0` (ICT-24)** — the overlap of a gap and an opposite one formed within 20 candles,
  pointing the way of the second; the most recent qualifying gap is paired.
- **`vi-1.0.0` (ICT-25)** — bodies apart, wicks overlapping, **at least 4 ticks**. Measured: body
  gaps of any size occur ~7 per 30 candles on *every* timeframe — the space between two trades does
  not grow with the candle — so a range-relative threshold removed them all above 5m. One point
  leaves ~1.6 per 30 everywhere. A fixed number of ticks is instrument-specific; NQ is the instrument.
- **`void-1.0.0` (ICT-26)** — kind `GAP` (two candles whose ranges do not touch: 0.4 per 30 at 1m,
  ~0 above) and kind `VOID` (≥ 3 same-way candles, each closing beyond the last and overlapping it by
  ≤ 20% of its range; reported when a candle ends the run, as the whole run). The draft's numbers,
  kept: about one void a day on 5m. "Closing beyond the last" is new — without it, up-closing candles
  each gapping lower counted. Runs are measured over at most 10 candles; the longest in two years was 6.
- **`opening-gap-1.0.0` (ICT-27)** — `NDOG` / `NWOG` from the last close before a closure to the
  first open after; zero gaps kept as a level. The closure is read from the data — candles of one
  contract at least 60 minutes apart — so holidays, unknown to the session calendar, count, and a roll
  at a reopen is never a gap. Charts of an hour or less.
- **`org-1.0.0` (ICT-28)** — the 16:14 close to the next 09:30 open, with no other 09:30 between,
  within one day of candles; 1m to 15m charts. Split from `opening-gap-1.0.0`, as the draft had it,
  because a detector names one concept.

**On the chart.** Most hits are boxes over their own candles. A gap's lifecycle is one box at its
latest state, running on while open/tapped/at CE and faded once filled, inverted or expired; an
NDOG runs to the next closure, an NWOG to the right edge; an ORG to the next one's 16:15 close, with
quarter lines. All eight rules are switches, off by default.

Two weeks of real 5m NQ: 301 FVG, 79 BPR, 163 VI, 2 gaps, 8 voids, 8 NDOG + 2 NWOG, 10 ORG. A
first scan of all eight takes 1.2 s; again, 0.18 s.

Not in this decision: drills for any of them (T13), the Evidence statistics the curriculum asks for
— fill rates, the ORG "70%" claim (T21) — and ICT-29, which needs its definition checked first.

**Reconsider if** T21's evidence shows a rule's parameter makes it useless — for example if
`maxAgeCandles` cuts off most gaps before they resolve on some timeframe.

**DEC-054 — The drill engine: any rule, two modes, graded on real data** `IMPLEMENTED` ·
Claude, Session 017 (2026-10-15), T13 — S-4

The FVG drill generalised: a drill is **one question a versioned rule answers about a static chart**,
and the engine — window, answer key, grading, explanations — is the same for all of them. A new
drill is one constant in `DrillKind` (and a row in V14's `ck_drill_kind`).

- **Ten drills.** *Spot* (mode A, 30 candles, "mark every …"): fair value gaps, swing highs, swing
  lows, volume imbalances, displacement, relative equal highs/lows, liquidity sweeps. *Name* (mode B,
  40 candles, one structure drawn): its structure label (HH/LH/EH or HL/LL/EL), BOS or CHoCH, sweep
  or run. Each is graded against its rule, named on screen with its definition — generated from the
  rule's parameters, so the words cannot drift from the rule.
- **Real data, graded.** The default market is NQ; 1-, 5- and 15-minute charts; a random stretch of
  the imported history, open throughout by the calendar, every candle present (the calendar knows
  the hours, not the holidays), no contract roll inside. A drill is graded exactly when its market is
  real. On DEMO-NQ only the FVG drill runs, still as the labelled fixture of DEC-034 (and still not
  at one minute) — every other drill on a simulated market is refused with that reason.
- **The answer key is the rule's stored hits** — the scanner the overlay and the replay use (DEC-049),
  so a drill can never disagree with what the chart draws. A hit is asked about only when everything
  the rule read is on screen: from the candle before its start when the start is a swing (its left
  neighbour is what makes it one), to the candle that confirmed it.
- **A hit the window cuts is excused, not counted.** A gap whose first candle is before the window, a
  swing whose confirming candle is after it, a sweep of a level drawn before it: nobody could judge
  these from the chart, so a mark on one counts neither way. The excused candles are fixed when the
  drill is made (V14 `excused`) and named in the result. Before T13 such a mark counted as extra
  (BUG-050). Measured on 5-minute real NQ (17 drills each): sweeps excuse 2.1 candles per drill
  (88% of drills have one) against 3.4 asked — most swept levels formed before the window;
  equal highs/lows 1.0 (71%); swings, gaps and imbalances 0.2–0.5; displacement none.
- **Naming without giving the name away.** A classification drill picks one whole hit at random and
  sends what the chart shows — the side (high or low), where its level starts, the candle, the price
  — and the names to choose from, one group per side (a swing high is never offered "lower low").
  Never its name, direction, end or evidence: a run is known two candles after a sweep would be, so
  even the end would give it away. A BOS or CHoCH depends on the structure before it, so only breaks
  whose latest swing high and low *and* the swings they were labelled against are all on screen are
  asked about — read from the stored `STRUCTURE` hits under the same rule version.
- **Answers.** Mode A: the marked candles, scored found / missed / extra (with excused marks apart),
  shown as recall and precision against the rule. Mode B: one of the offered names, right or wrong,
  with the rule's own. V14 adds `choice` and makes the answer marks *or* a choice, never both; V6's
  trigger still refuses any change once answered. Graded drills are kept for the learning state
  (T16); nothing reads them yet.
- **Explanations moved to the server.** `DrillText` renders every hit from its stored evidence, in the
  instrument's prices and no times — the client adds those, in the exchange's zone. The FVG text is
  unchanged word for word; its tests moved to Java with it. `GET /api/drills/kinds` lists the
  catalogue.

Measured on the dev database's two years of real NQ: every drill on every timeframe found a window,
in 35–150 ms (median 40–60 ms at five minutes, 105 drills, none failed).

Removed as dead: `SetupScanner.scanFvgs` and the repository query behind it (the FVG drill's private
path), and the client's `explainFvg`.

Not in this decision: drills for FVG state, IFVG, BPR, voids, opening gaps, ORG, swing tiers, MSS,
liquidity states and turtle soup — rarer in a short window or needing more context than one; each is
a constant when wanted. Mode C prediction drills (T15) and the learning state (T16).

**Reconsider if** learners find the excused sweeps confusing — a longer window for the sweep drill
would show more of the levels being swept — or if 40 candles prove too little context to name a break.

**DEC-055 — Lesson cards: the curriculum's words, every rule in words, real examples at random**
`IMPLEMENTED` · Claude, Session 017 (2026-10-15), T14 — mode L

The first step of the concept loop (`ICT_CONCEPTS.md` Part 0): a one-minute card per concept, read
before spotting and naming it. Nothing on a card is written for the card.

- **The Learn tab** lists the curriculum as the ladder of Part 12 — all 72 concepts by level, the
  detected ones marked — and opens on the first concept Tradeez can show on a chart.
- **A card** carries the concept's **What.** paragraph from the document, its level, detectability and
  status, what to know first and what it builds toward (the document's own cross-references, already
  read by the registry, DEC-049), the rules that detect it in words, where the app draws it (the
  Setups layer or the New York clock layer), the drills that practise it — "Practise it" opens the
  Drills tab on that drill — and up to three real NQ examples. `GET /api/concepts/{id}/lesson`.
- **Examples are not hand-picked.** Each comes from its own random two-day stretch of the imported
  history: the rule scans it (every hit stored, DEC-026), one hit is taken at random — those the chart
  can show whole first — and shown on the 32 candles up to the one it is known by and 8 after, with no
  roll inside and no overlap with another example. "Show others" asks again; 1m, 5m or 15m. On the
  dev database's two years of real NQ every one of the 18 detected concepts gave three examples at five
  minutes, in 121–613 ms. An opening range gap never fits whole (its 16:15 close is seventeen hours
  before the 09:30 open) and is drawn from the chart's left edge.
- **Drawn as the chart draws it** — the Setups layer's own renderer — with a faint band behind the
  hit's candles, since a small gap on forty candles is easy to miss. The caption names the hit's candle
  with its date (real history, not a replay: nothing is hidden, DEC-045 does not apply) and, when they
  differ, the candle it became known by.
- **Every rule in words.** The drill engine's text moved to `analysis/RuleText` and now covers all 18
  rules: a definition generated from the parameters, a label and an explanation per hit from its
  evidence. `DetectorContractTest` holds it to that: every numeric parameter of every rule appears in
  its definition, and every hit of every rule on the pattern market renders without a missing evidence
  key. Lifecycle and liquidity events now say "a later candle" — the one the caption names — rather
  than "this candle".
- **Concepts without a detector** still get a card — what they are, and that Tradeez does not detect
  them yet; those the clock layer draws say so and where.

Found while building it: nothing in the code. The examples' first wording called the candle that
moved a gap "this candle" while the caption named the gap's own — fixed before it was committed.

Not in this decision: drawn diagrams beyond the examples, the document's **Disagreements.** notes on
cards, examples for the clock-drawn concepts, and the learning state that will track which cards were
read (T16).

**Reconsider if** random examples too often show a rule's odd cases to a beginner — then order them
(cleanest first) without hand-picking: by the rule's own numbers, stated on the card.

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

*Re-measured on real NQ by DEC-050 (Session 017, T10):* about 3 gaps per 30 candles on every
timeframe from 1m to 1h, inside the range accepted here; kept unchanged.

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

*Extended by DEC-048 (Session 017, T8):* the ICT curriculum needed three — and the marginal cost
argued above held: each was a hit test and a draw function, plus one shared text editor.

**DEC-048 — Three ICT drawing tools: Fibonacci, zone, label** `IMPLEMENTED` · Claude, Session 017
(2026-10-14), T8 — ICT-21, ICT-42, ICT-43

DEC-037 kept the drawing layer at four shapes "until a curriculum needs them". ICT's needs three:

- **Fibonacci with ICT's own levels** (ICT-43): 0, 0.5, 0.62, 0.705, 0.79, 1, and the targets −0.27,
  −0.62, −1, −2. Drawn from the start of a leg to its end: level 1 at the start, 0 at the end — so a
  bullish leg drawn low → high puts the optimal trade entry (0.62–0.79, ICT-42) below the high, shaded,
  with 0.705 drawn heavier, and the targets above it. The way ICT draws it, and the way TradingView
  does. Levels run to the chart's right edge: the targets lie in the future, and a replay walks into
  them. A level's price is printed on the tick grid (an order cannot be placed at 0.705 of a tick).
- **Zone with consequent encroachment** (ICT-21): a box — a gap, a block — with its exact middle
  dashed and faint quarter lines. The CE is kept in half-ticks, so a zone an odd number of ticks tall
  keeps an exact integer midpoint (INVARIANT 3), printed with one more decimal (NQ's x.125). A zone
  can carry a name ("BISI 5m").
- **Label**: words at a point, for marking what was seen ("Asia high swept").

Words are typed in a field over the chart — on placing a label, or by double-clicking a label or a
zone — at most 80 characters. A label with no words is not kept. Text is stored in its own column
(V11), not in `style`; the server checks it belongs to a label or zone and that a label has some.

Two guards came with it. The server now checks every drawing has the anchor count its kind needs
(a malformed shape stored would break its chart on every load; V11 adds the same CHECK). And a
creation that is never dragged — a click with a two-point tool — leaves nothing behind (BUG-049),
because a Fibonacci of no length prints all ten levels at one price.

Not in this decision: user-editable level sets, extending levels left, colours per drawing, and
the detector side of ICT-21/42 (`ote-1.0.0`, T18), which grades against rules rather than drawing.


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
