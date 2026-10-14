import type { ClockLayer, ClockLine, ClockWindow } from '../api/clock'
import type { DomainBar } from '../chart/types'
import { formatPrice } from '../chart/priceFormat'
import type { Projection } from '../drawing/types'
import { killzoneColor, lineLayer, windowLayer, type ClockLayerKey } from './layers'

/**
 * Paints the New York clock onto its overlay canvas: windows as shading, prices as lines.
 *
 * A plain function of (context, layer, bars, projection) like the drawing renderer, so it can be
 * tested with a recording context. The clock sits under the drawings and over the candles, faintly:
 * it is context for reading price, not something to read instead of it.
 */

export interface ClockRenderOptions {
  width: number
  height: number
  /** The plotting area; nothing is painted over the price or time scale. */
  pane: { width: number; height: number }
  shown: ReadonlySet<ClockLayerKey>
  /** The chart's timeframe in seconds. */
  step: number
  tickSize: number
}

/** The killzone strip along the top, and the thinner Silver Bullet and macro strip under it. */
const STRIP = 14
const SUBSTRIP = 6

const FONT = '10px system-ui, sans-serif'

/**
 * Where a time falls on screen.
 *
 * The chart can place bar times only. Any other time is placed by proportion inside the bar that
 * contains it — 08:30 sits in the middle of an hourly 08:00 candle. A time inside a closed market
 * sits at the edge of the last bar before it, because the chart draws gaps with no width; a time
 * past the newest bar sits at that bar's right edge, which is where a running session ends for now.
 * Null when the chart cannot place the bars at all.
 */
export function timeToX(
  time: number,
  bars: readonly DomainBar[],
  step: number,
  projection: Projection,
): number | null {
  if (bars.length === 0) return null
  // The last bar opening at or before the time.
  let lo = 0
  let hi = bars.length - 1
  let index = -1
  while (lo <= hi) {
    const mid = (lo + hi) >> 1
    if (bars[mid].time <= time) {
      index = mid
      lo = mid + 1
    } else {
      hi = mid - 1
    }
  }
  const at = Math.max(index, 0)
  const x = projection.timeToX(bars[at].time)
  if (x === null) return null
  const spacing = barSpacing(at, bars, projection)
  if (index === -1) return x - spacing / 2
  const fraction = Math.min(time - bars[at].time, step) / step
  return x - spacing / 2 + fraction * spacing
}

function barSpacing(index: number, bars: readonly DomainBar[], projection: Projection): number {
  const neighbour = index + 1 < bars.length ? index + 1 : index - 1
  if (neighbour < 0) return 6
  const a = projection.timeToX(bars[index].time)
  const b = projection.timeToX(bars[neighbour].time)
  return a === null || b === null ? 6 : Math.abs(b - a)
}

export function renderClock(
  ctx: CanvasRenderingContext2D,
  layer: ClockLayer | null,
  bars: readonly DomainBar[],
  projection: Projection,
  options: ClockRenderOptions,
): void {
  ctx.clearRect(0, 0, options.width, options.height)
  if (!layer || bars.length === 0) return

  const { pane, shown } = options
  const span = (from: number, to: number): [number, number] | null => {
    const x0 = timeToX(from, bars, options.step, projection)
    const x1 = timeToX(to, bars, options.step, projection)
    if (x0 === null || x1 === null || x1 - x0 < 0.5 || x1 < 0 || x0 > pane.width) return null
    return [x0, x1]
  }

  ctx.save()
  ctx.beginPath()
  ctx.rect(0, 0, pane.width, pane.height)
  ctx.clip()
  ctx.font = FONT
  ctx.textBaseline = 'middle'

  for (const window of layer.windows) {
    if (!shown.has(windowLayer(window))) continue
    const at = span(window.from, window.to)
    if (at) drawWindow(ctx, window, at, pane)
  }
  for (const line of layer.lines) {
    if (!shown.has(lineLayer(line))) continue
    const at = span(line.from, line.to)
    const y = projection.priceToY(line.priceTicks)
    if (at && y !== null && y >= 0 && y <= pane.height) drawLine(ctx, line, at, y, options.tickSize)
  }

  ctx.restore()
}

function drawWindow(
  ctx: CanvasRenderingContext2D,
  window: ClockWindow,
  [x0, x1]: [number, number],
  pane: { height: number },
): void {
  const width = x1 - x0
  switch (window.kind) {
    case 'TRADING_DAY':
      ctx.strokeStyle = 'rgba(139, 148, 158, 0.45)'
      ctx.lineWidth = 1
      ctx.setLineDash([4, 4])
      ctx.beginPath()
      ctx.moveTo(Math.round(x0) + 0.5, 0)
      ctx.lineTo(Math.round(x0) + 0.5, pane.height)
      ctx.stroke()
      ctx.setLineDash([])
      label(ctx, window.label, x0 + 4, pane.height - 8, width, '#8b949e')
      return
    case 'KILLZONE': {
      const color = killzoneColor(window.label)
      ctx.fillStyle = withAlpha(color, 0.06)
      ctx.fillRect(x0, 0, width, pane.height)
      ctx.fillStyle = withAlpha(color, 0.35)
      ctx.fillRect(x0, 0, width, STRIP)
      label(ctx, window.label, x0 + 3, STRIP / 2, width, '#e6edf3')
      return
    }
    case 'SILVER_BULLET':
      ctx.fillStyle = 'rgba(88, 166, 255, 0.07)'
      ctx.fillRect(x0, 0, width, pane.height)
      ctx.fillStyle = 'rgba(88, 166, 255, 0.7)'
      ctx.fillRect(x0, STRIP, width, SUBSTRIP)
      return
    case 'MACRO':
      ctx.fillStyle = 'rgba(210, 153, 34, 0.75)'
      ctx.fillRect(x0, STRIP + SUBSTRIP, width, SUBSTRIP)
      return
  }
}

function drawLine(
  ctx: CanvasRenderingContext2D,
  line: ClockLine,
  [x0, x1]: [number, number],
  y: number,
  tickSize: number,
): void {
  const { color, dash, width } = lineStyle(line)
  const yy = Math.round(y) + 0.5
  ctx.strokeStyle = color
  ctx.lineWidth = width
  ctx.setLineDash(dash)
  ctx.beginPath()
  ctx.moveTo(x0, yy)
  ctx.lineTo(x1, yy)
  ctx.stroke()
  ctx.setLineDash([])

  // Labelled at its right end, where the eye arrives from the newest candles.
  const text = `${line.label} ${formatPrice(line.priceTicks, tickSize)}`
  const textWidth = ctx.measureText(text).width
  if (x1 - x0 > textWidth + 8) {
    ctx.fillStyle = color
    ctx.fillText(text, x1 - textWidth - 4, yy - 7)
  }
}

function lineStyle(line: ClockLine): { color: string; dash: number[]; width: number } {
  switch (line.kind) {
    case 'OPEN':
      return { color: 'rgba(230, 237, 243, 0.7)', dash: [2, 3], width: 1 }
    case 'PREVIOUS_DAY':
      return { color: '#f0883e', dash: [], width: 1.5 }
    case 'PREVIOUS_WEEK':
      return { color: '#db61a2', dash: [], width: 1.5 }
    case 'SESSION':
      return { color: killzoneColor(line.label), dash: [6, 3], width: 1 }
  }
}

/** Text only where it fits: a label spilling out of a narrow window would name the wrong one. */
function label(
  ctx: CanvasRenderingContext2D,
  text: string,
  x: number,
  y: number,
  room: number,
  color: string,
): void {
  if (ctx.measureText(text).width + 6 > room) return
  ctx.fillStyle = color
  ctx.fillText(text, x, y)
}

function withAlpha(hex: string, alpha: number): string {
  const n = Number.parseInt(hex.slice(1), 16)
  return `rgba(${(n >> 16) & 255}, ${(n >> 8) & 255}, ${n & 255}, ${alpha})`
}
