# Tradeez — Zero-Cost Market-Data Strategy

**Decision date:** 2026-08-27  
**Constraint:** Spend €0 during initial development; do not use cryptocurrency or a CFD/spot-index feed as a disguised futures substitute.

## Decision

Tradeez begins with deterministic, explicitly simulated futures datasets named `DEMO-NQ` and
`DEMO-MNQ`, plus a local CSV-import adapter for data the project owner is legally permitted to
use. No paid provider, card, subscription, crypto feed, scraped chart, or public vendor dataset is
required for Phase 0 or the first software vertical slice.

This is development data, not a claim of market realism. Every screen using it must show:

> **SIMULATED DATA — NOT MARKET HISTORY**

## Why not use a free “Nasdaq” proxy?

A broker's USA-100/Nasdaq-100 feed is not NQ. It does not contain the actual CME contract,
expiry, rollover, futures basis, exchange volume, or CME session/holiday behavior. It can be useful
for generic price-chart development, but calling it NQ would train the wrong domain model and make
the presentation misleading.

Dukascopy was evaluated and rejected as the default source. Its official terms describe
personal/non-commercial limits and conditions on public presentation or redistribution; a
third-party download library's code license does not license the underlying market data. See the
[Dukascopy XML data agreement](https://www.dukascopy.com/plugins/cont.php?ref_id=1273) and
[JForex API terms](https://www.dukascopy.com/swiss/docs/api/index.php).

## Phase 0 datasets

### `DEMO-NQ`

- Tick size: 0.25 index points.
- Multiplier: $20 per point; $5 per tick.
- Exchange/session metadata modeled after the NQ product.
- Prices represented internally as integer ticks.
- Seeded output, with generator version stored on every replay result.
- One-second ordered demo events aggregated to 1m/3m/5m/15m/30m/1h views.

### `DEMO-MNQ`

- Same price path and tick size as `DEMO-NQ`.
- Multiplier: $2 per point; $0.50 per tick.
- Allows position-sizing exercises to demonstrate the real economic difference between NQ and
  MNQ without purchasing market data.

### Required scenarios

The generator and fixed golden fixtures must cover:

- trend, range, high-volatility and low-volatility regimes;
- opening gaps and maintenance-break gaps;
- exact tick-grid rounding;
- stop only, target only, and both touched inside one display candle;
- ambiguity inside the smallest available OHLC interval;
- limit, market and stop-order cases;
- FVG geometry, swing breaks, equal highs/lows and sweep-shaped examples;
- session boundary, exchange timezone and DST-transition cases;
- reconnect/resume and replay determinism.

Fixtures designed to demonstrate a curriculum rule must be marked `HAND_AUTHORED_FIXTURE`; they
must never be counted as historical evidence that the rule is profitable.

## CSV import path

`CsvMarketDataProvider` lets Fabio import a local futures export when his broker/platform permits
that use. Minimum fields:

- provider/source declaration;
- actual contract symbol;
- UTC timestamp or timestamp plus explicit source timezone;
- open, high, low, close;
- timeframe;
- optional volume.

Import validation rejects duplicate timestamps, invalid OHLC relationships, off-grid prices,
non-monotonic time, unexplained gaps, mixed contracts, and missing source metadata. Imported data
is private/local by default and is never included in builds, version control, demos, or public API
responses automatically.

## ⚠️ What simulated data cannot do — the drill boundary

*Added Session 004 (Claude).* This section sharpens "it does not prove that Tradeez teaches NQ
well," because the consequence is bigger than that sentence implies.

The required-scenarios list above includes "FVG geometry, swing breaks, equal highs/lows and
sweep-shaped examples." Generating those means **the generator is authored to contain the very
patterns the detector then finds.** That is fine — excellent, in fact — for *testing a detector*,
because injected ground truth is exactly what a unit test needs.

It is **circular as training material.** A learner drilled on generated FVGs is learning to spot
the generator's artifacts, not the market's. A seeded price process can reproduce the *shape* of a
fair value gap; it cannot reproduce the *reason* one matters — real displacement from real order
flow, real stops resting above real prior highs, real session and news behaviour.

**Therefore (DEC-034): drills are gated on data class.**

| Data class | May a detector run on it? | May it back a **graded** drill? |
|---|---|---|
| `SIMULATED` | ✅ yes — this is how detectors are unit-tested | ❌ **no** |
| `IMPORTED_PRIVATE` | ✅ | ✅ (local/single-user only) |
| `LICENSED_DEPLOYABLE` | ✅ | ✅ |

A drill generated from `SIMULATED` data is a **developer fixture** and must be labelled as such. It
is never served to a learner as a graded exercise, and never contributes to a concept rating or
spaced-repetition schedule. This extends the DEC-026 epistemic rule: we already refuse to claim a
methodology is objectively true; we must equally refuse to teach it from data we invented.

**Roadmap consequence:** `NEXT UP` task 7 (first FVG detector → `detected_setup` → identification
drill) may be **built and tested** on `DEMO-NQ`, but the drill it produces is not shippable to a
learner until real data clears the readiness gate below. The detector work is not blocked; the
*curriculum claim* is.

## Deployment, not the app, is what triggers licensing

*Added Session 004 (Claude).* Neither document has stated this plainly, and it resolves the
apparent tension between "€0 now" and "real NQ eventually."

- Tradeez running **locally**, single user, displaying data Fabio is licensed to use personally →
  permitted only to the extent of that source licence.
- The **same application** on a public URL serving authenticated users → display and distribution.
  Needs a licence.

The `data_class` enum already encodes this, and `IMPORTED_PRIVATE` literally means *never leaves
this machine*. So the clean architecture is:

| Environment | Dataset | Licensing exposure |
|---|---|---|
| Public deployment (Vercel + host) | `DEMO-NQ` / `DEMO-MNQ` only | **none** |
| Local development | `IMPORTED_PRIVATE` real NQ, if permitted | governed by source terms |
| Jury/classroom display | `IMPORTED_PRIVATE` only if terms explicitly permit that presentation | verify before use |

This means the CSV import path is **not a consolation prize — it is the primary route to real
data** when the source licence permits local import.

**Practical next step for Fabio, worth checking before assuming anything must be bought:** many
futures brokers let account holders export their own historical data for personal use. If Fabio's
broker does, that is a legitimate, already-licensed route to real NQ history at €0 — and it makes
`CsvMarketDataProvider` the highest-value adapter in the project rather than a fallback. Permission
to export or view locally does not automatically grant permission to show the data to a jury or
other users; that must be checked separately.

## Real NQ readiness gate

Generated data proves that the software works; it does not prove that Tradeez teaches NQ well.
Before the product claims real-market NQ training, a real dataset must satisfy all of the following:

1. Actual NQ/MNQ contracts and documented symbology.
2. Sufficient timestamp resolution for the declared fill model.
3. Documented sessions, holidays, revisions and rollover policy.
3b. **Full electronic session coverage, not regular hours only.** *(Added Session 004 — Claude.)*
   This criterion is why a Nasdaq **ETF** proxy was rejected alongside the CFD proxy: QQQ trades
   only during regular US hours, so an ICT curriculum built on it would have **no Asian or London
   session to teach from**, and killzone training would be structurally impossible. The same trap
   applies to a broker export — if Fabio's platform exports RTH-only bars, that dataset fails this
   gate for the ICT track even though it is real, licensed NQ data. Check the session coverage of
   any candidate export before building curriculum on it.
4. Written permission for the intended classroom, jury-demo, cached replay and authenticated
   browser-display use.
5. Recorded source/version and a retention/deletion policy.
6. Validation against known contract specifications and golden days.

CME offers academic resources and private simulated environments, but its historical-data
education offering is described as discounted rather than universally free
([CME Academic Resources](https://www.cmegroup.com/education/academic-resources)). These programs
can be explored later without blocking development.

## Provider boundary

All sources implement `MarketDataProvider`. Initial adapters:

1. `GeneratedMarketDataProvider` — enabled by default.
2. `CsvMarketDataProvider` — local, permission-dependent imports.
3. Future licensed provider adapter — disabled until the readiness gate passes.

Provider symbols never leak into the core domain. The domain stores normalized instruments,
contracts, integer-tick prices, UTC timestamps, source/version metadata, and explicit data class:
`SIMULATED`, `IMPORTED_PRIVATE`, or `LICENSED_DEPLOYABLE`.
