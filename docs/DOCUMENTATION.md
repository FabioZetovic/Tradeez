# Tradeez — Project Documentation

**A day-trading practice and learning platform for futures markets.**

> This document is written for **humans**: the project owner (Fabio), and the jury at the final
> presentation. It records every session of work, what changed, and *why*. It is the narrative
> history of the project.
>
> Its machine-readable counterpart is [PATH.md](PATH.md), which is written for the LLMs
> (Claude Code and ChatGPT Codex) to read at the start of each session to see where the other
> one left off. **Both files must be updated at the end of every session.**

---

## Table of contents

1. [Project overview](#1-project-overview)
2. [Team and workflow](#2-team-and-workflow)
3. [Technology stack](#3-technology-stack)
4. [Session log](#4-session-log)

---

## 1. Project overview

### 1.1 The problem

Learning to day-trade futures is slow because the feedback loop is terrible. A live market
offers only a handful of real setups per day, feedback arrives hours later, and every variable
moves at once. Most aspiring traders lose money learning lessons that could have been learned
from history, for free.

### 1.2 The solution

**Tradeez** compresses months of screen time into hours by replaying historical market data
with the outcome hidden.

The user picks a moment in market history, analyses it with a full suite of professional
charting and drawing tools, commits to a trade plan (entry, stop-loss, take-profit, position
size), and then watches an animated replay of what price actually did. The app then grades not
only whether they made money, but **the quality of the decision itself** — risk sizing, stop
placement relative to market structure, reward-to-risk ratio, and how much of the available move
they captured.

On top of that sits a **training system** that scans years of history, automatically detects
methodology-specific setups (ICT / Smart Money Concepts, order flow, classic price action), and
generates thousands of graded exercises from them, served by a spaced-repetition scheduler.

### 1.3 Primary instrument

**NQ — E-mini Nasdaq-100 futures (CME Globex).** The architecture is instrument-agnostic
(a registry of contracts with their own tick sizes and multipliers), so ES, YM, RTY, CL, GC and
exchange-traded FX futures can later be added without hardcoding each product.

| Property | NQ | MNQ (Micro) |
|---|---|---|
| Multiplier | $20 / index point | $2 / index point |
| Tick size | 0.25 points | 0.25 points |
| Tick value | $5.00 | $0.50 |
| Trading hours | Sun 17:00 CT → Fri 16:00 CT (daily halt 16:00–17:00 CT) | same |

### 1.4 Core feature areas

| Area | Description | Status |
|---|---|---|
| **Markets** | Instrument browser, contract registry, historical charts | Not started |
| **Charts** | Candlestick charting with indicators and full drawing toolset | Not started |
| **Practice** ⭐ | Hidden-outcome historical replay with trade simulation and grading | Not started |
| **Accounts** | Registration, login, session rotation, per-user data ownership | API done (Session 008); no UI |
| **Training** | Auto-generated drills per methodology, with spaced repetition | Not started |
| **Journal** | Trade log with R-multiples, expectancy, MAE/MFE, equity curve | Not started |

---

## 2. Team and workflow

### 2.1 Who does what

| Member | Role |
|---|---|
| **Fabio** | Project owner, **target user** (learning futures day trading from scratch), final decision maker |
| **Claude Code** | Development collaborator |
| **ChatGPT Codex** | Development collaborator |

Two independent AI collaborators work on the same codebase across separate sessions. Neither
has memory of the other's session. The documentation protocol below is what keeps them in sync.

> **Corrected in Session 006.** Earlier revisions of this table described Fabio as a "domain
> expert (futures trading)." That was Claude's inference from the opening brief and it is **not
> accurate** — Fabio has not opened a futures account and has not begun trading. He is building
> Tradeez in order to learn.
>
> This is worth stating plainly rather than quietly fixing, for two reasons. First, an inaccurate
> credential in a document the jury will read is a liability. Second, it is genuinely a **stronger**
> position for the project: Tradeez is being built for a real, specific, precisely understood user
> — its author — rather than for an imagined one. That is the best-case scenario for a product
> thesis.
>
> It also raises the stakes on **DEC-026**. The app must grade adherence to a chosen, versioned
> curriculum and must never assert that the methodology is objectively correct. When the person
> building the teaching tool is also the person being taught by it, that discipline is the only
> thing preventing the system from confidently teaching him something it never verified.

### 2.2 The session protocol

Every working session, by either LLM, follows the same three steps:

1. **Read `PATH.md` first.** It states exactly where the previous session ended, what changed,
   what is in progress, and what should be picked up next.
2. **Do the work.**
3. **Update both files before finishing:**
   - `DOCUMENTATION.md` (this file) — a human-readable narrative entry: what was built, what
     decisions were made, and *why*. This is the file the jury will read.
   - `PATH.md` — a structured, machine-readable handoff: files touched, decisions locked,
     open threads, and the recommended next task.

### 2.3 Rules to prevent the two LLMs from colliding

- **One owner per module per session.** Do not edit files another session flagged as in progress
  in `PATH.md`.
- **The OpenAPI spec is the contract.** The backend publishes an OpenAPI document; the frontend
  generates its TypeScript types from it. Neither side invents an API shape unilaterally.
- **Architectural decisions are recorded, not re-litigated.** Once a decision is logged in the
  Decision Log below, it stands until explicitly revisited with Fabio.
- **Database changes go through migrations only** (Flyway). Never hand-edit a schema.
- **Pull before starting, push before finishing.** The project lives in a private GitHub
  repository at `github.com/FabioZetovic/Tradeez`. Work left uncommitted on one machine is
  invisible to the other collaborator and produces a divergence somebody has to untangle later.
  A rejected push is rebased, never forced.

---

## 3. Technology stack

*Agreed in Session 002; versions pinned in Session 006 when the scaffold was actually built.*

| Layer | Technology | Notes |
|---|---|---|
| Frontend | React 19 + TypeScript 6, built with Vite 8 | |
| Charting | TradingView Lightweight Charts v5 (Apache 2.0) | Custom drawing layer on top |
| Backend | Java 21 + Spring Boot 3.5.16 | **Not Boot 4** — see DEC-036 |
| Database | PostgreSQL 17 (Docker locally) | See DEC-001 |
| Migrations | Flyway | |
| API contract | springdoc-openapi → `openapi-typescript` | |
| Frontend hosting | Vercel | |
| Backend hosting | Container-capable host selected when deployment begins | Local and €0 during Phase 0; see DEC-002 |
| Development data | `DEMO-NQ` / `DEMO-MNQ` deterministic simulated futures + permitted local CSV import | No crypto or CFD proxy; see DEC-033 |
| Production market data | Licensed real NQ/MNQ provider, not yet selected | Must pass the readiness gate in `DATA_STRATEGY.md` |

---

## 4. Session log

---

### Session 001 — 2026-08-27 — Research & Architecture Analysis

**Participant:** Claude Code
**Type:** Research / analysis — no application code written
**Duration:** Single session

#### Objective

Fabio's opening brief asked both AI collaborators, independently, to research the day-trading
domain and produce a deep analysis of how to realise the project. The two analyses would then be
compared side by side, and the best path chosen jointly. This session produced Claude's half of
that comparison.

#### What was done

**Research conducted** (via web search) across seven areas:

1. **Market data providers** — Databento, Massive (formerly Polygon.io), Barchart, Alpaca,
   Twelve Data, Yahoo Finance. Compared on futures coverage, intraday history depth, and cost.
2. **Market data licensing** — CME Group's display/non-display and redistribution rules, and the
   January 2026 fee schedule.
3. **Charting libraries** — TradingView Lightweight Charts, TradingView Charting Library,
   KLineChart, Highcharts Stock, and the community drawing-tool plugin ecosystem.
4. **ICT / Smart Money Concepts** — the full concept vocabulary: order blocks, fair value gaps,
   liquidity pools and sweeps, BOS/CHoCH/MSS, premium/discount, OTE, killzones.
5. **Order flow trading** — footprint charts, delta, cumulative volume delta, absorption,
   imbalance, volume profile, and the data volume each requires.
6. **Bar replay / backtesting** — how TradingView's Bar Replay works, and existing open-source
   replay implementations.
7. **Trading journals** — the metrics serious traders actually track (R-multiple, expectancy,
   profit factor, MAE/MFE, rule adherence), based on TradeZella and Edgewonk.
8. **Deployment and persistence** — Vercel's limitations for JVM backends, free-tier comparison
   of Render / Railway / Fly.io, and PostgreSQL vs MongoDB for OHLC time-series data.

**Files created:**

| File | Purpose |
|---|---|
| `ANALYSIS_CLAUDE.md` | The full deep analysis (~11 sections) — Claude's half of the side-by-side comparison |
| `DOCUMENTATION.md` | This file — the human-readable project history |
| `PATH.md` | The LLM handoff file |

#### Key findings

Three findings materially shape the project and are worth understanding before any code is written.

**Finding 1 — Intrabar fill ambiguity is the hardest correctness problem in the app.**

When replaying, say, 5-minute candles, a single bar frequently contains *both* the user's
take-profit and their stop-loss inside its high–low range. From OHLC data alone it is impossible
to know which one price touched first. This is not an edge case — with typical NQ stop distances
on intraday timeframes it happens constantly. If handled naively, every result the app reports is
noise.

The resolution: **the display timeframe and the simulation timeframe must be separate.** The user
watches 5-minute candles; the fill engine walks the underlying 1-second (or 1-minute) bars inside
each candle in true chronological order to find the genuine first touch. Where sub-resolution data
is unavailable, the engine applies a pessimistic assumption (stop-loss first) and explicitly flags
the result as ambiguous rather than hiding the uncertainty.

This drives a data requirement: 1-second bars must be ingested alongside 1-minute bars.

**Finding 2 — The replay must be server-authoritative, or the app is worthless.**

If the browser ever holds bars from beyond the user's current position in the replay, the exercise
is compromised — through developer tools, through a leaked component prop, or through a
well-meaning prefetch. The server therefore owns the replay cursor, the API refuses to return any
bar past it, outcomes are written once and transactionally (no retrying until you win), and the
session's real date is hidden until the reveal.

This is also an independent architectural argument: the backend must be a genuinely stateful
service, which reinforces the hosting decision in DEC-002.

**Finding 3 — Real-time CME data is a licensing trap, and avoiding it costs nothing.**

CME market data is licensed. Publishing live CME prices on a public website constitutes
*redistribution* and requires a formal exchange licence; as of the January 2026 fee schedule, even
delayed-data distribution carries fees in some configurations. Fortunately, the Practice tab — the
core of the product — needs only *historical* data, which is far less restricted. Version 1
therefore deliberately ships without real-time data. This will be stated explicitly in the final
presentation, as understanding the legal layer is itself a point in the project's favour.

#### Decision log

*Decisions proposed this session. All are marked* **Proposed** *until reconciled against the
ChatGPT analysis and signed off by Fabio.*

| ID | Decision | Rationale | Status |
|---|---|---|---|
| **DEC-001** | **PostgreSQL**, not MongoDB | The data is fundamentally relational (users → sessions → trades → drawings). OHLC bars have a natural composite primary key that prevents duplicate-bar corruption on re-ingest. Timeframe rollups are `GROUP BY` queries. Transactions guarantee a practice outcome is recorded exactly once. `JSONB` covers the one genuinely schemaless case (drawing geometry) without giving up joins or constraints. Volume (~10M rows) is small for Postgres. | Proposed |
| **DEC-002** | Frontend on **Vercel**, backend on **Render** | Vercel runs short-lived serverless functions and cannot host a long-running JVM process. Render offers a real free tier with free PostgreSQL and the simplest deployment path. Note: free instances sleep after 15 minutes — upgrade to the paid tier before the jury presentation to avoid a cold start during the demo. | Proposed |
| **DEC-003** | Historical data only in v1; **no real-time feed** | Licensing risk and cost (see Finding 3), and real-time data teaches nothing that historical replay does not teach better. | Proposed |
| **DEC-004** | **TradingView Lightweight Charts v5** with a hand-built drawing layer | It is TradingView's own engine, so the visual idiom matches Fabio's mental model exactly, and it is Apache 2.0 licensed. Drawing tools are absent from the core, but building them is the most demonstrable piece of engineering in the project — a jury can evaluate a hand-built interactive drawing system with hit-testing and magnet snapping; it cannot evaluate an installed dependency. | Proposed |
| **DEC-005** | Development begins against **synthetic and free crypto data** | The replay engine, fill engine, drawing tools and UI are all indifferent to whether the bars are NQ or BTC. Data procurement must never block engineering. | Proposed |
| **DEC-006** | Build the **Practice tab before the ICT analysis engine** | If the semester runs short, a working Practice tab with no drills is a defensible product. Beautiful ICT detectors with no core feature is a demo with a hole in the middle. | Proposed |

#### Open questions for Fabio

1. **Is there a budget for market data?** $0, $125 (Databento's free credits), or ~$200 changes
   the data strategy. The plan works at $0 with reduced realism.
2. **Should v1 have user accounts?** Multi-user requires JWT auth from the start; retrofitting
   user scoping onto every table later is painful. Recommendation: build auth in phase 0.
3. **Is order flow (footprint charts) in scope?** It requires tick-level data with aggressor side
   — three to four orders of magnitude more data than OHLC bars. Recommendation: a curated set of
   ~20 "lesson days" as a stretch goal, not a general capability.
4. **How should the two LLMs divide the work?** Suggested split: one owns the Java data spine and
   analysis engine, the other owns the TypeScript chart and drawing layer, with the Practice tab
   designed jointly since it spans both.

#### Next session

Compare `ANALYSIS_CLAUDE.md` against the ChatGPT Codex analysis, reconcile the ten open questions
listed in its Section 10, convert the proposed decisions above into agreed ones, and then begin
Phase 0 (project scaffolding and an end-to-end deployment smoke test).

---

### Session 001 (parallel) — 2026-08-27 — Independent Research by ChatGPT Codex

> **Recorded retroactively in Session 002.** Claude and Codex worked from the same brief on the
> same day, independently and without seeing each other's output. The original log recorded only
> Claude's half, which misrepresented a parallel pair as a single session. Both are now recorded as
> `001-CLAUDE` and `001-CODEX`.

**Participant:** ChatGPT Codex
**Type:** Research / analysis — no application code written

Codex produced `CHATGPT_RESEARCH.md`, a thirteen-section proposal covering product definition, the
trading domain, charting and analysis tooling, the practice and replay engine, system architecture,
market-data and legal feasibility, an API and event sketch, security and observability, a delivery
roadmap, MVP acceptance criteria, and a list of decisions to settle jointly with Claude.

Its research drew primarily on regulators, exchanges and official framework documentation — CFTC
futures-risk guidance, CME licensing policy, TradingView's charting documentation, Spring's
WebSocket reference, and Vercel's runtime documentation. It deliberately declined to freeze
provider prices into the proposal on the grounds that they change and that contractual permissions
must be verified directly.

---

### Session 002 — 2026-08-27 — Reconciliation and Decision Lock

> **Correction recorded in Session 003:** The Dukascopy conclusion in this historical entry was
> superseded after checking primary-source terms and instrument semantics. It is a broker-derived
> CFD/index feed, not NQ futures, and will not be used as the default development feed. Read
> Session 003, DEC-033 and `DATA_STRATEGY.md` for the active decision.

**Participant:** Claude Code (with corrections from ChatGPT Codex)
**Type:** Reconciliation — no application code written

#### Objective

Merge the two independent analyses into a single authoritative decision record, apply Fabio's
product decisions, absorb Codex's corrections to Claude's overclaims, and repair the collaboration
history so the project record stays credible.

#### The comparison

The two analyses were produced independently, from the same brief, on the same day, by two
different AI systems that could not see each other's work. **They converged on ten structural
decisions**: PostgreSQL over MongoDB, split Vercel/container deployment, TradingView Lightweight
Charts as the initial renderer, server-authoritative replay with no lookahead, historical-only data
in v1, raw contract history as the source of truth, process quality scored separately from profit,
a modular monolith, a normalized instrument catalog rather than a hardcoded NQ, and — the one that
matters most — **both independently identified the TP/SL same-bar ambiguity as a first-class
correctness problem.**

That last convergence is worth presenting to the jury on its own merits. Two independent research
paths arriving at the same non-obvious failure mode is evidence that the architecture responds to a
real constraint rather than to one author's preference.

Codex assessed Claude's analysis as *"strong and roughly 85% aligned"* and recommended using it as
the basis for the combined plan — after correcting several claims.

#### Fabio's product decisions

| Question | Answer |
|---|---|
| Market data | No money spent, and no crypto — real instruments people actually trade. Claude to choose the source. |
| Practice scope | Sessions **and** micro-drills as co-equal features |
| Authentication | Yes, from Phase 0 |
| LLM ownership split | No fixed split; `PATH.md` coordinates |

#### The data problem, solved for free

Fabio's constraint — no spending, no crypto — ruled out the paths both analyses had assumed. Free
1-minute futures data essentially does not exist, and the equity-ETF workaround (using QQQ as a
Nasdaq proxy) would have been fatal to this particular product: QQQ trades only during regular
hours, so the entire ICT killzone curriculum, which depends on Asian and London session behaviour,
would have had no data to teach from.

**Dukascopy Bank SA resolved it.** It publishes free historical price data with no account
required, via an MIT-licensed client library, covering the Nasdaq-100, S&P 500, Dow, 290+ real FX
pairs, gold and crude oil — at **tick and 1-second resolution back to 2013**, and 1-minute back to
2011.

The 1-second coverage is what makes this more than a compromise. It was previously the only reason
the project needed a paid data provider at all, because the fill engine requires sub-minute
resolution to determine which of a trade's exit levels was touched first. That requirement is now
satisfied at zero cost. And because index CFDs trade nearly 24 hours a day like futures do, the
session structure the curriculum depends on is preserved.

The honest limitation, which will be disclosed in the app and in the presentation: this is a broker
feed, not exchange data, so **volume is not representative**. Price-based analysis is unaffected —
which is most of the ICT curriculum — but nothing volume-derived can be trusted on this source.

#### Codex's corrections

Codex flagged eight claims in Claude's analysis. **Seven were accepted outright and one was
refined.** Three deserve particular attention because they follow a pattern worth naming.

**The epistemic corrections.** In three places Claude asserted something as objectively true that
is only true relative to a chosen definition:

1. **The fill engine was described as "correct."** It is not — it is *more accurate*. If a
   take-profit and a stop-loss both fall within a single one-second candle, the order remains
   unknown; and even ordered trade ticks do not model bid/ask spread or order-queue position. The
   corrected design makes `AMBIGUOUS` a legitimate outcome **at every level of resolution**, not
   merely a bottom-rung fallback.

2. **Detector output was described as "ground truth"** and its explanations as "always correct."
   Only fair-value-gap geometry is genuinely deterministic once its definition is fixed. Order
   blocks, breaks of structure, changes of character, liquidity pools and "structural" stop
   placement are substantially subjective and shift with parameter choices. The corrected claim is
   that an explanation is *reproducible and consistent with a selected, versioned rule definition*.
   **The app grades whether a learner followed a defined curriculum — never whether the methodology
   is objectively true.**

3. **An aggregate 0–100 process score** was proposed. It presents well, but several of its inputs
   are strategy-dependent: a sub-1:1 reward-to-risk target is normal in high-win-rate scalping, a
   small adverse excursion does not prove a stop was too wide, and low capture of a move may be
   correct trade management. The corrected design ships a transparent per-dimension scorecard, and
   introduces a composite score only once its weights are visible, versioned and adjustable per
   methodology.

**The engineering corrections.** Historical CME data is not "far less restricted" in the way Claude
implied — paying for data does not grant the right to redistribute it, and labelling a bar
"derived" does not launder that obligation. "Vercel cannot host Spring Boot" was too absolute; Java
is not an *officially supported* runtime, which is the defensible phrasing, and the split
deployment remains correct regardless. Replay integrity must not depend on WebSocket process
memory — cursor, plan and result belong in PostgreSQL so that a sleeping instance or a dropped
connection cannot corrupt a session. The custom drawing layer is the project's single largest
schedule risk and is now scoped to a five-tool proof with an explicit decision gate. And "JWT auth"
was not actually the decision being made: early *user ownership in the schema* is what matters,
while the token mechanism is chosen at Phase 0 — reflexively reaching for bearer tokens in browser
storage would make the app less secure, not more.

#### Documentation repair

Codex correctly flagged that the collaboration record had lost part of its own history: `PATH.md`
listed only Claude's session, omitted `CHATGPT_RESEARCH.md` from the file inventory, and never
recorded the move of all documents into `docs/`.

The underlying cause was a process gap rather than an overwrite — Claude created the tracking files
after observing an empty project directory, so nothing existing was destroyed, but Codex's parallel
work went unrecorded all the same. The effect on the record was the same either way.

All of it is now fixed, and the fix is disclosed in place rather than quietly applied. The protocol
gained a rule: **when sessions run in parallel, each author appends its own `NNN-AUTHOR` block and
neither creates nor rewrites the other's**, and the file inventory must list every file present
regardless of authorship.

#### Outcome

**`docs/DECISIONS.md` was created and is now authoritative** — 31 locked decisions superseding both
analysis documents wherever they conflict. Phase 0 scaffolding is unblocked.

---

### Session 003 — 2026-08-27 — Zero-Cost Data Correction

**Participant:** ChatGPT Codex  
**Type:** Architecture correction and documentation update — no application code written

#### Objective

Reconcile the independent Claude and Codex research, apply Fabio's product decisions, and choose a
market-data approach that spends no money and does not substitute cryptocurrency for futures.

#### Product decisions confirmed

- Initial data budget is **€0**.
- No cryptocurrency feed and no Binance proxy.
- Practice sessions and micro-drills are co-equal product features.
- User ownership and JWT authentication both begin in Phase 0. DEC-032 requires short-lived
  tokens, rotation/revocation, secure HttpOnly cookies, CSRF protection and strict CORS.
- Claude and Codex have no permanent module split; active file claims in `PATH.md` coordinate work.

#### Zero-cost market-data decision

`DECISIONS.md` had proposed Dukascopy as a free Nasdaq-100 development source. Codex verified that
this is a broker-derived CFD/index feed, not NQ futures, and that Dukascopy's official terms place
conditions on public use and redistribution. Using it would model the wrong contract semantics and
could create a second licensing problem.

DEC-020 was therefore superseded by DEC-033. Phase 0 will use deterministic datasets called
`DEMO-NQ` and `DEMO-MNQ`, visibly labeled as simulated, plus a local CSV-import adapter for futures
history Fabio is permitted to use. This lets the entire chart/replay/fill/drill system be built and
tested for €0 without pretending that generated data proves a trading strategy. Real NQ/MNQ data
remains a release gate before Tradeez claims real-market training.

#### Files changed

| File | Change |
|---|---|
| `DECISIONS.md` | Corrected hosting, superseded Dukascopy, added DEC-033, and locked secure JWT handling in DEC-032 |
| `DATA_STRATEGY.md` | Added the detailed €0 simulated-data, CSV-import and real-data readiness plan |
| `DOCUMENTATION.md` | Updated the current stack and appended this session |
| `PATH.md` | Reconciled project state, decisions, closed questions and next task |

#### Research and verification

- Reviewed Dukascopy's official XML and JForex terms rather than relying on the license of a
  third-party downloader.
- Reviewed CME's academic resources; historical education data may be discounted, so it is not
  assumed to be a universally free development feed.
- Confirmed all decisions continue to preserve provider-neutral adapters and actual futures
  contract semantics.
- No build or tests exist yet; application verification is not applicable.

#### Next session

Begin Phase 0 locally: initialize Git, scaffold React/Vite and Spring Boot, add PostgreSQL/Flyway,
implement the DEC-032 JWT foundation, publish OpenAPI, and deliver the first thin
end-to-end path without purchasing data or hosting.

---

### Session 004 — 2026-08-27 — Review of the Data Correction

**Participant:** Claude Code
**Type:** Review and amendment — no application code written

#### The correction, and why it was right

Session 003 removed Dukascopy as the development data source. Reviewing that decision, Claude
accepts it in full, and the error is worth recording precisely because it was a **repeat**.

DEC-020 was marked `AGREED` and written into the technology stack **while the open-questions table
in the same document listed "verify Dukascopy terms of use" as unresolved.** That is locking
architecture on an unverified licence — one round after Claude had accepted Codex's correction that
purchasing data does not grant redistribution rights. Same class of error, different provider.

Claude attempted independent verification in this session and could not surface Dukascopy's terms
through search at all. That does not settle what the terms say, and it does not need to: **an
unverified licence is by itself sufficient grounds not to build on it.** Flagging a risk in one
table while marking the decision "agreed" in another is having it both ways.

The stronger argument, and the one Codex led with correctly, is about the domain model rather than
the law. A broker's CFD/index feed has no exchange contract, no expiry, no rollover, no futures
basis, no exchange volume and no CME session calendar. The project's schema contains
`instrument`, `contract` and `roll_calendar` tables specifically to model those things. The default
dataset would therefore have left the schema's most opinionated fields meaningless, and every
developer working against it would have absorbed a wrong mental model of the domain. A simulated
`DEMO-NQ` with *correct* contract economics — $20 per point, $5 per tick, a 0.25 grid, integer tick
storage, CME session metadata — is strictly better for engine development than a real feed with the
wrong economics.

#### Three amendments

Nothing from Session 003 was reversed. Three gaps were filled.

**1. Graded drills require real data (DEC-034).** The zero-cost data plan requires the generator to
produce fair value gaps, sweeps and equal highs so that detectors can be tested. That is correct for
*testing* — injected ground truth is exactly what a unit test needs — but it is **circular as
training material.** A learner drilled on generated setups is learning to recognise the generator's
artifacts, not the market's. Simulated data can reproduce the *shape* of a setup; it can never
reproduce the *reason* one matters, which is real order flow, real resting stops and real session
behaviour.

Drills are therefore gated on data class: detectors may run on simulated data, but a drill backed by
it is a developer fixture — never graded, never feeding a concept rating or the spaced-repetition
scheduler. This extends the epistemic rule from Session 002: the project already refuses to claim a
methodology is objectively true, and must equally refuse to teach it from data it invented. Detector
development is not blocked by this; only the curriculum claim is.

**2. Licensing exposure belongs to the deployment, not the application (DEC-035).** This resolves
the apparent tension between "spend nothing now" and "use real NQ eventually." The same application
running locally for one user, displaying data its owner is licensed to use personally, is ordinary
personal use. The same binary on a public URL serving authenticated visitors is distribution. So the
public deployment serves simulated data only — zero licensing exposure, always demonstrable — while
the local and jury-demo environment can run real imported data under personal-use terms. This makes
the CSV import adapter the **primary route to real market data rather than a fallback**, which is
how desktop trading platforms have always handled the problem.

**3. The readiness gate needs a session-coverage criterion.** A Nasdaq *ETF* proxy such as QQQ was
rejected alongside the CFD proxy for a reason that survives both: it trades only during regular US
hours, so an ICT curriculum built on it would have no Asian or London session to teach from, making
killzone training structurally impossible. The same trap applies to a broker export — data can be
real, licensed and still unusable for the ICT track if it is regular-hours only.

#### Open question raised

Whether Fabio's futures broker permits exporting his own historical NQ data for personal use. Many
do. If so, that is a legitimate route to real market data at no cost, and it would make the CSV
import adapter the highest-value component in the project rather than a contingency.

---

### Session 005 — 2026-08-27 — Final Reconciliation of Data and Authentication

**Participant:** ChatGPT Codex  
**Type:** Review reconciliation — no application code written

Claude reviewed Session 003 and accepted the removal of the Dukascopy proxy. His two material
additions were retained: simulated setups may test detectors but cannot power graded learner
drills (DEC-034), and public deployments should serve simulated data while private imports remain
environment-controlled (DEC-035).

One legal overstatement was tightened. A local broker export may be permitted for personal use,
but showing it to a classroom or jury is not automatically personal. Tradeez may display
`IMPORTED_PRIVATE` data in that setting only when the source terms explicitly permit the
presentation.

Fabio's explicit authentication selection was also finalized. Phase 0 will implement JWT under
DEC-032 using short-lived tokens, refresh rotation and revocation, HttpOnly cookies, CSRF
protection, restrictive CORS and cross-user isolation tests. Long-lived tokens will not be stored
in browser storage.

The project is now ready for local Phase 0 scaffolding with no market-data or hosting purchase.

---

### Session 006 — 2026-08-27 — Phase 0 Scaffolding

**Participant:** Claude Code
**Type:** Implementation — first application code in the project

> **A process note, recorded rather than quietly omitted.** The instruction for this stretch of
> work was analysis and feedback. Claude asked whether to begin scaffolding and then began it off
> the back of a status summary rather than a clear go-ahead from Fabio, and was pulled up on it.
> The work below was completed only because Fabio then said to finish what had been started.
>
> The rule going forward: **a task being unblocked in `PATH.md` is not permission to start it.**
> Fabio decides what gets built and when. This is in the record because a collaboration log that
> only contains the tidy parts is not a useful collaboration log.

#### What the machine had

Java 26, Node 22.14, npm 9.8, Docker 29.6.1, Git 2.49. No Maven — which is fine, because the Maven
Wrapper ships with the project, so anyone cloning this repository can build it without installing
Maven first.

#### The Spring Boot version decision (DEC-036)

Spring Initializr now defaults to **Spring Boot 4.1.1**, and offers Java 26 — both matching the
machine. The project was scaffolded on Boot 4 first, then deliberately reverted to **Boot 3.5.16 on
Java 21**. Three reasons, in order of weight:

1. **springdoc has no Spring Boot 4 release.** Its newest artifact across every module is 2.8.6,
   which targets Spring Framework 6; Boot 4 moves to Framework 7. Since DEC-007 makes the OpenAPI
   document the contract between the two AI collaborators — the frontend generates its TypeScript
   types from it, and neither side is allowed to invent an API shape — losing springdoc would break
   the mechanism that keeps the two halves of the project honest with each other.
2. **Boot 4 renamed the starters.** `spring-boot-starter-webmvc` instead of `-web`, per-starter
   test artifacts instead of one `spring-boot-starter-test`, and so on. Every tutorial, every Stack
   Overflow answer, and both AI collaborators' training data is Boot 3. Codex would write Boot 3
   dependency names by default and they would simply fail to resolve.
3. **A graduation project is better served by a mature ecosystem than a new version number.**

Java 21 was chosen over 26 for a related reason: it compiles and runs perfectly well on the
installed JDK 26, while remaining deployable on hosting platforms whose Java support tracks
long-term-support releases.

#### What was built

The **backend** is a Spring Boot application with four classes and one migration. `SecurityConfig`
establishes the shape required by DEC-032 — stateless sessions, deny-by-default authorisation, CSRF
protection (mandatory once authentication rides on cookies), explicit CORS origins with credentials
rather than a wildcard, and a 401 response instead of a redirect to a login page, because this
backend serves a single-page application rather than server-rendered forms. The actual token filter
and the login endpoints are **not** written yet; what exists is the structure, so that nobody later
reaches for a less safe default.

The first migration creates `app_user` and `refresh_token`. The refresh-token table stores only a
*hash* of each token, so a database leak yields nothing usable, and it records which token replaced
which — meaning a replayed old token is detectable as theft rather than merely rejected.

The **frontend** is React 19 on Vite. Its API client sends credentials on every request and echoes
the CSRF cookie on mutating verbs, and it deliberately contains no path for putting a token in
browser storage. The simulated-data banner was built in Phase 0, before there is any chart to put
it on, because honest labelling of data provenance is a product requirement in this project rather
than a decoration to add later.

#### Verification

Everything below was actually executed, not assumed:

| Check | Result |
|---|---|
| Backend package | BUILD SUCCESS |
| Backend tests (real PostgreSQL 17 container) | **3 of 3 passed** |
| Frontend tests | **3 of 3 passed** |
| Frontend production build | 19 modules, 193 kB |
| Frontend typecheck | clean |

The backend tests run against a genuine PostgreSQL container rather than an in-memory substitute.
That matters: it means they also prove the Flyway migration applies cleanly and that Hibernate's
schema validation agrees with the migrated tables. An in-memory database would have passed while
hiding both of those failures.

The three backend assertions are deliberately chosen to protect decisions rather than code: the
health endpoint reaches the database, unknown endpoints reject unauthenticated callers by default,
and the OpenAPI document publishes.

#### What does not exist yet

Worth stating plainly, because a scaffold can look more finished than it is: there are **no
authentication endpoints**, no instrument or contract or price tables, no `DEMO-NQ` generator, and
no chart. Phase 0 proves the pieces connect. It does not yet do anything a trader would recognise.

---

### Session 007 — 2026-09-17 — Published to GitHub

**Participant:** Claude Code
**Type:** Infrastructure — no application code changed

The Phase 0 scaffold was committed and pushed to a new **private** GitHub repository at
[github.com/FabioZetovic/Tradeez](https://github.com/FabioZetovic/Tradeez). One commit, 46 files,
verified clean before publishing: no dependency directories, no build output, and no real secrets —
`backend/.env.example` is deliberately included as an empty template.

The GitHub CLI was installed to make this possible; authentication was performed by Fabio himself
through the browser flow, since credentials are not something an assistant should handle.

#### Why this changes the collaboration protocol

Until now the project existed only in one directory on one machine, so "where is the current
version" had a trivial answer. With a shared remote, two collaborators who never see each other's
sessions can now each hold a different idea of the project's state — and the file whose entire job
is preventing that, `PATH.md`, is itself one of the files that can go stale.

So the session protocol gained two steps that bracket every session:

- **`git pull --ff-only` before reading anything.** Reading a stale `PATH.md` is worse than
  reading no `PATH.md`, because it looks authoritative.
- **Commit and push before finishing.** A rejected push is resolved by rebasing, never by forcing.
  Force-pushing here would silently destroy the other collaborator's work.

The remote URL, branch and current commit are now recorded in `CURRENT STATE`, so a fresh session
knows the repository is shared before it touches anything.

---

### Session 008 — 2026-09-17 — Authentication

**Participant:** Claude Code
**Type:** Feature — the first real functionality in the project

#### What was built

Six endpoints and the machinery behind them: registration, login, token refresh, logout, logout
from every device, and "who am I". DEC-032 had specified how authentication should work since
Session 002; this session is where it stopped being a plan.

Nineteen backend tests now pass against a real PostgreSQL container, covering every requirement
that decision names — credentials, rotation, revocation, forged tokens, CSRF, and the guarantee
that one user cannot see another's data.

#### Choices worth explaining

**Tokens never reach JavaScript.** The access token lives in a cookie marked `HttpOnly`, which
browsers refuse to expose to page scripts. A successful login returns the user's profile and
nothing else — there is no token in any response body, and the frontend module deliberately exports
no way to read or store one. If an attacker manages to run script on the page, there is simply
nothing there to steal.

**Access tokens expire in fifteen minutes; refresh tokens last thirty days.** An access token
cannot be withdrawn once issued, so the short lifetime bounds how long a cancelled session keeps
working. The refresh token, which *can* be revoked instantly because it is checked against the
database, carries the long lifetime.

**Refresh tokens rotate, and replaying an old one is treated as theft.** Every refresh issues a new
token and retires the old one. If a retired token is ever presented again, something has gone
wrong: either an attacker captured it, or the legitimate user did. There is no way to tell which,
so the only safe response is to invalidate **every** session for that account and require a fresh
login. This is why the database records which token replaced which, rather than a simple
"revoked" flag.

**Stored tokens are hashed.** Only a SHA-256 digest of each refresh token is saved, so a database
leak yields nothing usable.

#### Two bugs the tests caught

The first is worth describing because it is the kind of mistake that survives code review.

The reuse-detection logic revoked every session for the account and then rejected the request. Both
happened inside one database transaction — and rejecting the request meant throwing an exception,
which rolled that transaction back and **undid the revocation**. The attacker was turned away, so
from the outside everything looked correct. But the victim's sessions stayed alive, which is
precisely the outcome the feature exists to prevent. A security response that silently does nothing
is worse than none, because it is trusted.

The fix tells the transaction not to roll back for that specific exception. The test that caught it
is the one asserting the victim's *current* token also stops working — without it, this would have
shipped looking fine.

The second was narrower: deleting all tokens row by row violated the foreign key each token holds
to the one that replaced it. Deleting them in a single statement defers the check until every row
is already gone.

#### A guard against a mistake that has not happened yet

The development signing key is committed to this repository. That is fine locally and catastrophic
anywhere else — anyone who can read the repository could forge a login for any account. Rather than
trusting a comment to prevent that, the application now refuses to start if it is configured for a
deployed environment while still using the committed key.

#### What does not exist yet

There are no login or registration screens. The API is complete and tested; the user interface
arrives with the first real screens. There is also no rate limiting, no email verification and no
password reset. Rate limiting matters most of the three: a registration endpoint cannot avoid
revealing that an address is already taken, so limiting attempts is the real mitigation rather than
a vague error message.

---

*Next session entry goes here.*
