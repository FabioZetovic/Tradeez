import { apiFetch } from './client'

/**
 * The economic calendar (T40, DEC-079): when the US releases that move NQ come out, from the agencies' own schedules.
 *
 * DEC-007 — mirrors the OpenAPI document for /api/news.
 */

export interface NewsEvent {
  key: string
  title: string
  agency: string
  /** Tradeez's own rating: the red and orange folders (R-8). */
  impact: 'HIGH' | 'MEDIUM'
  /** When it comes out, UTC epoch seconds. */
  at: number
  link: string
}

export interface News {
  events: NewsEvent[]
  /** The oldest of the releases' last good refreshes; absent until every one has had one. */
  checkedAt?: number
  /** The schedule may be out of date: never checked, or not for two days. */
  stale: boolean
  /** The last FOMC decision the schedule knows of, ISO date. */
  fomcKnownUntil: string
  fomcRunningOut: boolean
  sources: string
  /** FRED's terms ask for this wherever its data is used, with a link to them. */
  notice: string
  termsUrl: string
}

/** From this week's Monday in New York, two weeks, unless a window is given (UTC epoch seconds). */
export const getNews = (window?: { from: number; to: number }) =>
  apiFetch<News>(window ? `/api/news?from=${window.from}&to=${window.to}` : '/api/news')
