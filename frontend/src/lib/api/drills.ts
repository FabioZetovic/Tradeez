import { apiFetch } from './client'
import type { DomainBar } from '../chart/types'

/**
 * Drill API calls.
 *
 * DEC-007 — mirrors the OpenAPI document for /api/drills. Fields that exist only after the drill is
 * answered are optional: the server leaves null fields out of the JSON entirely.
 */

export interface RuleView {
  detector: string
  version: string
  parameters: Record<string, number>
  /** The rule in words, generated server-side from the same parameters. */
  definition: string
  methodologySource: string
}

/** The numbers the rule decided on. Explanations are rendered from these, never hand-written. */
export interface FvgEvidence {
  candle1Time: number
  candle1HighTicks: number
  candle1LowTicks: number
  candle2Time: number
  candle3Time: number
  candle3HighTicks: number
  candle3LowTicks: number
  gapTicks: number
  sumRangeTicks: number
  lookback: number
  thresholdTicks: number
}

export interface SetupView {
  id: string
  direction: 'BULLISH' | 'BEARISH'
  startTime: number
  /** The middle candle — the one to mark. */
  anchorTime: number
  endTime: number
  zoneLowTicks: number
  zoneHighTicks: number
  found: boolean
  evidence: FvgEvidence
}

export interface ResultView {
  found: number
  missed: number
  extra: number
  setups: SetupView[]
  /** Marked candles that are not the middle of a qualifying gap. */
  extraMarks: number[]
}

export interface DrillResponse {
  id: string
  kind: string
  instrument: string
  tickSize: number
  exchangeTimezone: string
  timeframe: string
  dataClass: string
  disclaimer?: string
  /** True on simulated data: a developer fixture, never graded (DEC-034). */
  fixture: boolean
  graded: boolean
  fixtureNotice?: string
  instructions: string
  rule: RuleView
  bars: DomainBar[]
  marks?: number[]
  result?: ResultView
}

export const DRILL_TIMEFRAMES = ['5m', '15m'] as const

export const startDrill = (body: { kind?: string; instrument?: string; timeframe?: string }) =>
  apiFetch<DrillResponse>('/api/drills', { method: 'POST', body: JSON.stringify(body) })

export const getDrill = (id: string) =>
  apiFetch<DrillResponse>(`/api/drills/${encodeURIComponent(id)}`)

export const answerDrill = (id: string, marks: number[]) =>
  apiFetch<DrillResponse>(`/api/drills/${encodeURIComponent(id)}/answer`, {
    method: 'POST',
    body: JSON.stringify({ marks }),
  })
