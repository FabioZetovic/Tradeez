import { apiFetch } from './client'

/**
 * What versioned rules found over a chart (T9, DEC-049): only hits confirmed by the chart's right
 * edge or the replay's cursor. Times are on the chart's own basis — real epoch seconds on the Chart
 * tab, the session's fictional week in practice.
 *
 * DEC-007 — mirrors the OpenAPI document until `npm run api:types` is wired in.
 */

export interface Rule {
  detector: string
  version: string
  conceptId: string
  parameters: Record<string, unknown>
  methodologySource: string
}

export interface Setup {
  detector: string
  /** ZONE spans lowTicks..highTicks; LEVEL and POINT are one price. */
  shape: 'ZONE' | 'LEVEL' | 'POINT'
  direction: 'BULLISH' | 'BEARISH' | 'NEUTRAL'
  /** Candle open times. */
  start: number
  anchor: number
  end: number
  /** When the hit could first be known. */
  confirmedAt: number
  /** Integer ticks (INVARIANT 3). */
  lowTicks: number
  highTicks: number
}

export interface SetupLayer {
  rules: Rule[]
  setups: Setup[]
}

export const getChartSetups = (
  symbol: string,
  timeframe: string,
  from: number,
  to: number,
  detectors: readonly string[],
) =>
  apiFetch<SetupLayer>(
    `/api/market/instruments/${encodeURIComponent(symbol)}/setups?timeframe=${encodeURIComponent(timeframe)}` +
      `&from=${from}&to=${to}&detectors=${detectors.map(encodeURIComponent).join(',')}`,
  )

export const getPracticeSetups = (sessionId: string, detectors: readonly string[]) =>
  apiFetch<SetupLayer>(
    `/api/practice/sessions/${encodeURIComponent(sessionId)}/setups?detectors=${detectors
      .map(encodeURIComponent)
      .join(',')}`,
  )
