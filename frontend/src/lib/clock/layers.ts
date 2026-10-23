import type { ClockLine, ClockWindow } from '../api/clock'
import { KILLZONE_COLORS, THEME } from '../theme'

/**
 * The parts of the New York clock a learner can switch on and off (T7). One switch per idea, in the
 * order ICT teaches them: the day, its windows, then the prices read off the clock.
 */
export const CLOCK_LAYERS = [
  { key: 'days', label: 'Days', title: 'Trading-day separators: the CME day starts at 18:00 New York' },
  { key: 'killzones', label: 'Killzones', title: 'Asia 20–24, London 02–05, NY AM 08:30–11, NY PM 13:30–16' },
  { key: 'silverBullet', label: 'Silver Bullet', title: 'The Silver Bullet hours: 03–04, 10–11, 14–15' },
  { key: 'macros', label: 'Macros', title: 'Macro times, as commonly cited (sessions-1.0.0)' },
  { key: 'opens', label: 'Opens', title: 'Midnight, 08:30, 09:30 and weekly opening prices' },
  { key: 'previousDay', label: 'PDH/PDL', title: "The previous trading day's high and low" },
  { key: 'previousWeek', label: 'PWH/PWL', title: "The previous week's high and low" },
  { key: 'sessionLevels', label: 'Session H/L', title: "Each killzone's high and low, once it has closed" },
  {
    key: 'ipda',
    label: 'IPDA',
    title: 'The highs and lows of the last 20, 40 and 60 trading days (ipda-1.0.0), when all lie on one contract',
  },
] as const

export type ClockLayerKey = (typeof CLOCK_LAYERS)[number]['key']

/**
 * The clock is drawn on intraday charts, up to 4h (T35). On a daily chart each of its windows is a
 * sliver inside one candle and each day separator a candle's edge: nothing left to read.
 */
export function drawsClock(timeframe: string): boolean {
  return timeframe !== '1d'
}

/** What a first look shows (T38, Lovable's): the killzones and the prices most often read off the clock. */
export const DEFAULT_CLOCK_LAYERS: readonly ClockLayerKey[] = ['killzones', 'opens', 'previousDay']

export function windowLayer(window: ClockWindow): ClockLayerKey {
  switch (window.kind) {
    case 'TRADING_DAY':
      return 'days'
    case 'KILLZONE':
      return 'killzones'
    case 'SILVER_BULLET':
      return 'silverBullet'
    case 'MACRO':
      return 'macros'
  }
}

export function lineLayer(line: ClockLine): ClockLayerKey {
  switch (line.kind) {
    case 'OPEN':
      return 'opens'
    case 'PREVIOUS_DAY':
      return 'previousDay'
    case 'PREVIOUS_WEEK':
      return 'previousWeek'
    case 'SESSION':
      return 'sessionLevels'
    case 'IPDA':
      return 'ipda'
  }
}

/** A killzone's colour, from its name or from a level named after it ("London H"). */
export function killzoneColor(label: string): string {
  const name = Object.keys(KILLZONE_COLORS).find((k) => label === k || label.startsWith(`${k} `))
  return name ? KILLZONE_COLORS[name as keyof typeof KILLZONE_COLORS] : THEME.subtle
}
