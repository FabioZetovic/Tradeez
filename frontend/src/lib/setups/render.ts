import type { Setup, SetupLayer } from '../api/setups'
import type { DomainBar } from '../chart/types'
import { timeToX } from '../chart/timeToX'
import type { Projection } from '../drawing/types'

/**
 * Paints detected setups onto their overlay canvas (T9, S-3): a zone as a box over its own candles
 * with its consequent encroachment dashed through the middle, a level as a line, a point as a mark.
 *
 * A plain function of (context, layer, bars, projection) like the clock and drawing renderers. It
 * draws exactly what the rule claims — a zone spans the candles that formed it, and is not extended
 * to the right: whether price came back to it is a different rule (fvg-state, T10).
 */

export interface SetupRenderOptions {
  width: number
  height: number
  /** The plotting area; nothing is painted over the price or time scale. */
  pane: { width: number; height: number }
  /** Detector ids switched on. */
  shown: ReadonlySet<string>
  /** The chart's timeframe in seconds. */
  step: number
}

const FONT = '10px system-ui, sans-serif'

const COLORS: Record<Setup['direction'], string> = {
  BULLISH: '63, 185, 80',
  BEARISH: '248, 81, 73',
  NEUTRAL: '139, 148, 158',
}

export function renderSetups(
  ctx: CanvasRenderingContext2D,
  layer: SetupLayer | null,
  bars: readonly DomainBar[],
  projection: Projection,
  options: SetupRenderOptions,
): void {
  ctx.clearRect(0, 0, options.width, options.height)
  if (!layer || bars.length === 0) return

  const { pane, step } = options
  ctx.save()
  ctx.beginPath()
  ctx.rect(0, 0, pane.width, pane.height)
  ctx.clip()
  ctx.font = FONT
  ctx.textBaseline = 'top'

  for (const setup of layer.setups) {
    if (!options.shown.has(setup.detector)) continue
    // From the first candle's left edge to the last one's right edge.
    const x0 = timeToX(setup.start, bars, step, projection)
    const x1 = timeToX(setup.end + step, bars, step, projection)
    if (x0 === null || x1 === null || x1 < 0 || x0 > pane.width) continue
    const rgb = COLORS[setup.direction]
    switch (setup.shape) {
      case 'ZONE':
        drawZone(ctx, setup, x0, x1, rgb, projection)
        break
      case 'LEVEL':
        drawLevel(ctx, setup, x0, x1, rgb, projection)
        break
      case 'POINT': {
        const x = timeToX(setup.anchor + step / 2, bars, step, projection)
        if (x !== null) drawPoint(ctx, setup, x, rgb, projection)
        break
      }
    }
  }
  ctx.restore()
}

function drawZone(
  ctx: CanvasRenderingContext2D,
  setup: Setup,
  x0: number,
  x1: number,
  rgb: string,
  projection: Projection,
): void {
  const top = projection.priceToY(setup.highTicks)
  const bottom = projection.priceToY(setup.lowTicks)
  // Half-ticks: the midpoint of an odd number of ticks lies between two (INVARIANT 3, ICT-21).
  const ce = projection.priceToY((setup.lowTicks + setup.highTicks) / 2)
  if (top === null || bottom === null || ce === null) return
  const width = x1 - x0
  const height = Math.max(bottom - top, 1)
  ctx.fillStyle = `rgba(${rgb}, 0.16)`
  ctx.fillRect(x0, top, width, height)
  ctx.strokeStyle = `rgba(${rgb}, 0.65)`
  ctx.lineWidth = 1
  ctx.strokeRect(Math.round(x0) + 0.5, Math.round(top) + 0.5, Math.round(width), Math.round(height))
  ctx.setLineDash([3, 3])
  ctx.beginPath()
  ctx.moveTo(x0, Math.round(ce) + 0.5)
  ctx.lineTo(x1, Math.round(ce) + 0.5)
  ctx.stroke()
  ctx.setLineDash([])
  label(ctx, setup.detector, x0 + 3, top + 2, width, height, rgb)
}

function drawLevel(
  ctx: CanvasRenderingContext2D,
  setup: Setup,
  x0: number,
  x1: number,
  rgb: string,
  projection: Projection,
): void {
  const y = projection.priceToY(setup.lowTicks)
  if (y === null) return
  const yy = Math.round(y) + 0.5
  ctx.strokeStyle = `rgba(${rgb}, 0.9)`
  ctx.lineWidth = 1.5
  ctx.beginPath()
  ctx.moveTo(x0, yy)
  ctx.lineTo(x1, yy)
  ctx.stroke()
  label(ctx, setup.detector, x0 + 3, yy - 13, x1 - x0, 12, rgb)
}

/** Under a bullish point's price, over a bearish one's: where the eye looks for a low or a high. */
function drawPoint(
  ctx: CanvasRenderingContext2D,
  setup: Setup,
  x: number,
  rgb: string,
  projection: Projection,
): void {
  const y = projection.priceToY(setup.lowTicks)
  if (y === null) return
  const below = setup.direction !== 'BEARISH'
  const tip = below ? y + 4 : y - 4
  const base = below ? tip + 6 : tip - 6
  ctx.fillStyle = `rgba(${rgb}, 0.9)`
  ctx.beginPath()
  ctx.moveTo(x, tip)
  ctx.lineTo(x - 4, base)
  ctx.lineTo(x + 4, base)
  ctx.closePath()
  ctx.fill()
}

/** Text only where it fits inside the shape: a label spilling out would name the wrong one. */
function label(
  ctx: CanvasRenderingContext2D,
  text: string,
  x: number,
  y: number,
  width: number,
  height: number,
  rgb: string,
): void {
  if (ctx.measureText(text).width + 6 > width || height < 12) return
  ctx.fillStyle = `rgba(${rgb}, 0.95)`
  ctx.fillText(text, x, y)
}
