# Learn audit (T44)

*Drafted by Claude 2026-10-26; **decided by Fabio 2026-10-26: all 72 proposals accepted** on the decision page
(https://claude.ai/artifact/R1tCrpfMjZR6D5VrTeoj8s), none changed — "i bascially accepted all of them i think you did
good by decising". DEC-081. Nothing in the curriculum changes in T44 — T45 applies the decisions.*

Fabio: *"I want you do remove every single page that doesnt have Real NQ example or a drill, of course before you do that
you will list them to me and we will together check if they are worth implementing on a another tab or if they will be
completely removed."*

**How this was made.** The facts — level, rule built, real NQ examples, drill, clock rule, what the page shows — are read
from the running app (`/api/concepts`, `/api/concepts/{id}/lesson`, `/api/drills/kinds`), not typed by hand. The
importance, the proposal and its reason are Claude's, one per row, for Fabio to accept or change in the last column.
The curriculum has **72** concepts (F-1…F-5 and ICT-01…ICT-84 with gaps in the numbering; the plan's "84 rows"
counted the highest number, not the rows).

## The proposal in numbers

| | Concepts | Lessons after |
|---|---|---|
| Keep — has a drill | 13 | 13 |
| Keep — needs a drill (to build in T45/T46) | 12 | 12 |
| Open reference card (never locked, no drill) | 8 | 8 |
| Merge into another lesson (its useful part becomes a section) | 26 | 0 |
| Remove | 13 | 0 |
| **Total** | **72** | **33** |

From 72 pages to 33: 25 lessons a learner works through, each with a drill, and 8
cards to read. **The drills to build**, most important first: IFVG (ICT-23), BSL/SSL (ICT-10), order block (ICT-30),
premium/discount (ICT-41), stop placement (ICT-80), daily bias (ICT-61), the Silver Bullet and 2022 model checklists
(ICT-71, ICT-70), then breaker (ICT-31), OTE (ICT-42), unicorn (ICT-36), PO3 (ICT-62). Until a drill exists its lesson
can still be read; the lock after it (R-14) waits for it.

## Proposed topics (for T45), in the order a learner meets them

Fabio: *"when person is trading, they will pick a concept, a strategy and stick with it"*. Each topic is a short path
ending in something tradeable; inside it, the most important lesson first. A new learner starts at **Structure** —
every other topic reads swings and breaks — and the Gaps topic opens on the FVG and IFVG straight after it.

1. **Structure** — ICT-01 Swing high / swing low (+ ICT-02) · ICT-03 Market structure and order flow · ICT-04 Displacement · ICT-06 BOS / CHoCH (SMC aliases) (+ ICT-05)
2. **Liquidity** — ICT-10 Buy-side and sell-side liquidity (+ ICT-11) · ICT-13 Liquidity sweep vs run (+ ICT-14, ICT-74) · ICT-16 Draw on liquidity (+ ICT-17) · ICT-15 External vs internal range liquidity · ICT-12 Relative equal highs / lows
3. **Gaps & imbalances** — ICT-20 Fair value gap (BISI / SIBI) (+ ICT-21, ICT-22, ICT-26) · ICT-23 Inversion FVG · ICT-25 Volume imbalance · ICT-24 Balanced price range *(card)* · ICT-27 New week / new day opening gaps (NWOG, NDOG) *(card)* · ICT-28 Opening range gap (RTH gap) *(card)* (+ ICT-78)
4. **Premium & discount** — ICT-41 Premium, discount, equilibrium (+ ICT-40) · ICT-42 Optimal trade entry (OTE) (+ ICT-43, ICT-72) · ICT-44 Standard deviation projections *(card)*
5. **Blocks** — ICT-30 Order block, mean threshold · ICT-31 Breaker block (+ ICT-32, ICT-75) · ICT-36 Unicorn (breaker + FVG) (+ ICT-73) · ICT-33 Rejection block *(card)* · ICT-37 The PD array matrix *(card)*
6. **Bias** — ICT-61 Daily bias (+ ICT-60) · ICT-62 Power of three (AMD) (+ ICT-63, ICT-76) · ICT-66 SMT divergence (NQ vs ES) · ICT-64 Daily profiles *(card)* (+ ICT-65)
7. **Models** — ICT-71 Silver Bullet model · ICT-70 The 2022 model
8. **Risk & management** — ICT-83 Risk rules and drawdown (+ F-5) · ICT-80 Stop placement (+ ICT-81) · ICT-82 Trade management
9. **Time & sessions (reference)** — ICT-50 Day boundaries and opening prices *(card)* (+ ICT-51, ICT-52, ICT-53, ICT-54)

## Every concept

Importance: **high** — a trader uses it on most days; **medium** — useful, not daily; **low** — rarely or never needed
on NQ intraday. "Fabio's decision" is what he chose on the decision page.

| ID | Name | Level | Rule built | Real NQ examples | Drill | Clock rule | The page today | Importance | Proposal | Why | Fabio's decision |
|---|---|---|---|---|---|---|---|---|---|---|---|
| F-1 | Candle anatomy | L0 | — | no | — | — | summary | low | **remove** | Level 0 is removed (Fabio: "nonsense"). Wick and body move into the gap lessons' "What it is", where they matter. || accepted |
| F-2 | Timeframes and top-down reading | L0 | — | no | — | — | summary | low | **remove** | Level 0. Its one useful line — read the higher timeframe first — moves into Daily bias (ICT-61). || accepted |
| F-3 | NQ, the contract | L0 | — | no | — | — | summary | low | **remove** | Level 0. Tick size and value ($5 NQ, $0.50 MNQ) move into Risk rules (ICT-83), where they are used. || accepted |
| F-4 | The New York clock | L0 | — | no | — | — | summary | low | **remove** | Level 0. The New York clock is the "Time & sessions" reference card (ICT-50). || accepted |
| F-5 | Risk in R, position size | L0 | — | no | — | — | summary | medium | **merge into ICT-83** | Risk in R is the opening of Risk rules; its drills already exist (position size, streak, recovery). || accepted |
| ICT-01 | Swing high / swing low | L1 | SWING | yes (3) | Swing highs; Swing lows | — | summary · rule swing-1.0.0 · data tiles · 3 NQ examples · drill button | high | **keep (has a drill)** | Everything else is built on swings. Two drills (highs, lows). || accepted |
| ICT-02 | Short-, intermediate-, long-term highs and lows | L1 | SWING_TIER | yes (3) | — | — | summary · rule swing-tier-1.0.0 · data tiles · 3 NQ examples | medium | **merge into ICT-01** | Short/intermediate/long-term swings are "which swings matter" — a section of the swing lesson, not a page. || accepted |
| ICT-03 | Market structure and order flow | L1 | STRUCTURE | yes (3) | Market structure | — | summary · rule structure-1.0.0 · data tiles · 3 NQ examples · drill button | high | **keep (has a drill)** | Reading higher highs and lower lows is the first thing a trader does on a chart. || accepted |
| ICT-04 | Displacement | L1 | DISPLACEMENT | yes (3) | Displacement | — | summary · rule displacement-1.0.0 · data tiles · 3 NQ examples · drill button | high | **keep (has a drill)** | Displacement is what makes a gap or a structure break count. || accepted |
| ICT-05 | Market structure shift (MSS) | L1 | MSS | yes (3) | — | — | summary · rule mss-1.0.0 · data tiles · 3 NQ examples | high | **merge into ICT-06** | MSS, BOS and CHoCH are the same question — did structure break, with or against the trend? One lesson, the existing BOS/CHoCH drill. || accepted |
| ICT-06 | BOS / CHoCH (SMC aliases) | L1 | BOS_CHOCH | yes (3) | Break of structure or change of character | — | summary · rule structure-1.0.0 · data tiles · 3 NQ examples · drill button | high | **keep (has a drill)** | Becomes "Structure breaks: BOS, CHoCH, MSS" with ICT-05 in it. || accepted |
| ICT-10 | Buy-side and sell-side liquidity | L2 | LIQUIDITY | yes (3) | — | — | summary · rule liquidity-1.0.0 · data tiles · 3 NQ examples | high | **keep — needs a drill** | Where the stops rest is ICT's whole premise. Drill: mark the swings holding buy-side / sell-side liquidity. || accepted |
| ICT-11 | Reference highs and lows (PDH/PDL, PWH/PWL, sessions) | L2 | — | no | — | yes | summary · chart clock layer | high | **merge into ICT-10** | Fabio: "serves absolutely no purpose" as a page. PDH/PDL and session highs are the most obvious BSL/SSL — a section of ICT-10, and they stay on the chart. || accepted |
| ICT-12 | Relative equal highs / lows | L2 | EQHL | yes (3) | Relative equal highs and lows | — | summary · rule eqhl-1.0.0 · data tiles · 3 NQ examples · drill button | medium | **keep (has a drill)** | Equal highs and lows are the textbook liquidity pool. || accepted |
| ICT-13 | Liquidity sweep vs run | L2 | SWEEP | yes (3) | Liquidity sweeps; Sweep or run | — | summary · rule sweep-1.0.0 · data tiles · 3 NQ examples · drill button | high | **keep (has a drill)** | Sweep vs run decides reversal or continuation. Two drills. || accepted |
| ICT-14 | Turtle soup (pattern) | L2 | TURTLE_SOUP | yes (3) | — | — | summary · rule sweep-1.0.0 · data tiles · 3 NQ examples | medium | **merge into ICT-13** | Turtle soup is a sweep traded as a reversal — the "How it is traded" of the sweep lesson. || accepted |
| ICT-15 | External vs internal range liquidity | L2 | — | no | External or internal liquidity next | — | summary · drill button | high | **keep (has a drill)** | ERL → IRL → ERL is how ICT reads where price goes next. Has a prediction drill; no chart examples (no marking rule) — the drill is the example. || accepted |
| ICT-16 | Draw on liquidity | L2 | — | no | Which liquidity is taken first | — | summary · drill button | high | **keep (has a drill)** | Draw on liquidity is the target of every trade. Prediction drill. || accepted |
| ICT-17 | Low / high resistance liquidity runs | L2 | — | no | — | — | summary | low | **merge into ICT-16** | Low/high-resistance runs are the "what is on the way" already shown in the draw-on-liquidity drill. || accepted |
| ICT-20 | Fair value gap (BISI / SIBI) | L3 | FVG | yes (3) | Fair value gaps | — | summary · rule fvg-1.0.0 · data tiles · 3 NQ examples · drill button | high | **keep (has a drill)** | The FVG is the core entry array. || accepted |
| ICT-21 | Consequent encroachment | L3 | — | no | — | — | summary | medium | **merge into ICT-20** | Consequent encroachment is the gap's midpoint — a line in the FVG lesson. || accepted |
| ICT-22 | FVG lifecycle (tapped, CE, filled) | L3 | FVG_STATE | yes (3) | — | — | summary · rule fvg-state-1.0.0 · data tiles · 3 NQ examples | medium | **merge into ICT-20** | Tapped, CE, filled is what happens to a gap — a section of the FVG lesson. || accepted |
| ICT-23 | Inversion FVG | L3 | IFVG | yes (3) | — | — | summary · rule ifvg-1.0.0 · data tiles · 3 NQ examples | high | **keep — needs a drill** | Fabio: "IFVG is much much much more important". Has a rule and real examples; first new drill to build: spot the inversion. || accepted |
| ICT-24 | Balanced price range | L3 | BPR | yes (3) | — | — | summary · rule bpr-1.0.0 · data tiles · 3 NQ examples | medium | **open reference card** | Balanced price range has a rule and examples, but is a refinement of two gaps — read, not drilled. || accepted |
| ICT-25 | Volume imbalance | L3 | VI | yes (3) | Volume imbalances | — | summary · rule vi-1.0.0 · data tiles · 3 NQ examples · drill button | medium | **keep (has a drill)** | Volume imbalance is a gap's small cousin and NQ prints many. || accepted |
| ICT-26 | Gap and liquidity void | L3 | VOID | yes (3) | — | — | summary · rule void-1.0.0 · data tiles · 3 NQ examples | low | **merge into ICT-20** | Gaps and liquidity voids are the FVG's bigger cousins — a paragraph in the FVG lesson. || accepted |
| ICT-27 | New week / new day opening gaps (NWOG, NDOG) | L3 | OPENING_GAP | yes (3) | — | — | summary · rule opening-gap-1.0.0 · data tiles · 3 NQ examples | medium | **open reference card** | NWOG/NDOG are levels ICT watches all week; a rule and examples exist, a drill would add little. || accepted |
| ICT-28 | Opening range gap (RTH gap) | L3 | ORG | yes (3) | — | — | summary · rule org-1.0.0 · data tiles · 3 NQ examples | low | **open reference card** | The RTH opening gap matters on NQ at 9:30; rule and examples exist. || accepted |
| ICT-29 | Implied fair value gap | L3 | — | no | — | — | summary | low | **remove** | Described only: no rule, no example, no drill. || accepted |
| ICT-30 | Order block, mean threshold | L4 | OB | yes (3) | — | — | summary · rule ob-1.0.0 · data tiles · 3 NQ examples | high | **keep — needs a drill** | The order block is ICT's second entry array after the FVG. Rule and examples exist; drill: spot it. || accepted |
| ICT-31 | Breaker block | L4 | BREAKER | yes (3) | — | — | summary · rule breaker-1.0.0 · data tiles · 3 NQ examples | medium | **keep — needs a drill** | The breaker is the failed order block traded back. Rule and examples exist. || accepted |
| ICT-32 | Mitigation block | L4 | MITIGATION | yes (3) | — | — | summary · rule mitigation-1.0.0 · data tiles · 3 NQ examples | low | **merge into ICT-31** | A mitigation block is a breaker without the sweep — one lesson, the difference in a sentence. || accepted |
| ICT-33 | Rejection block | L4 | REJECTION | yes (3) | — | — | summary · rule rejection-1.0.0 · data tiles · 3 NQ examples | low | **open reference card** | Rejection blocks are rarely traded on their own; rule and examples exist. || accepted |
| ICT-34 | Propulsion block | L4 | — | no | — | — | summary | low | **remove** | Described only, a judgement call; nothing to show or drill. || accepted |
| ICT-35 | Vacuum block | L4 | — | no | — | — | summary | low | **remove** | Described only (news-candle voids); the News tab covers when not to trade. || accepted |
| ICT-36 | Unicorn (breaker + FVG) | L4 | UNICORN | yes (3) | — | — | summary · rule unicorn-1.0.0 · data tiles · 3 NQ examples | medium | **keep — needs a drill** | Unicorn = breaker + FVG overlapping. Rule and examples exist; with the Unicorn model (ICT-73) in it. || accepted |
| ICT-37 | The PD array matrix | L4 | — | no | — | — | summary | low | **open reference card** | The PD array matrix is the map of all the arrays — useful once, as a reference. || accepted |
| ICT-40 | Dealing range | L5 | DEALING_RANGE | yes (3) | — | — | summary · rule dealing-range-1.0.0 · data tiles · 3 NQ examples | medium | **merge into ICT-41** | The dealing range is what premium and discount are measured in — one lesson. || accepted |
| ICT-41 | Premium, discount, equilibrium | L5 | PD | yes (3) | — | — | summary · rule pd-1.0.0 · data tiles · 3 NQ examples | high | **keep — needs a drill** | Buy in discount, sell in premium. Drill: is price in premium or discount of this range? || accepted |
| ICT-42 | Optimal trade entry (OTE) | L5 | OTE | yes (3) | — | — | summary · rule ote-1.0.0 · data tiles · 3 NQ examples | medium | **keep — needs a drill** | OTE is the 62–79% retracement entry. Rule and examples exist. || accepted |
| ICT-43 | ICT's Fibonacci levels and targets | L5 | — | no | — | — | summary | low | **merge into ICT-42** | ICT's Fibonacci levels are the OTE lesson's numbers. || accepted |
| ICT-44 | Standard deviation projections | L5 | STDEV | yes (3) | — | — | summary · rule stdev-1.0.0 · data tiles · 3 NQ examples | medium | **open reference card** | Standard-deviation targets: rule and examples exist; a target tool, read once. || accepted |
| ICT-50 | Day boundaries and opening prices | L6 | — | no | — | yes | summary · chart clock layer | medium | **open reference card** | Becomes one "Time & sessions" card with ICT-51…54: the day's opens, sessions, killzones, Silver Bullet hours, macros. Fabio: killzones do not deserve their own tab. The chart's clock layers stay. || accepted |
| ICT-51 | Sessions and session ranges | L6 | — | no | — | yes | summary · chart clock layer | medium | **merge into ICT-50** | Sessions — a section of the Time & sessions card. || accepted |
| ICT-52 | Killzones | L6 | — | no | — | yes | summary · chart clock layer | medium | **merge into ICT-50** | Killzones — Fabio: "not so important that it deserves another tab". || accepted |
| ICT-53 | Silver Bullet windows | L6 | — | no | — | yes | summary · chart clock layer | medium | **merge into ICT-50** | Silver Bullet hours — a section of the card; the model is ICT-71. || accepted |
| ICT-54 | Macros | L6 | — | no | — | yes | summary · chart clock layer | low | **merge into ICT-50** | Macros — a line in the card. || accepted |
| ICT-55 | CBDR, Asian range, Flout | L6 | — | no | — | yes | summary · chart clock layer | low | **remove** | CBDR / Asian range / Flout: drafted only, forex-centred. || accepted |
| ICT-56 | News times | L6 | — | no | — | — | summary | low | **remove** | News times now live on the News tab (T40). || accepted |
| ICT-57 | IPDA data ranges, quarterly shifts | L6 | — | no | — | yes | summary · chart clock layer | low | **remove** | IPDA data ranges and quarterly shifts are too abstract for a day trader; the chart layer can stay. || accepted |
| ICT-60 | Higher-timeframe narrative | L7 | — | no | — | — | summary | medium | **merge into ICT-61** | Higher-timeframe narrative is how daily bias is formed — one lesson. || accepted |
| ICT-61 | Daily bias | L7 | — | no | — | — | summary | high | **keep — needs a drill** | Bias is the first decision of the day. Drill (predict): from yesterday and the overnight, does today trade to the high or the low first? || accepted |
| ICT-62 | Power of three (AMD) | L7 | PO3 | yes (3) | — | — | summary · rule po3-1.0.0 · data tiles · 3 NQ examples | medium | **keep — needs a drill** | Power of three (accumulation, manipulation, distribution) with the Judas swing in it; rule and examples exist. || accepted |
| ICT-63 | Judas swing | L7 | JUDAS | yes (3) | — | — | summary · rule judas-1.0.0 · data tiles · 3 NQ examples | medium | **merge into ICT-62** | The Judas swing is PO3's manipulation leg. || accepted |
| ICT-64 | Daily profiles | L7 | DAILY_PROFILE | yes (3) | — | — | summary · rule daily-profile-1.0.0 · data tiles · 3 NQ examples | medium | **open reference card** | Daily profiles — rule and examples; with weekly profiles in it. || accepted |
| ICT-65 | Weekly profiles | L7 | WEEKLY_PROFILE | yes (2) | — | — | summary · rule weekly-profile-1.0.0 · data tiles · 2 NQ examples | low | **merge into ICT-64** | Weekly profiles — the same idea a week long. || accepted |
| ICT-66 | SMT divergence (NQ vs ES) | L7 | SMT | yes (3) | SMT divergence | — | summary · rule smt-1.0.0 · data tiles · 3 NQ examples · drill button | medium | **keep (has a drill)** | SMT divergence NQ vs ES; the one two-market drill. || accepted |
| ICT-67 | Intermarket (DXY, yields) | L7 | — | no | — | — | summary | low | **remove** | Out of scope: no DXY or yields data. || accepted |
| ICT-70 | The 2022 model | L8 | — | no | — | — | summary | high | **keep — needs a drill** | Fabio: a trader "will pick a concept, a strategy and stick with it". The 2022 model is the strategy that ties the lessons together. Drill: step through the checklist on a real setup. || accepted |
| ICT-71 | Silver Bullet model | L8 | — | no | — | — | summary | high | **keep — needs a drill** | Silver Bullet: the simplest full model on NQ (an FVG in the 10–11 window). Same checklist drill. || accepted |
| ICT-72 | OTE model | L8 | — | no | — | — | summary | low | **merge into ICT-42** | The OTE model is the OTE lesson's "How it is traded". || accepted |
| ICT-73 | Unicorn model | L8 | — | no | — | — | summary | low | **merge into ICT-36** | The Unicorn model is the Unicorn lesson's "How it is traded". || accepted |
| ICT-74 | Turtle soup model | L8 | — | no | — | — | summary | low | **merge into ICT-13** | The turtle soup model is the sweep lesson's "How it is traded" (with ICT-14). || accepted |
| ICT-75 | Breaker / mitigation entry | L8 | — | no | — | — | summary | low | **merge into ICT-31** | Breaker / mitigation entry is the breaker lesson's "How it is traded". || accepted |
| ICT-76 | Session open reversals (London, New York) | L8 | — | no | — | — | summary | low | **merge into ICT-62** | Session-open reversals are the Judas swing traded — part of PO3. || accepted |
| ICT-77 | Market maker buy / sell models | L8 | — | no | — | — | summary | low | **remove** | Market maker models: described only, judgement throughout. || accepted |
| ICT-78 | Opening range gap model | L8 | — | no | — | — | summary | low | **merge into ICT-28** | The opening range gap model is the ORG card's "How it is traded". || accepted |
| ICT-80 | Stop placement | L9 | — | no | — | — | summary | high | **keep — needs a drill** | Fabio's standard: "Stops are placed on recent high and lows after a liquidity sweep". Drill: pick where the stop goes. || accepted |
| ICT-81 | Targets and partials | L9 | — | no | — | — | summary | high | **merge into ICT-80** | Stops and targets are one decision — "Stops & targets", drilled together. || accepted |
| ICT-82 | Trade management | L9 | — | no | Manage an open trade | — | summary · drill button | high | **keep (has a drill)** | Hold, breakeven or half off, graded by real price. || accepted |
| ICT-83 | Risk rules and drawdown | L9 | — | no | risk drills (3) | — | summary | high | **keep (has a drill)** | The risk drills (position size, streak, recovery) are its drills; prop-firm evaluations live on this. || accepted |
| ICT-84 | Journaling and review | L9 | — | no | — | — | summary | low | **remove** | Journaling is what the Practice tab does (journal, scorecard, review); a lesson about it repeats it. || accepted |
