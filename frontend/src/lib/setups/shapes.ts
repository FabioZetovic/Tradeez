import type { Setup, SetupLayer } from '../api/setups'

/**
 * What each detector's hits look like on a chart (T9, T10): the step between the rows the server
 * sends and the boxes the renderer paints. Pure, so the choices are testable without a canvas.
 *
 * Most hits are drawn over their own candles. Some are drawn on into the future, because the hit
 * is a reference that stays in force until something ends it:
 * - a gap's lifecycle (FVG_STATE): the latest state confirmed so far, extended while the gap is open,
 *   tapped or at its CE, and ending at the candle that filled, inverted or expired it;
 * - a block's (OB, BREAKER, MITIGATION, REJECTION, UNICORN, T17) the same way: on while it stands —
 *   formed, tapped, its mean threshold lost — and ending at the candle that invalidated or expired it;
 * - a range's (DEALING_RANGE, its premium and discount halves PD, and its OTE, T18): on until a candle
 *   closes beyond the range, either way, or it expires;
 * - standard deviation projections (STDEV): each level until the next leg's same level, in its direction;
 * - opening gaps (OPENING_GAP): an NDOG until the next closure, an NWOG to the right edge;
 * - opening range gaps (ORG): until the next one, with its quarters.
 * Nothing live is drawn past a contract roll: no detector sees across one.
 *
 * The rules read off a day, a week or a session open once it is over (PO3, DAILY_PROFILE, WEEKLY_PROFILE,
 * JUDAS, T19) are drawn over their own candles, named for what they found. A day's or a week's range is
 * framed, not shaded: shaded, it would cover every candle in it, and two or three of them stack.
 *
 * Swing liquidity (LIQUIDITY) is one line per level at its latest state: running on while it rests,
 * stopped and faded once taken or expired.
 *
 * Swings and their ranks are points: above a high, below a low. A break of structure or a market
 * structure shift is a line at the level that broke, from the swing to the candle that broke it.
 */

export interface SetupShape {
  setup: Setup
  /** Chart time the shape starts at. */
  from: number
  /** Chart time it ends at, or the right edge of the newest candle. */
  to: number | 'edge'
  label: string
  /** Over and done with: drawn fainter. */
  faded: boolean
  /** Quarter lines inside the zone, as ICT reads opening range gaps (ICT-28). */
  quadrants: boolean
  /** A point drawn above its price — a high — rather than below it. */
  above: boolean
  /** A whole day's or week's range (T19): outlined, not filled. */
  framed: boolean
}

/** Kinds of point that mark a high (T11): drawn above the candle. */
const HIGHS = new Set(['HIGH', 'ITH', 'LTH', 'HH', 'LH', 'EH'])

/** The rules whose zone is a whole day or week (T19). */
const PERIODS = new Set(['PO3', 'DAILY_PROFILE', 'WEEKLY_PROFILE'])

/** A day's or a week's name on the chart (T19): the profile, the weekday, the window. */
function timeLabel(setup: Setup): string | undefined {
  switch (setup.detector) {
    case 'PO3':
      return 'Po3'
    case 'DAILY_PROFILE':
      return { LONDON_LOW: 'London low', LONDON_HIGH: 'London high', NY_REVERSAL: 'NY reversal' }[setup.kind ?? '']
    case 'WEEKLY_PROFILE':
      return `${(setup.kind ?? '').slice(0, 1)}${(setup.kind ?? '').slice(1, 3).toLowerCase()} ${setup.direction === 'BULLISH' ? 'low' : 'high'} of week`
    case 'JUDAS':
      return setup.kind === 'LONDON' ? 'Judas London' : 'Judas NY'
  }
  return undefined
}

/** What a hit is called on the chart, where its kind or rule id would not read well. */
const LABELS: Record<string, string> = {
  SWING: '',
  DISPLACEMENT: 'Disp',
  CHOCH: 'CHoCH',
  TURTLE_SOUP: 'Turtle soup',
  SWEEP: 'Sweep',
  RUN: 'Run',
}

/**
 * Rules whose hits are one zone's states, in the order they are reached (for hits confirmed at the
 * same moment), which of them leave the zone in force, and the zone's name on the chart.
 */
interface Lifecycle {
  order: readonly string[]
  live: ReadonlySet<string>
  /** The zone's name, or how to name it from the hit: premium or discount by its side. */
  name: string | ((setup: Setup) => string)
}
const GAP: Omit<Lifecycle, 'name'> = {
  order: ['OPEN', 'TAPPED', 'CE', 'FILLED', 'INVERTED', 'EXPIRED'],
  live: new Set(['OPEN', 'TAPPED', 'CE']),
}
const BLOCK: Omit<Lifecycle, 'name'> = {
  order: ['FORMED', 'TAPPED', 'MEAN_THRESHOLD', 'INVALIDATED', 'EXPIRED'],
  live: new Set(['FORMED', 'TAPPED', 'MEAN_THRESHOLD']),
}
const RANGE: Omit<Lifecycle, 'name'> = {
  order: ['FORMED', 'TAPPED', 'BROKEN_HIGH', 'BROKEN_LOW', 'EXPIRED'],
  live: new Set(['FORMED', 'TAPPED']),
}
const LIFECYCLES: Record<string, Lifecycle> = {
  FVG_STATE: { ...GAP, name: 'FVG' },
  OB: { ...BLOCK, name: 'OB' },
  BREAKER: { ...BLOCK, name: 'Breaker' },
  MITIGATION: { ...BLOCK, name: 'Mitigation' },
  REJECTION: { ...BLOCK, name: 'Rejection' },
  UNICORN: { ...BLOCK, name: 'Unicorn' },
  DEALING_RANGE: { ...RANGE, name: 'Range' },
  PD: { ...RANGE, name: (s) => (s.direction === 'BEARISH' ? 'Premium' : 'Discount') },
  OTE: { ...RANGE, name: 'OTE' },
}

/** stdev-1.0.0's kinds, which hold no digits, as the projections they are. */
const DEVIATIONS: Record<string, string> = { ONE: 'SD −1', TWO: 'SD −2', TWO_AND_A_HALF: 'SD −2.5', FOUR: 'SD −4' }
/** What a state adds to the zone's name: nothing for the one it starts in. */
const STATE_WORDS: Record<string, string> = {
  CE: 'CE',
  TAPPED: 'tapped',
  FILLED: 'filled',
  INVERTED: 'inverted',
  MEAN_THRESHOLD: 'MT lost',
  INVALIDATED: 'invalidated',
  BROKEN_HIGH: 'high broken',
  BROKEN_LOW: 'low broken',
  EXPIRED: 'expired',
}

export function toShapes(layer: SetupLayer, step: number): SetupShape[] {
  const rolls = [...layer.rolls].sort((a, b) => a - b)
  /** Where something still in force at {@code time} stops: the next roll, or the right edge. */
  const until = (time: number): number | 'edge' => rolls.find((r) => r > time) ?? 'edge'
  const shapes: SetupShape[] = []
  const lifecycles = new Map<string, Setup>()
  const liquidity = new Map<string, Setup>()

  for (const setup of layer.setups) {
    const base = {
      setup,
      faded: false,
      quadrants: false,
      above: HIGHS.has(setup.kind ?? ''),
      framed: PERIODS.has(setup.detector),
    }
    if (setup.detector in LIFECYCLES) {
      const key = `${setup.detector}|${setup.anchor}|${setup.direction}`
      const seen = lifecycles.get(key)
      if (!seen || later(setup, seen)) lifecycles.set(key, setup)
      continue
    }
    switch (setup.detector) {
      case 'LIQUIDITY': {
        const key = `${setup.anchor}|${setup.direction}`
        const seen = liquidity.get(key)
        if (!seen || setup.confirmedAt > seen.confirmedAt) liquidity.set(key, setup)
        break
      }
      case 'OPENING_GAP': {
        const next =
          setup.kind === 'NDOG'
            ? layer.setups.find((s) => s.detector === 'OPENING_GAP' && s.anchor > setup.anchor)
            : undefined
        shapes.push({
          ...base,
          from: setup.anchor,
          // Until the last candle before the next closure.
          to: next ? firstNumber(until(setup.anchor), next.start + step) : until(setup.anchor),
          label: setup.kind ?? setup.detector,
        })
        break
      }
      case 'STDEV': {
        // Until the next leg's projection of the same multiple, in the same direction.
        const next = layer.setups.find(
          (s) => s.detector === 'STDEV' && s.kind === setup.kind && s.direction === setup.direction && s.anchor > setup.anchor,
        )
        const end = until(setup.anchor)
        shapes.push({
          ...base,
          from: setup.anchor,
          to: next ? firstNumber(end, next.anchor) : end,
          label: DEVIATIONS[setup.kind ?? ''] ?? 'SD',
        })
        break
      }
      case 'ORG': {
        // Until the next one's close candle: through the regular session, to 16:15.
        const next = layer.setups.find((s) => s.detector === 'ORG' && s.anchor > setup.anchor)
        const end = until(setup.anchor)
        shapes.push({
          ...base,
          from: setup.anchor,
          to: next ? firstNumber(end, next.start + step) : end,
          label: 'ORG',
          quadrants: true,
        })
        break
      }
      default:
        shapes.push({
          ...base,
          from: setup.start,
          to: setup.end + step,
          label: timeLabel(setup) ?? LABELS[setup.detector] ?? LABELS[setup.kind ?? ''] ?? setup.kind ?? setup.detector,
        })
    }
  }

  for (const level of liquidity.values()) {
    const resting = level.kind === 'RESTING'
    shapes.push({
      setup: level,
      from: level.start,
      to: resting ? until(level.start) : level.end + step,
      // Sell-side under a low, buy-side over a high: swing highs point down (DEC-053).
      label: level.direction === 'BEARISH' ? 'BSL' : 'SSL',
      faded: !resting,
      quadrants: false,
      above: false,
      framed: false,
    })
  }

  for (const state of lifecycles.values()) {
    const { live, name } = LIFECYCLES[state.detector]
    const stillLive = live.has(state.kind ?? '')
    shapes.push({
      setup: state,
      from: state.start,
      to: stillLive ? until(state.start) : state.end + step,
      label: [typeof name === 'string' ? name : name(state), STATE_WORDS[state.kind ?? '']].filter(Boolean).join(' '),
      faded: !stillLive,
      quadrants: false,
      above: false,
      framed: false,
    })
  }
  return shapes
}

function later(a: Setup, b: Setup): boolean {
  if (a.confirmedAt !== b.confirmedAt) return a.confirmedAt > b.confirmedAt
  const { order } = LIFECYCLES[a.detector]
  return order.indexOf(a.kind ?? '') > order.indexOf(b.kind ?? '')
}

/** The earlier of a time and a time-or-edge. */
function firstNumber(a: number | 'edge', b: number): number {
  return a === 'edge' ? b : Math.min(a, b)
}
