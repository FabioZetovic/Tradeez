import { describe, expect, it } from 'vitest'
import { createTimeFormatter, timezoneLabel } from './timeAxis'

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
