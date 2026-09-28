import { apiFetch } from './client'
import type { DomainBar } from '../chart/types'

/**
 * Market data calls.
 *
 * DEC-007 — these shapes mirror the OpenAPI document. When `npm run api:types` is wired into the
 * flow they should be replaced by the generated types rather than maintained by hand.
 */

export interface Instrument {
  symbol: string
  displayName: string
  exchange: string
  currency: string
  /** Smallest price increment, e.g. 0.25. Everything price-shaped is a multiple of this. */
  tickSize: number
  /** Cash value of one tick, in cents. 500 = $5.00. */
  tickValueCents: number
  /** SIMULATED | IMPORTED_PRIVATE | LICENSED_DEPLOYABLE — see DEC-033/034/035. */
  dataClass: string
  exchangeTimezone: string
}

export interface BarSeries {
  instrument: string
  timeframe: string
  dataClass: string
  seed: number | null
  generatorVersion: string
  /** Non-null for simulated data. Rendered verbatim — the UI does not compose its own wording. */
  disclaimer: string | null
  bars: DomainBar[]
}

export const TIMEFRAMES = ['1m', '3m', '5m', '15m', '30m', '1h'] as const
export type TimeframeCode = (typeof TIMEFRAMES)[number]

export const listInstruments = () => apiFetch<Instrument[]>('/api/market/instruments')

/**
 * Bars for the public chart.
 *
 * There is deliberately no seed parameter. The backend serves one fixed public market and rejects
 * any attempt to choose another (DEC-038): a simulated market is a pure function of its seed, so a
 * choosable seed would let anyone read a practice session's future.
 */
export function getBars(params: {
  symbol: string
  timeframe: string
  from: number
  to: number
}): Promise<BarSeries> {
  const query = new URLSearchParams({
    timeframe: params.timeframe,
    from: String(params.from),
    to: String(params.to),
  })
  return apiFetch<BarSeries>(
    `/api/market/instruments/${encodeURIComponent(params.symbol)}/bars?${query}`,
  )
}

/**
 * A window of generated history ending at a fixed point.
 *
 * The end is deliberately a constant rather than `Date.now()`. The generator is a pure function of
 * time (DEC-012), so anchoring to "now" would mean the chart showed different data on every reload
 * and no two people could ever discuss the same candle. A fixed anchor makes the demo reproducible.
 */
export const DEMO_ANCHOR_EPOCH = 1710338400 // 2024-03-13 14:00 UTC, mid-session

export function demoWindow(timeframe: string, barCount = 200): { from: number; to: number } {
  const seconds: Record<string, number> = {
    '1m': 60, '3m': 180, '5m': 300, '15m': 900, '30m': 1800, '1h': 3600,
  }
  const span = (seconds[timeframe] ?? 300) * barCount
  return { from: DEMO_ANCHOR_EPOCH - span, to: DEMO_ANCHOR_EPOCH }
}
