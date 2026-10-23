# Tradeez

Learn to trade with **ICT concepts** by paper trading real NQ futures history (DEC-041). Replay a
moment with the outcome hidden, commit to a trade plan, watch it play out, and get feedback on the
**quality of the decision** — not just the profit. The curriculum is specified concept by concept in
[docs/ICT_CONCEPTS.md](docs/ICT_CONCEPTS.md).

> **Educational software.** Not financial advice, and not a trading venue: practice trades are simulated on past
> market data and never reach a market. Simulated results do not guarantee live results. Futures are leveraged;
> most retail day traders lose money, and losses can exceed the money deposited.

Graduation project (*završni rad*) by **Fabio Žetović**, built collaboratively with two AI
engineering assistants, **Claude Code** and **ChatGPT Codex**.

### What works today

- **A guide** (T29, at `/help` since T36): what Learn, Drills, Practice and Chart are each for, in that order, and
  what the app is not. "How it works" in the account menu opens it. Every screen has its own address. English only
  (Q-08).
- **Accounts:** register and sign in. Sessions use HttpOnly cookies with rotating refresh tokens.
  Passwords follow `password-1.0.0` (DEC-064): 12 characters to 72 bytes, not a common password, not
  your own name or email, and not found in a known breach (Have I Been Pwned, k-anonymous — the password
  never leaves the server). The account menu changes the password or signs out of every device; sign-in,
  sign-up and the password change are throttled per address and per account.
- **Chart:** two years of **real NQ** one-minute history (Oct 2024 →), from Massive's free plan
  (DEC-042), aggregated to 3-minute … 1-hour, 4-hour and daily candles — the last two counted from the
  trading day's 18:00 New York open (DEC-060). Jump to any date or 1W–2Y back; times in New York time,
  the clock ICT is taught in. Every series names its source.
- **New York clock** over every chart (DEC-047): trading-day separators, ICT killzones, Silver Bullet
  hours and macros shaded; midnight, 08:30, 09:30 and weekly opens, PDH/PDL, PWH/PWL and killzone
  highs/lows drawn — each switchable, computed from one-minute bars under versioned rules, never
  ahead of a replay's cursor and never across a contract roll.
- **Drawing tools:** horizontal line, trend line, rectangle and position (risk/reward), and three
  ICT tools (DEC-048): a Fibonacci with ICT's levels — the 62–79% optimal trade entry shaded and the
  −0.27 to −2 targets — a zone with its 50% (consequent encroachment), and text labels. They are
  saved per account, with undo and redo.
- **Detected setups** over every chart (DEC-049, DEC-050, DEC-052, DEC-053, DEC-061, DEC-062, DEC-063, DEC-073):
  thirty-two versioned rules — ICT's gaps (fair value gaps and their lifecycle, inversion gaps, balanced price
  ranges, volume imbalances, gaps and voids, NDOG/NWOG, the opening range gap), market structure (swings
  and their ranks, HH/HL labels, BOS/CHoCH, displacement, the market structure shift), liquidity (resting
  and taken BSL/SSL, equal highs/lows, sweep or run, turtle soup) and blocks (order blocks, breakers,
  mitigation and rejection blocks, unicorns — each followed from forming to tapped, its mean threshold
  lost, and invalidated) and ranges (the dealing range of the last leg that broke structure, its premium
  and discount halves, the optimal trade entry, standard deviation projections) and time (power of three,
  daily and weekly profiles, the Judas swing — each read once its day, week or window is over) and SMT
  divergence (NQ against ES, each market's own swings; ES drawn under NQ over the same minutes, and a
  "find the SMT" drill, DEC-075) — each
  named by the rule that found it, its parameters measured on real NQ. The clock's reference levels end
  where they were taken, and it can draw ICT's 20-, 40- and 60-day ranges (IPDA) within one contract. Off by default; in a replay, only what was known by the cursor. Every detector is
  checked to be causal, and the list of concepts comes straight from `docs/ICT_CONCEPTS.md`.
- **Evidence** (DEC-065): every rule counted over the whole imported history — how often it fires, how
  its zones' lives ended (filled, invalidated, left past their end), how its kinds divide — on each
  concept's lesson card, as base rates with their sample sizes, never as a forecast.
- **Honest costs** (DEC-072): every market order slips a tick, a target fills only once price trades through it, and
  fees are charged — the learner's own numbers or a realistic standard — so a practice edge is not a fill-model gift.
  R and the wins are counted after those costs, and the risk limit is checked against the loss at the stop with them
  (DEC-076).
- **No peeking** (DEC-077): the practice session in hand and the drill being answered are held back from the Chart
  tab — candles, setups and clock prices — until they are over, and the chart says so.
- **Managing a trade** (DEC-071): a real NQ trade is open and in profit — hold it, move the stop to breakeven, or
  take half off? Each choice is played out on the real candles that followed, and the result says which paid most
  here and that only many such drills say which pays.
- **Risk drills against the clock** (DEC-070): how many contracts a stop allows, how deep a losing streak goes,
  what gain a drawdown needs — answered in the head, timed, and shown worked out step by step.
- **Statistics that show their sample size** (DEC-069): the journal adds the trades up — win rate, expectancy,
  profit factor — overall and by model, killzone, weekday and whether the learner kept their own rules, with the
  number of trades first on every row and a range that says when a result cannot yet be told from luck.
- **Every trade reviewed as it happened** (DEC-068): once a trade is over, the app tells it in order — what the
  rules had seen before it was placed and while it ran, each in the rule's own numbers, whether price reached the
  level the learner expected, and, after an early close, what the trade would have done — and can draw it all
  on the chart.
- **A scorecard for every trade** (DEC-067): what the trade did and how it was made, side by side and never
  added up — a tick or a cross only on the learner's own rules (a risk limit set before the session, the bias
  they stated). The journal compares how sure the learner said they were with how often their targets came
  first, with the number of trades behind every figure.
- **ICT plans** (DEC-066): a practice trade can name the model it follows — the 2022 model, Silver Bullet,
  OTE and five more — the learner's bias and the liquidity they expect; the plan panel shows the killzone, a
  daily-bias suggestion with its reasons, and the model's checklist read off the chart at that moment. Kept
  with the trade as adherence, scored apart from how it turned out.
- **Practice** on real NQ — paper trading a replay: a session opens at a random hidden moment from
  the imported history — optionally in an ICT killzone and on a chosen weekday — with its **date
  hidden** until the session ends (only the weekday and New York time are shown, DEC-045). Step it
  bar by bar or play it at 1–8 bars a second, on any timeframe from 1m to daily — switch at any moment;
  the candle still forming is built only from what came before (DEC-051) — and place a bracket trade
  whenever you see a setup; when it is over, take the next (DEC-046).
  - A bracket is a draft until you place it — it says so, and can be discarded. A placed trade ends
    at its stop or target, or whenever you close it at market (DEC-057). With two contracts or more,
    some can come off at a price before the target, and the stop on the rest can then move to the
    entry (DEC-074).
  - Each trade is resolved on the finest bars stored (one-minute for real NQ today), and a genuinely
    undecidable one is reported as *ambiguous*, not guessed.
  - Each result shows P&L, R-multiple, worst and best excursion; finished trades stay on the chart.
  - A running score in R counts closed trades only. Ending the session reveals the date (or, on
    simulated data, the seed that reproduces it).
  - A journal keeps every session, and trades can't be edited.
- **Learn**: the ICT curriculum as a ladder of levels, a card per concept (DEC-055) — what it is, in
  the words of `docs/ICT_CONCEPTS.md`; the exact rule Tradeez uses, in words generated from its
  parameters; three real NQ examples the rule found at random (never hand-picked), each explained in
  the rule's own numbers; and a button to the drill that practises it. Your progress on it (DEC-059):
  solid after ten graded attempts at 80% or more over the last twenty, reviews spaced as Anki spaces
  them, cards marked read, and the next step suggested — the ladder suggests, it never locks.
- **Drills** on real NQ, graded against the rule (DEC-054). *Spot*: mark every fair value gap, swing
  high, swing low, volume imbalance, displacement, pair of equal highs/lows or sweep in 30 candles.
  *Name*: one structure is drawn — is it a higher or lower high, a BOS or a CHoCH, a sweep or a run?
  The answer is the versioned rule's, named on screen and withheld until you answer; each hit is
  explained in the rule's own numbers, and a candle the chart cuts off counts neither way. On the
  simulated market only the FVG drill is left, labelled a developer fixture and never graded.
  *Predict* (DEC-058): the chart stops and two targets are drawn — which liquidity is taken first, the
  buy-side above or the sell-side below (each with its distance and the obstacles on the way); or, just
  after price took a level, does it reach the gap behind it (internal) or the next level beyond
  (external)? The candles after the moment arrive only with your answer, and grade it.

**Not built yet:** the ICT detectors for blocks, ranges and beyond,
a score across sessions. The ordered roadmap is in [docs/PATH.md](docs/PATH.md) → NEXT UP.
The history of how each piece was built is in [docs/DOCUMENTATION.md](docs/DOCUMENTATION.md).

---

## Repository

`https://github.com/FabioZetovic/Tradeez` — **private**.

```bash
git clone https://github.com/FabioZetovic/Tradeez.git
```

Both AI collaborators work against this remote. **Pull before starting, push before finishing.**
If a push is rejected, rebase — never force-push, which would destroy the other collaborator's
work.

---

## Read this before writing any code

| Document | What it is |
|---|---|
| **[docs/DECISIONS.md](docs/DECISIONS.md)** | ⭐ **Authoritative.** The architecture decision record. Supersedes everything else. |
| [docs/PATH.md](docs/PATH.md) | LLM handoff — current state, file claims, next task, invariants |
| [docs/ICT_CONCEPTS.md](docs/ICT_CONCEPTS.md) | The ICT curriculum: every concept, its draft rule, how it is taught |
| [docs/DATA_STRATEGY.md](docs/DATA_STRATEGY.md) | Required before touching market data or the drill engine |
| [docs/BUGS.md](docs/BUGS.md) | Known defects, deliberate limitations, and lessons from fixed bugs |
| [docs/DOCUMENTATION.md](docs/DOCUMENTATION.md) | Human-readable project history, session by session |
| [docs/ANALYSIS_CLAUDE.md](docs/ANALYSIS_CLAUDE.md) · [docs/CHATGPT_RESEARCH.md](docs/CHATGPT_RESEARCH.md) | The two original independent analyses |

### The invariants most likely to be broken by accident

1. **No lookahead.** No response, socket message or client store may contain market data
   timestamped after the replay cursor. The server owns the cursor.
2. **Prices are integer ticks.** Never `float`/`double` for price or P&L.
3. **All timestamps are UTC.** Convert to exchange time only at the presentation edge.
4. **Ambiguity is surfaced, never hidden.** If the fill engine cannot determine which of TP/SL was
   touched first, the result is `AMBIGUOUS`.
5. **Schema changes only via Flyway.** Never hand-edit the database.
6. **Never teach from data we invented.** Simulated data can test detectors; it can never back a
   graded drill.

---

## Stack

| Layer | Choice | Why |
|---|---|---|
| Frontend | React 19 + TypeScript 7 + Vite 8, on Node 24 LTS | DEC-056 |
| Styling | Tailwind CSS 4, design tokens in `src/index.css` | DEC-044 |
| Backend | Java 25 + Spring Boot 4.1 | DEC-056 (supersedes DEC-036) |
| Database | PostgreSQL 17 | DEC-001 |
| Migrations | Flyway | DEC-030 |
| API contract | springdoc-openapi → `openapi-typescript` | DEC-007 |
| Auth | JWT in HttpOnly cookies, CSRF, strict CORS; password-1.0.0, breach check, CSP on the built page | DEC-032, DEC-064 |
| Market data | Real NQ via Massive (free plan); Databento importer for deeper history; simulated `DEMO-NQ` only behind labelled fixtures (Practice on request, the FVG drill) | DEC-040, DEC-042, DEC-054 |

---

## Running it locally

Everything below costs **€0** and needs no hosting account or market-data subscription.

**Prerequisites:** JDK 25+, Node 24.20+, Docker. Dependencies are kept at their newest versions (DEC-056).

**1 — Start the database**

```bash
docker compose up -d
```

**2 — Start the backend** (http://localhost:8080)

```bash
cd backend && ./mvnw spring-boot:run
```

**3 — Start the frontend** (http://localhost:5173)

```bash
cd frontend && npm install && npm run dev
```

Open the app, create an account, and you land on the chart. The **Practice** tab runs replay
sessions.

**4 — Import real NQ history** (once; free)

Put a Massive API key in `backend/.env` — the gitignored file, **not** `.env.example`:

```
MASSIVE_API_KEY=your-key
```

Then, from `backend/`, dry-run first (it lists what it would fetch), then import:

```bash
./mvnw spring-boot:run -Dspring-boot.run.arguments="--spring.main.web-application-type=none --tradeez.import.symbol=NQ --tradeez.import.from=2024-10-08 --tradeez.import.to=2026-10-03 --tradeez.import.dry-run=true"
```

Run it again with `--tradeez.import.dry-run=false`. About 45 requests at the free plan's five a
minute; finished months are skipped on a rerun. SMT divergence (ICT-66) reads ES beside NQ: import ES the same
way, with `--tradeez.import.symbol=ES`.

If ports 8080 or 5173 are taken (Docker Desktop holds both on some machines, see ENV-002), run the
backend with `PORT=8081 ./mvnw spring-boot:run` and the frontend with
`VITE_API_PROXY_TARGET=http://localhost:8081 npm run dev`. `.claude/launch.json` does exactly this.

### Useful endpoints

| URL | What |
|---|---|
| `http://localhost:8080/api/health` | Health, including a real database round-trip, plus `startedAt` / `builtAt` to spot a stale process |
| `http://localhost:8080/swagger-ui.html` | Interactive API docs |
| `http://localhost:8080/v3/api-docs` | OpenAPI JSON — the source for generated TS types |

### Tests

```bash
cd backend && ./mvnw test
```

```bash
cd frontend && npm run test:run
```

```bash
cd frontend && npm run typecheck && npm run lint
```

Backend tests run against a real PostgreSQL container via Testcontainers, so Docker must be
running. If it is not, the tests say so in plain words rather than failing with an initializer
error (ENV-001). An in-memory database would pass while hiding migration and schema-validation
failures.

Which lines the tests run, file by file — a map of what is untested, not a score to chase:

```bash
cd backend && ./mvnw -Pcoverage test
```

```bash
cd frontend && npm run coverage
```

The backend report is `backend/target/site/jacoco/index.html`, the frontend's
`frontend/coverage/index.html`. Neither is committed.

### API types

DEC-007 makes the OpenAPI document the contract, and `npm run api:types` (backend running) generates
`src/lib/api/schema.d.ts` from it. **Not wired in yet:** the payload types in `src/lib/api/*.ts`
are still hand-written mirrors of the backend DTOs — keep them in step until they are replaced.

---

## Layout

```
Tradeez/
├── backend/           Spring Boot — com.tradeez.{identity,marketdata,charting,practice,analysis,drills,common,config}
│   └── src/main/resources/db/migration/   Flyway migrations (V1–V10)
├── frontend/          React + Vite + Tailwind
│   └── src/{components,hooks,lib/{api,chart,drawing,practice,drills},test}/
├── docs/              Decisions, strategy, history, handoff
└── docker-compose.yml Local PostgreSQL
```
