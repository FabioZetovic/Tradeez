import { describe, expect, it } from 'vitest'
import {
  createTickMarkFormatter,
  createTimeFormatter,
  formatSessionTime,
  nextAxisTimezone,
  timezoneLabel,
  zoneFor,
} from './timeAxis'

const CHICAGO = 'America/Chicago'

// 2024-03-13 14:00 UTC. Chicago was on daylight saving (UTC-5) that day, so 09:00 local.
const SUMMER = Date.UTC(2024, 2, 13, 14, 0, 0) / 1000
// 2024-01-10 14:00 UTC. Chicago on standard time (UTC-6), so 08:00 local.
const WINTER = Date.UTC(2024, 0, 10, 14, 0, 0) / 1000

describe('time axis', () => {
  it('renders UTC verbatim when asked', () => {
    expect(createTimeFormatter(CHICAGO, 'utc')(SUMMER)).toBe('14:00 UTC')
  })

  it('renders exchange-local time by default', () => {
    expect(createTimeFormatter(CHICAGO, 'exchange')(SUMMER)).toBe('09:00')
  })

  it('applies the right offset on each side of a daylight-saving change', () => {
    // The whole reason Intl does this rather than a hardcoded offset. Chicago is UTC-5 in March and
    // UTC-6 in January; a fixed offset would mislabel every session boundary for half the year, and
    // killzone timings would be taught an hour wrong.
    const exchange = createTimeFormatter(CHICAGO, 'exchange')
    expect(exchange(SUMMER)).toBe('09:00')
    expect(exchange(WINTER)).toBe('08:00')
  })

  it('labels the timezone as the exchange observes it at that moment', () => {
    expect(timezoneLabel(CHICAGO, 'exchange', new Date(SUMMER * 1000))).toBe('CDT')
    expect(timezoneLabel(CHICAGO, 'exchange', new Date(WINTER * 1000))).toBe('CST')
    expect(timezoneLabel(CHICAGO, 'utc', new Date())).toBe('UTC')
  })
})

describe('formatSessionTime', () => {
  it('names the weekday and exchange time, with the zone, and no date', () => {
    // 2024-03-13 14:00 UTC is a Wednesday, 09:00 in Chicago (CDT, UTC-5).
    const text = formatSessionTime(1710338400, 'America/Chicago')
    expect(text).toBe('Wed 09:00 CDT')
    expect(text).not.toMatch(/2024|Mar|13/)
  })

  it('follows daylight saving: the same UTC hour is 08:00 CST in January', () => {
    // 2024-01-10 14:00 UTC.
    expect(formatSessionTime(1704895200, 'America/Chicago')).toBe('Wed 08:00 CST')
  })
})

describe('createTickMarkFormatter (BUG-029)', () => {
  // 2024-03-13 14:00 UTC = 09:00 CDT. 2024-02-22 00:00 UTC = Wed 21 Feb 18:00 CST.
  const NINE_CDT = 1710338400
  const UTC_MIDNIGHT = 1708560000

  it('labels hour ticks in exchange time, matching the crosshair', () => {
    const tick = createTickMarkFormatter('America/Chicago', 'exchange')
    expect(tick(NINE_CDT, 'time')).toBe('09:00')
    expect(tick(NINE_CDT, 'time')).toBe(createTimeFormatter('America/Chicago', 'exchange')(NINE_CDT))
    expect(tick(NINE_CDT + 5, 'seconds')).toBe('09:00:05')
  })

  it('labels a day tick with its true local weekday and time, not a misleading date', () => {
    // The renderer puts day ticks at UTC midnight, which is early evening in Chicago.
    const tick = createTickMarkFormatter('America/Chicago', 'exchange')
    expect(tick(UTC_MIDNIGHT, 'day')).toBe('Wed 18:00')
  })

  it('keeps UTC when UTC is chosen', () => {
    const tick = createTickMarkFormatter('America/Chicago', 'utc')
    expect(tick(NINE_CDT, 'time')).toBe('14:00')
    expect(tick(UTC_MIDNIGHT, 'day')).toBe('Thu 00:00')
  })

  it('labels month and year ticks in the chosen zone', () => {
    const tick = createTickMarkFormatter('America/Chicago', 'exchange')
    // 2024-01-01 00:00 UTC is still 31 Dec 2023 in Chicago.
    expect(tick(1704067200, 'year')).toBe('2023')
    expect(tick(1704067200, 'month')).toBe('Dec')
  })
})

describe('New York time (T3, DEC-043)', () => {
  // ICT states every window in New York time, so it is the chart's default clock.
  it('renders New York time, one hour ahead of Chicago all year', () => {
    // SUMMER is 14:00 UTC under EDT (UTC-4) → 10:00; WINTER is 14:00 UTC under EST (UTC-5) → 09:00.
    expect(createTimeFormatter(CHICAGO, 'new-york')(SUMMER)).toBe('10:00')
    expect(createTimeFormatter(CHICAGO, 'new-york')(WINTER)).toBe('09:00')
    expect(createTickMarkFormatter(CHICAGO, 'new-york')(SUMMER, 'time')).toBe('10:00')
  })

  it('labels the toggle EDT or EST by the date, not by the instrument', () => {
    expect(timezoneLabel(CHICAGO, 'new-york', new Date(SUMMER * 1000))).toBe('EDT')
    expect(timezoneLabel(CHICAGO, 'new-york', new Date(WINTER * 1000))).toBe('EST')
  })

  it('cycles New York → exchange → UTC → New York', () => {
    expect(nextAxisTimezone('new-york')).toBe('exchange')
    expect(nextAxisTimezone('exchange')).toBe('utc')
    expect(nextAxisTimezone('utc')).toBe('new-york')
  })

  it('maps each mode to its zone', () => {
    expect(zoneFor(CHICAGO, 'new-york')).toBe('America/New_York')
    expect(zoneFor(CHICAGO, 'exchange')).toBe(CHICAGO)
    expect(zoneFor(CHICAGO, 'utc')).toBe('UTC')
  })
})

describe('the hidden-date clock (T4, DEC-025)', () => {
  // Monday 09:35 on the fictional week: New York wall-clock time written as UTC.
  const SHOWN = Date.UTC(2000, 0, 10, 9, 35) / 1000

  it('renders the time as given, labelled NY, with no EST/EDT to give the season away', () => {
    expect(createTimeFormatter(CHICAGO, 'wall-clock')(SHOWN)).toBe('09:35')
    expect(timezoneLabel(CHICAGO, 'wall-clock', new Date(SHOWN * 1000))).toBe('NY')
    expect(formatSessionTime(SHOWN, CHICAGO, 'NEW_YORK_WALL_CLOCK')).toBe('Mon 09:35 NY')
  })

  it('has no other clock to switch to', () => {
    expect(nextAxisTimezone('wall-clock')).toBe('wall-clock')
  })
})
