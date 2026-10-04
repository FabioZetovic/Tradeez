# ICT_CONCEPTS.md — The ICT Curriculum, Concept by Concept

> **What this file is.** Tradeez exists to help people learn to trade with **ICT concepts** — the
> methodology taught by Michael J. Huddleston, "The Inner Circle Trader" — by **paper trading on real
> historical NQ charts** (DEC-041). This file is the curriculum specification: every ICT concept and
> structure the app will teach, what it means, how the app can detect it, how the app teaches it,
> and what has to be built for it. Part 13 turns the concepts into work; the order of that work lives
> in [`PATH.md`](PATH.md) → NEXT UP.
>
> Written by Claude in Session 017 (2026-10-04) at Fabio's request. Keep the **Status** column of the
> master table current as tasks land. Concept IDs are stable and never renumbered.

---

## Contents

- [How to read this file](#how-to-read-this-file)
- [The honest frame](#the-honest-frame--read-before-teaching-anything)
- [Master table](#master-table)
- [Part 0 — How Tradeez teaches a concept](#part-0--how-tradeez-teaches-a-concept)
- [Part 1 — Foundations (Level 0)](#part-1--foundations-level-0)
- [Part 2 — Market structure (Level 1)](#part-2--market-structure-level-1)
- [Part 3 — Liquidity (Level 2)](#part-3--liquidity-level-2)
- [Part 4 — Imbalances and gaps (Level 3)](#part-4--imbalances-and-gaps-level-3)
- [Part 5 — Blocks (Level 4)](#part-5--blocks-level-4)
- [Part 6 — Ranges and Fibonacci (Level 5)](#part-6--ranges-and-fibonacci-level-5)
- [Part 7 — Time (Level 6)](#part-7--time-level-6)
- [Part 8 — Narrative and bias (Level 7)](#part-8--narrative-and-bias-level-7)
- [Part 9 — Entry models (Level 8)](#part-9--entry-models-level-8)
- [Part 10 — Execution and management (Level 9)](#part-10--execution-and-management-level-9)
- [Part 11 — Not taught, and why](#part-11--not-taught-and-why)
- [Part 12 — The curriculum ladder](#part-12--the-curriculum-ladder)
- [Part 13 — From concepts to work](#part-13--from-concepts-to-work)
- [Part 14 — Data each concept needs](#part-14--data-each-concept-needs)
- [Part 15 — Open questions](#part-15--open-questions)
- [Sources](#sources)

---

## How to read this file

**Time is New York time.** ICT states every time window in New York local time
(`America/New_York`), including daylight saving. Tradeez stores UTC and converts at the edge
(INVARIANT 4). Two traps:

- The US and Europe change clocks on different Sundays (US: second Sunday of March, first Sunday of
  November; UK/EU: last Sunday of March, last Sunday of October). For a few weeks a year London opens
  an hour earlier or later *in New York time*. ICT's windows are defined in New York time, so the app
  uses New York time and never a fixed UTC offset.
- CME runs on Chicago time, which is always New York minus one hour (both follow US daylight saving).

**IDs.** Foundations are `F-n`; concepts are `ICT-nn`. Gaps in the numbering are deliberate room for
later concepts.

**Rules.** Each detectable concept gets a versioned rule `name-MAJOR.MINOR.PATCH` (DEC-026), like the
implemented `fvg-1.0.0` (DEC-039). A rule written here is a **draft** until it is implemented and
recorded as a decision. **Default parameters are starting points**: before a rule is locked, its
parameters are measured on real NQ, the way the FVG threshold was measured — and any change after
that is a new version.

**Detectability** — how far software can decide the concept:

| Class | Meaning | What the app may grade |
|---|---|---|
| **D** — deterministic | Pure geometry or clock once parameters are fixed (an FVG, a session high, a CE level). | Identification and classification, against the rule. |
| **R** — rule-based, parameter-sensitive | Mechanical once a definition is chosen, but reasonable definitions disagree (order blocks, MSS, displacement). | The same, **always naming the rule and its parameters** ("under `ob-1.0.0`…"). |
| **J** — judgement | ICT applies it with discretion (bias, draw on liquidity, narrative). | Never "right/wrong". Taught by prediction drills graded on what real price did, and by checklists the learner fills in. |

**Confirmation time.** Every pattern has a moment it *becomes knowable*: an FVG at candle 3's close, a
swing high only after the candles to its right close, an MSS at the breaking candle's close. Drills,
overlays and practice sessions use the confirmation time, never the pattern's start time — otherwise
the app leaks the future (INVARIANT 1, DEC-009). Each concept below states its confirmation time.

**Discontinuities.** A session break (the daily 17:00–18:00 halt, the weekend) and a **contract roll**
(the continuous series switches from one quarterly contract to the next, with a real price jump —
DEC-024) are both gaps in the data. No candle pattern spans one, except the concepts that are *about*
those gaps (ICT-27, ICT-28).

**Status:** ✅ implemented · 📐 rule drafted here · 📝 described, needs design · ⛔ out of scope (Part 11).

**Teaching modes** are listed in Part 0: A–F come from DEC-023; **G (Evidence)** and **L (Lesson
card)** are proposed here.

---

## The honest frame — read before teaching anything

1. **ICT is not a standardised specification.** Huddleston taught it over many years through video
   lectures — notably a private mentorship in 2016–2017 and a free mentorship on YouTube in 2022.
   Terms evolved, some definitions shift between years, and a large community ("Smart Money
   Concepts", SMC) renamed and altered many of them. Where definitions disagree, this file says so.
2. **So Tradeez teaches its own versioned reading** of each concept and grades whether the learner
   applied *that* definition — never whether ICT is correct (INVARIANT 8, DEC-026).
3. **No published, independent evidence establishes that these concepts give a trading edge.** The
   app does not assume they do. Its Evidence mode (G) measures how often a concept's claimed behaviour
   actually happened in the imported history, with the sample size and date range — descriptive,
   about the past, never a promise.
4. **Drills are built only from real data** (DEC-034, INVARIANT 8b). The detectors may run anywhere;
   a graded exercise needs real market history.
5. **Educational, not financial advice** (DEC-015). Futures are leveraged; simulated results do not
   predict live results.

This frame is not a disclaimer bolted on at the end. It is what makes the app defensible to a jury
and genuinely useful to a learner: a beginner who sees "ICT says X; on 500 real NQ days, X happened
62% of the time" learns more than one who is told X is true.

---

## Master table

| ID | Concept | Level | Detect | Status | Rule (draft) | Needs |
|---|---|---|---|---|---|---|
| F-1 | Candle anatomy | 0 | D | 📝 | — | 1m |
| F-2 | Timeframes and top-down reading | 0 | D | 📝 | — | 1m → aggregates |
| F-3 | NQ, the contract | 0 | D | 📝 | — | instrument registry |
| F-4 | The New York clock | 0 | D | 📝 | — | session calendar |
| F-5 | Risk in R, position size | 0 | D | ✅ partly (practice slice) | — | — |
| ICT-01 | Swing high / swing low | 1 | D | 📐 | `swing-1.0.0` | 1m |
| ICT-02 | Short-, intermediate-, long-term highs and lows | 1 | D | 📐 | `swing-tier-1.0.0` | 1m |
| ICT-03 | Market structure and order flow | 1 | D / J | 📐 | `structure-1.0.0` | 1m |
| ICT-04 | Displacement | 1 | R | 📐 | `displacement-1.0.0` | 1m |
| ICT-05 | Market structure shift (MSS) | 1 | R | 📐 | `mss-1.0.0` | 1m |
| ICT-06 | BOS / CHoCH (SMC aliases) | 1 | R | 📐 | `structure-1.0.0` | 1m |
| ICT-10 | Buy-side and sell-side liquidity | 2 | D | 📐 | `liquidity-1.0.0` | 1m |
| ICT-11 | Reference highs and lows (PDH/PDL, PWH/PWL, sessions) | 2 | D | 📐 | `levels-1.0.0` | 1m, sessions |
| ICT-12 | Relative equal highs / lows | 2 | R | 📐 | `eqhl-1.0.0` | 1m |
| ICT-13 | Liquidity sweep vs run | 2 | R | 📐 | `sweep-1.0.0` | 1m |
| ICT-14 | Turtle soup (pattern) | 2 | R | 📐 | via `sweep-1.0.0` | 1m |
| ICT-15 | External vs internal range liquidity | 2 | R / J | 📝 | — | 1m |
| ICT-16 | Draw on liquidity | 2 | J | 📝 | — | 1m |
| ICT-17 | Low / high resistance liquidity runs | 2 | R / J | 📝 | — | 1m |
| ICT-20 | Fair value gap (BISI / SIBI) | 3 | D | ✅ `fvg-1.0.0` | `fvg-1.0.0` (DEC-039) | 1m |
| ICT-21 | Consequent encroachment | 3 | D | 📐 | part of each zone rule | — |
| ICT-22 | FVG lifecycle (tapped, CE, filled) | 3 | D | 📐 | `fvg-state-1.0.0` | 1m |
| ICT-23 | Inversion FVG | 3 | D | 📐 | `ifvg-1.0.0` | 1m |
| ICT-24 | Balanced price range | 3 | D | 📐 | `bpr-1.0.0` | 1m |
| ICT-25 | Volume imbalance | 3 | D | 📐 | `vi-1.0.0` | 1m |
| ICT-26 | Gap and liquidity void | 3 | D / R | 📐 | `void-1.0.0` | 1m |
| ICT-27 | New week / new day opening gaps (NWOG, NDOG) | 3 | D | 📐 | `opening-gap-1.0.0` | 1m, sessions |
| ICT-28 | Opening range gap (RTH gap) | 3 | D | 📐 | `opening-gap-1.0.0` | 1m, sessions |
| ICT-29 | Implied fair value gap | 3 | R | 📝 | — | 1m |
| ICT-30 | Order block, mean threshold | 4 | R | 📐 | `ob-1.0.0` | 1m |
| ICT-31 | Breaker block | 4 | R | 📐 | `breaker-1.0.0` | 1m |
| ICT-32 | Mitigation block | 4 | R | 📐 | `mitigation-1.0.0` | 1m |
| ICT-33 | Rejection block | 4 | R | 📐 | `rejection-1.0.0` | 1m |
| ICT-34 | Propulsion block | 4 | R / J | 📝 | — | 1m |
| ICT-35 | Vacuum block | 4 | R | 📝 | — | 1m, news calendar |
| ICT-36 | Unicorn (breaker + FVG) | 4 | D | 📐 | `unicorn-1.0.0` | 1m |
| ICT-37 | The PD array matrix | 4 | J | 📝 | — | — |
| ICT-40 | Dealing range | 5 | R / J | 📐 | `dealing-range-1.0.0` | 1m |
| ICT-41 | Premium, discount, equilibrium | 5 | D | 📐 | `pd-1.0.0` | 1m |
| ICT-42 | Optimal trade entry (OTE) | 5 | D | 📐 | `ote-1.0.0` | 1m |
| ICT-43 | ICT's Fibonacci levels and targets | 5 | D | 📐 | drawing preset | — |
| ICT-44 | Standard deviation projections | 5 | R | 📐 | `stdev-1.0.0` | 1m |
| ICT-50 | Day boundaries and opening prices | 6 | D | 📐 | `opens-1.0.0` | 1m, sessions |
| ICT-51 | Sessions and session ranges | 6 | D | 📐 | `sessions-1.0.0` | 1m, sessions |
| ICT-52 | Killzones | 6 | D | 📐 | `sessions-1.0.0` | sessions |
| ICT-53 | Silver Bullet windows | 6 | D | 📐 | `sessions-1.0.0` | sessions |
| ICT-54 | Macros | 6 | D | 📐 | `sessions-1.0.0` | sessions |
| ICT-55 | CBDR, Asian range, Flout | 6 | D | 📐 | `sessions-1.0.0` | 1m |
| ICT-56 | News times | 6 | D | 📝 | — | news calendar |
| ICT-57 | IPDA data ranges, quarterly shifts | 6 | D / J | 📐 | `ipda-1.0.0` | daily, 60+ days |
| ICT-60 | Higher-timeframe narrative | 7 | J | 📝 | — | multi-timeframe |
| ICT-61 | Daily bias | 7 | J | 📝 | `daily-bias-1.0.0` (suggestion only) | daily |
| ICT-62 | Power of three (AMD) | 7 | D after / J before | 📐 | `po3-1.0.0` | 1m |
| ICT-63 | Judas swing | 7 | R | 📐 | `judas-1.0.0` | 1m, sessions |
| ICT-64 | Daily profiles | 7 | D after | 📐 | `daily-profile-1.0.0` | 1m, sessions |
| ICT-65 | Weekly profiles | 7 | D after | 📐 | `weekly-profile-1.0.0` | 1m |
| ICT-66 | SMT divergence (NQ vs ES) | 7 | R | 📐 | `smt-1.0.0` | 1m NQ **and ES** |
| ICT-67 | Intermarket (DXY, yields) | 7 | J | ⛔ for now | — | other markets |
| ICT-70 | The 2022 model | 8 | R | 📐 | `model-2022-1.0.0` | composition |
| ICT-71 | Silver Bullet model | 8 | R | 📐 | `model-sb-1.0.0` | composition |
| ICT-72 | OTE model | 8 | R | 📐 | `model-ote-1.0.0` | composition |
| ICT-73 | Unicorn model | 8 | R | 📐 | `model-unicorn-1.0.0` | composition |
| ICT-74 | Turtle soup model | 8 | R | 📐 | `model-turtle-1.0.0` | composition |
| ICT-75 | Breaker / mitigation entry | 8 | R | 📐 | `model-breaker-1.0.0` | composition |
| ICT-76 | Session open reversals (London, New York) | 8 | R | 📐 | `model-reversal-1.0.0` | composition |
| ICT-77 | Market maker buy / sell models | 8 | J | 📝 | — | multi-timeframe |
| ICT-78 | Opening range gap model | 8 | R | 📐 | `model-org-1.0.0` | composition |
| ICT-80 | Stop placement | 9 | R / J | 📝 | — | — |
| ICT-81 | Targets and partials | 9 | R / J | 📝 | — | — |
| ICT-82 | Trade management | 9 | J | 📝 | — | — |
| ICT-83 | Risk rules and drawdown | 9 | D | 📝 | — | — |
| ICT-84 | Journaling and review | 9 | — | ✅ partly (journal) | — | — |

---

## Part 0 — How Tradeez teaches a concept

### The concept loop

Every concept is taught with the same loop, in this order:

1. **Learn** (L) — a one-minute card: what it is, a picture, why ICT cares, the exact rule Tradeez
   uses, and two or three **real NQ examples found by the detector** (not hand-picked).
2. **Spot** (A) — identification drills: "click every fair value gap in these 30 candles". Fast,
   repeated dozens of times per sitting.
3. **Name** (B) — classification drills: "is the marked zone an order block, a breaker or a
   mitigation block?"
4. **Predict** (C) — the future is hidden: "which gets taken first — the buy-side above or the
   sell-side below?" Graded by what real price did, not by opinion.
5. **Trade** (D) — a replay paper-trading session where the concept is part of the plan checklist.
6. **Review** — after the trade, the app overlays what its rules saw at each moment, with the reasons
   in the detector's own numbers.
7. **Repeat** — misses come back on a spaced-repetition schedule; the next concept unlocks when this
   one is solid.

### Teaching modes

| Mode | What the learner does | Time per rep | Grading |
|---|---|---|---|
| **L** Lesson card | Reads a short card built from this file plus detected real examples | 1 min | none |
| **A** Identification | Clicks every instance on a static chart | ~15 s | precision / recall against the rule |
| **B** Classification | Chooses the name of a marked structure | ~10 s | against the rule |
| **C** Prediction | Answers a question about the hidden future, then sees it | ~20 s | against real price (real data only) |
| **D** Execution | Paper-trades a replay: plan → locked bracket → candle-by-candle replay → result | 3–10 min | scorecard (DEC-027): process and outcome shown separately |
| **E** Management | Decides mid-trade (hold, breakeven, partial) and sees each alternative's outcome | ~30 s | against real price |
| **F** Risk | Computes contracts, risk in R and dollars under a time limit | ~15 s | arithmetic |
| **G** Evidence *(new)* | Reads how often a concept's claimed behaviour happened in the imported history | — | none — descriptive statistics with sample size and date range |

### What may be graded

- **D and R concepts:** modes A and B, graded against the versioned rule, with the rule named on screen.
- **J concepts:** never graded as right or wrong. Taught through C (graded by the real outcome), D
  (the learner states their reading in the plan; review shows what happened) and G.
- **Everything graded runs on real data** (DEC-034). The schema already forbids a graded simulated
  drill (`ck_drill_simulated_never_graded`).

---

## Part 1 — Foundations (Level 0)

These are not ICT concepts. ICT assumes all of them, and a beginner (Fabio's position, and the app's
target user) usually has none of them.

### F-1 · Candle anatomy

**What.** A candle summarises a time period: **open**, **high**, **low**, **close**. The **body** runs
between open and close; the **wicks** run from the body to the high and low. ICT speaks of
**up-close** candles (close above open) and **down-close** candles (close below open) rather than
green and red — order blocks (ICT-30) are defined that way. Also: **range** (high − low) and
**close location** (where in its range a candle closed — a candle that closes near its high is
"strong").

**Teach.** L, then B drills ("which candle closed strongest?"), F-style quick questions.

### F-2 · Timeframes and top-down reading

**What.** The same market seen at different candle sizes: monthly, weekly, daily, 4-hour, 1-hour,
15-minute, 5-minute, 1-minute. ICT reads top-down: the higher timeframe decides *where* price is
likely going (narrative, ICT-60); the lower timeframe decides *when and where* to enter. Concepts are
"fractal" in ICT's language — an FVG on a daily chart is the same shape as one on a 1-minute chart.

**Tradeez.** Every coarser timeframe is aggregated from stored 1-minute bars (DEC-040). The chart
already switches timeframes.

**Teach.** L; A drills that show the same moment on two timeframes ("this 15-minute FVG — find it on
the 1-minute chart").

### F-3 · NQ, the contract

**What the learner must know.**

| | NQ | MNQ |
|---|---|---|
| Underlying | Nasdaq-100 index | same |
| Tick | 0.25 index points | 0.25 |
| Tick value | $5.00 | $0.50 |
| Point value | $20 | $2 |
| Trading hours | Sunday 18:00 → Friday 17:00 New York, with a daily halt 17:00–18:00 | same |
| Cash index hours | 09:30–16:00 New York ("regular trading hours", RTH) | same |
| Contract months | March, June, September, December (H, M, U, Z) | same |

Each contract expires on the third Friday of its month; trading volume moves to the next contract
about a week before. A continuous chart switches contracts at that point, and because Tradeez shows
**raw prices** (DEC-024) the switch is a visible price jump. That jump is not a market move, and
detectors never treat it as one.

**Teach.** L, F drills (contracts and dollars per point), and a B drill: "this jump — market move or
contract roll?"

### F-4 · The New York clock

**What.** Every ICT time window is New York time. The learner should know what is happening at each
hour of the NQ day: Asia (evening), London (early morning), the 08:30 economic releases, the 09:30
cash open, lunch, the afternoon, the 16:00 cash close, the 17:00 halt.

**Tradeez.** A 24-hour session strip under the chart (task T7, see PATH.md), always in New York time,
with the DST rules above and tests for transition days (INVARIANT 4).

**Teach.** L; C drills ("at 03:30 New York, which session is open?").

### F-5 · Risk in R, position size

**What.** **R** is the amount risked on a trade — entry to stop. Results are measured in R so that
trades of different sizes compare. Contracts = risk in dollars ÷ (stop distance in ticks × tick
value). On a small account, NQ is usually too big and MNQ is the honest answer — that itself is worth
teaching.

**Tradeez.** The practice slice already computes R, ticks and dollars in integer ticks (INVARIANT 3).

**Teach.** F drills; R appears on every result card.

---

## Part 2 — Market structure (Level 1)

### ICT-01 · Swing high / swing low

**What.** A **swing high** is a candle whose high is higher than the candles on either side of it; a
**swing low** is the mirror. ICT's basic swing is three candles: a high with a lower high on each side.

**Why ICT cares.** Swing points are where stops rest (liquidity, ICT-10) and what structure breaks
(ICT-05) are measured against.

**Tradeez rule — `swing-1.0.0` (draft).**
- Candle *i* is a swing high when its high is **strictly greater** than the highs of the *N* candles to
  its left and **greater than or equal to** the highs of the *N* candles to its right. Default *N* = 1.
- The asymmetry settles ties: of two adjacent equal highs, the earlier one is the swing.
- Swing lows mirror this.
- Never across a contract roll.
- **Confirmation:** the close of candle *i + N*.

**Disagreements.** Some traders use *N* = 2 (five-candle fractals, as in Bill Williams' fractals); ICT
uses three-candle swings for short-term structure.

**Teach.** A (click the swing highs), B (swing or not?).

### ICT-02 · Short-, intermediate-, long-term highs and lows (STH/ITH/LTH, STL/ITL/LTL)

**What.** ICT ranks swings. A **short-term high** is any swing high. An **intermediate-term high** is
a short-term high with lower short-term highs on both sides of it. A **long-term high** is an
intermediate-term high with lower intermediate-term highs on both sides. Lows mirror.

**Why ICT cares.** The tier says how significant a level is: breaking an intermediate-term high
matters more than breaking a short-term one, and intermediate-term points frame dealing ranges
(ICT-40).

**Tradeez rule — `swing-tier-1.0.0` (draft).** Tiers are computed from `swing-1.0.0` points exactly as
defined above. **Confirmation:** an ITH is known only once the next short-term high to its right is
confirmed — a deliberate lag the overlays must respect.

**Teach.** A (mark the intermediate-term highs), B (tier of the marked swing).

### ICT-03 · Market structure and order flow

**What.** Read in sequence, swing points form structure: **higher highs and higher lows** (bullish),
**lower highs and lower lows** (bearish). ICT's broader idea is **institutional order flow**: in
bullish order flow, bullish PD arrays (Part 4–5) hold when price returns to them and bearish ones
fail; bearish order flow is the reverse.

**Tradeez rule — `structure-1.0.0` (draft).** Label each confirmed swing HH / HL / LH / LL relative to
the previous swing of the same kind. The structure state is bullish after an HH and an HL, bearish
after an LL and an LH, and undefined otherwise. Order flow ("are PD arrays being respected?") is J and
is taught, not computed.

**Teach.** A (label the swings), C ("bullish or bearish structure at the cursor?" — then reveal the
next swing).

### ICT-04 · Displacement

**What.** An energetic move: one or a few large-bodied candles in one direction, closing strong,
usually leaving fair value gaps behind. ICT treats displacement as the signature of large orders —
a structure break without displacement is not trusted.

**Tradeez rule — `displacement-1.0.0` (draft).**
- A run of 1 to 3 consecutive candles in the same direction (all up-close or all down-close),
- whose combined body is at least **2 ×** the average high–low range of the 14 candles before the
  run,
- and which leaves at least one `fvg-1.0.0` gap.
- Parameters: `maxCandles = 3`, `multiple = 2.0`, `lookback = 14`, `requiresFvg = true`.
- **Confirmation:** close of the run's last candle.

**Disagreements.** Some define displacement purely as "creates an FVG"; others by a multiple of ATR;
ICT describes it visually. The multiple is a placeholder until measured on real NQ.

**Teach.** A (mark displacement legs), B ("displacement or just a big candle?").

### ICT-05 · Market structure shift (MSS)

**What.** The first sign that the direction may have turned. For a bullish MSS: price takes out an
old low (sell-side liquidity), then reverses with displacement and **closes above the swing high**
that came before that low. Bearish mirrors. In ICT's teaching the sequence matters — sweep, then
displacement, then break — which is what separates an MSS from an ordinary break.

**Tradeez rule — `mss-1.0.0` (draft), bullish case.**
1. A swing low *L* trades at least one tick below an earlier, still-resting swing low (a sweep,
   ICT-13). Parameter `requiresSweep = true`.
2. *H* is the most recent swing high confirmed before *L*.
3. Within `maxCandles = 20` candles after *L*, a candle **closes** above *H* (`breakBy = close`; the
   alternative is `wick`).
4. The leg from *L* to that close contains a displacement (`requiresDisplacement = true`).
- **Confirmation:** close of the breaking candle.

**Disagreements.** Close versus wick beyond the level; whether the sweep is required; which swing
counts. Each is a parameter.

**Teach.** A (find the MSS), B ("MSS or not — and which condition failed?"), C ("an MSS just
confirmed: does price reach the FVG it left before making a new high?").

### ICT-06 · BOS / CHoCH (SMC aliases)

**What.** Terms from the Smart Money Concepts community, not ICT's own. **Break of structure (BOS)**:
price breaks the last swing *in* the trend's direction (continuation). **Change of character
(CHoCH)**: the first break *against* the trend — close in meaning to ICT's MSS, minus the sweep and
displacement conditions.

**Tradeez.** Learners meet these words in YouTube content, so the structure overlay can label breaks
as BOS / CHoCH on request, with a note that these are SMC terms. `structure-1.0.0` covers both.

**Teach.** B ("BOS or CHoCH?") — the classification drill DEC-023 already names.

---

## Part 3 — Liquidity (Level 2)

### ICT-10 · Buy-side and sell-side liquidity (BSL / SSL)

**What.** Resting orders. Above an old high sit the stop-losses of sellers and the buy-stop entries
of breakout traders — **buy-side liquidity**. Below an old low sit sell stops — **sell-side
liquidity**. ICT's core claim is that price is drawn to these pools: large participants need that
liquidity to fill their orders.

**Tradeez rule — `liquidity-1.0.0` (draft).** Every confirmed swing high is a BSL level and every swing
low an SSL level, from its confirmation time until price trades at least one tick beyond it; then it
is **taken**, with the time it was taken. The same applies to the reference levels of ICT-11. A
level's state at any moment is computable without lookahead.

**Teach.** A (mark resting BSL/SSL at the cursor), C ("which is taken first?").

### ICT-11 · Reference highs and lows

**What.** The levels everyone can see, and therefore where liquidity gathers:

- **PDH / PDL** — previous day's high / low (and **PDC**, its close)
- **PWH / PWL** — previous week's high / low; **PMH / PML** for the month
- **Session highs and lows** — Asia, London, New York AM, New York PM (ICT-51)
- **Overnight (Globex) high / low** — 18:00 to 09:30
- **RTH high / low** — 09:30 to 16:00

**Tradeez rule — `levels-1.0.0` (draft).** Computed from 1-minute bars. One parameter matters:
**`dayBoundary`** — the CME session day (18:00 → 17:00 New York, default, matching how futures charts
build a daily candle) or the New York midnight day that ICT's later material leans on. Each level
becomes a `liquidity-1.0.0` level once its period closes.

**Teach.** A (draw today's PDH/PDL), C ("which of these levels is taken first today?").

### ICT-12 · Relative equal highs / lows (EQH / EQL)

**What.** Two or more swing highs at nearly the same price. To ICT they are "engineered liquidity" —
an obvious double top invites stops above it, so it is a likely target. "Relative" matters: they need
not match to the tick.

**Tradeez rule — `eqhl-1.0.0` (draft).** Two confirmed swing highs, at least 3 candles apart, whose
highs differ by at most `tolerance` — default the larger of 1 tick and 10% of the 14-candle average
range — with no candle between them trading above the higher of the two. Lows mirror.
**Confirmation:** the second swing's confirmation.

**Teach.** A (find the equal highs), C ("are these equal highs taken before the session ends?") and
G (how often relative equal highs were taken within a session, on real NQ).

### ICT-13 · Liquidity sweep vs run

**What.** Two outcomes when price reaches a liquidity level. A **sweep** (also *raid*, *stop hunt*,
*run on stops*) trades through the level and comes straight back: the stops were the target. A
**run** (a breakout) trades through and stays: the level was a waypoint.

**Tradeez rule — `sweep-1.0.0` (draft).** A resting level is taken by at least one tick.
- **Sweep:** a candle closes back on the original side within `closeBackWithin = 1` candle (the same
  candle, i.e. a wick through) — up to 3 as a parameter.
- **Run:** the taking candle closes beyond the level and the next `holdCandles = 2` candles do not
  close back.
- Otherwise: undecided.
- **Confirmation:** the close that decides it.

**Teach.** B ("sweep or run?"), C ("price just took the London high — sweep or run?").

### ICT-14 · Turtle soup (the pattern)

**What.** A failed breakout of an old high or low, traded in the reverse direction. The name comes
from Linda Raschke and Larry Connors, who faded false breaks of the "Turtle" traders' 20-day
breakouts; ICT applies it to any obvious old high or low. In Tradeez terms it is a **sweep**
(ICT-13) of a reference level (ICT-11) or of equal highs/lows (ICT-12). The trading model is ICT-74.

**Teach.** A (find the turtle soups), G (how often a sweep of the previous day's high led to a move
of at least 1R back inside).

### ICT-15 · External vs internal range liquidity (ERL / IRL)

**What.** Inside a dealing range (ICT-40), the range's own high and low are **external range
liquidity**; fair value gaps and other imbalances inside it are **internal range liquidity**. ICT's
claim: price alternates — after taking external liquidity it seeks internal liquidity, and vice versa.

**Tradeez.** 📝 Labels each level and gap as ERL or IRL relative to the current dealing range. The
alternation claim is a natural G question: after taking ERL, how often did price reach an IRL gap
within *N* candles?

**Teach.** B (ERL or IRL?), G.

### ICT-16 · Draw on liquidity (DOL)

**What.** The level price is expected to reach next — the "magnet". Choosing it is the heart of ICT
analysis and pure judgement: higher-timeframe narrative, nearby pools, imbalances left open.

**Tradeez.** 📝 Never graded as right or wrong in advance. Two uses:
- **C drills:** "the buy-side at X or the sell-side at Y — which is reached first?" Graded by what
  happened.
- **Practice plans** ask for the learner's DOL. Review shows whether price reached it, and when.

### ICT-17 · Low / high resistance liquidity runs (LRLR / HRLR)

**What.** The path to a target. A **low-resistance** run has little in the way — few opposing swing
points or PD arrays — and tends to move cleanly; a **high-resistance** run is choppy, with many
obstacles.

**Tradeez.** 📝 Partly measurable: count the opposing swings and PD arrays between price and the
target, and show that count as a fact. Calling it "low resistance" stays the learner's judgement.

**Teach.** B with two charts side by side ("which path is low-resistance?"), G (time to target
against the obstacle count).

---

## Part 4 — Imbalances and gaps (Level 3)

### ICT-20 · Fair value gap (FVG) — BISI / SIBI

**What.** Three consecutive candles where the middle candle moved so fast that the first and third
candles' wicks do not overlap. The untraded space between them is the gap. A bullish FVG is a
**BISI** in ICT's words (buy-side imbalance, sell-side inefficiency); a bearish one is a **SIBI**.
ICT's claim: price tends to return to rebalance it, and a respected FVG is an entry zone.

**Tradeez rule — `fvg-1.0.0` ✅ (DEC-039).**
- Bullish: candle 1's high is below candle 3's low. Bearish: candle 1's low is above candle 3's high.
- Consecutive candles, no market closure between them (and, with real data, no contract roll).
- The gap is at least 25% of the average high–low range of the 14 candles before candle 1, in integer
  ticks, rounded up, at least one tick.
- **Confirmation:** candle 3's close.

**On real data.** The 25% was measured on the *simulated* market. It must be re-measured on real NQ,
and if it changes, that is `fvg-1.1.0`, never an edit of 1.0.0.

**Teach.** A ✅ (exists as a fixture; becomes graded on real data), C ("does price return to this gap
before the session ends?"), G.

### ICT-21 · Consequent encroachment (CE)

**What.** The midpoint of a gap — of an FVG, an opening gap, a void, or a long wick. ICT treats it
as the key level inside the zone: price respecting the CE is a sign of strength. Large gaps are also
split into **quadrants** (25%, 50%, 75%).

**Tradeez.** Part of every zone rule. A midpoint of an odd number of ticks falls between two ticks,
so CE is stored in **half-ticks** (twice the price in ticks) to stay integer (INVARIANT 3). "Price
reached the CE" means it traded at or beyond that exact half-tick.

**Teach.** A (draw the CE), part of every zone drill.

### ICT-22 · FVG lifecycle

**What.** What has happened to a gap since it formed. ICT's language: price **trades into** it,
reaches its **CE**, **fills** it, or **closes through** it.

**Tradeez rule — `fvg-state-1.0.0` (draft).** At any time *t* after confirmation, the state is one of
`OPEN` (untouched) → `TAPPED` (price traded into the near edge) → `CE` (reached the midpoint) →
`FILLED` (traded to the far edge) → `INVERTED` (a candle closed beyond the far edge, ICT-23). Computed
only from candles up to *t*.

**Teach.** B ("state of this gap at the cursor?"), C ("will this gap be filled today?"), G.

### ICT-23 · Inversion fair value gap (IFVG)

**What.** An FVG that failed: price closed straight through it. ICT's claim: the failed gap flips —
a failed bullish FVG then acts as resistance (and vice versa).

**Tradeez rule — `ifvg-1.0.0` (draft).** A `fvg-1.0.0` gap becomes an inversion gap when a candle
**closes** beyond its far edge. **Confirmation:** that close.

**Teach.** A, C ("price is returning to this inversion gap from below: does it reject?"), G.

### ICT-24 · Balanced price range (BPR)

**What.** Price shoots up through an area (leaving a bullish FVG) and then straight back down
(leaving a bearish FVG) — or the reverse. The overlap of the two gaps is a balanced price range,
which ICT treats as a strong zone.

**Tradeez rule — `bpr-1.0.0` (draft).** A bullish and a bearish `fvg-1.0.0` gap, the second confirmed
within `maxCandles = 20` of the first, whose price ranges overlap. The zone is the overlap, with its
CE. **Confirmation:** the second gap's confirmation.

**Teach.** A, B.

### ICT-25 · Volume imbalance

**What.** Two consecutive candles whose **bodies** do not touch while their **wicks** overlap — a
small gap between one candle's close and the next one's open. ICT treats it as a minor imbalance
that price often returns to close.

**Tradeez rule — `vi-1.0.0` (draft).** Bullish: candle 2's body is entirely above candle 1's body,
and candle 2's low is at or below candle 1's high. The zone runs between candle 1's body top and
candle 2's body bottom. Bearish mirrors. Consecutive, same contract.
**Confirmation:** candle 2's close.

**Teach.** A, B ("volume imbalance, FVG or neither?").

### ICT-26 · Gap and liquidity void

**What.** A true **gap** is when one candle's whole range sits above (or below) the previous
candle's — no overlap at all. A **liquidity void** is larger: a fast, one-sided run over several
candles with very little overlap between them. ICT's claim for both: price tends to come back
through them.

**Tradeez rule — `void-1.0.0` (draft).** Gap: candle 2's low is above candle 1's high (bullish) —
deterministic. Void: at least 3 consecutive same-direction candles, each overlapping the previous by
no more than 20% of its range. The void parameters are R and will be measured.

**Teach.** A, G.

### ICT-27 · New week / new day opening gaps (NWOG / NDOG)

**What.** The futures market closes daily at 17:00 New York and reopens at 18:00; on weekends from
Friday 17:00 to Sunday 18:00. The price difference across each closure is an opening gap: the **new
week opening gap** (Friday's last price to Sunday's open) and the **new day opening gap** (17:00
close to 18:00 open). ICT tracks their CE as reference levels, and keeps several recent NWOGs on the
chart.

**Tradeez rule — `opening-gap-1.0.0` (draft).** Gap = the last trade before the closure (the close of
the 16:59 one-minute candle) to the first open after it. Stored with its CE. No minimum size; zero
gaps are stored as zero.

**Teach.** A (mark the NWOG and its CE), C ("is the NWOG's CE reached this week?"), G.

### ICT-28 · Opening range gap (RTH gap)

**What.** For index futures ICT also uses the gap between the previous day's **16:14** one-minute
close (the regular session's end, in his convention) and the **09:30** open. A figure attributed to
ICT in community summaries says price closes at least half of this gap within the first 30 minutes
about 70% of the time — exactly the kind of claim the Evidence mode should test rather than repeat.

**Tradeez rule — `opening-gap-1.0.0` (draft).** Opening range gap = close of the 16:14 one-minute
candle → open of the 09:30 candle, on consecutive trading days. Stored with its CE and quadrants.

**Teach.** A, C ("does price reach this gap's CE by 10:00?"), G (the 70% claim, measured).

### ICT-29 · Implied fair value gap

**What.** A later ICT idea: three candles with a large middle candle where the outer candles' wicks
reach into the middle candle's body, so no true gap exists, but the space between the outer wicks'
midpoints is treated as an implied gap. *Verify the exact definition against ICT's lectures before a
rule is drafted* — community descriptions vary.

**Status.** 📝 Not in the first curriculum.

---

## Part 5 — Blocks (Level 4)

### ICT-30 · Order block (OB), mean threshold

**What.** A **bullish order block** is the last down-close candle (or run of consecutive down-close
candles) before a displacement move up that breaks structure; a **bearish order block** is the last
up-close candle before a displacement down. ICT's claim: that candle is where large orders were
placed, and price returning to it finds support or resistance. The **mean threshold** is the
midpoint of the block's body; ICT treats a close through it as weakening the block.

**Tradeez rule — `ob-1.0.0` (draft), bullish case.**
- The last down-close candle before a `displacement-1.0.0` leg up that closes above a confirmed swing
  high (a break of structure or an MSS).
- `mergeRun = true`: if several down-close candles come in a row, the block spans them.
- `zone = full` (the candles' high to low; the alternative `body` uses open to low).
- Mean threshold = midpoint of the body, in half-ticks.
- The block is **invalidated** when a candle closes below its low.
- **Confirmation:** close of the candle that breaks the swing high — a block is only an order block
  once the move away from it has happened.

**Disagreements.** Full range versus body; one candle versus a run; whether the break must be an
MSS; whether an FVG must follow. These are parameters, which is why this is class R.

**Teach.** A (find the order blocks), B ("order block or not — which condition is missing?"),
C ("price returns to this block: does it hold the mean threshold?"), G.

### ICT-31 · Breaker block

**What.** A failed order block that flips. Bullish breaker: a swing high forms, price falls to a
**lower low that takes sell-side liquidity**, then rallies with displacement **back above that swing
high**. The last up-close candle(s) at that swing high — which failed as a bearish block — become
support. Bearish mirrors.

**Tradeez rule — `breaker-1.0.0` (draft), bullish case.**
1. Swing low *L1*, then swing high *H*, then a lower swing low *L2* that trades below *L1* (sweep).
2. A candle closes above *H*, the leg containing a displacement.
3. The zone is the last up-close candle (or run) before the drop from *H* to *L2*.
- **Confirmation:** the close above *H*.

**Teach.** A, B ("breaker or mitigation block?" — the sweep is the difference).

### ICT-32 · Mitigation block

**What.** The same flip as a breaker **without the sweep**: in a bullish case price makes a low
*L1*, rallies to *H*, falls to a **higher** low *L2* (it fails to take *L1*), then breaks above *H*.
The up-close candle(s) at *H* become support. ICT treats it as weaker than a breaker.

**Tradeez rule — `mitigation-1.0.0` (draft).** As `breaker-1.0.0`, with *L2* **above** *L1*.

**Teach.** B, paired with ICT-31.

### ICT-33 · Rejection block

**What.** At a swing high (or low) where candles left long wicks, the zone from the highest candle
body up to the wick's extreme. ICT's claim: when price returns, it reacts at the body level, and a
close above it invalidates the block.

**Tradeez rule — `rejection-1.0.0` (draft), bearish case.** A confirmed swing high whose candle's upper
wick is at least `wickShare = 50%` of its range; the zone runs from the highest body (open or close)
among the swing candle and its neighbours to the swing high. Wick CE in half-ticks. **Confirmation:**
the swing's confirmation.

**Teach.** A, B.

### ICT-34 · Propulsion block

**What.** A candle that trades into an older order block and then propels price away; ICT treats the
propelling candle as a new block whose mean threshold should hold. Depends on ICT-30 and on judging
"propels", hence R/J.

**Status.** 📝 After ICT-30 is measured.

### ICT-35 · Vacuum block

**What.** A gap created by a volatility event — a news release or a session open — that ICT expects
price to come back and fill.

**Status.** 📝 Needs an economic calendar (ICT-56) to tie a gap to an event.

### ICT-36 · Unicorn (breaker + FVG)

**What.** A breaker block (ICT-31) that overlaps an FVG (ICT-20) in the same direction. The overlap is
the zone; ICT presents it as a high-quality entry.

**Tradeez rule — `unicorn-1.0.0` (draft).** A `breaker-1.0.0` zone and a same-direction `fvg-1.0.0`
gap, the gap formed in the breaker's displacement leg, with overlapping price ranges.
**Confirmation:** the later of the two.

**Teach.** A, B. The trading model is ICT-73.

### ICT-37 · The PD array matrix

**What.** "PD arrays" (premium and discount arrays) is ICT's umbrella term for every zone above:
old highs/lows, rejection blocks, order blocks, FVGs, liquidity voids, breakers and mitigation
blocks. His matrix orders them — premium arrays above equilibrium, discount arrays below — and
teaches choosing the array that fits the narrative: in bullish order flow, buy from discount arrays.
The order commonly reproduced from the 2016–17 mentorship, from the extreme toward equilibrium, is
roughly: old high or low → rejection block → order block → fair value gap → liquidity void →
breaker → mitigation block. *Verify against the lecture before teaching it as ICT's exact order.*

**Tradeez.** 📝 J. Taught as a B/C exercise ("bullish context, price in discount: which array do you
expect a reaction from?" — graded by what price did) and by G (reaction rates per array type).

---

## Part 6 — Ranges and Fibonacci (Level 5)

### ICT-40 · Dealing range

**What.** The range price is trading within on the timeframe being read: from a swing low to a swing
high. Picking *which* swings is the judgement — usually the most recent significant ones, often the
leg that produced the last displacement or MSS.

**Tradeez rule — `dealing-range-1.0.0` (draft).** The most recent confirmed swing high and swing low
that bracket the current price, where the more recent of the two ended a `displacement-1.0.0` leg.
Swing tier is a parameter (`tier = intermediate` by default).

**Teach.** The learner draws a dealing range and sees the rule's beside it. Not graded as right or
wrong; A drills grade only against the rule, named on screen.

### ICT-41 · Premium, discount, equilibrium

**What.** **Equilibrium** is the 50% level of the dealing range. Above it is **premium** (expensive —
where ICT looks to sell), below it is **discount** (cheap — where ICT looks to buy). The rule of thumb:
buy in discount in bullish order flow; sell in premium in bearish.

**Tradeez rule — `pd-1.0.0` (draft).** Equilibrium = (range high + range low) / 2, in half-ticks.
Deterministic given a dealing range.

**Teach.** B ("premium or discount?"), and in practice plans: the app notes whether a long was taken
in discount — as a fact, not a verdict (DEC-027).

### ICT-42 · Optimal trade entry (OTE)

**What.** After a move that shifted structure, ICT's preferred entry is a retracement into **62% to
79%** of that leg, with **70.5%** as the "sweet spot". For a bullish OTE the Fibonacci is drawn from
the leg's low to its high.

**Tradeez rule — `ote-1.0.0` (draft).** Given a leg (by default the leg that produced the last
`mss-1.0.0`), the OTE zone is the 62%–79% retracement, rounded outward to the tick grid, with the
70.5% line. Deterministic given the leg.

**Teach.** A (draw the OTE zone), C ("does price reach the OTE before a new high?"), G.

### ICT-43 · ICT's Fibonacci levels and targets

**What.** ICT uses his own level set: **0, 0.5 (equilibrium), 0.62, 0.705, 0.79, 1** for
retracements, and **−0.27, −0.62** (his common take-profit extensions), **−1** (a symmetrical move)
and **−2** beyond.

**Tradeez.** A Fibonacci drawing tool with an "ICT" preset (task T8). The current drawing layer has
four shapes (DEC-037); a Fibonacci tool is a new shape and a deliberate scope extension.

**Teach.** L, A.

### ICT-44 · Standard deviation projections

**What.** Measure a defined leg and project multiples of its length beyond it, as targets or likely
turning points. The multiples usually cited from ICT's 2022 material are −1, −2, −2.5 and −4. He
applies this to the manipulation leg of a session (ICT-62), the MSS leg, or a range such as the CBDR
(ICT-55).

**Tradeez rule — `stdev-1.0.0` (draft).** Inputs: a leg (start, end) and a list of multiples; levels at
*k* leg-lengths beyond the leg's end, in the chosen direction. Which leg to measure is the R part and
is a parameter.

**Teach.** A, G (how often the −2 projection of the first leg was reached in the session).

---

## Part 7 — Time (Level 6)

ICT treats time as at least as important as price: the same pattern means more inside a killzone
than outside it. Every window here is New York time and is versioned together in **`sessions-1.0.0`**,
because published versions of these windows disagree and Tradeez must pick one and say so.

### ICT-50 · Day boundaries and opening prices

**What.** ICT uses opening prices as references — in a bullish day he looks to buy *below* the open
and sell above it later (the logic of ICT-62). The ones that matter:

| Opening price | Time (NY) | Use |
|---|---|---|
| New York midnight open | 00:00 | ICT's "true day" reference in his later material |
| 08:30 open | 08:30 | economic releases |
| RTH open | 09:30 | cash market open; opening range gap (ICT-28) |
| Weekly open | Sunday 18:00 | weekly premium/discount |
| Monthly / quarterly open | first session | longer references |

**Tradeez rule — `opens-1.0.0` (draft).** Each is the open of the first one-minute candle at or after
the time. Drawn as lines on the chart (task T7).

**Teach.** A, B ("price is above the midnight open in a bullish narrative — premium or discount for
today?").

### ICT-51 · Sessions and session ranges

**What.** The NQ day in sessions. The high and low of each session become liquidity (ICT-11).

**`sessions-1.0.0` — proposed default (index futures):**

| Session / window | NY time | Note |
|---|---|---|
| Asia | 20:00 – 00:00 | some versions: 19:00–22:00 or 20:00–22:00 as the killzone |
| London | 02:00 – 05:00 | London killzone |
| New York AM | 08:30 – 11:00 | the index version; forex versions use 07:00–10:00 |
| London close | 10:00 – 12:00 | mostly a forex idea |
| New York lunch | 12:00 – 13:00 | ICT warns against trading lunch |
| New York PM | 13:30 – 16:00 | some versions: 13:00–15:00 |

**Teach.** A (mark the Asia range), C ("which session's high is taken first in New York?").

### ICT-52 · Killzones

**What.** The windows above where ICT expects the day's meaningful moves: **London**, **New York AM**,
**New York PM** (and Asia as a quieter one). ICT's guidance is to look for setups inside killzones and
to be sceptical outside them.

**Tradeez.** Killzone shading on the chart (T7); setups in drills and practice carry an "in killzone"
fact; G answers how much of the day's range formed inside each killzone on real NQ.

### ICT-53 · Silver Bullet windows

**What.** Three one-hour windows, **03:00–04:00, 10:00–11:00 and 14:00–15:00** New York, in which
ICT's Silver Bullet model (ICT-71) looks for an FVG entry toward a liquidity target.

**Tradeez.** Part of `sessions-1.0.0`; shaded on request.

### ICT-54 · Macros

**What.** Short windows (about 20–30 minutes) in which, by ICT's account, the price-delivery
algorithm runs on liquidity or rebalances a gap. The schedule commonly cited (NY time): **02:33–03:00,
04:03–04:30, 08:50–09:10, 09:50–10:10, 10:50–11:10, 11:50–12:10, 13:10–13:40, 15:15–15:45**.
*Cross-checked against several community summaries; verify against ICT's own lectures before locking
`sessions-1.0.0`.*

**Tradeez.** Part of `sessions-1.0.0`. A natural G question: is the average one-minute range inside a
macro actually higher than outside it, on real NQ?

### ICT-55 · CBDR, Asian range, Flout

**What.** Forex-origin ranges ICT uses to project the next day's high and low with standard deviations
(ICT-44): **CBDR** (central bank dealers range) **14:00–20:00**, the **Asian range 20:00–00:00**, and
the **Flout** (15:00–00:00, combining them). ICT's guidance prefers a small CBDR.

**Tradeez.** D for the ranges themselves. Low priority for NQ; kept so the curriculum is complete.

### ICT-56 · News times

**What.** Scheduled releases move NQ: **08:30** (CPI, jobs, claims), **10:00** (ISM, sentiment),
**14:00** (FOMC decisions, with the press conference after). ICT advises caution — or standing aside —
around high-impact releases; volatility around them creates vacuum blocks (ICT-35).

**Tradeez.** 📝 Needs an economic calendar with historical dates. No source chosen (Q-15). Until then,
the fixed release times can be shaded as "possible news".

### ICT-57 · IPDA data ranges, quarterly shifts

**What.** ICT's "interbank price delivery algorithm" idea: on the daily chart, the highs and lows of the
last **20, 40 and 60 trading days** define the liquidity price is likely to reach, and the market's
character shifts every three to four months ("quarterly shifts").

**Tradeez rule — `ipda-1.0.0` (draft).** The 20/40/60-day highs and lows at each day's close, drawn as
levels. Their *use* is J.

**Teach.** A, G (how often the 20-day high or low was reached within the next 20 days).

---

## Part 8 — Narrative and bias (Level 7)

### ICT-60 · Higher-timeframe narrative

**What.** The story before the trade: on the weekly and daily charts, where is the draw on liquidity,
which PD array is price reacting to, is price in premium or discount of the higher-timeframe range?
The lower timeframes only time an entry in the direction the narrative suggests.

**Tradeez.** 📝 J. Practice plans include narrative fields (higher-timeframe DOL, the PD array in
play, bias). Review shows what happened against them. Nothing is graded right or wrong in advance.

### ICT-61 · Daily bias

**What.** The expectation for the day: will it reach higher (toward buy-side) or lower (toward
sell-side)? One method ICT has taught, as commonly summarised: if yesterday closed above the high of
the day before, look for continuation toward the next buy-side liquidity; if yesterday ran that high
but closed back inside, look the other way; an inside day says little. *Verify against the source
before teaching it as ICT's method.*

**Tradeez.** `daily-bias-1.0.0` may compute a **suggestion with its reasoning**, never a verdict. The
graded part is after the fact: a C drill ("will today take the previous day's high or low first?")
and G (how often each suggestion matched, on real NQ, with the sample size).

### ICT-62 · Power of three (AMD)

**What.** ICT describes a day (or a session, or a week) in three phases: **accumulation** around the
open, **manipulation** — a move away from the open *against* the real direction, often taking
liquidity — and **distribution** (expansion) in the real direction, closing near the extreme. A
bullish daily candle in this picture: open → low below the open → high → close near the high.

**Tradeez rule — `po3-1.0.0` (draft).** After the fact, from one-minute bars: a day is
**Po3-consistent bullish** when it closed above its open, its low formed before its high, and the low
lay below the open by at least `manipulationShare = 15%` of the day's range. Bearish mirrors. The open
is a parameter (midnight, 18:00 or 09:30).

**Teach.** B (classify past days), C (at 09:00, with the day hidden after it: "has the manipulation
leg already happened?"), G (share of Po3-consistent days on real NQ).

### ICT-63 · Judas swing

**What.** The manipulation leg of ICT-62 at a session open: a false move away from the open, usually
taking a nearby liquidity level (Asia's high, the previous day's low), that reverses and becomes the
day's real move. Typically between midnight and the London open, or in the first half-hour after
09:30 for index futures.

**Tradeez rule — `judas-1.0.0` (draft).** Within a window (00:00–05:00 or 09:30–10:00), price moves
away from that window's opening price, sweeps (ICT-13) a reference level on that side, and then
closes back through the opening price within the window. **Confirmation:** that close.

**Teach.** A, C, G.

### ICT-64 · Daily profiles

**What.** Recurring shapes of a day, by where the high and low formed: **London makes the low, New
York continues higher**; **London makes the high, New York reverses**; **New York reversal** of an
earlier move; **consolidation** with no expansion.

**Tradeez rule — `daily-profile-1.0.0` (draft).** After the fact, classify each day by the session in
which its high and its low formed (ICT-51) and the direction of the close. Deterministic once
`sessions-1.0.0` is fixed.

**Teach.** B (classify past days), G (the distribution of profiles over the imported history — on
real NQ, not as told).

### ICT-65 · Weekly profiles

**What.** ICT's named shapes of a week, by which day forms the weekly high or low. Bullish versions:
**classic Tuesday low of the week**; **Wednesday low of the week**; **consolidation Thursday
reversal** (the week ranges, then Thursday runs a weekly extreme and reverses); **seek and destroy
Friday** (a choppy week that runs both sides, a low-probability profile). Bearish versions mirror.

**Tradeez rule — `weekly-profile-1.0.0` (draft).** After the fact: the weekday of the weekly high and
of the weekly low, and their order, map to a named profile or `unclassified`.

**Teach.** B, G (how often each profile occurred).

### ICT-66 · SMT divergence (NQ vs ES)

**What.** "Smart money technique": two normally correlated markets disagree at a liquidity level. If
NQ makes a higher high but ES fails to make one at the same time, ICT reads the failure as a crack in
the move — a bearish SMT. Lows mirror. Typical pairs: NQ / ES / YM.

**Tradeez rule — `smt-1.0.0` (draft).** Swing highs in NQ and ES confirmed within `alignCandles = 2`
of each other: one exceeds its own previous swing high, the other does not. **Needs ES bars for the
same period** — the free Massive plan covers CME, so ES can be imported alongside NQ (Part 14).

**Teach.** A (two charts, find the SMT), C, G.

### ICT-67 · Intermarket analysis

**What.** ICT also reads the dollar index, bond yields and other markets for context.

**Status.** ⛔ for now — different data sources and licences; only SMT (ICT-66) is in scope.

---

## Part 9 — Entry models (Level 8)

Models combine the concepts above into a trade. Each is a **composition of rules**, so the app can:

1. find every historical instance (for G and for choosing practice moments),
2. start a practice session just before an instance forms ("give me a 2022 model setup"), and
3. check a learner's trade tagged with a model against that model's checklist **at the decision
   moment** — adherence, scored separately from outcome (DEC-027).

The parameters below are drafts like all others.

### ICT-70 · The 2022 model

**What.** The model of ICT's free 2022 mentorship, and the best first model for a beginner because
every step is checkable:

1. Know the higher-timeframe draw on liquidity (ICT-16, ICT-60).
2. Wait for a killzone (ICT-52).
3. Price **sweeps** an obvious short-term high or low (ICT-13) — often the opposite side of the draw.
4. **Displacement** causes a **market structure shift** (ICT-04, ICT-05).
5. The displacement leaves an **FVG** (ICT-20): enter on the retrace into it.
6. Stop beyond the swing that made the sweep; target the opposing liquidity (or a standard deviation
   projection, ICT-44).

**Tradeez rule — `model-2022-1.0.0` (draft).** In a `sessions-1.0.0` killzone: a `sweep-1.0.0` of a
`levels-1.0.0` or swing level, followed by an `mss-1.0.0` in the opposite direction whose leg contains
a `fvg-1.0.0` gap. Entry at the gap's near edge (`entry = edge`, alternative `ce`); stop beyond the
sweep's extreme (`stop = sweep`, alternative `fvg-candle-1`); target the nearest opposing resting
liquidity. **Confirmation:** the gap's confirmation.

### ICT-71 · Silver Bullet model

**What.** Inside a Silver Bullet window (ICT-53), with a liquidity target identified, take the FVG that
forms in the window and points toward the target; enter on the retrace into it; target the
liquidity. ICT frames a minimum distance to the target so that the trade is worth taking.

**Tradeez rule — `model-sb-1.0.0` (draft).** The first `fvg-1.0.0` gap confirmed inside a Silver Bullet
window, pointing toward resting liquidity at least `minTargetDistance` away (a parameter, measured in
points). Entry, stop and target as in ICT-70.

### ICT-72 · OTE model

**What.** After an MSS, enter in the OTE zone (ICT-42) of the leg that broke structure; stop beyond the
leg's start; targets at −0.27, −0.62 or the opposing liquidity (ICT-43).

**Tradeez rule — `model-ote-1.0.0` (draft).** `mss-1.0.0`, then price trades into the `ote-1.0.0` zone of
its leg before making a new extreme beyond the leg's end.

### ICT-73 · Unicorn model

**What.** Enter in the overlap of a breaker and an FVG (ICT-36); stop beyond the breaker; target
liquidity.

**Tradeez rule — `model-unicorn-1.0.0` (draft).** A `unicorn-1.0.0` zone; entry at its near edge.

### ICT-74 · Turtle soup model

**What.** Fade a sweep of an obvious old high or low (ICT-14): enter back inside the range once the
sweep is confirmed; stop beyond the sweep's extreme; target the other side of the range or the nearest
opposing liquidity.

**Tradeez rule — `model-turtle-1.0.0` (draft).** A `sweep-1.0.0` of a `levels-1.0.0` level (PDH/PDL, PWH/PWL
or session extreme) or of `eqhl-1.0.0` equal highs/lows; entry at the sweep candle's close.

### ICT-75 · Breaker / mitigation entry

**What.** Enter on the retrace into a breaker (ICT-31) or mitigation block (ICT-32) after the
structure break that created it; stop beyond the block.

**Tradeez rule — `model-breaker-1.0.0` (draft).** First retrace into a `breaker-1.0.0` or `mitigation-1.0.0`
zone after its confirmation.

### ICT-76 · Session open reversals

**What.** The Judas swing (ICT-63) traded: in London, price takes Asia's high or low and reverses; at
the New York open, price takes London's high or low and reverses. Entries use the MSS and FVG of the
reversal.

**Tradeez rule — `model-reversal-1.0.0` (draft).** A `judas-1.0.0` swing followed by an `mss-1.0.0` within
the same window; entry and stop as in ICT-70.

### ICT-77 · Market maker buy / sell models (MMBM / MMSM)

**What.** ICT's larger-scale template. In a **sell model**, price leaves an *original consolidation*,
rises in stages (the "buy side of the curve", each stage leaving consolidations and PD arrays), reaches
a premium higher-timeframe PD array or buy-side liquidity (the "smart money reversal"), and then falls
back through the same stages (the "sell side of the curve"), using the earlier consolidations as
targets. The **buy model** mirrors it.

**Tradeez.** 📝 J. Taught with annotated real examples and a B drill ("which side of the curve is price
on?"). Not a mechanical model in the first curriculum.

### ICT-78 · Opening range gap model

**What.** Trade toward the CE of the opening range gap (ICT-28) in the first 30 minutes after 09:30,
on the claim that half the gap usually fills early.

**Tradeez rule — `model-org-1.0.0` (draft).** At 09:30, entry toward the gap's CE; target the CE; the
model's real-data hit rate is shown by G before a learner is taught to rely on it.

---

## Part 10 — Execution and management (Level 9)

### ICT-80 · Stop placement

**What.** ICT places stops beyond the structure that would prove the idea wrong: beyond the swing that
made the sweep, beyond the FVG's first candle, or beyond an order block.

**Tradeez.** The scorecard can state *where* a stop sat relative to detected structure ("2 ticks
beyond the sweep low"). Whether that was "good" depends on the model (DEC-027), so it is shown as a
fact, and graded only as adherence to the model the learner chose.

### ICT-81 · Targets and partials

**What.** ICT targets the opposing liquidity — the nearest pool first, a larger one second — or the
Fibonacci extensions (ICT-43) and standard deviations (ICT-44); partial profits are often taken at the
first target.

**Tradeez.** The practice bracket has one target today. Partial targets are task T28.

### ICT-82 · Trade management

**What.** What to do mid-trade: when (if ever) to move the stop to breakeven, when to take partials,
when to exit early. ICT warns against moving to breakeven too early.

**Tradeez.** Taught by E drills: the trade is frozen at +1.5R, the learner chooses, and the app shows
what each choice would have produced on the real continuation.

### ICT-83 · Risk rules and drawdown

**What.** Risk a small, fixed fraction per trade; reduce size after a losing streak; cap trades per
day. And the arithmetic every trader should feel: a 50% loss needs a 100% gain to recover.

**Tradeez.** F drills; daily limits in the replay mode (task T5) can be offered as a setting the
learner turns on.

### ICT-84 · Journaling and review

**What.** Every trade recorded with its model, killzone, draw on liquidity, result in R, and what was
learned. ICT stresses reviewing one's own trades.

**Tradeez.** The practice journal exists; tagging by model and killzone, and statistics by tag with
sample sizes, are tasks T22 and T25.

---

## Part 11 — Not taught, and why

| Topic | Why not |
|---|---|
| Order flow, footprint, delta (Q-03) | Not part of ICT, and impossible without licensed trade and quote data. DEC-041 makes it out of scope for v1. |
| Commitment of Traders, interest-rate differentials | ICT uses them for long-term positioning, not day trading; separate data. |
| Seasonal tendencies | Position-trading material; years of daily data per market. |
| Intermarket analysis beyond SMT (ICT-67) | Other markets, other licences. |
| "Quarterly theory" | A community extension of ICT's time ideas, not ICT's own. |
| SMC "inducement" and other SMC-only terms | Not ICT's. BOS/CHoCH are kept only as aliases (ICT-06). |
| ICT's later models not yet well documented | Added when a stable public description exists. |

---

## Part 12 — The curriculum ladder

| Level | Theme | Concepts | Unlocks when |
|---|---|---|---|
| 0 | Foundations | F-1 … F-5 | always open |
| 1 | Market structure | ICT-01 … ICT-06 | Level 0 cards read and the F-5 risk drill passed |
| 2 | Liquidity | ICT-10 … ICT-17 | Level 1 identification at ≥ 80% over the last 20 attempts |
| 3 | Imbalances and gaps | ICT-20 … ICT-29 | Level 1 solid (FVG may be opened early — it already exists) |
| 4 | Blocks | ICT-30 … ICT-37 | Levels 2 and 3 solid |
| 5 | Ranges and Fibonacci | ICT-40 … ICT-44 | Level 1 solid |
| 6 | Time | ICT-50 … ICT-57 | always open — it is reference knowledge |
| 7 | Narrative and bias | ICT-60 … ICT-67 | Levels 2, 5 and 6 solid |
| 8 | Entry models | ICT-70 … ICT-78 | each model unlocks when its component concepts are solid |
| 9 | Execution | ICT-80 … ICT-84 | with the first model |

"Solid" is measured on D and R concepts only (graded drills on real data). J concepts unlock by use,
never by a score. Thresholds are placeholders until the learning-state task (T16) settles them;
everything below Level 8 stays reachable for revision.

The practice replay is never locked: a beginner can paper-trade from day one. The ladder shapes what
the app *suggests*, not what the learner is allowed to look at.

---

## Part 13 — From concepts to work

### Shared pieces (build once, used by every concept)

| ID | Piece | What it is |
|---|---|---|
| S-1 | Detector framework | A `Detector` contract: rule id and version, parameters, evidence, **confirmation time**; the "never across a session break or contract roll" rule shared; `detected_setup` generalised to every kind; on-demand scanning over real data (DEC-026). |
| S-2 | Concept registry | The concepts of this file as data: id, level, detectability, prerequisites, lesson text, linked rules. |
| S-3 | Overlay renderer | Zones (rectangle + CE line), levels (labelled lines), swing markers, session shading — drawn from detected setups, never past the replay cursor. |
| S-4 | Drill engine | Modes A, B and C for any detector, real data only, explanations rendered from evidence. Generalises the FVG drill. |
| S-5 | Learning state | Attempts, per-concept rating, spaced repetition, unlocks (INVARIANT 8b). |
| S-6 | Evidence engine | Batch statistics per rule over the imported history, with sample size and date range; worded as description, never as edge. |
| S-7 | Practice for ICT | Replay paper-trading mode; plan fields for model, DOL, bias, killzone; checklist checked at the decision moment; review overlay. |
| S-8 | New York clock | Session calendar in New York time with DST tests, including the US/EU mismatch weeks. |

### Concept → work

| Concepts | Detector | Overlay | Drills | Practice / evidence | Task |
|---|---|---|---|---|---|
| F-3, F-4, ICT-11, ICT-50 – ICT-55 | `levels`, `opens`, `sessions` | session strip, killzone shading, level lines | A, C | levels in plan context | T7 |
| ICT-21, ICT-42, ICT-43 | — | Fibonacci with ICT preset, zone + CE | A | drawn by the learner | T8 |
| (all) | S-1, S-2, S-3 | — | — | — | T9 |
| ICT-20 – ICT-28 | `fvg` (re-measured), `fvg-state`, `ifvg`, `bpr`, `vi`, `void`, `opening-gap` | zones with state | A, B, C | G: fill rates, the ORG claim | T10 |
| ICT-01 – ICT-06 | `swing`, `swing-tier`, `structure`, `displacement`, `mss` | swing markers, structure labels | A, B, C | — | T11 |
| ICT-10, ICT-12 – ICT-14 | `liquidity`, `eqhl`, `sweep` | liquidity lines, taken/resting | A, B, C | G: sweep outcomes | T12 |
| (all D/R) | — | — | S-4 engine, S-5 state | — | T13, T16 |
| ICT-15 – ICT-17 | ERL/IRL labelling, obstacle count | labels | B, C | G | T15 |
| ICT-30 – ICT-37 | `ob`, `breaker`, `mitigation`, `rejection`, `unicorn` | zones | A, B, C | G: reaction rates | T17 |
| ICT-40 – ICT-44 | `dealing-range`, `pd`, `ote`, `stdev` | range, equilibrium, OTE zone | A, B, C | premium/discount fact in review | T18 |
| ICT-53, ICT-54, ICT-57, ICT-62 – ICT-65 | `po3`, `judas`, `daily-profile`, `weekly-profile`, `ipda` | windows, labels | B, C | G: profile distributions | T19 |
| ICT-66 | `smt` | paired charts | A, C | needs ES import | T20 |
| (all) | — | — | — | S-6 Evidence views | T21 |
| ICT-60, ICT-61, ICT-70 – ICT-78 | model compositions | model checklist | D | checklist at decision time | T22 |
| ICT-80 – ICT-84 | — | — | E, F | scorecard, journal stats | T23 – T28 |

Task numbers refer to the ordered list in [`PATH.md`](PATH.md) → NEXT UP.

---

## Part 14 — Data each concept needs

| Need | Concepts | Free Massive plan (DEC-042) | Later |
|---|---|---|---|
| NQ one-minute bars | almost all | ✅ about 2 years back | Databento importer (built, DEC-040) for 5–10 years |
| Continuous front contract | all | ⚠️ per-contract tickers only — Tradeez picks the front contract itself | Databento's `NQ.v.0` |
| ES one-minute bars | ICT-66 SMT | ✅ same plan, separate import | — |
| One-second bars | precise practice fills (DEC-008) | ❌ (from $29/month) — fills fall back to minute bars and report `AMBIGUOUS` more often | Massive Starter or Databento |
| Daily history 60+ days | ICT-57, ICT-60 | ✅ aggregated from minutes | — |
| Economic calendar | ICT-35, ICT-56 | ❌ no source chosen (Q-15) | — |
| Dollar index, yields | ICT-67 | ❌ out of scope | — |

Two years of one-minute NQ is roughly 700,000 bars and, at 50,000 bars per request and 5 requests a
minute, a few minutes of importing. It covers every concept in Levels 0–8 except the news-linked ones.

---

## Part 15 — Open questions

| ID | Question | Recommendation |
|---|---|---|
| Q-13 | Which ICT period is the default reading — the 2022 mentorship, or the more detailed 2016–17 material? | The 2022 mentorship for models and time windows (public, consistent, checkable); the PD-array definitions from the earlier material, versioned. |
| Q-14 | Which killzone table is `sessions-1.0.0`? | The index-futures version in ICT-51 (New York AM 08:30–11:00). |
| Q-15 | Where do historical news dates come from? | Decide when Level 6 is built; until then shade the fixed release times only. |
| Q-08 | Croatian or English UI? (existing) | Unchanged — Fabio. Concept names stay English either way: ICT's vocabulary is English. |
| Q-09 | Jury display rights (existing) | Massive's free plan is "individual use"; ask before showing real data to the jury. |

---

## Sources

The primary source is Huddleston's own teaching — his YouTube channel *The Inner Circle Trader*,
including the 2022 mentorship playlist — which this file paraphrases rather than quotes. Time windows
and gap definitions were cross-checked against community summaries; these are not ICT's own and
sometimes disagree, which is why every window above is versioned:

- [ICT macro times — innercircletrader.net (community tutorial)](https://innercircletrader.net/tutorials/ict-macro-time-based-strategy/)
- [ICT killzones — innercircletrader.net (community tutorial)](https://innercircletrader.net/tutorials/master-ict-kill-zones/)
- [ICT macros — LuxAlgo concept library](https://www.luxalgo.com/library/concept/ict-macros/)
- [NWOG — innercircletrader.net](https://innercircletrader.net/tutorials/ict-new-week-opening-gap-nwog/) · [NDOG](https://innercircletrader.net/tutorials/ict-new-day-opening-gap-ndog/)
- [ICT opening range gap — TradingView community indicator notes](https://www.tradingview.com/script/C1G95phi-ICT-Opening-Range-Gap/)
- [Breaker block](https://www.luxalgo.com/library/concept/breaker-block/) · [Mitigation block — LuxAlgo](https://www.luxalgo.com/library/concept/mitigation-block/)
- [ICT weekly profiles — OpoFinance summary](https://blog.opofinance.com/en/ict-weekly-profiles/)
- Turtle soup: Linda Bradford Raschke and Laurence Connors, *Street Smarts* (1995).
- Contract specifications: CME Group, E-mini Nasdaq-100 and Micro E-mini Nasdaq-100 futures.
