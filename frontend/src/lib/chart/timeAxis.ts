/**
 * Time-axis formatting for the chart (BUG-014).
 *
 * Everything in this project is stored in UTC (INVARIANT 4), and showing UTC on the axis is
 * unambiguous — but it is not how a trader reads a chart. An ICT curriculum is built on session
 * times: the London open, the New York open, the previous session's high. Mentally offsetting every
 * label by five or six hours, and remembering which of those it is this month, is exactly the
 * friction that makes killzone training harder than the concept itself.
 *
 * So the axis renders in the exchange's own timezone by default, with UTC available for anyone who
 * wants the raw stored value. Daylight saving is handled by `Intl`, which knows that America/Chicago
 * was UTC-6 in January and UTC-5 in July — getting that wrong would mislabel every session boundary
 * for half the year.
 */

export type AxisTimezone = 'exchange' | 'utc'

export function createTimeFormatter(exchangeTimezone: string, mode: AxisTimezone) {
  const timeZone = mode === 'utc' ? 'UTC' : exchangeTimezone

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

/** Short label for the timezone toggle, e.g. "CDT" or "UTC". */
export function timezoneLabel(exchangeTimezone: string, mode: AxisTimezone, at: Date): string {
  if (mode === 'utc') return 'UTC'
  // en-US, not en-GB: only the US locale renders American zone abbreviations (CDT/CST). en-GB
  // gives "GMT-5", which is correct but not how a trader labels a chart.
  const parts = new Intl.DateTimeFormat('en-US', {
    timeZone: exchangeTimezone,
    timeZoneName: 'short',
  }).formatToParts(at)
  return parts.find((p) => p.type === 'timeZoneName')?.value ?? exchangeTimezone
}
