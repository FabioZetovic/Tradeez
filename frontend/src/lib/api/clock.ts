import { apiFetch } from './client'

/**
 * The New York clock over a chart (T7, DEC-047): windows to shade and prices to draw, built on the
 * server from one-minute bars under versioned rules. Times are on the chart's own basis — real epoch
 * seconds on the Chart tab, the session's fictional week in practice.
 *
 * DEC-007 — mirrors the OpenAPI document until `npm run api:types` is wired in.
 */

type WindowKind = 'TRADING_DAY' | 'KILLZONE' | 'SILVER_BULLET' | 'MACRO'
type LineKind = 'OPEN' | 'PREVIOUS_DAY' | 'PREVIOUS_WEEK' | 'SESSION' | 'IPDA'

export interface ClockWindow {
  kind: WindowKind
  label: string
  from: number
  to: number
}

export interface ClockLine {
  kind: LineKind
  label: string
  from: number
  to: number
  /** Integer ticks (INVARIANT 3). */
  priceTicks: number
  /** A high or low: the minute that traded beyond it — its liquidity taken (T12). Absent while it rests. */
  takenAt?: number
}

export interface ClockLayer {
  rules: { sessions: string; opens: string; levels: string; liquidity: string; ipda: string }
  windows: ClockWindow[]
  lines: ClockLine[]
  /** Practice only: ask again once the cursor reaches this. */
  nextChange?: number
}

export const getChartClock = (symbol: string, from: number, to: number) =>
  apiFetch<ClockLayer>(
    `/api/market/instruments/${encodeURIComponent(symbol)}/clock?from=${from}&to=${to}`,
  )

export const getPracticeClock = (sessionId: string) =>
  apiFetch<ClockLayer>(`/api/practice/sessions/${encodeURIComponent(sessionId)}/clock`)
