import type { ClockLayer, ClockLine, ClockWindow } from '../api/clock'
import type { DomainBar } from '../chart/types'
import { LabelSpace } from '../chart/labelSpace'
import { timeToX } from '../chart/timeToX'
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
  const labels: LineLabel[] = []
  for (const line of layer.lines) {
    if (!shown.has(lineLayer(line))) continue
    // A level whose liquidity was taken ends where it was taken (liquidity-1.0.0, T12).
    const at = span(line.from, line.takenAt ?? line.to)
    const y = projection.priceToY(line.priceTicks)
    if (at && y !== null && y >= 0 && y <= pane.height) {
      labels.push(drawLine(ctx, line, at, y, options.tickSize))
      if (line.takenAt !== undefined) drawTaken(ctx, at[1], Math.round(y) + 0.5, lineStyle(line).color)
    }
  }
  // Words after lines, most important first, each into free space (BUG-048).
  const space = new LabelSpace()
  labels.sort((a, b) => LABEL_PRIORITY[a.kind] - LABEL_PRIORITY[b.kind])
  for (const label of labels) {
    const width = ctx.measureText(label.text).width
    if (label.room < width + 8) continue
    const x = label.right - width - 4
    const baseline = space.place(x, label.y - 7, width)
    if (baseline === null) continue
    ctx.fillStyle = label.color
    ctx.fillText(label.text, x, baseline)
  }

  ctx.restore()
}

interface LineLabel {
  kind: ClockLine['kind']
  text: string
  color: string
  right: number
  room: number
  y: number
}

/** Liquidity first: the levels ICT expects price to run for, then the clock's own prices. */
const LABEL_PRIORITY: Record<ClockLine['kind'], number> = {
  PREVIOUS_WEEK: 0,
  PREVIOUS_DAY: 1,
  SESSION: 2,
  OPEN: 3,
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

/** Draws the line; its label — at its right end, where the eye arrives from the newest candles — is
 * returned to be placed once every line is down. */
function drawLine(
  ctx: CanvasRenderingContext2D,
  line: ClockLine,
  [x0, x1]: [number, number],
  y: number,
  tickSize: number,
): LineLabel {
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
  return {
    kind: line.kind,
    text: `${line.label} ${formatPrice(line.priceTicks, tickSize)}`,
    color,
    right: x1,
    room: x1 - x0,
    y: yy,
  }
}

/** A small cross where a level's liquidity was taken. */
function drawTaken(ctx: CanvasRenderingContext2D, x: number, y: number, color: string): void {
  ctx.strokeStyle = color
  ctx.lineWidth = 1.5
  ctx.beginPath()
  ctx.moveTo(x - 3, y - 3)
  ctx.lineTo(x + 3, y + 3)
  ctx.moveTo(x - 3, y + 3)
  ctx.lineTo(x + 3, y - 3)
  ctx.stroke()
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
