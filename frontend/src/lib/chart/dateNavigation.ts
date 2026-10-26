import { NEW_YORK } from './timeAxis'

/**
 * Moving around real history (T3, DEC-040).
 *
 * The chart shows a window of history ending at a chosen moment: the newest imported minute by
 * default, or the close of any day the learner picks. Dates are New York dates, like everything
 * else ICT talks about (DEC-043).
 */

const DAY = 86_400

/**
 * Calendar days loaded per timeframe. Sized so every timeframe gets a few thousand candle slots —
 * well under the backend's 20,000-bar cap — and so that a window crossing a weekend still holds a
 * few days of trading.
 */
export const WINDOW_DAYS: Record<string, number> = {
  '1m': 4,
  '3m': 10,
  '5m': 14,
  '15m': 40,
  '30m': 80,
  '1h': 160,
  // The New York clock's widest range (ClockService.MAX_RANGE), so it covers the whole window.
  '4h': 200,
  // The backend's widest range: about 280 trading days. No clock on daily charts (drawsClock).
  '1d': 400,
}

/** "5m" → 300. */
export function timeframeSeconds(code: string): number {
  const unit = code.slice(-1)
  const n = Number(code.slice(0, -1))
  return unit === 'm' ? n * 60 : unit === 'h' ? n * 3600 : unit === 's' ? n : n * 86_400
}

export function windowDays(timeframe: string): number {
  return WINDOW_DAYS[timeframe] ?? 14
}

/** The stretch of chart to load: {@link windowDays} of history ending at {@code end}. */
export function chartWindow(timeframe: string, end: number): { from: number; to: number } {
  return { from: end - windowDays(timeframe) * DAY, to: end }
}

/** Quick jumps back from the newest data. */
export const JUMPS = [
  { label: 'Latest', days: 0 },
  { label: '1W', days: 7 },
  { label: '1M', days: 30 },
  { label: '3M', days: 91 },
  { label: '1Y', days: 365 },
  { label: '2Y', days: 730 },
] as const

/**
 * Keeps a window's end inside the data: never after the newest minute, and never so early that the
 * window would reach back before the first one. Jumping further back than the history goes shows
 * its first full window, not one lonely day (found by clicking "2Y" in the running app).
 */
export function clampEnd(end: number, dataFrom: number, dataTo: number, windowSeconds = DAY): number {
  return Math.min(dataTo, Math.max(dataFrom + windowSeconds, end))
}

/**
 * Epoch seconds of a wall-clock time in a zone — "17:00 on 2025-03-10 in New York".
 *
 * `Intl` can format an instant in a zone but cannot parse a local time in one. So: read the wall
 * time as if it were UTC, measure the zone's offset there, correct, and measure once more — the second
 * pass only matters on the days the clocks change.
 */
export function zonedTimeToEpoch(date: string, hour: number, minute: number, timeZone: string): number {
  const [y, m, d] = date.split('-').map(Number)
  const wallAsUtc = Date.UTC(y, m - 1, d, hour, minute) / 1000
  const first = wallAsUtc - offsetSeconds(wallAsUtc, timeZone)
  return wallAsUtc - offsetSeconds(first, timeZone)
}

/** A zone's offset from UTC at an instant, in seconds: New York in summer is −14,400. */
function offsetSeconds(epoch: number, timeZone: string): number {
  const parts = new Intl.DateTimeFormat('en-US', {
    timeZone,
    hourCycle: 'h23',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  }).formatToParts(new Date(epoch * 1000))
  const get = (type: string) => Number(parts.find((p) => p.type === type)?.value)
  const local = Date.UTC(get('year'), get('month') - 1, get('day'), get('hour'), get('minute'), get('second'))
  return local / 1000 - epoch
}

/**
 * The end of a trading day in New York: 17:00, when CME stops for the day. Picking a date shows that
 * day's trading up to its close.
 */
export function sessionEndOn(date: string): number {
  return zonedTimeToEpoch(date, 17, 0, NEW_YORK)
}

/** "YYYY-MM-DD" of an instant in New York, for a date input. */
export function newYorkDate(epoch: number): string {
  return new Intl.DateTimeFormat('en-CA', {
    timeZone: NEW_YORK,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(new Date(epoch * 1000))
}

/**
 * The close of the trading session before the one `time` is in (T38's price change): the last candle before 17:00
 * New York of the day before — or of the same day, once the evening session has begun. Over a weekend that is
 * Friday's last candle. Null when the window does not reach back that far.
 */
export function previousSessionClose(bars: ReadonlyArray<{ time: number; close: number }>, time: number): number | null {
  const today = sessionEndOn(newYorkDate(time))
  const boundary = time >= today ? today : sessionEndOn(newYorkDate(today - DAY))
  for (let i = bars.length - 1; i >= 0; i--) if (bars[i].time < boundary) return bars[i].close
  return null
}
