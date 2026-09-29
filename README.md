# Tradeez

A futures day-trading practice platform. Replay a moment in market history with the outcome
hidden, commit to a trade plan, watch it play out, and get graded on the **quality of the
decision** — not just the profit.

> **Educational software.** Not financial advice, and not a trading venue. Simulated results do not
> guarantee live results. Futures are leveraged instruments; most retail day traders lose money.

Graduation project (*završni rad*) by **Fabio Žetović**, built collaboratively with two AI
engineering assistants, **Claude Code** and **ChatGPT Codex**.

### What works today

- **Accounts:** register and sign in. Sessions use HttpOnly cookies with rotating refresh tokens.
- **Chart:** a simulated E-mini Nasdaq market (`DEMO-NQ`, `DEMO-MNQ`, clearly labelled as
  simulated), from 1-minute to 1-hour candles, with times shown in exchange time.
- **Drawing tools:** horizontal line, trend line, rectangle and position (risk/reward). They are
  saved per account, with undo and redo.
- **Practice:** a session opens at a random hidden moment. You place a bracket trade and lock it,
  then step the replay forward bar by bar.
  - Each trade is resolved on one-second data, and a genuinely undecidable one is reported as
    *ambiguous*, not guessed.
  - A result card shows P&L, R-multiple, worst and best excursion, and the seed that reproduces it.
  - A journal keeps every session, and results can't be edited.

**Not built yet:** graded drills (next), any score or rating, real market data, and CSV import.
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
| Frontend | React 19 + TypeScript + Vite | |
| Backend | Java 21 + Spring Boot 3.5 | Not Boot 4 — see DEC-036 |
| Database | PostgreSQL 17 | DEC-001 |
| Migrations | Flyway | DEC-030 |
| API contract | springdoc-openapi → `openapi-typescript` | DEC-007 |
| Auth | JWT in HttpOnly cookies, CSRF, strict CORS | DEC-032 |
| Market data | Generated `DEMO-NQ` / `DEMO-MNQ` (local CSV import planned) | DEC-033 |

---

## Running it locally

Everything below costs **€0** and needs no hosting account or market-data subscription.

**Prerequisites:** JDK 21+, Node 20+, Docker.

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

### Regenerating the API types

With the backend running:

```bash
cd frontend && npm run api:types
```

`src/lib/api/schema.d.ts` is generated and gitignored — never hand-edit it, and never hand-write a
server payload type on the client.

---

## Layout

```
Tradeez/
├── backend/           Spring Boot — com.tradeez.{identity,marketdata,charting,practice,common,config}
│   └── src/main/resources/db/migration/   Flyway migrations (V1–V5)
├── frontend/          React + Vite
│   └── src/{components,hooks,lib/{api,chart,drawing,practice},test}/
├── docs/              Decisions, strategy, history, handoff
└── docker-compose.yml Local PostgreSQL
```
