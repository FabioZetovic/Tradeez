import { apiFetch } from './client'
import type { DomainBar } from '../chart/types'
import type { SetupLayer } from './setups'

/**
 * Practice API calls.
 *
 * DEC-007 — these shapes mirror the OpenAPI document for /api/practice and should be replaced by
 * the generated types once `npm run api:types` is wired in.
 *
 * Fields that describe the future are optional, not merely nullable: the server leaves null fields
 * out of the JSON entirely, so "not revealed yet" arrives as an absent key.
 */

export type Direction = 'LONG' | 'SHORT'
/** ANALYSING is flat: no trade running. COMPLETE is ended: no more trades (DEC-046). */
type PracticeStatus = 'ANALYSING' | 'IN_TRADE' | 'COMPLETE'
export type Outcome = 'TARGET' | 'STOP' | 'AMBIGUOUS' | 'TIME_EXIT' | 'CLOSED'

export type TimeBasis = 'UTC' | 'NEW_YORK_WALL_CLOCK'

export interface PlanView {
  direction: Direction
  stopTicks: number
  targetTicks: number
  contracts: number
  /** The last visible close when the plan was locked. */
  referenceTicks: number
  /** The cursor when the plan was locked, UTC epoch seconds. */
  lockedAt: number
  plannedRewardRisk: number
  /** T22: the model named, the learner's bias and expected liquidity — each absent when not given. */
  model?: TradingModel
  bias?: Bias
  dolTicks?: number
  /** T22: what the app saw when it was placed. */
  killzone?: string
  suggestedBias?: Bias
  checklist?: ChecklistView
  /** T23: how sure the learner was, in percent, and the risk limit they had set then — each absent when not. */
  confidence?: number
  riskLimitCents?: number
  /** T28: where some of the contracts come off, how many, and whether the stop then moves — absent without one. */
  partialTicks?: number
  partialContracts?: number
  breakevenAfterPartial?: boolean
}

/** T28: the contracts a partial took off — when, where, and what each made before commission. */
export interface PartialView {
  time: number
  ticks: number
  contracts: number
  pnlTicks: number
  /** Filled at an opening price beyond the level. */
  gap: boolean
}

/** ICT's entry models a trade can follow (T22, DEC-066), in the curriculum's order. */
export const MODELS = [
  { value: 'MODEL_2022', label: 'The 2022 model' },
  { value: 'SILVER_BULLET', label: 'Silver Bullet' },
  { value: 'OTE', label: 'OTE model' },
  { value: 'UNICORN', label: 'Unicorn model' },
  { value: 'TURTLE_SOUP', label: 'Turtle soup model' },
  { value: 'BREAKER', label: 'Breaker or mitigation entry' },
  { value: 'SESSION_REVERSAL', label: 'Session open reversal' },
  { value: 'OPENING_RANGE_GAP', label: 'Opening range gap model' },
] as const
export type TradingModel = (typeof MODELS)[number]['value']
export type Bias = 'BULLISH' | 'BEARISH'

/** A model's checklist, checked against the rules confirmed by the cursor. */
interface ChecklistView {
  model: TradingModel
  title: string
  version: string
  items: { id: string; text: string; met: boolean }[]
  met: number
  total: number
}

/** What the app sees for a bracket being drafted (T22). */
export interface PlanContext {
  killzone?: string
  suggestedBias?: Bias
  biasReasoning: string
  biasRule: string
  checklist?: ChecklistView
}

interface EntryView {
  time: number
  ticks: number
}

export interface ResultView {
  outcome: Outcome
  /** Present when the outcome is an assumption rather than a fact. Must be shown (INVARIANT 6). */
  assumption?: string
  entryTime: number
  entryTicks: number
  exitTime: number
  exitTicks: number
  /** Per contract held to the exit — every one, or those a partial left — after slippage, before commission. */
  pnlTicks: number
  /** All contracts, commission included. */
  netPnlCents: number
  commissionCents: number
  maeTicks: number
  mfeTicks: number
  gapExit: boolean
  /** The net result, costs paid, in multiples of the planned risk (DEC-076). */
  rMultiple: number
  resolution: string
  engineVersion: string
  fillModelVersion: string
  /** T28: the contracts a partial took off before the exit; absent when none did. */
  partial?: PartialView
}

/** One trade of the replay. Only the last can still be running. */
export interface TradeView {
  /** 1, 2, 3… in the order the trades were locked. */
  number: number
  plan: PlanView
  /** Absent until the replay reveals the bar the trade was entered in. */
  entry?: EntryView
  /** Absent until the replay has passed the exit. */
  result?: ResultView
  /** T23: outcome and process apart, nothing added up. Absent until the replay has passed the exit. */
  scorecard?: ScorecardView
  /** T28: the partial's fill, once the replay has passed it — while the rest still runs, too. */
  partial?: PartialView
}

/** One statement about a trade; a verdict only where the rule is the learner's own (DEC-027). */
export interface ScoreLine {
  id: string
  label: string
  text: string
  verdict?: 'KEPT' | 'BROKEN'
}

export interface ScorecardView {
  version: string
  outcome: ScoreLine[]
  process: ScoreLine[]
}

/** Stated confidence against how often the target came first (calibration-1.0.0, T23). */
export interface Calibration {
  version: string
  question: string
  trades: number
  decided: number
  hits: number
  meanConfidence?: number
  low?: number
  high?: number
  closedByHand: number
  timeExits: number
  ambiguous: number
  buckets: { confidence: number; trades: number; hits: number }[]
  summary: string[]
}

/** The trades whose results have been revealed, added up. A running trade counts for nothing. */
export interface ScoreView {
  closedTrades: number
  /** Trades that made money once every cost was paid (DEC-076). */
  wins: number
  losses: number
  totalR: number
  netPnlCents: number
}

export interface PracticeState {
  id: string
  instrument: string
  tickSize: number
  tickValueCents: number
  /** IANA zone for labelling times, e.g. America/Chicago. */
  exchangeTimezone: string
  timeframe: string
  status: PracticeStatus
  startTime: number
  /** Nothing at or after this instant has been revealed. */
  cursor: number
  /**
   * How to read every time in this session. UTC: real epoch seconds. NEW_YORK_WALL_CLOCK: a real
   * session behind a hidden date (DEC-025) — New York wall-clock time written as UTC on a fictional
   * week. Render it in UTC, label it New York, and never show its date.
   */
  timeBasis: TimeBasis
  dataClass: string
  disclaimer?: string
  trades: TradeView[]
  score: ScoreView
  /**
   * Absent until COMPLETE — the seed is the future (DEC-038). A decimal string, never parsed to a
   * number: a 64-bit seed above 2^53 would be silently rounded, and a rounded seed reproduces a
   * different market (BUG-028).
   */
  seed?: string
  /** Simulated data only. */
  generatorVersion?: string
  /** Real data only: where the bars came from, e.g. massive. */
  dataSource?: string
  /** Real data only, present once COMPLETE: the hidden moment's real time — the date reveal. */
  revealedStartTime?: number
}

export interface SessionResponse {
  state: PracticeState
  bars: DomainBar[]
}

export interface SessionSummary {
  id: string
  instrument: string
  timeframe: string
  status: PracticeStatus
  createdAt: number
  /** Trades placed, the running one included. */
  trades: number
  score: ScoreView
}

export interface PlanRequest {
  direction: Direction
  stopTicks: number
  targetTicks: number
  contracts: number
  /** T28: some of the contracts off at a price before the target, and optionally the stop to the entry after. */
  partialTicks?: number
  partialContracts?: number
  breakevenAfterPartial?: boolean
}

/** What the learner means by the trade (T22), and how sure they are (T23): all optional. */
export interface PlanIntent {
  model?: TradingModel
  bias?: Bias
  dolTicks?: number
  /** 10, 20 … 90: percent sure the target comes before the stop. */
  confidence?: number
}


/** ICT killzones in New York time (sessions-1.0.0, DEC-043), for choosing a practice moment. */
export const KILLZONES = [
  { value: 'ASIA', label: 'Asia · 20:00–24:00' },
  { value: 'LONDON', label: 'London · 02:00–05:00' },
  { value: 'NEW_YORK_AM', label: 'New York AM · 08:30–11:00' },
  { value: 'NEW_YORK_PM', label: 'New York PM · 13:30–16:00' },
] as const


/**
 * A new session on NQ (T41): the session time is the one choice — no timeframe (the server's 5m) and no weekday. The
 * API still takes both.
 */
export const createSession = (body: {
  instrument?: string
  /** One of KILLZONES' values; absent means any time. */
  killzone?: string
}) =>
  apiFetch<SessionResponse>('/api/practice/sessions', {
    method: 'POST',
    body: JSON.stringify(body),
  })

export const listSessions = () => apiFetch<SessionSummary[]>('/api/practice/sessions')

/** The view timeframe (T34) is optional: absent, the session's own. */
const onTimeframe = (timeframe?: string) =>
  timeframe ? `?timeframe=${encodeURIComponent(timeframe)}` : ''

export const getSession = (id: string, timeframe?: string) =>
  apiFetch<SessionResponse>(`/api/practice/sessions/${encodeURIComponent(id)}${onTimeframe(timeframe)}`)

/** The killzone, the daily-bias suggestion and the named model's checklist for a bracket being drafted. */
export const getPlanContext = (id: string, body: { direction: Direction; stopTicks: number; targetTicks: number; model?: TradingModel }) =>
  apiFetch<PlanContext>(`/api/practice/sessions/${encodeURIComponent(id)}/context`, {
    method: 'POST',
    body: JSON.stringify(body),
  })

export const submitPlan = (id: string, plan: PlanRequest & PlanIntent) =>
  apiFetch<PracticeState>(`/api/practice/sessions/${encodeURIComponent(id)}/plan`, {
    method: 'POST',
    body: JSON.stringify(plan),
  })

/** Steps {@code bars} candles of {@code timeframe}; a candle still forming counts as the first. */
export const advanceSession = (id: string, bars: number, timeframe?: string) =>
  apiFetch<SessionResponse>(`/api/practice/sessions/${encodeURIComponent(id)}/advance`, {
    method: 'POST',
    body: JSON.stringify({ bars, timeframe }),
  })

export const skipToResult = (id: string, timeframe?: string) =>
  apiFetch<SessionResponse>(`/api/practice/sessions/${encodeURIComponent(id)}/skip${onTimeframe(timeframe)}`, {
    method: 'POST',
  })

/** Closes the running trade at the next price, and moves the replay to the fill (DEC-057). */
export const closeTrade = (id: string, timeframe?: string) =>
  apiFetch<SessionResponse>(`/api/practice/sessions/${encodeURIComponent(id)}/close${onTimeframe(timeframe)}`, {
    method: 'POST',
  })

/** One moment of a trade's review (review-1.0.0, T24), on the session's clock. */
export interface ReviewMoment {
  time: number
  /** Before the decision, from the decision to the exit, or after it. */
  phase: 'BEFORE' | 'TRADE' | 'AFTER'
  kind: 'DECISION' | 'ENTRY' | 'RULE' | 'DRAW' | 'PARTIAL' | 'EXIT' | 'HELD'
  title: string
  /** A rule's hit: why it qualified, in the rule's own numbers. */
  lines: string[]
}

export interface TradeReviewView {
  version: string
  rules: string[]
  moments: ReviewMoment[]
  /** What has no moment: a draw not reached, the bracket's exit still ahead, hits not listed. */
  summary: string[]
  /** The hits told, to draw on the chart. */
  layer: SetupLayer
}

/** Only once the replay has passed the trade's exit (409 before). */
export const getTradeReview = (id: string, number: number) =>
  apiFetch<TradeReviewView>(`/api/practice/sessions/${encodeURIComponent(id)}/trades/${number}/review`)

/** Finished trades added up (journal-stats-1.0.0, T25): the count behind every figure. */
export interface StatisticsGroup {
  label: string
  trades: number
  wins: number
  losses: number
  winRate?: number
  avgWinR?: number
  avgLossR?: number
  /** The mean result in R, and its 95% range (absent below two trades). */
  expectancyR?: number
  low?: number
  high?: number
  profitFactor?: number
  totalR: number
  netPnlCents: number
  /** The range in words. */
  reading: string
}

export interface Statistics {
  version: string
  overall: StatisticsGroup
  /** T41: trades not closed by hand before the plan's own exit. */
  heldToPlan: number
  /** T41: fees and slippage over every trade, as each scorecard counts them. */
  costsPaidCents: number
  /** T41: the best and worst trade, R; absent without trades. */
  bestR?: number
  worstR?: number
  dimensions: { id: string; title: string; groups: StatisticsGroup[] }[]
  notes: string[]
}

export const getStatistics = () => apiFetch<Statistics>('/api/practice/statistics')

export const getCalibration = () => apiFetch<Calibration>('/api/practice/calibration')

/** The learner's risk limit as set (T23), and the costs every trade pays — fixed at the standard (T41, DEC-080). */
export interface PracticeSettings {
  riskLimitCents?: number
  /** Commission and fees per contract, round trip. */
  feesCents: number
  /** Ticks the entry and a close at market slip. */
  marketSlippageTicks: number
  /** Ticks a stop fill slips. */
  stopSlippageTicks: number
  /** Ticks price must trade beyond a target before it fills: 0, the touch. */
  limitThroughTicks: number
  /** The fill model these make, as results store it. */
  fillModel: string
}

export const getPracticeSettings = () => apiFetch<PracticeSettings>('/api/practice/settings')

/** Sets the risk limit, or unsets it with undefined. Costs are not the learner's to set (DEC-080). */
export const saveRiskLimit = (riskLimitCents: number | undefined) =>
  apiFetch<PracticeSettings>('/api/practice/settings', {
    method: 'PUT',
    body: JSON.stringify({ riskLimitCents }),
  })

/** One finished trade in the journal (T41), newest placed first. */
export interface JournalTrade {
  sessionId: string
  /** 1, 2, 3… within its session. */
  number: number
  /** When the plan was locked, read by timeBasis: the session's own clock while it is open, the real moment after. */
  at: number
  timeBasis: TimeBasis
  instrument: string
  tickSize: number
  direction: Direction
  entryTicks: number
  outcome: 'TARGET' | 'STOP' | 'AMBIGUOUS' | 'TIME_EXIT' | 'CLOSED'
  /** Net of costs (DEC-076). */
  r: number
  netPnlCents: number
  model?: string
}

export const listTrades = (limit = 50) => apiFetch<JournalTrade[]>(`/api/practice/trades?limit=${limit}`)

/** Only while flat. Reveals the seed or the real date; ending twice changes nothing. */
export const endSession = (id: string) =>
  apiFetch<PracticeState>(`/api/practice/sessions/${encodeURIComponent(id)}/end`, {
    method: 'POST',
  })
