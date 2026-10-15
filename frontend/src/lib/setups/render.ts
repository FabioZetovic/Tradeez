import type { Setup, SetupLayer } from '../api/setups'
import type { DomainBar } from '../chart/types'
import { timeToX } from '../chart/timeToX'
import type { Projection } from '../drawing/types'
import { toShapes, type SetupShape } from './shapes'

/**
 * Paints detected setups onto their overlay canvas (T9, S-3): a zone as a box with its consequent
 * encroachment dashed through the middle, a level as a line, a point as a mark. Where each one starts
 * and ends is decided in {@link toShapes}; this only paints.
 *
 * A plain function of (context, layer, bars, projection) like the clock and drawing renderers.
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

/** Past the newest candle: {@link timeToX} places it at that candle's right edge. */
const EDGE = Number.MAX_SAFE_INTEGER

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

  const shown = { ...layer, setups: layer.setups.filter((s) => options.shown.has(s.detector)) }
  for (const shape of toShapes(shown, step)) {
    const x0 = timeToX(shape.from, bars, step, projection)
    const x1 = timeToX(shape.to === 'edge' ? EDGE : shape.to, bars, step, projection)
    if (x0 === null || x1 === null || x1 < 0 || x0 > pane.width) continue
    const rgb = COLORS[shape.setup.direction]
    switch (shape.setup.shape) {
      case 'ZONE':
        drawZone(ctx, shape, x0, x1, rgb, projection)
        break
      case 'LEVEL':
        drawLevel(ctx, shape, x0, x1, rgb, projection)
        break
      case 'POINT': {
        const x = timeToX(shape.setup.anchor + step / 2, bars, step, projection)
        if (x !== null) drawPoint(ctx, shape, x, rgb, projection)
        break
      }
    }
  }
  ctx.restore()
}

function drawZone(
  ctx: CanvasRenderingContext2D,
  shape: SetupShape,
  x0: number,
  x1: number,
  rgb: string,
  projection: Projection,
): void {
  const { lowTicks, highTicks } = shape.setup
  const top = projection.priceToY(highTicks)
  const bottom = projection.priceToY(lowTicks)
  // Half-ticks: the midpoint of an odd number of ticks lies between two (INVARIANT 3, ICT-21).
  const ce = projection.priceToY((lowTicks + highTicks) / 2)
  if (top === null || bottom === null || ce === null) return
  const width = x1 - x0
  const height = Math.max(bottom - top, 1)
  const alpha = shape.faded ? 0.4 : 1
  ctx.fillStyle = `rgba(${rgb}, ${0.16 * alpha})`
  ctx.fillRect(x0, top, width, height)
  ctx.strokeStyle = `rgba(${rgb}, ${0.65 * alpha})`
  ctx.lineWidth = 1
  ctx.strokeRect(Math.round(x0) + 0.5, Math.round(top) + 0.5, Math.round(width), Math.round(height))
  ctx.setLineDash([3, 3])
  ctx.beginPath()
  ctx.moveTo(x0, Math.round(ce) + 0.5)
  ctx.lineTo(x1, Math.round(ce) + 0.5)
  ctx.stroke()
  if (shape.quadrants) {
    ctx.strokeStyle = `rgba(${rgb}, ${0.35 * alpha})`
    for (const q of [1, 3]) {
      const y = projection.priceToY(lowTicks + ((highTicks - lowTicks) * q) / 4)
      if (y === null) continue
      ctx.beginPath()
      ctx.moveTo(x0, Math.round(y) + 0.5)
      ctx.lineTo(x1, Math.round(y) + 0.5)
      ctx.stroke()
    }
  }
  ctx.setLineDash([])
  label(ctx, shape.label, x0 + 3, top + 2, width, height, rgb, alpha)
}

function drawLevel(
  ctx: CanvasRenderingContext2D,
  shape: SetupShape,
  x0: number,
  x1: number,
  rgb: string,
  projection: Projection,
): void {
  const y = projection.priceToY(shape.setup.lowTicks)
  if (y === null) return
  const yy = Math.round(y) + 0.5
  const alpha = shape.faded ? 0.4 : 1
  ctx.strokeStyle = `rgba(${rgb}, ${0.9 * alpha})`
  ctx.lineWidth = 1.5
  ctx.beginPath()
  ctx.moveTo(x0, yy)
  ctx.lineTo(x1, yy)
  ctx.stroke()
  label(ctx, shape.label, x0 + 3, yy - 13, x1 - x0, 12, rgb, alpha)
}

/** Over a high, under a low — where the eye looks for one — with its name beyond the mark. */
function drawPoint(
  ctx: CanvasRenderingContext2D,
  shape: SetupShape,
  x: number,
  rgb: string,
  projection: Projection,
): void {
  const y = projection.priceToY(shape.setup.lowTicks)
  if (y === null) return
  const below = !shape.above
  const tip = below ? y + 4 : y - 4
  const base = below ? tip + 6 : tip - 6
  ctx.fillStyle = `rgba(${rgb}, 0.9)`
  ctx.beginPath()
  ctx.moveTo(x, tip)
  ctx.lineTo(x - 4, base)
  ctx.lineTo(x + 4, base)
  ctx.closePath()
  ctx.fill()
  if (!shape.label) return
  const width = ctx.measureText(shape.label).width
  ctx.fillText(shape.label, x - width / 2, below ? base + 2 : base - 12)
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
  alpha: number,
): void {
  if (ctx.measureText(text).width + 6 > width || height < 12) return
  ctx.fillStyle = `rgba(${rgb}, ${0.95 * alpha})`
  ctx.fillText(text, x, y)
}
