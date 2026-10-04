/**
 * Time-axis formatting for the chart (BUG-014).
 *
 * Everything in this project is stored in UTC (INVARIANT 4), and showing UTC on the axis is
 * unambiguous — but it is not how a trader reads a chart. An ICT curriculum is built on session
 * times: the London open, the New York open, the previous session's high. Mentally offsetting every
 * label by five or six hours, and remembering which of those it is this month, is exactly the
 * friction that makes killzone training harder than the concept itself.
 *
 * So the axis renders in **New York time** by default — the clock every ICT time window is stated in
 * (killzones, the Silver Bullet, the midnight open; ICT_CONCEPTS.md, DEC-043) — with the exchange's
 * own zone (Chicago for CME) and UTC one click away. Daylight saving is handled by `Intl`, which
 * knows that New York was UTC-5 in January and UTC-4 in July — getting that wrong would mislabel
 * every session boundary for half the year.
 */

export type AxisTimezone = 'new-york' | 'exchange' | 'utc'

/** ICT states every time in New York local time. */
export const NEW_YORK = 'America/New_York'

/** The IANA zone a mode renders in. */
export function zoneFor(exchangeTimezone: string, mode: AxisTimezone): string {
  if (mode === 'utc') return 'UTC'
  if (mode === 'new-york') return NEW_YORK
  return exchangeTimezone
}

/** The toggle's order: New York → exchange → UTC → New York. */
export function nextAxisTimezone(mode: AxisTimezone): AxisTimezone {
  if (mode === 'new-york') return 'exchange'
  if (mode === 'exchange') return 'utc'
  return 'new-york'
}

export function createTimeFormatter(exchangeTimezone: string, mode: AxisTimezone) {
  const timeZone = zoneFor(exchangeTimezone, mode)

  const formatter = new Intl.DateTimeFormat('en-GB', {
    timeZone,
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  })

  const suffix = mode === 'utc' ? ' UTC' : ''

  return (epochSeconds: number): string =>
    formatter.format(new Date(epochSeconds * 1000)) + suffix
}

/** What a tick mark on the time axis marks. Mirrors the renderer's own categories. */
export type TickKind = 'year' | 'month' | 'day' | 'time' | 'seconds'

/**
 * Labels for the tick marks along the time axis (BUG-029).
 *
 * The crosshair label and the axis ticks are formatted by two different hooks in the renderer, and
 * the BUG-014 fix only set the first. The chart then showed Chicago time in the crosshair and UTC
 * along the axis, six hours apart, on the same screen.
 *
 * One limit is deliberate. The renderer places day, month and year ticks at *UTC* boundaries, and
 * moving them would mean shifting every timestamp handed to it — which breaks wherever a zone's
 * offset changes. So a "day" tick is labelled with what it truly is in the chosen zone — a weekday
 * and a time, e.g. "Wed 18:00" — rather than with a date that would suggest local midnight. Hour
 * and minute ticks are unaffected: the exchange zones used here are whole hours from UTC.
 */
export function createTickMarkFormatter(exchangeTimezone: string, mode: AxisTimezone) {
  const timeZone = zoneFor(exchangeTimezone, mode)
  const time = new Intl.DateTimeFormat('en-GB', {
    timeZone,
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  })
  const seconds = new Intl.DateTimeFormat('en-GB', {
    timeZone,
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false,
  })
  const weekday = new Intl.DateTimeFormat('en-GB', { timeZone, weekday: 'short' })
  const month = new Intl.DateTimeFormat('en-GB', { timeZone, month: 'short' })
  const year = new Intl.DateTimeFormat('en-GB', { timeZone, year: 'numeric' })

  return (epochSeconds: number, kind: TickKind): string => {
    const at = new Date(epochSeconds * 1000)
    switch (kind) {
      case 'year':
        return year.format(at)
      case 'month':
        return month.format(at)
      case 'day':
        return `${weekday.format(at)} ${time.format(at)}`
      case 'seconds':
        return seconds.format(at)
      case 'time':
        return time.format(at)
    }
  }
}

/** Short label for the timezone toggle, e.g. "EDT", "CST" or "UTC". */
export function timezoneLabel(exchangeTimezone: string, mode: AxisTimezone, at: Date): string {
  if (mode === 'utc') return 'UTC'
  const timeZone = zoneFor(exchangeTimezone, mode)
  // en-US, not en-GB: only the US locale renders American zone abbreviations (EDT/CST). en-GB
  // gives "GMT-5", which is correct but not how a trader labels a chart.
  const parts = new Intl.DateTimeFormat('en-US', {
    timeZone,
    timeZoneName: 'short',
  }).formatToParts(at)
  return parts.find((p) => p.type === 'timeZoneName')?.value ?? timeZone
}

/**
 * A single instant for prose, in the exchange's zone: "Tue 09:35 CDT".
 *
 * Weekday and time, no date. On simulated data the date means nothing, while the weekday and the
 * session time are exactly what a learner reasons about — Monday's open, the New York session.
 */
export function formatSessionTime(epochSeconds: number, exchangeTimezone: string): string {
  const at = new Date(epochSeconds * 1000)
  const text = new Intl.DateTimeFormat('en-GB', {
    timeZone: exchangeTimezone,
    weekday: 'short',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }).format(at)
  return `${text} ${timezoneLabel(exchangeTimezone, 'exchange', at)}`
}
