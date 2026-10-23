import { describe, expect, it } from 'vitest'
import { drawsClock } from '../clock/layers'
import {
  previousSessionClose,
  chartWindow,
  clampEnd,
  JUMPS,
  newYorkDate,
  sessionEndOn,
  WINDOW_DAYS,
  zonedTimeToEpoch,
} from './dateNavigation'

const DAY = 86_400
const utc = (iso: string) => Date.parse(iso) / 1000

describe('moving around real history (T3)', () => {
  it('a picked date shows that day up to the 17:00 New York close, on both sides of DST', () => {
    // Friday 7 March 2025 is EST (UTC-5); Monday 10 March is EDT (UTC-4) — the clocks changed on
    // the 9th. A fixed offset would be an hour wrong on one side.
    expect(sessionEndOn('2025-03-07')).toBe(utc('2025-03-07T22:00:00Z'))
    expect(sessionEndOn('2025-03-10')).toBe(utc('2025-03-10T21:00:00Z'))
    expect(sessionEndOn('2025-11-03')).toBe(utc('2025-11-03T22:00:00Z'))
  })

  it('converts a wall-clock time in any zone, including on a clock-change day', () => {
    expect(zonedTimeToEpoch('2025-03-09', 12, 0, 'America/New_York')).toBe(utc('2025-03-09T16:00:00Z'))
    expect(zonedTimeToEpoch('2025-07-01', 17, 0, 'America/Chicago')).toBe(utc('2025-07-01T22:00:00Z'))
  })

  it('names an instant by its New York date, which can differ from the UTC date', () => {
    // 02:00 UTC on 4 March is still 3 March in New York.
    expect(newYorkDate(utc('2025-03-04T02:00:00Z'))).toBe('2025-03-03')
    expect(newYorkDate(sessionEndOn('2025-03-10'))).toBe('2025-03-10')
  })

  it('loads a calendar window per timeframe that stays under the backend cap of 20,000 bars', () => {
    const seconds: Record<string, number> = {
      '1m': 60, '3m': 180, '5m': 300, '15m': 900, '30m': 1800, '1h': 3600, '4h': 14_400, '1d': 86_400,
    }
    expect(Object.keys(WINDOW_DAYS)).toEqual(Object.keys(seconds))
    for (const [tf, days] of Object.entries(WINDOW_DAYS)) {
      expect((days * DAY) / seconds[tf]).toBeLessThan(20_000)
      // And inside the backend's widest range, 400 days.
      expect(days).toBeLessThanOrEqual(400)
      // Long enough that a window ending on a Monday morning still reaches back past the weekend.
      expect(days).toBeGreaterThanOrEqual(4)
    }
    expect(chartWindow('5m', 1_000_000_000)).toEqual({ from: 1_000_000_000 - 14 * DAY, to: 1_000_000_000 })
  })

  it('T35: where the New York clock is drawn, its range covers the whole window — 200 days at most', () => {
    // The server refuses a wider clock (ClockService.MAX_RANGE), and the overlay would quietly vanish.
    for (const [tf, days] of Object.entries(WINDOW_DAYS)) {
      if (drawsClock(tf)) expect(days, tf).toBeLessThanOrEqual(200)
    }
    expect(drawsClock('4h')).toBe(true)
    expect(drawsClock('1d')).toBe(false)
  })

  it('keeps the window end inside the imported data', () => {
    const from = utc('2024-10-08T00:00:00Z')
    const to = utc('2026-10-03T00:00:00Z')
    const window = 14 * DAY
    expect(clampEnd(to + 5 * DAY, from, to, window)).toBe(to)
    // Two years back lands before the first minute: show the first full window, not an empty or
    // one-day chart.
    expect(clampEnd(to - 730 * DAY, from, to, window)).toBe(from + window)
    const middle = utc('2025-06-01T00:00:00Z')
    expect(clampEnd(middle, from, to, window)).toBe(middle)
    // History shorter than a window: the newest minute still wins.
    expect(clampEnd(to - 730 * DAY, to - 3 * DAY, to, window)).toBe(to)
  })

  it('offers the jumps the curriculum talks about, newest first', () => {
    expect(JUMPS.map((j) => j.label)).toEqual(['Latest', '1W', '1M', '3M', '1Y', '2Y'])
    expect(JUMPS[0].days).toBe(0)
  })
})

describe('the previous session close, for the price change (T38)', () => {
  const at = (iso: string, close: number) => ({ time: Date.parse(iso) / 1000, close })
  // Thursday 1 Oct 2026 and Friday 2 Oct (EDT, UTC-4); the session closes at 17:00 New York = 21:00 UTC.
  const bars = [
    at('2026-10-01T20:55:00Z', 100), // Thursday's last candle before the close
    at('2026-10-01T22:00:00Z', 101), // Thursday evening: Friday's session
    at('2026-10-02T14:00:00Z', 105),
    at('2026-10-02T20:55:00Z', 110), // Friday's last candle
  ]

  it("is the day before's close during the day, and the same day's once the evening session begins", () => {
    expect(previousSessionClose(bars, Date.parse('2026-10-02T14:00:00Z') / 1000)).toBe(100)
    expect(previousSessionClose(bars, Date.parse('2026-10-01T22:00:00Z') / 1000)).toBe(100)
  })

  it("is Friday's over the weekend, and null when the window does not reach it", () => {
    expect(previousSessionClose(bars, Date.parse('2026-10-05T13:30:00Z') / 1000)).toBe(110) // Monday
    expect(previousSessionClose(bars.slice(1), Date.parse('2026-10-02T14:00:00Z') / 1000)).toBeNull()
  })
})
