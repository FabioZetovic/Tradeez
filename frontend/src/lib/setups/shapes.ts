import type { Setup, SetupLayer } from '../api/setups'

/**
 * What each detector's hits look like on a chart (T9, T10): the step between the rows the server
 * sends and the boxes the renderer paints. Pure, so the choices are testable without a canvas.
 *
 * Most hits are drawn over their own candles. Three are drawn on into the future, because the hit
 * is a reference that stays in force until something ends it:
 * - a gap's lifecycle (FVG_STATE): the latest state confirmed so far, extended while the gap is open,
 *   tapped or at its CE, and ending at the candle that filled, inverted or expired it;
 * - opening gaps (OPENING_GAP): an NDOG until the next closure, an NWOG to the right edge;
 * - opening range gaps (ORG): until the next one, with its quarters.
 * Nothing live is drawn past a contract roll: no detector sees across one.
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
}

/** Kinds of point that mark a high (T11): drawn above the candle. */
const HIGHS = new Set(['HIGH', 'ITH', 'LTH', 'HH', 'LH', 'EH'])

/** What a hit is called on the chart, where its kind or rule id would not read well. */
const LABELS: Record<string, string> = {
  SWING: '',
  DISPLACEMENT: 'Disp',
  CHOCH: 'CHoCH',
}

/** The order a gap's states are reached in, for hits confirmed at the same moment. */
const LIFECYCLE = ['OPEN', 'TAPPED', 'CE', 'FILLED', 'INVERTED', 'EXPIRED']
const LIVE = new Set(['OPEN', 'TAPPED', 'CE'])
const STATE_LABEL: Record<string, string> = {
  OPEN: 'FVG',
  TAPPED: 'FVG tapped',
  CE: 'FVG CE',
  FILLED: 'FVG filled',
  INVERTED: 'FVG inverted',
  EXPIRED: 'FVG expired',
}

export function toShapes(layer: SetupLayer, step: number): SetupShape[] {
  const rolls = [...layer.rolls].sort((a, b) => a - b)
  /** Where something still in force at {@code time} stops: the next roll, or the right edge. */
  const until = (time: number): number | 'edge' => rolls.find((r) => r > time) ?? 'edge'
  const shapes: SetupShape[] = []
  const lifecycles = new Map<string, Setup>()

  for (const setup of layer.setups) {
    const base = { setup, faded: false, quadrants: false, above: HIGHS.has(setup.kind ?? '') }
    switch (setup.detector) {
      case 'FVG_STATE': {
        const key = `${setup.anchor}|${setup.direction}`
        const seen = lifecycles.get(key)
        if (!seen || later(setup, seen)) lifecycles.set(key, setup)
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
          label: LABELS[setup.detector] ?? LABELS[setup.kind ?? ''] ?? setup.kind ?? setup.detector,
        })
    }
  }

  for (const state of lifecycles.values()) {
    const live = LIVE.has(state.kind ?? '')
    shapes.push({
      setup: state,
      from: state.start,
      to: live ? until(state.start) : state.end + step,
      label: STATE_LABEL[state.kind ?? ''] ?? 'FVG',
      faded: !live,
      quadrants: false,
      above: false,
    })
  }
  return shapes
}

function later(a: Setup, b: Setup): boolean {
  if (a.confirmedAt !== b.confirmedAt) return a.confirmedAt > b.confirmedAt
  return LIFECYCLE.indexOf(a.kind ?? '') > LIFECYCLE.indexOf(b.kind ?? '')
}

/** The earlier of a time and a time-or-edge. */
function firstNumber(a: number | 'edge', b: number): number {
  return a === 'edge' ? b : Math.min(a, b)
}
