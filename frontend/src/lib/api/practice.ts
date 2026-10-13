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
type PracticeStatus = 'ANALYSING' | 'IN_TRADE' | 'COMPLETE'
export type Outcome = 'TARGET' | 'STOP' | 'AMBIGUOUS' | 'TIME_EXIT'

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
  dataClass: string
  disclaimer?: string
  plan?: PlanView
  /** Absent until the replay reveals the bar the trade was entered in. */
  entry?: EntryView
  /** Absent until COMPLETE. */
  result?: ResultView
  /**
   * Absent until COMPLETE — the seed is the future (DEC-038). A decimal string, never parsed to a
   * number: a 64-bit seed above 2^53 would be silently rounded, and a rounded seed reproduces a
   * different market (BUG-028).
   */
  seed?: string
  generatorVersion: string
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
  direction?: Direction
  outcome?: Outcome
  netPnlCents?: number
  rMultiple?: number
}

export interface PlanRequest {
  direction: Direction
  stopTicks: number
  targetTicks: number
  contracts: number
}

export const PRACTICE_TIMEFRAMES = ['1m', '5m', '15m'] as const

export const createSession = (body: { instrument?: string; timeframe?: string }) =>
  apiFetch<SessionResponse>('/api/practice/sessions', {
    method: 'POST',
    body: JSON.stringify(body),
  })

export const listSessions = () => apiFetch<SessionSummary[]>('/api/practice/sessions')

export const getSession = (id: string) =>
  apiFetch<SessionResponse>(`/api/practice/sessions/${encodeURIComponent(id)}`)

export const submitPlan = (id: string, plan: PlanRequest) =>
  apiFetch<PracticeState>(`/api/practice/sessions/${encodeURIComponent(id)}/plan`, {
    method: 'POST',
    body: JSON.stringify(plan),
  })

export const advanceSession = (id: string, bars: number) =>
  apiFetch<SessionResponse>(`/api/practice/sessions/${encodeURIComponent(id)}/advance`, {
    method: 'POST',
    body: JSON.stringify({ bars }),
  })

export const skipToResult = (id: string) =>
  apiFetch<SessionResponse>(`/api/practice/sessions/${encodeURIComponent(id)}/skip`, {
    method: 'POST',
  })
