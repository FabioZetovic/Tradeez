# Tradeez

A futures day-trading practice platform. Replay a moment in market history with the outcome
hidden, commit to a trade plan, watch it play out, and get graded on the **quality of the
decision** — not just the profit.

> **Educational software.** Not financial advice, and not a trading venue. Simulated results do not
> guarantee live results. Futures are leveraged instruments; most retail day traders lose money.

Graduation project (*završni rad*) by **Fabio Žetović**, built collaboratively with two AI
engineering assistants, **Claude Code** and **ChatGPT Codex**.

---

## Read this before writing any code

| Document | What it is |
|---|---|
| **[docs/DECISIONS.md](docs/DECISIONS.md)** | ⭐ **Authoritative.** The architecture decision record. Supersedes everything else. |
| [docs/PATH.md](docs/PATH.md) | LLM handoff — current state, file claims, next task, invariants |
| [docs/DATA_STRATEGY.md](docs/DATA_STRATEGY.md) | Required before touching market data or the drill engine |
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
| Market data | Generated `DEMO-NQ` / `DEMO-MNQ` + local CSV import | DEC-033 |

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

The home page shows backend and database status. If both read `UP`, the vertical is wired.

### Useful endpoints

| URL | What |
|---|---|
| `http://localhost:8080/api/health` | Health, including a real database round-trip |
| `http://localhost:8080/swagger-ui.html` | Interactive API docs |
| `http://localhost:8080/v3/api-docs` | OpenAPI JSON — the source for generated TS types |

### Tests

```bash
cd backend && ./mvnw test
```

```bash
cd frontend && npm run test:run
```

Backend tests run against a real PostgreSQL container via Testcontainers, so Docker must be
running. An in-memory database would pass while hiding migration and schema-validation failures.

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
├── backend/           Spring Boot — com.tradeez.{common,config,...}
│   └── src/main/resources/db/migration/   Flyway migrations
├── frontend/          React + Vite
│   └── src/{components,lib/api,test}/
├── docs/              Decisions, strategy, history, handoff
└── docker-compose.yml Local PostgreSQL
```
