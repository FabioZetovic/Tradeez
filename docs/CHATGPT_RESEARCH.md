# Tradeez — Product, Trading, and Technical Research

**Author:** ChatGPT Codex  
**Research date:** 2026-08-27  
**Status:** Proposal for comparison with Claude's independent analysis; no implementation decision is final until both proposals are reviewed.

## 1. Executive recommendation

Tradeez should begin as a **futures education and market-replay application**, not as a broker or live trade-execution platform. Its strongest differentiator is a deliberate practice loop:

1. Choose a market, timeframe, session, and training curriculum.
2. Receive a randomly selected historical chart with all future candles hidden.
3. Analyze only information that would have existed at that moment.
4. Record a directional thesis, entry, stop loss (SL), take profit (TP), setup tags, and confidence.
5. Replay the market candle-by-candle or tick-by-tick.
6. Score the trade with realistic contract values, fees, slippage, and unambiguous fill rules.
7. Review mistakes and measure progress by setup, session, instrument, and behavior.

The recommended stack is:

- **Frontend:** React + TypeScript + Vite, deployed to Vercel.
- **Chart:** begin with TradingView Lightweight Charts plus Tradeez-owned drawing/replay overlays. Keep a chart adapter so Advanced Charts or another engine can replace it later.
- **Backend:** Java Spring Boot, deployed to a container-capable service rather than Vercel.
- **Database:** PostgreSQL. Add TimescaleDB only if tick-volume and aggregate performance later justify it.
- **Market data:** a provider-neutral adapter. Start with licensed delayed/historical OHLCV data; add live top-of-book and depth only after provider and CME permissions are verified.
- **Scope:** NQ and MNQ first, then a carefully chosen futures catalog. Forex should be a later module because spot FX is a different, fragmented market-data model.

This order keeps the graduation project demonstrable and avoids the largest early traps: trying to clone all of TradingView, improperly redistributing exchange data, mixing education with financial advice, and building live infrastructure before the replay engine is correct.

## 2. Product definition

### The problem

New day traders usually assemble charts, education, replay, journaling, risk calculators, economic calendars, and statistics from separate products. That fragments the learning loop. A chart alone shows price; it does not force a trader to state a falsifiable plan or explain why a trade succeeded or failed.

### The proposed solution

Tradeez is a learning workspace with four connected areas:

- **Markets:** instrument discovery, contract specifications, sessions, watchlists, live/delayed charts, and event context.
- **Analyze:** charting, indicators, drawings, multi-timeframe context, and saved layouts.
- **Practice:** blind historical replay, simulated orders, curriculum-specific prompts, scoring, and review.
- **Journal/Progress:** annotated trade records, screenshots/replay links, behavioral tags, expectancy, and weak-area recommendations.

The product must clearly say that simulation is educational, not financial advice, and that simulated results do not guarantee live results. Futures are leveraged: the CFTC warns that they are volatile and complex, that many retail participants lose all of their funds, and that losses can exceed the initial amount deposited ([CFTC futures basics](https://www.cftc.gov/LearnAndProtect/EducationCenter/FuturesMarketBasics/index2.htm), [CFTC pre-trade guidance](https://www.cftc.gov/LearnAndProtect/AdvisoriesAndArticles/areyouabouttotrade.html)).

## 3. Trading domain the application must model

### Instruments and asset classes

Do not label every market “NQ.” Use a normalized instrument catalog:

- Equity-index futures: NQ/MNQ, ES/MES, YM/MYM, RTY/M2K.
- Energy: CL/MCL and selected related contracts.
- Metals: GC/MGC and selected related contracts.
- Rates: treasury futures.
- Agricultural products: later, because delivery, limits, and seasonality add complexity.
- FX futures: centralized exchange contracts.
- Spot forex: a later, separate asset class; prices and volume vary by venue and provider.

Each futures contract needs exchange, product root, expiry, multiplier, minimum tick, tick value, currency, session calendar, timezone, settlement type, first/last trade dates, and rollover policy. For NQ, CME materials specify a $20 index multiplier and a 0.25-point minimum tick worth $5 per contract ([CME equity product specifications](https://www.cmegroup.com/content/dam/cmegroup/education/modules/files/EQ240_EQ_for_AIT.pdf)). MNQ must be modeled as its own product rather than treated as a decimal NQ position.

### Continuous contracts and rollover

A replay system cannot silently stitch `NQ` history. Store the actual contract symbol on every bar or tick and separately expose a continuous-series view. Document whether continuous history is:

- volume/open-interest rolled or calendar rolled;
- unadjusted, difference-adjusted, or ratio-adjusted;
- allowed to open a simulated position across rollover.

For faithful practice, select an actual historical contract and prevent accidental look-ahead through later rollover adjustments.

### Sessions and time

Store timestamps in UTC but preserve the exchange timezone and trading-date/session definition. A futures “trading day” crosses midnight, observes exchange holidays, and contains maintenance breaks. The UI should support:

- exchange session, regular cash session, and custom session shading;
- previous day/week high and low;
- overnight high/low and opening ranges;
- daylight-saving transitions;
- economic-event markers with the timezone visible.

### Price and volume representations

MVP bars: OHLCV at 1-minute resolution, aggregated server-side to larger intervals. Later data levels:

1. Trades/OHLCV — enough for candles and basic replay.
2. Top of book — bid/ask and spread-aware simulated fills.
3. Market depth/order-book events — required for genuine DOM, footprint, imbalance, and order-flow replay.

Standard OHLCV cannot reconstruct historical bid/ask depth, aggressor side, queue position, or the exact path inside a candle. Therefore the application must never claim “order-flow training” from candle data alone.

## 4. Charting and analysis tools

### MVP chart capabilities

- Candlestick, bar, line, area, and volume views.
- Pan, zoom, crosshair, price/time scales, session separators, go-to-date.
- Timeframes: 1m, 3m, 5m, 15m, 30m, 1h, 4h, daily; tick/range/volume charts later.
- Indicator framework with SMA, EMA, VWAP, anchored VWAP, ATR, RSI, MACD, Bollinger Bands, volume, and session/opening-range studies.
- Drawing framework with trend/ray/extended lines, horizontal/vertical lines, rectangles, channels, Fibonacci retracement/extension, long/short risk-reward tool, text, arrows, brush, and measurement.
- Magnet/snap, lock, hide, clone, style, undo/redo, object tree, templates, and persistence.

TradingView Lightweight Charts is small, Apache-2.0 licensed, supports streaming custom data, and is designed for large bar arrays ([official overview](https://www.tradingview.com/lightweight-charts/)). It is a sound MVP renderer, but the team must build much of the advanced drawing UX. TradingView Advanced Charts has a richer interface and a custom Datafeed API for history and real-time updates, but access is supplied through TradingView's repositories and requires its licensing/onboarding path ([quick start](https://www.tradingview.com/charting-library-docs/latest/getting_started/quick-start/), [API reference](https://www.tradingview.com/charting-library-docs/latest/api/)). The architecture should isolate either choice behind `ChartAdapter`, `DrawingRepository`, and `MarketDataClient` interfaces.

### Price-action concepts worth teaching

Use observable definitions rather than presenting a named strategy as fact:

- trend and market structure: swing highs/lows, higher-high/higher-low sequences, breaks and failed breaks;
- support/resistance and supply/demand zones;
- candle body, wick, range, close location, displacement, gaps, and volatility;
- liquidity context: prior highs/lows, equal highs/lows, session extremes, and opening ranges;
- volume, VWAP, relative volume, and volatility regime;
- multi-timeframe alignment and session behavior;
- risk/reward, position sizing, maximum daily loss, and expectancy.

### ICT and other curricula

“ICT” vocabulary is popular but not a single exchange or academically standardized specification. Treat it as one configurable curriculum, not as market truth. Define every detector in plain, testable terms. Example modules could include:

- swing structure and market-structure shift;
- liquidity sweep of a defined prior extreme;
- three-candle fair-value-gap geometry;
- displacement threshold relative to ATR;
- order-block exercise using an explicit rule set;
- session “kill zone” exercises with user timezone conversion;
- premium/discount within a chosen dealing range.

Other curriculum packs can reuse the same engine:

- classical price action;
- risk management and trade planning;
- VWAP/opening-range trading;
- trend following or mean reversion;
- order flow, only when trade/quote/depth data exists.

The educational system should ask the learner to identify conditions and explain invalidation. It should score consistency with the chosen rule set, not promise that a pattern predicts profit.

## 5. Practice and replay engine

### Anti-look-ahead rules

At session creation, the server selects an instrument, actual contract, start timestamp, warm-up length, and hidden future range. It sends only bars at or before the replay cursor. Future bars must not be delivered to the browser and merely hidden with CSS. Indicators must be calculated from visible history only; lessons, autoscaling, tooltips, network responses, and cached API data must not leak future values.

Random selection needs constraints: minimum liquidity, valid session, no contract-expiry anomaly unless requested, enough warm-up data, and curriculum tags. Seeded randomness makes a replay reproducible and shareable.

### Trade plan before playback

Require the learner to commit:

- long/short/no-trade decision;
- entry order type and price;
- stop and target(s);
- quantity;
- setup tags and written thesis;
- confidence and optional screenshot/drawings.

Lock the original plan after submission. Later edits belong in a separate revision/event history so review cannot rewrite the past.

### Deterministic fill model

Define and version these rules:

- market, limit, stop, and stop-limit behavior;
- bid/ask spread, configurable slippage, commissions/fees;
- tick-size rounding and contract multiplier;
- partial fills and queue position (only with adequate data; otherwise explicitly unsupported);
- gap-through-stop behavior;
- target/stop priority when both occur inside one OHLC candle.

The last case is essential. A candle whose high touches TP and low touches SL does not reveal which came first. Safe choices are tick replay, a conservative “loss first” rule, or marking the result ambiguous. Never silently choose the favorable result.

### Playback and animation

Use a server-authoritative cursor and a frontend animation clock. Playback modes should include next candle, variable speed, pause, jump to order/event, and later tick streaming. Animations may interpolate display movement, but scoring must use real recorded events—not invented visual paths.

### Scoring and review

Separate **outcome quality** from **process quality**:

- realized P&L, ticks/points, R-multiple, maximum favorable/adverse excursion;
- risk-limit compliance and whether SL was moved improperly;
- plan completeness and rule adherence;
- setup-specific accuracy, expectancy, average win/loss, profit factor, drawdown, and sample size;
- calibration: whether confidence aligns with historical success;
- time/session/instrument breakdown and recurring mistake tags.

A profitable rule violation should receive poor process feedback; a well-planned loss can receive good process feedback. This discourages outcome bias.

## 6. Proposed system architecture

```text
React/Vite on Vercel
  |-- REST: users, catalog, lessons, journals, replay creation
  |-- WebSocket: live/replay events
  v
Spring Boot API on container hosting
  |-- auth/authorization
  |-- market-data provider adapters
  |-- bar aggregation and replay engine
  |-- deterministic simulation/scoring
  |-- curriculum and journal services
  v
PostgreSQL (+ optional TimescaleDB later)
  |-- relational user/trade/lesson/audit data
  |-- JSONB for versioned drawings and strategy parameters
  |-- partitioned OHLCV/tick tables
  v
Licensed external market-data source
```

Spring supports raw WebSocket and STOMP messaging. Its documentation specifically identifies financial applications as a strong use case where low latency, frequency, and volume combine, while also warning that proxies and cloud infrastructure must support long-lived upgrades ([Spring WebSocket reference](https://docs.spring.io/spring/reference/web/websocket.html)). REST should serve ordinary resources; WebSocket should serve quote/replay streams.

Vercel's official function runtimes list Node.js, Bun, Python, Rust, Go, Ruby, Wasm, and Edge—not Java ([Vercel runtimes](https://vercel.com/docs/functions/runtimes)). Therefore deploy the static Vite frontend to Vercel and Spring Boot to a Java/container host such as Railway, Render, Fly.io, or a cloud container service after comparing current limits and cost. Keep them behind stable `app` and `api` domains with restrictive CORS and secure cookies/tokens.

### Why PostgreSQL rather than MongoDB

PostgreSQL is the better default because the core domain has strong relationships and transactional invariants: user → replay session → immutable plan → orders → fills → result → review. It supports constraints, migrations, joins, window functions for analytics, JSONB for flexible drawings/lesson definitions, and time partitioning. It also avoids introducing separate stores during a student-scale MVP.

MongoDB would be attractive for schemaless chart documents and rapid event ingestion, but flexible data can still live in JSONB, while consistency and reporting are central here. Reconsider a dedicated time-series/event store only after measurements show PostgreSQL is the bottleneck.

### Suggested backend modules

- `identity`: accounts, roles, preferences, consent/disclosures.
- `instruments`: products, contracts, sessions, roll rules, tick economics.
- `marketdata`: provider interfaces, normalization, bars, quotes, entitlements.
- `charting`: layouts, drawings, indicator configurations.
- `replay`: selection, cursor, event stream, snapshots.
- `simulation`: orders, fills, commissions, P&L, ambiguity rules.
- `learning`: curricula, lessons, prompts, detectors, rubric versions.
- `journal`: notes, screenshots, tags, reviews, statistics.
- `audit`: immutable domain events and reproducibility metadata.

Begin as a modular monolith. Microservices would add operational cost without helping the first deliverable.

### Core data entities

- `users`, `roles`, `user_preferences`
- `instruments`, `contracts`, `exchange_sessions`, `holidays`, `roll_rules`
- `bars` (`contract_id`, `timeframe`, `open_time`, OHLCV, source, revision)
- later: `trades`, `quotes`, `book_events`
- `chart_layouts`, `drawing_objects`, `indicator_configs`
- `replay_sessions`, `replay_events`, `replay_snapshots`
- `trade_plans`, `orders`, `fills`, `positions`, `simulation_results`
- `curricula`, `lessons`, `rule_versions`, `attempts`, `rubric_scores`
- `journal_entries`, `tags`, `attachments`

Use decimal/numeric price types or integer ticks, never binary floating point for price/P&L. Every simulation result should store data source/version, contract, replay seed, engine version, fill-model version, lesson/rubric version, and original plan so it can be reproduced.

## 7. Market-data and legal feasibility

Market data is the largest external dependency. CME says real-time, delayed, end-of-day, historical, display, non-display, and distribution uses can require distinct agreements, controls, reporting, and fees. It lists hundreds of licensed distributors and notes that some provide charting and historical services ([CME licensing overview](https://www.cmegroup.com/market-data/license-data.html), [licensed distributors](https://www.cmegroup.com/market-data/license-data/licensed-market-data-distributors.html)). Its historical-distribution guidance says displaying controlled historical charts still requires the appropriate distribution license, and export/download changes the category ([historical distribution guidance](https://www.cmegroup.com/market-data/distributor/files/cme-group-data-licensing-policy-guidelines-historical-information-distribution.pdf)).

Before choosing a provider, obtain written answers for:

- Does the plan cover browser display to multiple authenticated users?
- May the backend cache and replay historical bars/ticks?
- Is derived scoring/indicator computation permitted?
- May data be redistributed, exported, screenshotted, or shared?
- What delayed interval, history depth, exchanges, and symbology are included?
- Are per-user entitlements and audit logs required?
- Does the provider supply actual contracts, continuous contracts, top of book, trades, or depth?
- Are educational/classroom and public-production uses treated differently?

Implement `MarketDataProvider` with methods such as instrument search, contract metadata, historical bars, optional historical events, and live subscription. Keep provider symbols outside the domain model via a mapping table. Do not scrape TradingView or another website.

For a classroom prototype, legally sourced static sample data or a provider's permitted trial/sandbox can prove replay. Before public launch, complete provider and exchange licensing review. This document is a technical risk assessment, not legal advice.

## 8. API and event sketch

REST examples:

- `GET /api/instruments`
- `GET /api/contracts/{id}/bars?from=&to=&timeframe=`
- `POST /api/replays` — server chooses/validates the hidden segment.
- `POST /api/replays/{id}/plans` — immutable initial plan.
- `POST /api/replays/{id}/commands` — pause, speed, next event.
- `GET /api/replays/{id}/result`
- `POST /api/chart-layouts`
- `GET /api/progress?curriculum=&instrument=&period=`

WebSocket events should carry a schema version, replay/session ID, monotonically increasing sequence number, exchange timestamp, and payload. Examples: `BAR_OPENED`, `BAR_UPDATED`, `TRADE_PRINT`, `ORDER_ACCEPTED`, `ORDER_FILLED`, `POSITION_UPDATED`, and `REPLAY_FINISHED`. Sequence numbers allow reconnect/resume and reveal dropped events.

## 9. Security, quality, and observability

- Keep data-provider secrets only in the backend.
- Enforce authorization on saved charts, journals, replay sessions, and WebSocket subscriptions.
- Rate-limit history queries and cap ranges/resolutions.
- Validate every order against tick size, quantity limits, cursor time, and session ownership.
- Use migrations and seed only synthetic/licensed demo data.
- Do not log credentials or full personal journal content.
- Record request IDs, provider latency/errors, WebSocket connection counts, replay lag, and simulation-engine version.
- Back up user-generated journals/layouts; raw provider data should follow contractual retention rules.

Testing priorities:

- property/unit tests for tick rounding, long/short P&L, gaps, commissions, and TP/SL conflicts;
- golden-data tests for indicators and contract rollover;
- replay determinism tests using the same seed/data/engine version;
- integration tests for database constraints and reconnect/resume;
- browser tests for drawing persistence and future-data leakage;
- load tests for concurrent replay streams before adding live streams.

## 10. Delivery roadmap

### Phase 0 — decisions and proof (1–2 weeks)

- Compare this proposal with Claude's report and record accepted decisions.
- Define MVP users, educational disclaimer, NQ/MNQ scope, and success metrics.
- Run market-data licensing/provider proof-of-concept.
- Prototype 5,000–50,000 candles and several drawings in the chosen chart engine.
- Specify fill rules, timestamp/session conventions, and contract rollover.

**Exit:** a licensed/approved demo dataset, chart proof, architecture decision record, and reproducible replay rules.

### Phase 1 — vertical-slice MVP (3–5 weeks)

- React/Vite shell, Spring Boot API, PostgreSQL migrations, local containers.
- NQ/MNQ instrument metadata and historical 1-minute bars.
- Candles, crosshair, volume, core drawings, and saved layout.
- Blind replay with next/play/pause/speed.
- Long/short plan with entry, SL, TP, one contract, deterministic result.
- Basic journal and R-multiple statistics.

**Exit:** a user can complete one honest, reproducible practice trade end-to-end.

### Phase 2 — learning product (3–5 weeks)

- Curriculum/rubric engine and classical price-action plus one explicitly defined ICT module.
- More indicators/drawings and multi-timeframe context.
- Fees/slippage, limit/stop orders, session filters, behavioral scoring.
- Dashboard by setup/session/instrument; replay sharing without exposing raw downloadable data.

### Phase 3 — breadth and fidelity

- Additional futures products and contract/roll management.
- Provider-approved delayed/live streaming.
- Tick or quote replay, depth/footprint only if data permissions and budget support it.
- Economic calendar, alerts, collaboration/coach review, accessibility, mobile polish.

### Explicitly defer

- Live broker order routing and custody.
- Copy trading or personalized buy/sell recommendations.
- A full TradingView clone, Pine Script compatibility, options analytics, and every asset class.
- “AI predicts the next move” claims.

## 11. MVP acceptance criteria

The first release is credible when:

- the browser never receives future replay bars;
- the same seed/data/engine version returns the same result;
- NQ/MNQ P&L and tick rounding are correct under automated tests;
- TP/SL same-bar ambiguity is surfaced or conservatively resolved by documented policy;
- drawings and plan survive reload;
- original plans and later revisions are distinguishable;
- every displayed dataset has recorded source and permitted use;
- the application includes visible risk/simulation disclosures;
- a student can explain the product and architecture from `DOCUMENTATION.md`.

## 12. Decisions to make with Claude before coding

1. Confirm PostgreSQL and the separate Vercel-frontend/container-backend deployment.
2. Choose Lightweight Charts versus pursuing Advanced Charts access.
3. Choose the legally permitted prototype dataset/provider and acceptable delay.
4. Define the exact vertical slice: recommended NQ/MNQ, one-minute replay, core drawings, one bracket trade, journal result.
5. Define TP/SL collision, slippage, commission, session, and rollover rules.
6. Choose the first curriculum and write objective definitions for every setup.
7. Agree on collaboration files and session protocol in `PATH.md`.

## 13. Research sources

- [CFTC — Futures Market Basics](https://www.cftc.gov/LearnAndProtect/EducationCenter/FuturesMarketBasics/index2.htm)
- [CFTC — If You Are About to Trade](https://www.cftc.gov/LearnAndProtect/AdvisoriesAndArticles/areyouabouttotrade.html)
- [CME — Equity product specifications](https://www.cmegroup.com/content/dam/cmegroup/education/modules/files/EQ240_EQ_for_AIT.pdf)
- [CME — Market-data licensing](https://www.cmegroup.com/market-data/license-data.html)
- [CME — Market-data policy education center](https://www.cmegroup.com/market-data/license-data/market-data-policy-education-center.html)
- [CME — Historical information distribution guidance](https://www.cmegroup.com/market-data/distributor/files/cme-group-data-licensing-policy-guidelines-historical-information-distribution.pdf)
- [CME — Licensed market-data distributors](https://www.cmegroup.com/market-data/license-data/licensed-market-data-distributors.html)
- [TradingView — Lightweight Charts](https://www.tradingview.com/lightweight-charts/)
- [TradingView — Advanced Charts quick start](https://www.tradingview.com/charting-library-docs/latest/getting_started/quick-start/)
- [TradingView — Chart/Datafeed API](https://www.tradingview.com/charting-library-docs/latest/api/)
- [Spring — WebSockets](https://docs.spring.io/spring/reference/web/websocket.html)
- [Spring — STOMP overview](https://docs.spring.io/spring/reference/web/websocket/stomp/overview.html)
- [Vercel — Supported runtimes](https://vercel.com/docs/functions/runtimes)
- [Vercel — Deployments](https://vercel.com/docs/deployments/overview)

Sources were selected primarily from regulators, exchanges, and official framework/product documentation. Provider prices and plan entitlements were intentionally not frozen into this proposal because they change and contractual permissions must be verified directly before selection.
