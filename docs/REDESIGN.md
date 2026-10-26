# REDESIGN.md — M6, the redesign: the complete plan

> **Purpose.** Everything needed to carry out the redesign Fabio asked for on 2026-10-23 without the
> conversation it came from. Written to survive a lost context: if you are a new session, this file is the
> plan — follow it, do not re-derive it, do not drift from it. PATH.md's M6 table is only an index into this.
>
> **Fabio, 2026-10-23:** "it is time that we change the damn design of the app, it looks extremely unpolished
> and vibe coded" … "Write it in depth and properly … once you lose context you will drift away from our plan,
> thats why you need to make it easier for yourself and write everything DETAILED" … "EXTREMELY DETAILED AND
> FULL FOOL PROOF PLAN".

---

## 0. How to resume (read this first, every time)

1. Follow PATH.md's PROTOCOL (read PATH.md first; update PATH.md, DOCUMENTATION.md, BUGS.md, DECISIONS.md
   after work). Then read this file top to bottom.
2. Look at **§1 Status**. Take the first task that is not ✅ and whose "Blocked by" is done. T44 (the Learn
   audit) may be done at any point — it waits on Fabio, not on code.
3. **Never contradict §3 (agreed decisions).** They are Fabio's answers. If a task seems to need something
   different, stop and ask him. A new choice made while building goes into **§10 (decision log)** here and
   into DECISIONS.md.
4. Each task in **§6** has: goal, Fabio's words, scope, steps, files, backend, tests, a done checklist. Work
   through the steps in order; tick the checklist in §1's notes column when merged.
5. Look at the references before building a screen: **Lovable** in `docs/redesign/lovable/` (27 JPEGs, its
   index in `index.json`), **the app before** in `docs/redesign/before/` (15 PNGs). The Read tool shows them.
   Lovable's source: <https://github.com/FabioZetovic/futures-flow-studio> (clone it to the scratchpad; to run
   it: `npm install`, then `npm run dev -- --port 5190 --strictPort` — through a temporary
   `.claude/launch.json` entry started with `preview_start`, removed again afterwards; `launch.json` is
   tracked).
6. Running Tradeez: Docker Desktop on, `docker start tradeez-postgres`; the backend `cd backend;
   .\mvnw.cmd spring-boot:run` (port 8080; `preview_start tradeez-backend` uses 8081), the frontend
   `npm run dev --prefix frontend` (proxies `/api` to 8080, or to `VITE_API_PROXY_TARGET`). The local test
   account is in comments in `backend/.env.example` — use it in the local app only, never repeat it in chat.
7. Screenshots at full resolution: the browser pane only returns ~800 px images. Use `puppeteer-core` with the
   installed Chrome (`C:/Program Files (x86)/Google/Chrome/Application/chrome.exe`), viewport 1920×1080 and
   1366×768, scroll to the top before full-page shots (the sticky header otherwise lands mid-image). Scripts
   that did this: `capture.cjs` / `lovable.cjs` patterns — sign in by typing into `input[type=email]` /
   `input[type=password]`, press Enter; wait for `load`, not `networkidle0` (Vite keeps a socket open).
   Save each finished task's screens to `docs/redesign/after/<task>/`.
8. Branches: one per task from `master` (`feat/t36-shell`, `feat/t37-overlays`, …), merged `--no-ff` only when
   Fabio says "merge", after: tests written with the code, mutation checks (one at a time, a passing baseline
   first, only failing tests count as kills; anchor check first), full suites, docs, a fresh clone with no
   `.env`. Stage files by name; grep staged changes for API keys and the test password before committing.
   **Do not touch the `feat/design` branch** (Fabio: "dont touch it"); it only holds the before-PDF.

---

## 1. Status

| Task | Name | Blocked by | State | Branch / merge | Notes |
|---|---|---|---|---|---|
| T36 | Theme and shell | — | ✅ merged 2026-10-23 | `feat/t36-shell` | DEC-078. Router (/chart /learn /drills/:kind? /practice /help /sign-in /sign-up; / → /chart until T39), AppShell + AccountMenu (shell/), footer short line + Read more, sign-in card, /help page, kit restyled + PageHeader + Glass (the rest of the kit with T39+, see §10). Screenshots docs/redesign/after/t36/. 398 tests, 14/14 mutants |
| T37 | Chart overlays in the paint step (BUG-095) | — | ✅ merged 2026-10-23 | `feat/t37-overlays` (from `feat/t36-shell`) | BUG-095 fixed: `ChartAdapter.addLayer` (a series primitive) + `useChartLayer`; clock and example band under the candles, setups over, drawings and drill marks on top; `useOverlayCanvas` deleted. BUG-096 (undo/delete saved one step late, older than T37) found and fixed. Screenshots docs/redesign/after/t37/. 404 tests, 17/17 mutants |
| T38 | Chart tab | T36, T37 | ✅ merged 2026-10-26 | `feat/t38-chart` | `components/chart/ChartPage.tsx` + `LayersMenu.tsx` (glass, search, presets, 5 groups, 40 layers, chips), `useChartLayers` (practice uses it too), drawing bar on lucide icons, price change since the previous session close, no instrument/beside/SMT. Screenshots docs/redesign/after/t38/. 412 tests, 18/18 mutants |
| T39 | Home | T36 (news card: T40) | ✅ merged 2026-10-26 | `feat/t39-home` (from `feat/t38-chart`) | `components/home/HomePage.tsx` at `/`: greeting, four step cards with real progress (definitions agreed with Fabio), Up next, Due for review (rings), Practice so far, the latest NQ session still; lessons at `/learn/:conceptId`; News card waits for T40. Screenshots docs/redesign/after/t39/. 421 tests, 16 mutants |
| T40 | News | T36 | ✅ merged 2026-10-26 | `feat/t40-news` | DEC-079: our own calendar — FRED release dates (8 releases) + the Fed's FOMC list, refreshed every 6 h, stale/FOMC warnings, FRED notice; News page `/news`, Home "News today", nav. Forex Factory ruled out by its terms. Screenshots docs/redesign/after/t40/ |
| T41 | Practice home | T36 | ✅ merged 2026-10-26 | `feat/t41-practice` | DEC-080: costs fixed at the standard (refused if sent, V29), start = session time + Start, four StatTiles (total, win rate, held to plan, costs paid), open sessions, per-trade journal (`GET /api/practice/trades`), more statistics behind a disclosure. Screenshots docs/redesign/after/t41/ |
| T42 | Practice session | T37, T38, T41 | ✅ merged 2026-10-26 | `feat/t42-session` | `/practice/:sessionId` full screen: drawing bar, chart card (top bar, layers, chart, Trades strip), 22rem trading column (score, plan, open trade, scorecard, review, replay sticky at its foot); practice layers its own and all off by default. Screenshots docs/redesign/after/t42/ |
| T43 | Drills | T36 | 🟢 built, waiting for "merge" | `feat/t43-drills` | `/drills` cards by topic (mode badge, time hint, the real record), `/drills/:kind` one click to a drill (eyebrow, title, task, chart, answer bar, result, Next drill), `/drills/risk/:slug` Lovable's risk card; no kind dropdown, no timeframe — the server draws 1m/5m/15m; `GET /api/drills/risk/kinds` for the risk records. Locks from T45 added later. Screenshots docs/redesign/after/t43/ |
| T44 | Learn audit (with Fabio) | Fabio's time | 🟢 decided — Fabio accepted all 72 (DEC-081) | `feat/t44-learn-audit` | docs/redesign/learn-audit.md: 72 rows from the API; proposed 13 keep + 12 keep-needs-a-drill + 8 reference cards, 26 merged, 13 removed; 9 topics |
| T45 | Learn rebuild | T36, T44 | ⬜ | | |
| T46 | Phones and tablets | T36–T45 | ⬜ | | |
| T47 | ES as a full instrument (BUG-094) | NQ "perfect" | ⬜ | | |
| T48 | Public landing page | T36 | ⬜ | | |
| T49 | AI chart assistant | everything | ⬜ | | last; budget decision |

---

## 2. Fabio's words (verbatim, 2026-10-23) — the source of truth

**His message:**

> First off i absolutely hate this chart that pops up once you load into the app. It is absolutely pointless,
> serves no purpose and just overwhelmes you. Since we are talking about chart all of the tools above are awful
> to look at. Setups Swings ITH/LTH... it is just too much of nothingness. They need to be grouped into sections
> for example on the sidebar. Also on the instrument tab if you switch to ES chart it literally doesnt display.
> So that whole section needs to be absolutely gone. Lobavle did that really well. I really like their Home
> page, and i like that the chart is not movable. On our website i would like to add news there as well. There
> is an old website called Forex Factory that people use for major news events to predict news candles and
> know when not to trade. Red and Orange folders are what traders follow and i want that in our website as
> well. Learn tab on our website is also trash it is all clustered and overwhelming to the user, pressing over
> all those tabs just causes anxiety. And trader doesnt even know which one to start. The whole foundations
> level 0 tab is nonsense. Every slide takes only 1/8th of a page, it doesnt provide any useful info or any
> value to the user. I want it removed. Also for example ICT-11 PDH PDL tab. It serves absolutely no purpose.
> When you press it, it says the name of the concept, it says one sentende under it that literally means
> absolutely nothing, It shows you a progress of a drill that DOESNT exist, and it shows REAL NQ EXAMPLES that
> dont exist also. Every tab that looks like that needs to be either removed or upgraded. Some of the stuff
> here is also not important for example i went into ICT-52 killzones. That is not so important that it
> deserves another tab. All it does is serve as a link to ICT-70 The 2022 model. and then if you press it it
> brings you to ict 70 essentially skipping 20 tabs of content. What i want to do is: when person is trading,
> they will pick a concept, a strategy and stick with it. They will not learn about IFVG and then read about
> timezones. IFVG is much much much more important concept so we need to capture that and let them know. I want
> you do remove every single page that doesnt have Real NQ example or a drill, of course before you do that you
> will list them to me and we will together check if they are worth implementing on a another tab or if they
> will be completely removed. ICT-80 Stop placement. ICT places stops beyond the structure that would prove the
> idea wrong: beyond the swing that made the sweep, beyond the FVG's first candle, or beyond an order block.
> this sentence from the app tells you absolutely nothing. Stops are placed on recent high and lows after a
> liquidity sweep for example. that sounds much more informing. I am afraid we will have to redo the whole
> learn section. Also i want it in a lovable style. With locked sessions, with progress measurements, and most
> importantly GROUPED TOGETHER, when stuff are grouped together it less overwhelmes the user and it has a
> higher chance of him coming back. Next is Practice section. We have a timeframe selection, does it matter
> which timeframe is it if you can literally change it once you enter the practice. Session time i like and i
> would like to keep it but day of the week is completely pointless, you said once day of the week is important
> bacuse on mondays market is more volatile than on wednesdays, what if that wednesday it is CPI day, then the
> market would be extremely volatile. I want to remove the day section entirely. This risk limit per trade i am
> not sure about also, i like the idea cause it suits real aplication but you are training and most people that
> will use the app will be on evaluation accounts from prop firms, lets keep it for now. But this whole section
> fees per round trip, slippage in ticks... needs to go, get removed and changed forever. What i like about
> lovable app is the stats, everything is made pretty, compact, informational, colourful but yeah most
> imprtantly not overwhelming and pretty to look at. I want this section copied fully. When you press practice
> on our app as i said it overwhelmes you completely. 45 indicators, chart is flashing different colors, colors
> of the theme are ugly, chart is absolutely too small, you cant dive into it. You know what i wanna do but this
> we will do at the fully end. I wanna add AI chatbot here that you cana sk questions about the chart but we
> will circle back to this after everything is done this will be a last task. I love our replay section, next
> bar, next 10 and play is done amazingly, it is just ugly, cause the interface is ugly. I love how lobavle
> looks, chart is fullscreen, you have a sidebar that lets you plan a trade, then gives you info about the trade
> like money risked, which R:R is it, whats the entry what the TP whats the SL, under that section i think we
> could put our replay section. And about the tools now i dont know where we should put them, i have an
> ambitious idea but idk if you can do it. Since my plan is to animate everything so it looks polished i was
> thinking of adding like a dropdown little expander that will be like liquid glass and when you hover it, the
> groupations of all indicators show up and you can then use them on the chart (chart is fullscreen, interface
> should be lovable styled). Also claude, the indicators on chart are hovering items, when you resize or move
> the chart, the indicators lag behind a lot, and it kills the experience. Our drills tab is also only
> functional nothing else, the time on a chart doesnt matter, fair value gap or session highs are the same on
> whichever timeframe you look at. Instead of listing a drill in a dropdown box why dont we make it like
> lovables, group them into sections and display them in a nice smooth interactive way. I like the lovables
> drills tab a lot. … it is mostly a lovables design but i dont like the colors, the colors remind me too much
> of Robinhood, so you can suggest color if you want or we will circle to that back anyways, you can pick a
> theme color and save it somewhere and give that color to every "theme colored" part of the app.

**His answers to the 16 questions** (numbering of the questions asked):

1. Lovable's code: "https://github.com/FabioZetovic/futures-flow-studio.git here is the github repo you can
   take anything from it".
2. Instruments: "I want it to be perfect on NQ first, after that we will add trading on ES too, they are
   similar but SMT divercence shows they are not the same so we will need it. also some people prefer executing
   on ES so they trade ES only, so later we will implement ES too … yes make the ES stay only when it comes to
   SMT lesson." Also: instruments "should not be visible as soon as person opens the chart".
3. News: "lets start with (a) a free source and then later we can upgrade to paid … i dont need past 2 years i
   just need upcoming ones. Same as in forex factory." On the Forex Factory weekly feed: "perfect, use that
   forex factory, it is perfect for what we need exactly."
4. News in a replay: "yes, just put high impact news" (no event name — it would reveal the date).
5. Learn grouping: "do it by topic".
6. Locks: "passing the drill unlocks the next session" (= the next lesson).
7. Rewriting lesson words and updating ICT_CONCEPTS.md with his review: "yes do it like that".
8. Costs: "costs paid, we sill need the R:R and the profitability of the trade, since ticks on NQ are fixed
   this shouldnt be a problem other than risk management which it is not my call to make for others".
9. Home: "yes the home page is the page after login, later we will make a home page proper that will come
   before the sign in pops up. for now make the home page same as lovable, maybe we should put high impact news
   on separate page" — agreed: Home shows today's and tomorrow's high-impact events, a News page the week.
10. The glass menu: "open it on click if they are too fiddly, the drawing toolbar can stay as slim vertical bar
    on the left."
11. Drill timeframe: "it can be random, whicherver you prefer, for drills timeframe is not as important as
    spotting the thing you are drilling".
12. Animation library: "sure you can".
13. Phones: "phone support is not out of scope but i am afraid that is way too complex to tackle straight at
    once, put that also on our task list but structure it so it should work out with all the other tasks".
14. "How it works" guide replaced by Home or kept: "both".
15. One branch or many: "whichever you prefer you are doing the coding".
16. Deadline: "we have like 8 months so we have A LOTT of time".

Suggestions he accepted: the indigo-violet accent "for now"; overlays drawn in the chart's paint step
("perfect, do that"); "i like every one of your other suggestions" — the lesson template (what it is, how to
spot it, how it is traded, real NQ examples, practise it), track/topic progress on Home, Learn and Drills linked
per concept, and listing every lesson page before deleting.

On the other agent (a design skill he tried the same day): "remove the files he made and bring the app to the
state it was before him" — done; nothing of it remains on `master`.

---

## 3. Agreed decisions (numbered — cite them in code comments and commits as R-1 … R-20)

- **R-1 NQ first.** Every screen works on NQ only until NQ is "perfect". ES returns as a full instrument in T47.
  Until then ES appears only inside the SMT lesson (ICT-66) and the SMT drill (`SMT_IDENTIFICATION`).
- **R-2 No instrument choice on the Chart tab or in Practice.** The Chart tab's Instrument and Beside selects
  go; the SMT layer goes from the Chart tab (it needs ES) until T47.
- **R-3 One theme colour by token.** `--color-accent`, indigo-violet `#7C6CFF` for now, used for everything
  "theme-coloured"; changeable in one place. Never used for prices or results.
- **R-4 Lovable's layout and polish, not its colours.** Its lime (`oklch(0.9 0.2 125)`) is not used anywhere.
- **R-5 Animated.** The `motion` library; reduced motion respected.
- **R-6 Home is the page after sign-in,** Lovable's Home on real data. A public landing page comes later (T48).
- **R-7 The "How it works" guide stays** as a help page (account menu), besides Home.
- **R-8 News from Forex Factory's own weekly feed** (`https://nfs.faireconomy.media/ff_calendar_thisweek.json`),
  upcoming events only, high (red) and medium (orange) impact; its terms checked before use; a paid source
  later. Home: today's and tomorrow's high-impact; the News page: the week.
- **R-9 In a practice replay** only "high-impact USD news at hh:mm", never the event's name — and only once a
  historical calendar exists (this feed has none). Not part of T40.
- **R-10 Chart tab full screen**, the slim drawing bar on the left, the 41 layers grouped by topic behind a
  **glass menu opened by click** (not hover), chips for the layers on.
- **R-11 The chart's overlays move with the candles**: drawn in Lightweight Charts' own paint step (T37).
- **R-12 Home's chart does not move** (no pan/zoom). The Chart tab's and Practice's charts stay fully
  interactive — Fabio pans and zooms them ("you cant dive into it", "when you resize or move the chart").
- **R-13 Learn grouped by topic**, with locks, progress, and a clear start. Level 0 removed. Every page without
  a real NQ example or a drill is listed (T44) and decided with Fabio before anything is removed.
- **R-14 A lesson unlocks when the drill of the lesson before it is passed.** Reference cards never lock.
- **R-15 Lesson words rewritten** in plain trader language (his stop example is the standard);
  `ICT_CONCEPTS.md` updated to match; Fabio reviews before it ships.
- **R-16 Practice starts with the session time only** (Any, Asia, London, New York AM, New York PM) — no
  timeframe (5-minute start; the chart switches inside) and no weekday. The risk limit stays "for now".
- **R-17 No cost settings.** Fees and slippage settings are removed for good; every trade uses one standard
  cost; results stay net (DEC-076) and "costs paid" is shown.
- **R-18 Practice session layout:** a full-screen chart; a right side panel to plan the trade (money at risk,
  R:R, entry, stop, target), with the replay controls (Next bar, Next 10, Play and its speeds) under it.
- **R-19 Drills:** no timeframe choice — picked at random among those the drill supports; grouped into
  sections of cards like Lovable's.
- **R-20 Phones later (T46),** but every earlier task builds layouts that can reflow. The AI chart assistant is
  the last task (T49).

---

## 4. Principles for every task

1. **Real data only.** Nothing invented on screen. Lovable's mock data and features the app does not have are
   listed in §5.9 — build them for real or leave them out.
2. **Behaviour is kept.** The redesign changes look and arrangement, not what the app computes: every rule
   version, no-peeking (DEC-077), net R (DEC-076), the fills (fill-1.4.0), the scorecard (scorecard-1.1.0),
   the review, calibration, the learning state. Their tests keep passing; when a test only checked a layout,
   rewrite it for the new layout, never weaken what it proves.
3. **One primary action per screen;** settings behind a click; overlays off until asked for (except a small
   default set on the Chart tab).
4. **Checked at 1920×1080 and 1366×768** with screenshots before every merge (§0.7).
5. **Reflow-ready (R-20):** no fixed pixel widths for content columns; side panels have a width but the main
   area flexes; nothing relies on hover alone (touch later).
6. **Lean (DEC-044 and the working agreement):** Tailwind only; new dependencies only where named in this plan
   (`motion`, `lucide-react`, a router, two `@fontsource` fonts); replaced components deleted in the same task;
   knip clean; newest versions (DEC-056).
7. **English only** (Q-08). Dates in one English format everywhere (not the machine locale).
8. **Accessible:** focus visible (accent ring), contrast AA for text, switches with `role="switch"` and
   `aria-checked`, menus closable with Esc, everything reachable by keyboard.

---

## 5. Lovable, screen by screen (measured from its code; images in `docs/redesign/lovable/`)

Its stack (for reference only — **we do not adopt it**): TanStack Start + Router, shadcn/Radix, `lucide-react`
icons, `tw-animate-css`, Tailwind v4. Its chart (`CandleChart.tsx`) is a hand-drawn SVG of invented candles
(`makeCandles`), which is why it "does not move". Its data (`data/content.ts`, `data/market.ts`) is invented.

### 5.1 Shell (every screen) — `AppShell.tsx`; images 03, 04, 05

- **Header:** sticky, `h-16`, `border-b`, `bg-background/80 backdrop-blur`; inner container
  `max-w-[1600px] px-6`, items `gap-8`.
- **Logo:** a 32×32 `rounded-lg` square in the primary colour with a white rising-line icon (stroke 2.5),
  then "Tradeez" in the display font, `text-lg font-bold tracking-tight`.
- **Nav:** Home, Learn, Drills, Practice, Chart — pills `rounded-lg px-3 py-1.5 text-sm font-medium`; active
  `bg-secondary text-foreground`, others `text-muted-foreground hover:text-foreground`.
- **Account:** right-aligned button — a 28 px circle with initials (`bg-accent text-accent-foreground`
  `text-xs font-bold`), the name, a chevron. Menu: a 240 px panel (`bg-popover p-1.5`), the email in `text-xs`
  muted, rows with icons (Profile, Practice check, Settings), "Sign out" in the destructive colour; it fades and
  slides in from the top.
- **Footer:** `border-t px-6 py-3 text-center text-xs text-muted-foreground`: "Educational practice on past
  data, not financial advice." + an underlined "Read more" that expands the full disclaimer (`max-w-3xl`).
- **Page background:** a radial glow of the primary colour at 14% in the top-left (`--gradient-glow`), fixed.

### 5.2 Home — `routes/index.tsx`; image 03

- Container `max-w-[1280px] px-6 py-10`.
- Date (`text-sm` muted, "Friday, 23 October"); H1 "Good morning, Fabio." (`font-display text-5xl font-bold
  tracking-tight`); a line under it (`text-lg` muted, `max-w-xl`): "Learn the rule, drill it, then practise it
  on a real day you can't see ahead of."
- **Four step cards** (`grid md:grid-cols-4 gap-4`), each `panel p-5`, hover lifts 2 px and lightens:
  "STEP n" (mono `text-xs` muted) and an icon in the primary colour (BookOpen, Crosshair, Target,
  CandlestickChart) on one row; the title (`text-2xl` display, `mt-6`); one line (`text-sm` muted); a 4 px
  progress bar (`bg-secondary`, primary fill). Learn 44%, Drills 30%, Practice 15%, Chart 0% (invented).
- **Three cards** (`grid lg:grid-cols-3 gap-4 mt-6`):
  - **Up next** — tinted with the accent (`bg-accent`), `p-6`: "UP NEXT" (`text-xs uppercase tracking-widest`),
    the concept's title (`text-2xl`), its plain sentence, "Continue lesson →".
  - **Due for review** — a clock icon + "DUE FOR REVIEW"; rows: a 36 px progress ring, the title, its code in
    mono, an arrow; each row links to the lesson.
  - **Practice so far** — "PRACTICE SO FAR"; the total R in mono `text-4xl` (green or red); "4 simulated trades
    · 75% followed plan"; "Open journal →" in the primary colour.
- **A chart card** (`mt-6`, full width, links to /chart): "NQ · 5m · latest data", the price (`text-xl` mono)
  and the change (green/red); a 224 px static chart.

### 5.3 Learn — `routes/learn.index.tsx`; image 06

- Container `max-w-[1100px] px-6 py-10`. **PageHeader** (shared): eyebrow "STEP 1" (primary, `text-xs
  uppercase tracking-widest`), H1 "Learn" (`text-4xl` display bold), a description (`max-w-xl` muted); on the
  right a panel with a 48 px ring and "4 / 72 — concepts complete".
- One section per level: "L1" (mono, primary) + the level's name (`text-xl` display); a two-column grid of
  concept cards (`panel p-4`, `gap-3`): a 40 px progress ring, the code (mono `text-xs` muted), the title; on
  the right the state — done: a check; due: a "Review" pill with a clock; next: a "Next" pill in the primary
  colour **and the whole card glows**; later: a lock icon.
- **NextStep** card at the bottom: "NEXT STEP / Drill what you've learned / Spot swings and gaps on real
  charts" and an arrow that slides on hover.

### 5.4 A lesson — `routes/learn.$conceptId.tsx`; images 07–10

- "← Curriculum"; "ICT-14 · Level 3 · Imbalance" (mono `text-sm` primary); H1 the title (`text-4xl`); the
  plain sentence (`text-lg` muted, `max-w-2xl`).
- A segmented control **Rule · Examples · Stats**; content fades in on switch:
  - Rule: a panel, "THE RULE TRADEEZ USES", the rule in `text-lg`, and "This is ICT's claim written as a
    testable rule. It is not proof that the method works."
  - Examples: two charts side by side (each `h-64`) with a caption in mono ("NQ 5m · Tue 3 Jun 2025").
  - Stats: three tiles — a number (`text-3xl` mono) and a label (e.g. "FVGs in 2y NQ (5m) · 18,904",
    "Filled within session · 61%", "Your accuracy · —").
- Buttons: "Practise it in a drill" (primary), "Mark as reviewed" (soft). NextStep "Drill this concept".
- A "later" lesson opened directly (image 10) is **not** locked in Lovable — ours must be (R-14).

### 5.5 Drills — `routes/drills.index.tsx`, `drills.$drillId.tsx`; images 11–16

- PageHeader "STEP 2 / Drills / Short exercises on one idea at a time, on real NQ."
- Sections "Pattern drills" (Crosshair icon) and "Risk drills" (Calculator icon), `text-xl` display; a
  three-column grid of cards (`panel p-5`, hover lift): the title (`text-lg` display), one line, and a footer row:
  a clock icon with the time ("3 min", "30 s each") and "Best 8/10" (invented).
- NextStep "Put it together in Practice / Plan a trade on a replayed day."
- **Pattern drill screen:** "← All drills", eyebrow "PATTERN DRILL", H1, one line; a panel with the chart
  (`h-80`) and an invisible grid of one button per candle over it — hover tints it with the primary colour
  (10%), marked 20%; after "Check": found in green (25%), wrong marks in red (25%). Footer: before — "Tap the
  middle candle of each gap. N marked." + Check; after — "2/3 found · 1 extra. Green bars show the gaps the rule
  found." + "Next chart".
- **Risk drill screen:** a `max-w-xl` panel: "QUESTION 4 OF 10" and a countdown pill (red under 10 s); the
  question in `text-lg` with values in mono bold; an input and "Answer"; the result in a green or red box with
  a check or a cross, "Correct" / "Not quite. The answer is 1", and the working in one line.

### 5.6 Practice — `routes/practice.index.tsx`; images 17, 18

- PageHeader "STEP 3 / Practice / A real day replayed bar by bar, the future hidden. Plan, execute, then
  review."
- **Start card** (accent-tinted, `p-6`, flex): "Start a session" (`text-2xl`), "A random NQ day from the last
  two years. You won't see the date until the end."; on the right two selects ("Random day / Trend days /
  Range days" — invented; "Start 08:30 NY / Start 02:00 NY") and "▶ Start".
- **Four tiles** (`grid md:grid-cols-4 gap-3`), each a button: the label with a chevron, the value in mono
  `text-3xl` (Total in green), and on click a sentence fades in under it (the chevron turns):
  Total "+3.98R" — "Average +1.00R per trade across 4 simulated trades. Best +2.10R, worst −1.00R."; Win rate
  "75%" — "3 of 4 trades closed in profit. With only 4 trades this number can be off by 40 points or more.";
  Followed plan "3 / 4" — "One trade was closed early before the stop or target. Process and outcome are scored
  separately."; Costs paid "$19.00" — "$4.75 per round turn: commission plus 0.25-point slippage on each fill."
- **Journal** panel: header "Journal"; a table When · Side · Entry · Result · R · P&L — one row **per trade**,
  side in green/red, R semibold green/red, P&L signed.
- NextStep "Explore the full chart".

### 5.7 Practice session — `routes/practice.session.tsx`; images 19–22

- Full height (`h-[calc(100vh-64px-45px)]`, `gap-3 p-3`). **Left panel** (the chart): a header row —
  "← Journal", "NQ · 5m" (display bold), "Tue · 09:05 NY · date hidden" (mono muted), the price (`text-xl`
  mono) right-aligned; the chart; a bottom bar — "Next bar", "+1 hour" (soft buttons with icons), a progress bar
  of the day, "60/120".
- **Right panel** (`w-80 p-5`): "Trade plan" / "Set your stop and target before entering."; Long|Short
  segmented; Stop (points), Target (points), Contracts inputs; a summary box (`bg-secondary p-4`): Entry, Stop
  (red), Target (green), Reward : risk ("1.50R"), Money at risk ("$400.00"); at the bottom "Place long", then
  "Play to result".
- **Scorecard** in the same panel: "+1.48R" (`text-5xl` mono green), "Target hit · +$591.00 after costs";
  OUTCOME (Max favourable, Max adverse); PROCESS (✓ lines in the primary colour, "–" lines muted); "The result
  and how the trade was made are scored separately. A good result does not make it a good trade."; "Back to
  journal".

### 5.8 Chart — `routes/chart.tsx`; images 23–27

- Full height, `gap-3 p-3`, three columns:
  - **Tools** (a slim panel): pointer, horizontal line, trend line, rectangle, position (↕), text, magnet; a
    divider; undo, redo, delete — 36 px square buttons, the active one accent-tinted.
  - **Chart panel:** a top bar — "NQ" (display `text-xl` bold), the price and change; "+ ES beside" (dropped,
    R-2); the timeframes segmented (1m…1d); the jumps segmented (Latest, 1W, 1M, 3M, 1Y, 2Y); a date pill
    ("10/02/2026 · EDT"); on the right a **Layers** button (primary when the drawer is open) with a count badge.
    Under it, the layers on as **removable chips** (accent-tinted pills with ×). The chart. A status bar in mono
    `text-xs`: "Bars 2,760 · Tick 0.25 = $5.00 · History 2024-10-07 → 2026-10-02".
  - **Layers drawer** (`w-80`, slides in from the right): "Layers" + "Clear all"; a search ("Search 41
    layers"); preset chips **Clean · Session map · Killzones + FVG · Silver Bullet**; groups with switch rows:
    **Time & sessions** (Days, Killzones, Silver Bullet, Macros, Session H/L, IPDA) · **Levels** (Opens,
    PDH/PDL, PWH/PWL, BSL/SSL, EQH/EQL, NWOG/NDOG, ORG) · **Structure** (Swings, ITH/LTH, HH/HL,
    Displacement, MSS, BOS/CHoCH, Sweep/run, Turtle soup) · **Gaps & blocks** (FVG, FVG state, IFVG, BPR, VI,
    Gaps/voids, OB, Breaker, Mitigation, Rejection) · **Models** (Unicorn, Dealing range, Premium/discount, OTE,
    Std dev, Po3, Judas, Daily profile, Weekly profile, SMT). Presets: Clean = none; Session map = Days,
    Killzones, Opens, PDH/PDL; Killzones + FVG = Killzones, FVG, Opens; Silver Bullet = Silver Bullet, FVG,
    BSL/SSL, MSS.
- **Ours differs (R-10):** not a drawer that narrows the chart but a **glass panel opened by click over the
  chart**; the groups, search, presets, chips and switches are taken as they are.

### 5.9 What Lovable invented — do not copy as is

| Lovable | Why not | What we do |
|---|---|---|
| "Random day / Trend days / Range days" | no such classification exists | leave out |
| "Best 8/10", "12 streak" on drill cards | invented records | the real record from the learning state (attempts, recent accuracy) |
| "Question 4 of 10" | our risk drills are single questions | show the kind's record ("7 of 9 right so far") |
| Process "✓ Risk under 1% of account" | no account size in the app | our scorecard's real lines (risk limit, plan, bias, killzone, execution) |
| "75% followed plan", "3 / 4" | invented | computed: trades held to the bracket ÷ trades (closed by hand = not followed) |
| Stats tiles' numbers, "18,904 FVGs" | invented | `/api/practice/statistics`, the evidence (DEC-065) |
| Locked lessons with stats | its locks are only icons | real locks (R-14) |
| Menu "Profile", "Settings", "Practice check" | "Practice Check" is our test account's display name; no profile page | menu: name + email; Change password; How it works; Sign out; Sign out everywhere |
| "+ ES beside" on the Chart | R-2 | removed until T47 |
| Plan inputs in points ("Stop (points)") | our plan is prices, dragged on the chart | keep prices; show the distance in points beside each |
| "Play to result" | our replay never jumps to the result | our Next bar / Next 10 / Play / speeds / Skip to exit (exists: `skip`) |
| A static SVG chart | ours is Lightweight Charts on real data | Home: our chart with interaction off (R-12); elsewhere interactive |

---

## 6. The tasks

### T36 — Theme and shell

**Goal.** The look every other task builds on, and the frame around every screen. After T36 every existing
screen runs inside the new shell with the new theme; their insides are rebuilt by the later tasks.

**Fabio:** "it is mostly a lovables design but i dont like the colors … pick a theme color and save it
somewhere and give that color to every 'theme colored' part of the app."

**Steps.**

1. **Decide the router** (record as a decision, §10 and DECISIONS.md as DEC-078): the app switches sections
   by React state today (`App.tsx`, `useState<Section>`); Home, News, lesson and drill pages need real
   addresses (the back button, links from Home into a lesson). **Recommendation: React Router** (newest, data
   router not needed — `createBrowserRouter` with plain routes); TanStack Router is Lovable's choice but adds
   code generation. The URL map:
   `/` Home · `/learn` · `/learn/:conceptId` · `/drills` · `/drills/:kind` (a drill session) ·
   `/drills/risk/:kind` · `/practice` · `/practice/:sessionId` · `/chart` · `/news` · `/help` (the guide) ·
   `/sign-in` · `/sign-up`. Signed out: every route except sign-in/up redirects to `/sign-in`, and back after.
   Unknown routes → `/`. Vite dev serves the SPA for every path already; the built site needs the host's
   fallback to `index.html` (note for T31 deployment).
2. **Tokens** — replace the `@theme` block in `frontend/src/index.css` (colours as CSS custom properties; the
   chart adapter keeps reading the same names):

   | Token | Value | Use |
   |---|---|---|
   | `--color-canvas` | `oklch(0.155 0.008 260)` | page background |
   | `--color-surface` | `oklch(0.195 0.010 260)` | cards, panels, the chart's pane |
   | `--color-raised` | `oklch(0.235 0.012 260)` | hover, menus, inputs |
   | `--color-sunken` | `oklch(0.255 0.012 260)` | segmented tracks, summary boxes (Lovable's secondary) |
   | `--color-line` | `oklch(1 0 0 / 8%)` | borders, dividers |
   | `--color-line-strong` | `oklch(1 0 0 / 12%)` | inputs' borders |
   | `--color-ink` | `oklch(0.96 0.005 260)` | text |
   | `--color-subtle` | `oklch(0.66 0.015 260)` | secondary text |
   | `--color-accent` | `#7C6CFF` | **the theme colour (R-3)** — primary buttons, the active nav pill's text/underline, focus rings, progress fills and rings, selected toggles and segments, the "Up next" tint |
   | `--color-accent-ink` | `#ffffff` | text on the accent |
   | `--color-accent-soft` | `#7C6CFF` at 16% | tinted cards and chips |
   | `--color-up` | `oklch(0.76 0.17 155)` | up candles, long, wins, positive R |
   | `--color-down` | `oklch(0.66 0.21 22)` | down candles, short, losses, errors |
   | `--color-warn` | `oklch(0.78 0.15 65)` | levels on the chart, assumptions, notices |
   | `--color-news-high` / `--color-news-medium` | `#e5484d` / `#f5a524` | Forex Factory's red / orange folders |
   | `--color-session-london` / `-nyam` / `-nypm` | `oklch(0.65 0.12 245 / .12)` / `oklch(0.8 0.14 80 / .10)` / `oklch(0.72 0.11 190 / .10)` | killzone shading |
   | `--radius` | `0.875rem` | cards `rounded-xl` (≈18 px), controls `rounded-lg` |
   | `--shadow-panel` | `0 1px 0 oklch(1 0 0 / 5%) inset, 0 20px 40px -24px oklch(0 0 0 / .6)` | cards |
   | `--shadow-glow` | `0 0 0 1px` accent 40% `, 0 10px 30px -10px` accent 45% | the "next" card, primary hover |
   | `--gradient-glow` | radial `60% 50% at 20% 0%`, accent at 12%, to transparent | the page background |
   | glass | `--color-surface` at 60% + `backdrop-blur-xl` + `--color-line` + `--shadow-panel` | T38's menu, popovers |

   Check every pairing for AA contrast (accent on canvas for text, white on accent for buttons). The chart
   (`lib/chart/lightweightAdapter.ts`) takes its candle, grid, text and crosshair colours from these.
3. **Type:** `@fontsource-variable/space-grotesk` (headings and body) and `@fontsource-variable/jetbrains-mono`
   (every number: prices, R, money, times, codes); remove `@fontsource-variable/inter`. A `num` utility
   (mono + `tabular-nums`). Scale: page title `text-4xl`/`text-5xl` (Home), section `text-xl`, card title
   `text-lg`/`text-2xl` (step cards), body `text-sm`/`text-base`, captions `text-xs`; eyebrows `text-xs
   uppercase tracking-widest` in the accent.
4. **Motion** (`motion` package): page transitions (fade + 8 px rise, 200 ms ease-out); cards lift 2 px on
   hover (150 ms); panels and the glass menu scale 0.98→1 + fade (160 ms); tiles expand with height animation
   (200 ms); numbers count up once on first view (600 ms); everything off under `prefers-reduced-motion`.
5. **Icons:** `lucide-react` (Lovable's), replacing text glyphs (←, ✓, ✗, ▾) in the shared kit and the shell.
6. **The shared kit** — rewrite `components/ui.tsx` (and `lib/styles.ts` for class helpers), each with hover,
   focus-visible (accent ring), disabled and loading states:
   `Button` (primary = accent fill; soft = sunken; ghost; danger; sizes sm/md/lg/icon) · `Segmented` (sunken
   track, the selected segment accent-filled, mono labels) · `Card` (surface, `--radius`, `--shadow-panel`,
   optional `interactive` lift) · `PageHeader` (eyebrow, title, description, right slot) · `StatTile`
   (label + chevron, value in mono 3xl with tone, expandable detail) · `Ring` (progress ring, size, value) ·
   `ProgressBar` · `Switch` (`role="switch"`) · `Chip` (accent-soft, removable with ×) · `Badge` (states:
   done, due, next, locked) · `Glass` (the glass surface, a positioned popover with Esc and outside-click
   closing) · `NextStep` (the "Next step →" card) · `Table` (header row muted, rows with hover) · form fields
   (`Field` with label, hint, error; inputs and selects in the new style) · `Notice` (warn-tinted).
   Keep the existing exports' names where screens use them, so later tasks change insides, not every import.
7. **The shell** (`App.tsx` → `AppShell` + routes): the header as §5.1 — logo, nav **Home · Learn · Drills ·
   Practice · Chart · News** (News added), the account menu (initials circle in accent-soft; menu rows: the
   name and email, "Change password" (the existing dialog), "How it works" → `/help`, "Sign out", "Sign out
   everywhere"); the footer as §5.1 with our disclaimer text (DEC-015 / T29) behind "Read more"; the page glow.
   Container widths: content pages `max-w-[1280px]` (Lovable's Home) — **not** 1100 like Lovable's inner pages,
   which leave most of a 1920 screen empty; full-screen pages (Chart, practice session) use the whole width.
8. **Sign-in / sign-up** in Lovable's centred card (images 01, 02): the logo above, the form, "Create an account
   instead" / "Sign in instead", the disclaimer line below. Keep `SignInForm`, `PasswordRules` behaviour.
9. **The guide** (`components/Guide.tsx`) becomes the `/help` page, restyled; the first-visit pop-up over the
   chart is removed (Fabio hated what greets him) — Home replaces it as the first screen.
10. Every existing screen mounted under its route, unchanged inside (restyled only through the kit).

**Files.** New: `components/shell/AppShell.tsx`, `components/shell/AccountMenu.tsx` (moved), `routes.tsx` (or
`router.tsx`), `components/ui/*` if the kit is split per component. Changed: `App.tsx`, `main.tsx` (fonts,
router), `index.css`, `components/ui.tsx`, `lib/styles.ts`, `lib/chart/lightweightAdapter.ts` (colours),
`components/Guide.tsx`, `components/SignInForm.tsx`. Removed: Inter, the old header/footer markup.

**Tests.** Every existing suite keeps passing (they query roles and text; where one checked a class like
`text-subtle`, update it to the new token). New: the router (each URL renders its screen; signed-out redirect
and return; unknown URL → Home), the account menu (opens, Esc closes, sign-out calls the API), the footer's
"Read more", the kit (Switch toggles `aria-checked`, StatTile expands, Glass closes on Esc and outside click).

**Done when.** ☐ every screen reachable by URL and nav ☐ the theme applied everywhere (no GitHub-palette hex
left: grep `#0d1117`, `#161b22`, `#58a6ff`) ☐ the accent only in `--color-accent` ☐ fonts loaded, Inter gone ☐
reduced motion respected ☐ screenshots 1920/1366 of every screen in `docs/redesign/after/t36/` ☐ lint,
typecheck, knip, all tests ☐ DEC-078 (router, theme, fonts, motion) ☐ PATH/DOCUMENTATION updated.

### T37 — Chart overlays in the chart's paint step (BUG-095)

**Goal.** Clock shading, levels, setups and drawings move with the candles in the same frame — no lag on pan,
zoom or resize.

**Fabio:** "the indicators on chart are hovering items, when you resize or move the chart, the indicators lag
behind a lot, and it kills the experience."

**Cause (BUG-095).** `components/ClockOverlay.tsx`, `SetupOverlay.tsx` and `DrawingOverlay.tsx` draw on their
own `<canvas>` (`hooks/useOverlayCanvas.ts`), redrawn from `adapter.onViewportChange` — which fires after the
chart has painted, so for a frame or more they trail the candles.

**How.** Lightweight Charts (v5) **series primitives** (`ISeriesPrimitive`: `paneViews()` returning renderers
that draw with the chart's own canvas target, `priceAxisViews()`/`timeAxisViews()` for labels, `hitTest`,
`autoscaleInfo`). They render inside the chart's paint, in the same frame as the candles.

**Steps.**
1. Extend the `ChartAdapter` (`lib/chart/types.ts`, DEC-004: nothing else imports the library) with
   `attachPrimitive(p)` / `detachPrimitive(p)` and a small primitive interface of our own (draw(ctx, projection)
   with the same Projection the overlays use today — `timeToX`, `priceToY`), implemented in
   `lightweightAdapter.ts` by wrapping ours into an `ISeriesPrimitive` on the candle series; the fake adapter
   records attached primitives for tests.
2. **Clock first:** move `ClockOverlay`'s drawing (windows shading, lines, labels) into a primitive; remove its
   canvas. Check pan/zoom at 1920 on the Chart tab and in practice.
3. **Setups:** the same for `SetupOverlay` (zones, levels, points, labels, the SMT companion's marks).
4. **Drawings:** the painting of `DrawingOverlay` moves into a primitive; **pointer handling stays** (hit-testing
   on our geometry, dragging handles, creating shapes) on a transparent layer as now — only painting moves. The
   drill overlay (`components/drills/DrillOverlay.tsx`) and the example charts get the same treatment where
   they draw.
5. Delete `useOverlayCanvas` if nothing uses it any more.

**Risks.** The largest technical task in M6. Do one overlay per commit; keep each working on its own. Device
pixel ratio (draw in media coordinates via the target's `useMediaCoordinateSpace`), and z-order (shading under
candles: `zOrder: 'bottom'`; drawings on top: `'top'`).

**Tests.** The overlays' geometry tests (`DrawingOverlay.test.tsx`, the clock and setup layer tests) move to the
primitives' draw functions with a fake context; the adapter test checks attach/detach. Lag cannot be tested in
jsdom: **checked in the browser** — pan and zoom fast at 1920 on the Chart tab, a practice session and a drill;
record a short capture or a sequence of screenshots in `docs/redesign/after/t37/`.

**Done when.** ☐ no visible lag on pan, zoom, window resize, timeframe switch ☐ all overlays still correct
(clock, setups, drawings, review markers, drill marks) ☐ BUG-095 moved to Fixed with its lesson ☐ tests, lint,
knip.

### T38 — Chart tab

**Goal.** Lovable's Chart (§5.8): the chart fills the window; everything else is one click away.

**Fabio:** "all of the tools above are awful to look at … They need to be grouped into sections" · "the whole
section needs to be absolutely gone" (the instrument choice) · "a dropdown little expander that will be like
liquid glass" · "open it on click" · "the drawing toolbar can stay as slim vertical bar on the left".

**Layout** (`components/chart/ChartPage.tsx`, replacing `ChartWorkspace.tsx`'s markup; full width, height
`100dvh` minus header and footer, `gap-3 p-3`):
- **Left:** the slim drawing bar (`DrawingToolbar.tsx` restyled: 36 px icon buttons from lucide — pointer,
  horizontal line, trend line, rectangle, position, Fibonacci, zone, label, magnet/snap; divider; undo, redo,
  delete), the active tool accent-tinted.
- **Centre (the chart card):** top bar — "NQ" (display bold) with the last price and the change (green/red),
  the timeframes (`Segmented` 1m … 1d), the jumps (Latest, 1W, 1M, 3M, 1Y, 2Y), the date (a pill opening the
  date picker), the time-axis zone toggle (New York / exchange / UTC), and on the right the **Layers** button
  (accent when open, a count badge of layers on). Under it the **chips** of the layers on (accent-soft, × to
  remove). Then the chart, to the bottom. A status line in mono `text-xs`: bars shown, "Tick 0.25 = $5.00",
  the history's range.
- **No** instrument select, **no** Beside select, **no** SMT layer (R-1, R-2). `CompanionChart` stays for the
  SMT lesson and drill only.
- The hidden-stretch notice (DEC-077) kept, restyled as a `Notice` under the top bar.

**The glass layers menu** (`components/chart/LayersMenu.tsx`, replacing `ClockToggles.tsx` and
`SetupToggles.tsx`):
- Opened by the Layers button (click; R-10), anchored to the chart card's top-right, over the chart (the chart
  does not shrink); `Glass` surface, `w-[22rem]`, max height 70% of the chart, scrolls inside.
- Contents as Lovable's drawer: "Layers" + "Clear all"; a search ("Search 40 layers"); presets **Clean ·
  Session map · Killzones + FVG · Silver Bullet** (Silver Bullet = the Silver Bullet window, FVG, BSL/SSL, MSS);
  the five groups with `Switch` rows — Time & sessions, Levels, Structure, Gaps & blocks, Models — exactly the
  §5.8 lists **minus SMT**. Each row's name has the rule's version and one line as a tooltip (what
  `SetupToggles` shows today in its long titles).
- Closes on Esc, outside click, or the button; keyboard: Tab through switches, Space toggles.
- The clock layers (Days, Killzones, Silver Bullet, Macros, Opens, PDH/PDL, PWH/PWL, Session H/L, IPDA) and the
  setup layers keep their existing persistence (`hooks/useClockLayers`, `useSetupLayers`).
- Default on the first visit: Killzones, Opens, PDH/PDL (Lovable's default) — everything else off.

**Steps.** 1) `ChartPage` layout with the existing chart, drawing and data hooks; 2) `LayersMenu` with groups,
search, presets, chips; 3) remove the instrument/beside UI and the SMT layer from the Chart tab (the API still
serves ES for SMT); 4) delete `ClockToggles`, `SetupToggles` and their tests (their behaviour re-tested in
`LayersMenu.test.tsx`); 5) the status line; 6) screenshots.

**Tests.** `LayersMenu.test.tsx`: opens on click, closes on Esc and outside click, search filters, a preset sets
its layers, a switch toggles a layer and its chip appears, × on a chip removes it, "Clear all"; the App/Chart
tests updated (no instrument select; NQ requested); the hidden notice still shown.

**Done when.** ☐ the chart fills the window at 1920 and 1366 ☐ no instrument or beside control ☐ every layer
reachable in ≤ 2 clicks, grouped ☐ chips ☐ drawing bar works (all tools, undo/redo/delete) ☐ screenshots ☐
tests ☐ BUG-094 left open for T47 with a note that ES left the Chart tab.

### T39 — Home

**Goal.** Lovable's Home (§5.2) on real data: the page after sign-in (R-6).

**Fabio:** "I really like their Home page, and i like that the chart is not movable" · "the home page is the
page after login … for now make the home page same as lovable".

**Layout** (`components/home/HomePage.tsx`, route `/`, container `max-w-[1280px] px-6 py-10`):
1. **Greeting:** the date in English ("Friday, 23 October"); "Good morning|afternoon|evening, {first name}."
   (the account's display name; by the browser's local hour); the line "Learn the rule, drill it, then practise
   it on a real day you can't see ahead of."
2. **Four step cards** (Learn, Drills, Practice, Chart), each a link, with the icon, one line and a progress
   bar — **real values only** (agree the definitions with Fabio at the start of T39; proposal):
   Learn = `summary.complete / summary.concepts` from `GET /api/learning`; Drills = pattern-drill kinds whose
   concept is `solid` ÷ pattern-drill kinds; Practice and Chart = **no bar** — a number line instead
   ("12 sessions · 48 trades"; "Two years of NQ, every ICT layer"). Never an invented percentage.
3. **Up next** (accent-soft card): `learning.suggested` → its title (`GET /api/concepts`) and its one-line plain
   description, "Continue lesson →" to `/learn/:id`. If nothing is suggested: "Everything open is complete —
   review what's due."
4. **Due for review:** `learning.due` (in curriculum order), up to 5 rows: a 36 px `Ring` at
   `recentAccuracy × 100`, the title, the code, → `/learn/:id`. Empty: "Nothing due today."
5. **Practice so far:** `GET /api/practice/statistics` → `overall.totalR` (mono 4xl, green/red), "N practice
   trades · X% held to plan" (the held-to-plan count from T41; before T41, trades only), "Open journal →".
   Empty: "No trades yet — start a session."
6. **News today** (after T40; hidden before): today's and tomorrow's **high-impact** USD events — time (New York,
   mono), a red folder, the title; "Week ahead →" `/news`. Empty: "No high-impact news today."
7. **The NQ chart card:** the latest NQ day on 5-minute, **not movable** (R-12: the adapter's mount option
   `interactive: false` → Lightweight Charts `handleScroll: false, handleScale: false`, like `followsOnly`),
   killzone shading only; "NQ · 5m · latest data", the last price and the day's change; click → `/chart`.

**Backend.** Probably nothing: `GET /api/learning`, `/api/concepts`, `/api/practice/statistics`,
`/api/market/instruments/NQ/bars`, `/api/news` exist (news after T40). Add a summary endpoint only if the page
needs too many calls (decide when measured).

**Tests.** `HomePage.test.tsx`: each card from mocked responses; every empty state (a new account); greeting by
hour; the chart mounted with `interactive: false`; links go to the right routes.

**Done when.** ☐ every number real, none invented ☐ empty states ☐ chart does not pan or zoom ☐ screenshots
(a new account and the test account) ☐ tests.

### T40 — News

> **Replanned 2026-10-26 (DEC-079).** Step 0 found that Forex Factory's terms forbid republishing its calendar.
> Fabio chose our own calendar from the agencies' schedules (FRED and the Fed). What follows is the original plan,
> kept for the record; DEC-079 says what was built.

**Goal.** Forex Factory's calendar for the week, high and medium impact (R-8).

**Fabio:** "Red and Orange folders are what traders follow and i want that in our website as well" · "i just
need upcoming ones. Same as in forex factory" · on the feed: "perfect, use that forex factory".

**Step 0 — terms.** Read Forex Factory's terms and the feed's notes (faireconomy.media) and confirm the weekly
export may be shown in the app with credit. If not allowed: stop and tell Fabio; fall back to a free calendar API
(R-8). Record the outcome in §10.

**Backend** (`com.tradeez.news`):
- `NewsClient`: `java.net.http.HttpClient`, GET `https://nfs.faireconomy.media/ff_calendar_thisweek.json`,
  timeout 10 s, a plain `User-Agent: Tradeez`. Parses the array of `{title, country, date, impact, forecast,
  previous}` (`date` is ISO-8601 with an offset, e.g. `2026-10-19T08:30:00-04:00`; `impact` is High, Medium, Low
  or Holiday). Unknown fields ignored.
- `NewsService`: keeps the last good copy with its `fetchedAt`; refreshes at most once an hour, on demand (no
  background job needed); on a failed fetch serves the old copy and logs it; never fetches from a test.
- `NewsController`: `GET /api/news?impact=high,medium&currency=USD` (both optional; default high+medium, all
  currencies) → `{ updatedAt, source: "Forex Factory", events: [{ title, currency, at (epoch s), impact (HIGH |
  MEDIUM | LOW | HOLIDAY), forecast, previous }] }`, sorted by time. Authenticated (deny-by-default); add
  `/api/news` to `ReadRateLimit`'s paths. No key, nothing in `.env`.
- Config: `tradeez.news.feed-url` (overridable for tests), `tradeez.news.refresh` (1h).

**Frontend:** the News page (`/news`, `components/news/NewsPage.tsx`): PageHeader "News" / "This week's economic
calendar — the red and orange folders traders plan around."; filters (impact: High, Medium; currency: USD by
default, All); the week grouped by day ("Monday 26 October"), rows: time in New York (mono), currency, a folder
icon filled red (high) or orange (medium), the title, forecast, previous; today marked with an accent edge;
past events dimmed; "Updated hh:mm · Calendar: Forex Factory" at the bottom. The Home card (T39).

**Not in T40:** news inside a practice replay (R-9) — needs a historical calendar; written down as a follow-up.

**Tests.** `NewsServiceTest` from a saved sample (`src/test/resources/news/ff_calendar_sample.json`): parsing,
the offset → instant (and a week across the DST change, shown in New York time correctly), filters, the hourly
cache, a failed fetch serving the old copy; `NewsApiTest`: auth, query params; `NewsPage.test.tsx`: grouping,
filters, colours by impact, empty week.

**Done when.** ☐ terms confirmed and noted ☐ feed fetched only by the backend, cached ☐ page and Home card ☐
tests ☐ screenshots.

### T41 — Practice home

**Goal.** Lovable's Practice page (§5.6): start, stats, journal — compact, not overwhelming.

**Fabio:** no timeframe, no weekday ("I want to remove the day section entirely"); session time kept; risk limit
kept "for now"; fees and slippage "needs to go, get removed and changed forever"; "the stats … I want this
section copied fully"; "costs paid".

**Layout** (`components/practice/PracticeHome.tsx`, route `/practice`, container 1280):
1. PageHeader: eyebrow "STEP 3", "Practice", "A real day replayed bar by bar, the future hidden. Plan, execute,
   then review."
2. **Start card** (accent-soft, `p-6`): "Start a session" / "A random NQ day from the last two years. You won't
   see the date until the end." — the session time select (**Any time · Asia 20:00–24:00 · London 02:00–05:00 ·
   New York AM 08:30–11:00 · New York PM 13:30–16:00**, the killzone list already in the code) and "▶ Start".
   Under it, small: "Your risk limit per trade: $___" (the existing field, its validation, `parseDecimal`).
   **Removed:** the timeframe segmented control and the weekday select.
3. **Four tiles** (`StatTile`, expand on click with a real sentence):
   - **Total** — `overall.totalR` (green/red); detail "Average {avg}R per trade across {n} trades. Best {best}R,
     worst {worst}R."
   - **Win rate** — wins ÷ trades; detail "{w} of {n} trades made money after costs." + the sample-size reading
     the statistics already give (`reading`).
   - **Held to plan** (Lovable's "Followed plan") — trades not closed by hand ÷ trades; detail "{k} closed by hand
     before the stop or target. Process and outcome are scored separately."
   - **Costs paid** — fees + slippage in money over all trades; detail "Every trade pays the standard costs: a
     tick of slippage in, a tick at the stop, $4.50 a round trip."
4. **Journal** — one row per **trade** (Lovable's): When · Side · Entry · Result · R · P&L · Model. "When" is the
   session's shown time ("Tue 09:05 NY") while the session is open and the real date once it is complete; a row
   opens the session at that trade. Above it, **Open sessions** (status Flat/In trade, trades, total R, Resume)
   — compact.
5. **More statistics** (a disclosure): the existing statistics by model, killzone and weekday, and calibration.

**Backend.**
- **Costs (R-17):** new trades always use the standard fill model (DEC-072's standard, `mkt-1t/slip-1t/thru-1t/
  comm-450c`); `PUT /api/practice/settings` accepts only `riskLimitCents` (cost fields refused with a clear
  400, or ignored — decide and test); `GET /api/practice/settings` returns the limit and the standard costs
  read-only (the plan panel shows them). Stored older trades keep their own fill model (results unchanged).
  Record as a decision (DEC: "costs fixed at the standard").
- **Statistics:** add to `StatisticsView.overall` (or beside it): `heldToPlan` (trades whose outcome is not
  CLOSED), `costsPaidCents` (commission + slippage money, computed as the scorecard's costs line does),
  `bestR`, `worstR`.
- **Journal:** `GET /api/practice/trades?limit=50` — the user's finished trades across sessions, newest first:
  `{ sessionId, number, when (shown or real, per the rule above), side, entryTicks, outcome, r, netPnlCents,
  model }`. Ownership checked as everywhere (DEC-022). Counted only once revealed (as statistics are).
- Start: the UI sends no timeframe (the backend defaults to 5m) and no weekday; the API keeps accepting both
  (tests use them) — decision recorded.

**Remove** (frontend): the timeframe and weekday controls, `CostsFields`, the old journal table and its date
formatting with the machine locale.

**Tests.** Backend: settings refuse/ignore cost fields; a new trade uses the standard model even after old
settings; statistics' new fields (held to plan, costs paid, best/worst) on a seeded set; the trades endpoint
(order, revealed only, other users' trades never). Frontend: start sends only the session time; the tiles and
their details from mocked statistics; the journal rows and their links; the risk limit kept.

**Done when.** ☐ no cost settings anywhere ☐ start = session time + Start ☐ four tiles real ☐ per-trade journal
☐ tests ☐ screenshots ☐ DECISIONS updated.

### T42 — Practice session

**Goal.** The replay as Lovable drew it (§5.7), with our replay controls: a big chart, a side panel to trade
(R-18).

**Fabio:** "45 indicators, chart is flashing different colors, colors of the theme are ugly, chart is
absolutely too small, you cant dive into it" · "I love our replay section … it is just ugly" · "chart is
fullscreen, you have a sidebar that lets you plan a trade, then gives you info about the trade like money
risked, which R:R is it, whats the entry what the TP whats the SL, under that section i think we could put our
replay section".

**Layout** (`components/practice/SessionPage.tsx`, route `/practice/:sessionId`, full screen, `gap-3 p-3`):
- **Left:** the slim drawing bar (T38's).
- **Centre — the chart card:** top bar: "← Practice", "NQ", the view timeframes (the session's
  `VIEW_TIMEFRAMES`), the shown time ("Tue 09:05 NY · date hidden"), status pill (Flat / In trade), the price;
  the Layers button and chips (T38's menu; **all layers off by default** in practice — Fabio: "45 indicators …
  flashing"); the chart to the bottom; the hidden-date never shown until the end.
- **Right — the trading panel** (`w-[22rem]`, scrolls inside, sections separated by thin lines):
  1. **Score line:** closed trades · total R · net $ (from the session's score).
  2. **Plan a trade** (flat, no draft): "Plan long" / "Plan short" (buy/sell tones). With a draft:
     "Draft · long at market · not placed" + flip; **Entry** "next price after 26,046.00"; **Stop** and
     **Target** as price inputs (the existing `PriceField`, `parseDecimal`) with the distance in points beside
     each ("−10.00 pts", "+20.00 pts") — also dragged on the chart as now; **Contracts**; **Partial** (≥ 2
     contracts, existing); **"Add detail"** folding the intent (model, bias, draw on liquidity, confidence) and
     the model checklist from `/context`.
  3. **The numbers** (sunken box, Lovable's): Entry · Stop (red) · Target (green) · Reward : risk · Money at
     risk · "$x at the stop with costs" · the limit verdict if a limit is set (BUG-088 numbers).
  4. **Place trade** (primary) · Discard.
  5. **In a trade:** the open trade (side, contracts, entry, stop — "moved to the entry" after a partial —,
     target, the partial's state), **Close at market**.
  6. **Replay** (always at the panel's bottom): **Next bar · Next 10 · Play/Pause** with speeds **1× 2× 4× 8×**,
     **Skip to exit** while in a trade (existing `skip`), **End session…** (confirm).
  7. **After a trade:** its scorecard (`ResultCard`, restyled to Lovable's: big R, "Target hit · +$591.00 after
     costs", Outcome, Process) and "Read the review" (opens the review overlay on the chart).
- **Trades:** the session's trade log (`TradeLog`) in a collapsible strip under the chart ("Trades (3)"),
  clicking a trade shows it on the chart as now.
- **Session end:** the panel shows the result and the revealed date, "Back to Practice".

**Behaviour kept (tests prove it):** everything in `PracticeView.test.tsx` / `PracticeSession` suites — the
draft is not a trade, flip and discard (BUG-090/091), the partial, close at market, skip, play/pause and speeds,
end session, review gating, hidden date until the end, the hidden stretches.

**Steps.** 1) the route and layout with the existing hooks (`usePracticeSession`); 2) the panel sections moving
`PlanPanel`/`PlanIntent` content into them; 3) the replay block; 4) scorecard and review in the panel; 5) the trade
strip; 6) delete the old layout markup; 7) carry the tests over (they query by role and text — update only
layout-specific queries).

**Done when.** ☐ the chart fills the space left of the panel at 1920 and 1366 ☐ plan → place → replay → result →
review → end works ☐ no layer on by default ☐ every existing behaviour test passes ☐ screenshots of each state ☐
tests.

### T43 — Drills

**Goal.** Lovable's Drills (§5.5): sections of cards, one click to start, a clean drill screen.

**Fabio:** "the time on a chart doesnt matter" · "it can be random" · "group them into sections and display them
in a nice smooth interactive way. I like the lovables drills tab a lot."

**The list** (`components/drills/DrillsPage.tsx`, route `/drills`, container 1280): PageHeader "STEP 2 /
Drills / Short exercises on one idea at a time, on real NQ."; sections by topic (the same topics as Learn,
T44/T45), each a heading with an icon and a three-column grid of cards. The current kinds placed (adjust to
T44's topics):
- **Gaps & imbalances:** `FVG_IDENTIFICATION`, `VI_IDENTIFICATION`.
- **Structure:** `SWING_HIGH_IDENTIFICATION`, `SWING_LOW_IDENTIFICATION`, `DISPLACEMENT_IDENTIFICATION`,
  `STRUCTURE_CLASSIFICATION`, `BOS_CHOCH_CLASSIFICATION`.
- **Liquidity:** `EQHL_IDENTIFICATION`, `SWEEP_IDENTIFICATION`, `SWEEP_RUN_CLASSIFICATION`,
  `LIQUIDITY_FIRST_PREDICTION` (and the external/internal prediction kind, if listed by `/api/drills/kinds`).
- **SMT:** `SMT_IDENTIFICATION` (ES beside — R-1's exception).
- **Trade management:** `OPEN_TRADE_MANAGEMENT`.
- **Risk:** position size (`CONTRACTS`), losing streak (`STREAK`), recovery (`RECOVERY`).
**Card:** the title, one line (from `GET /api/drills/kinds`), the mode as a badge (Spot · Name · Predict ·
Manage · Sums), a time hint (pattern ~2–3 min; risk: the real countdown, e.g. "45 s each"), **the real record**
(the kind's concept from `GET /api/learning`: "{attempts} tries · {recentAccuracy}% recently"; risk: "{right} of
{answered} right"), and after T45 its lock ("Unlocks after: {lesson}"). Hover lift; click → the drill.
NextStep "Put it together in Practice".

**The drill screen** (`/drills/:kind`): "← All drills", eyebrow (mode, e.g. "SPOT"), the title, the one-line
task; the chart card at the container's full width (the SMT drill with ES under it); the rule folded
("Graded against fvg-1.0.0"); the answer bar under the chart — Spot: "{n} marked · Clear · Check answer";
Name/Predict/Manage: their choice buttons. **Result:** "{found}/{total} found · {extra} extra", marks coloured
(found green, missed outlined, wrong red — `DrillOverlay`), the explanation per hit, "Next drill" (same kind)
and "All drills". The risk drill (`/drills/risk/:kind`): Lovable's card — the countdown pill (red under 10 s),
the question with values in mono bold, the input (`parseDecimal`), the result box (green/red) with the working,
"Next question".

**Backend.** `POST /api/drills` with no `timeframe` → **random** among the kind's supported real timeframes
(1m, 5m, 15m; `DrillService.REAL_TIMEFRAMES`) — today a blank timeframe means 5m (`DEFAULT_TIMEFRAME`). Inject
the `Random` (or a supplier) so tests can fix it. The frontend stops sending a timeframe.

**Remove:** the kind dropdown, the timeframe segmented control (`DRILL_TIMEFRAMES`), `DrillView`'s top panel.

**Tests.** Backend: no timeframe → one of the supported, distributed (a fixed seed, or many draws hitting ≥ 2);
an explicit timeframe still honoured. Frontend: sections and cards from mocked kinds and learning state; a card
starts its drill without a timeframe; the drill screen's states (marking, checking, result, next); risk drill
countdown and answer; existing `DrillView.test.tsx` / `RiskDrills.test.tsx` behaviour carried over.

**Done when.** ☐ no dropdown, no timeframe choice ☐ every kind reachable from a card ☐ records real ☐ screenshots ☐
tests.

### T44 — Learn audit (with Fabio)

**Goal.** The list Fabio asked for before anything is removed: "before you do that you will list them to me and
we will together check if they are worth implementing on a another tab or if they will be completely removed".

**Output:** `docs/redesign/learn-audit.md` (and a readable page for Fabio if he wants one), one row per concept
in `docs/ICT_CONCEPTS.md` (84 rows: F-* and ICT-*), columns:
id · name · current level · **rule built** (detectors with this `conceptId`, from `DetectorRegistry`) · **real
NQ examples** (lesson examples exist = a rule exists and found hits) · **drill** (a `DrillKind` with this
`conceptId`) · clock rule (for time concepts) · what its page shows today (one line) · **importance** (high /
medium / low — Fabio: "IFVG is much much much more important" than time zones) · **proposal**: *keep (has a
drill)* · *keep — needs a drill* · *merge into {id}* · *open reference card (never locked)* · *remove*.
Also: the proposed **topics** and the order inside each (T45).

**Method:** derive the columns from code and the API (`/api/concepts`, `/api/drills/kinds`, detectors' concept
ids), not by hand; then add the proposal and importance per row with a reason. Level 0 (Foundations) is already
decided: removed (Fabio: "nonsense"), but list its rows so their useful bits can move into lessons.

**Then:** go through it with Fabio; record each decision in the file (a "Fabio's decision" column) and in §10.
Nothing in the curriculum changes in T44.

### T45 — Learn rebuild

**Goal.** Learn as Lovable drew it (§5.3, §5.4): grouped by topic, locked, measured, and worth reading (R-13,
R-14, R-15).

**Fabio:** "trader doesnt even know which one to start" · "with locked sessions, with progress measurements, and
most importantly GROUPED TOGETHER" · "do it by topic" · "passing the drill unlocks the next session" · his stop
example.

**The list** (`/learn`, container 1280): PageHeader "STEP 1 / Learn / ICT's ideas grouped by topic — start with
the first open lesson."; right: a `Ring` and "{done} / {total} lessons". **Topics** as sections (from T44, e.g.
Liquidity · Gaps & imbalances · Structure · Blocks · Ranges · Time & sessions · Models · Risk & management ·
SMT), each with its own small progress ("3 of 7") and its lessons in importance order as Lovable's cards (ring,
code, title, state badge: done ✓ / due "Review" / next "Next" + glow / locked 🔒 with "Pass {prev} drill to
unlock" on hover/focus). A first-time learner sees "Start here" on the first lesson of the recommended topic.

**The lesson** (`/learn/:id`): "← Learn"; eyebrow "{topic} · {code}"; the title; the one-line plain summary;
then the **template**:
1. **What it is** — two or three plain sentences.
2. **How to spot it** — what to look for on the chart (bullets).
3. **How it is traded** — entry, stop, target in plain words (Fabio's standard: "Stops are placed on recent high
   and lows after a liquidity sweep").
4. **Real NQ examples** — the random examples (DEC-055) with their explanations, "Show others".
5. **The rule Tradeez uses** — folded: the versioned rule (RuleText) and "not proof the method works".
6. **What the data says** — the evidence (DEC-065) as tiles.
7. **Practise it** — the drill (primary button) and "Mark as read".
A locked lesson opened by URL shows the summary and "Pass the {prev} drill to unlock", not the content.
Reference cards (T44) show the template without the drill and never lock.

**The words (R-15):** every kept lesson's sections written in plain trader language, topic by topic; Fabio
reviews each topic's text before it merges. Where the text lives — **recommendation:** one Markdown file per
lesson in `backend/src/main/resources/lessons/{id}.md` with the template's headings, served by `LessonService`;
`ICT_CONCEPTS.md` keeps the table (topic, order, importance, kind) and the one-line summaries, updated to match.

**Backend.**
- `ICT_CONCEPTS.md` table gains **topic, order, importance, kind** (lesson / reference / removed);
  `ConceptRegistry` parses them; removed concepts disappear from `/api/concepts` and the learning state.
- **Locks — `learning-1.1.0`:** a lesson is open when it is the first in its topic, or the lesson before it in the
  topic has its drill **passed** — "passed" = the concept's state is `solid` under the current rule
  (`LearningRule`; confirm the threshold with Fabio at the start); reference cards always open. `LevelView`
  replaced by a `TopicView` (topic, lessons, complete, open). `suggested` = the first open, incomplete lesson of
  the most advanced topic in progress (or the first topic).
- Lessons served with the template's sections (`LessonResponse` gains them).

**Remove:** Level 0; `LearnView`'s sidebar list and the 1/8-page cards; every page T44 removes; progress bars of
drills that do not exist (a lesson without a drill shows none).

**Tests.** `ConceptRegistryTest` (new columns, removed rows), `LearningRuleTest` (topic unlocks: first open,
next opens on solid, reference never locked), `LearningApiTest`, `LessonApiTest` (sections served); frontend:
the list (topics, badges, locks, start here), a locked lesson by URL, the template sections, "Practise it" starts
the right drill.

**Done when.** ☐ Fabio's T44 decisions applied ☐ every lesson in the template ☐ his text review per topic ☐
locks work end to end (pass a drill → the next opens) ☐ screenshots ☐ ICT_CONCEPTS.md updated ☐ tests.

### T46 — Phones and tablets

**Goal.** Every screen usable below 1366 px down to a phone (390×844), built on the reflow-ready layouts of
T36–T45 (R-20). Breakpoints: ≥ 1280 desktop (designed), 1024–1279 laptop, 768–1023 tablet, < 768 phone.
- Shell: nav into a menu sheet; account menu inside it.
- Chart: the drawing bar becomes a bottom toolbar (or a "Draw" button); the glass layers menu full width;
  touch pan and zoom (Lightweight Charts supports them); drawing by touch (tap-tap for two-anchor tools).
- Practice session: the side panel becomes a bottom sheet with tabs (Plan · Replay · Result).
- Drills: tap to mark candles; the answer bar sticky at the bottom.
- Learn, Home, Practice home, News: single column.
Tests: layout tests at the breakpoints where logic changes; screenshots at 390×844, 768×1024, 1366×768.

### T47 — ES as a full instrument (BUG-094)

After NQ is "perfect" (Fabio's call): the instrument switch returns (Chart top bar "NQ ▾", practice start,
drills); the SMT layer returns to the Chart tab with ES beside; **BUG-094** (the Chart tab on ES shows no chart)
reproduced and fixed with a test — start by comparing ES's `dataFrom`/`dataTo` from `/api/market/instruments`
with the window the chart asks for (`clampEnd`, `windowDays`).

### T48 — Public landing page

The page before sign-in (route `/` when signed out): what Tradeez is, for whom, the method (Learn → Drill →
Practice), real screenshots, honesty (real data, not advice, simulated results), Sign up / Sign in. In the new
design system; English only; the disclaimer.

### T49 — AI chart assistant (last)

A chat beside the chart that answers questions about what is on it (Fabio: "after everything is done this will be
a last task"). Scoped when everything else is done: the provider (a Claude model via the API), a **budget
decision by Fabio** (no money spent without him), privacy and **no peeking** — in practice and drills the
assistant may only see what the learner has been shown (DEC-009, DEC-077).

---

## 7. Our code today, and what each piece becomes

| File (frontend/src) | Becomes | Task |
|---|---|---|
| `App.tsx` (sections by `useState`) | router + `AppShell` | T36 |
| `main.tsx`, `index.css`, `lib/styles.ts`, `components/ui.tsx` | fonts, tokens, the kit | T36 |
| `components/AccountMenu.tsx` | shell's account menu | T36 |
| `components/SignInForm.tsx`, `PasswordRules.tsx` | restyled (centred card) | T36 |
| `components/Guide.tsx` | `/help` page; the first-visit pop-up removed | T36 |
| `components/SimulatedDataBanner.tsx` | kept as a `Notice` | T36 |
| `components/ClockOverlay.tsx`, `SetupOverlay.tsx`, `DrawingOverlay.tsx`, `hooks/useOverlayCanvas.ts` | chart primitives | T37 |
| `components/drills/DrillOverlay.tsx`, learn example charts (their drawing) | primitives where they paint | T37 |
| `components/ChartWorkspace.tsx` | `chart/ChartPage.tsx` | T38 |
| `components/ClockToggles.tsx`, `SetupToggles.tsx` | `chart/LayersMenu.tsx` (deleted) | T38 |
| `components/DrawingToolbar.tsx` | restyled slim bar (lucide icons) | T38 |
| `components/PriceChart.tsx` | kept; mount option `interactive` | T38/T39 |
| `components/CompanionChart.tsx` | SMT lesson and drill only | T38 |
| — | `home/HomePage.tsx` | T39 |
| — | `news/NewsPage.tsx`, `lib/api/news.ts` | T40 |
| `components/practice/PracticeView.tsx` (home part), `StatisticsPanel.tsx` | `PracticeHome.tsx` | T41 |
| `PracticeView.tsx` (session part), `PracticeSession.tsx`, `PlanPanel.tsx`, `PlanIntent.tsx`, `ResultCard.tsx`, `TradeLog.tsx` | `SessionPage.tsx` + panel sections | T42 |
| `components/drills/DrillView.tsx`, `RiskDrills.tsx` | `DrillsPage.tsx`, drill and risk screens | T43 |
| `components/learn/LearnView.tsx`, `LessonCard.tsx`, `LearningProgress.tsx` | topic list + lesson template | T45 |

Backend touch points: practice settings/statistics/trades (T41), `com.tradeez.news` (T40), drills' random
timeframe (T43), curriculum topics and `learning-1.1.0` locks (T45), `docs/ICT_CONCEPTS.md` (T44/T45).

API in use today (for mapping): `/api/me`, `/api/auth/*`, `/api/market/instruments`, `…/bars`, `…/setups`,
`…/clock`, `/api/charts/{symbol}/drawings`, `/api/concepts`, `/api/concepts/{id}/lesson`, `/api/learning`,
`/api/learning/concepts/{id}/read`, `/api/drills`, `/api/drills/kinds`, `/api/drills/{id}/answer`,
`/api/drills/risk`, `/api/drills/risk/{id}/answer`, `/api/practice/sessions` (+ `/{id}`, `/advance`, `/skip`,
`/close`, `/plan`, `/context`, `/end`, `/clock`, `/setups`, `/trades/{n}/review`), `/api/practice/settings`,
`/api/practice/statistics`, `/api/practice/calibration`. `GET /api/learning` returns `summary` (concepts,
complete, rated, solid, due, ratedAttempts, predictions), `levels` (level, theme, open, complete,
unlocksWhen), `concepts` (id, state, attempts, recentCount, recentAccuracy, practised, readAt, lastPractised,
due, open), `suggested`, `due`.

## 8. Cross-cutting

- **Docs per task:** PATH.md (status, session log), this file's §1 and §10, DOCUMENTATION.md (a plain paragraph
  for the jury), DECISIONS.md (planned: DEC-078 shell/router/theme/fonts/motion · DEC-079 costs fixed at the
  standard · DEC-080 news · DEC-081 Learn topics and locks, `learning-1.1.0`), BUGS.md (BUG-095 fixed in T37,
  BUG-094 in T47), README features, `ICT_CONCEPTS.md` (T44/T45). Numbers are tentative — take the next free one.
- **Before/after for the jury:** `docs/redesign/before/` and `docs/redesign/after/<task>/` side by side.
- **Risks:** T37 (primitives — the hardest; do it overlay by overlay); T45's volume (dozens of lessons to write
  and review — topic by topic); the router change touching every test that renders `App`; Space Grotesk at small
  sizes (check legibility at 12–13 px; if poor, keep it for headings and use a neutral sans for small body text —
  ask Fabio); `backdrop-filter` blur over the chart canvas (check frame rate while panning with the menu open);
  the Forex Factory feed's availability and terms (T40 step 0).
- **Never:** invent numbers; show a future candle; spend money; enter real payment or credentials anywhere;
  paste or log keys.

## 9. References

- `docs/redesign/lovable/` — 01 sign-in · 02 sign-up · 03 home · 04 account menu · 05 footer "Read more" · 06
  learn · 07–09 a lesson (Rule, Examples, Stats) · 10 a "later" lesson · 11 drills · 12–14 pattern drill (before,
  marked, checked) · 15–16 risk drill (asked, answered) · 17–18 practice (tiles, a tile open) · 19–22 session
  (plan, short, placed, scorecard) · 23–27 chart (layers open, search, preset, ES beside, closed). `index.json`
  lists each file with its title.
- `docs/redesign/before/` — the app on 2026-10-23 (commit 291fceb): 01 sign-in … 15 account menu, titles in
  `index.json`.
- Lovable source: <https://github.com/FabioZetovic/futures-flow-studio> — `src/styles.css` (tokens),
  `src/components/AppShell.tsx`, `ui-kit.tsx` (Btn, Seg, PageHeader, NextStep, Ring, Logo), `routes/*.tsx`.

## 10. Decision log (filled while building)

| Date | Task | Decision | By |
|---|---|---|---|
| 2026-10-23 | all | The plan above (R-1 … R-20) | Fabio + Claude |
| 2026-10-23 | T36 | DEC-078: React Router (declarative), the accent `#7C6CFF` in one token, Space Grotesk + JetBrains Mono, `motion`, `lucide-react` | Claude |
| 2026-10-23 | T36 | `/` goes to the chart until Home exists (T39); Home and News join the nav with their tasks, so no link leads nowhere | Claude |
| 2026-10-23 | T36 | The kit grows as screens use it (knip forbids unused exports): T36 adds PageHeader and Glass; StatTile, Ring, ProgressBar, Switch, Badge, NextStep, Table come with T39+ | Claude |
| 2026-10-23 | T36 | The footer's short line keeps DEC-015's three points; "Read more" opens the full text | Claude |
| 2026-10-23 | T36 | The header is as wide as the page under it (full width on chart pages, 1280 on reading pages), not Lovable's fixed 1600, so the logo lines up with the content | Claude |
| 2026-10-23 | T36 | Hex colours, not the plan's oklch values: the canvases need the same values in `lib/theme.ts` | Claude |
| 2026-10-23 | T37 | One adapter method, `addLayer({ zOrder, paint })`, not the plan's `attachPrimitive`/`detachPrimitive` pair: the handle's `redraw` and `remove` are all a layer needs, and nothing outside the adapter sees the library's primitive types (DEC-004) | Claude |
| 2026-10-23 | T37 | The New York clock goes under the candles with its lines (one layer, as the plan says for shading); setups stay over the candles as before | Claude |
| 2026-10-23 | T37 | Lag is checked by photographing the chart mid-drag and straight after a wheel zoom (headless Chrome), not by a video | Claude |
| 2026-10-23 | T37 | A live drill was not started for the check: it is recorded on the test account and hides its candles from the Chart tab until answered (DEC-077). The drill layer is the same plumbing, covered by its tests | Claude |
| 2026-10-23 | T38 | The price change is measured from the previous session's close (the last candle before 17:00 New York the day before, or the same day once the evening session has begun; Friday's over a weekend) — a real value, shown only when the window reaches it | Claude |
| 2026-10-23 | T38 | Practice's session screen uses the same layers menu now, so `ClockToggles` and `SetupToggles` could be deleted as planned (T42 restyles the rest of that screen) | Claude |
| 2026-10-23 | T38 | SMT is left out of the layer list everywhere a single market is charted, and a choice of it remembered from before is never sent | Claude |
| 2026-10-23 | T38 | `Switch` joined the kit with its first user, the layers menu | Claude |
| 2026-10-23 | T38 | The Chart tab's `main` has a 12 px gutter (Lovable's `p-3`); the chart card fills the rest of the window's height | Claude |
| 2026-10-23 | T39 | Fabio agreed the progress definitions ("Go with that"): Learn = concepts complete ÷ concepts; Drills = pattern drills (marking and naming) whose concept is solid ÷ pattern drills; Practice = "N sessions · N trades"; Chart = no number | Fabio |
| 2026-10-23 | T39 | Practice so far shows the total R net of costs and the trade count; "held to plan" waits for T41's count, as planned | Claude |
| 2026-10-23 | T39 | The Home chart is the latest session (from the previous 17:00 New York close), killzones only, with the change from that close; `still` is a mount option of its own (no scroll, no scale in time or price) | Claude |
| 2026-10-23 | T39 | Numbers do not count up (§T36 step 4): left for the polish pass, not needed to read them | Claude |
| 2026-10-23 | T39 | No summary endpoint: Home makes eight small requests, all already there; decide again if it is ever slow | Claude |
| 2026-10-23 | T40 | Forex Factory's terms forbid republishing its calendar (step 0): stopped and asked | Claude |
| 2026-10-26 | T40 | Our own calendar from the official schedules, via FRED (free key) and the Fed's FOMC list — DEC-079 | Fabio |
| 2026-10-26 | T40 | Refreshed every six hours rather than yearly by hand, after Fabio asked about schedules that change ("shit changes in the market quick") | Fabio + Claude |
| 2026-10-26 | T40 | Not rate-limited like the chart's reads: one indexed query over a few rows | Claude |
| 2026-10-26 | T41 | Cost fields sent to the settings are refused with a 400, not ignored (the plan left it open) — DEC-080 | Claude |
| 2026-10-26 | T41 | The costs are an app setting (`tradeez.practice.costs`), the standard by default: the worked-example tests keep their simpler costs through it, per test context | Claude |
| 2026-10-26 | T41 | Held to plan = not closed by hand; a flat at the session's close counts as held | Claude |
| 2026-10-26 | T41 | A journal row opens its session; opening it at that very trade, and `/practice/:id` addresses, come with T42's session screen | Claude |
| 2026-10-26 | T42 | Practice keeps its own layers (`tradeez.practice.*`), all off at first; the Chart tab keeps its defaults | Claude |
| 2026-10-26 | T42 | The session screen is the existing `PracticeSession` rearranged, not a new `SessionPage`: every behaviour test carried over unchanged but for the layout | Claude |
| 2026-10-26 | T42 | The replay sits sticky at the column's foot, so it stays in reach however long the scorecard above it | Claude |
| 2026-10-26 | T42 | The plan panel, the scorecard and the review keep their own look for now; restyling their insides (Lovable's big R, the numbers box) is left for a polish pass | Claude |
| 2026-10-26 | T43 | A card's record is its concept's (S-2): two drills on one concept (swing highs and lows, ICT-01; sweeps and sweep-or-run, ICT-13) show the same record | Claude |
| 2026-10-26 | T43 | A prediction's or a managed trade's card shows "{n} answered" (`practised`), not an accuracy: those are practice, never a rating (DEC-059) | Claude |
| 2026-10-26 | T43 | The risk cards' clock and record come from a new `GET /api/drills/risk/kinds`; each kind's clock moved onto `RiskQuestion.Kind` so the card and the question cannot disagree | Claude |
| 2026-10-26 | T43 | The drill screen shows the timeframe the server drew ("NQ · 15m") beside the title: chosen at random, still said | Claude |
| 2026-10-26 | T43 | Risk addresses read `/drills/risk/position-size`, `losing-streak`, `recovery`; an unknown one goes back to the cards | Claude |
| 2026-10-26 | T43 | The cards read in the 1280 column; a drill's screen takes the width for its chart. A kind no topic names still gets a card under "More drills", so every drill stays reachable until T44/T45 regroup them | Claude |
| 2026-10-26 | T43 | `NextStep` joins the kit (first used here) | Claude |
| 2026-10-26 | T44 | Fabio decided on a page with Accept / Change per concept, his choices kept in the page's own store and read back by Claude: all 72 proposals accepted (DEC-081). The audit has 72 rows, not 84 — 84 was the highest concept number | Fabio |
| 2026-10-26 | T44 | Topics for T45: Structure · Liquidity · Gaps & imbalances · Premium & discount · Blocks · Bias · Models · Risk & management · Time & sessions (reference); a new learner starts at Structure | Fabio |

