import { apiFetch } from './client'
import type { DomainBar } from '../chart/types'

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
  /** Per contract, after slippage, before commission. */
  pnlTicks: number
  /** All contracts, commission included. */
  netPnlCents: number
  commissionCents: number
  maeTicks: number
  mfeTicks: number
  gapExit: boolean
  rMultiple: number
  resolution: string
  engineVersion: string
  fillModelVersion: string
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
}

/** The trades whose results have been revealed, added up. A running trade counts for nothing. */
export interface ScoreView {
  closedTrades: number
  /** Trades that made ticks before commission. */
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
}

export const PRACTICE_TIMEFRAMES = ['1m', '5m', '15m'] as const

/** ICT killzones in New York time (sessions-1.0.0, DEC-043), for choosing a practice moment. */
export const KILLZONES = [
  { value: 'ASIA', label: 'Asia · 20:00–24:00' },
  { value: 'LONDON', label: 'London · 02:00–05:00' },
  { value: 'NEW_YORK_AM', label: 'New York AM · 08:30–11:00' },
  { value: 'NEW_YORK_PM', label: 'New York PM · 13:30–16:00' },
] as const

export const WEEKDAYS = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY'] as const

export const createSession = (body: {
  instrument?: string
  timeframe?: string
  /** One of KILLZONES' values; absent means any time. */
  killzone?: string
  /** One of WEEKDAYS; absent means any day. */
  weekday?: string
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

export const submitPlan = (id: string, plan: PlanRequest) =>
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

/** Only while flat. Reveals the seed or the real date; ending twice changes nothing. */
export const endSession = (id: string) =>
  apiFetch<PracticeState>(`/api/practice/sessions/${encodeURIComponent(id)}/end`, {
    method: 'POST',
  })
