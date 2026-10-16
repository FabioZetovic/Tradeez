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
  /** Real data only: first stored minute (epoch seconds). Absent when nothing is imported. */
  dataFrom?: number
  /** Real data only: end of the last stored minute (epoch seconds). */
  dataTo?: number
}

export interface BarSeries {
  instrument: string
  timeframe: string
  dataClass: string
  /** Where the bars came from (INVARIANT 7): 'generated', or the importer, e.g. 'massive'. */
  source: string
  /** Generated data only. */
  seed?: number | null
  generatorVersion?: string | null
  /** Non-null for simulated data. Rendered verbatim — the UI does not compose its own wording. */
  disclaimer: string | null
  bars: DomainBar[]
}

/** 4h and 1d count from the 18:00 New York open of the trading day, as the server builds them (T35). */
export const TIMEFRAMES = ['1m', '3m', '5m', '15m', '30m', '1h', '4h', '1d'] as const
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
