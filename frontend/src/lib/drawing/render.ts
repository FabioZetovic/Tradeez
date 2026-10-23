import { LabelSpace } from '../chart/labelSpace'
import { HANDLE_SIZE, positionMetrics, positionRight, project } from './geometry'
import {
  ceHalfTicks,
  fibLevelTicks,
  formatFibLevel,
  formatHalfTicks,
  ICT_FIB_LEVELS,
  LABEL_FONT_PX,
  OTE,
} from './ict'
import type { Drawing, Projection } from './types'
import { THEME } from '../theme'

/**
 * Paints drawings onto the overlay canvas.
 *
 * Separated from the React component so it is a plain function of (context, drawings, projection) —
 * no hooks, no lifecycle, and straightforward to reason about. The component's only job is to
 * decide *when* to call this.
 */

export interface RenderOptions {
  /** The plotting area's width: lines that run to the right edge stop at it. */
  width: number
  selectedId: string | null
  /** Used to label the position tool in currency rather than ticks. */
  tickValueCents: number
  /** Used to print prices on the Fibonacci and zone tools. */
  tickSize: number
}

export function renderDrawings(
  ctx: CanvasRenderingContext2D,
  drawings: Drawing[],
  projection: Projection,
  options: RenderOptions,
): void {
  for (const drawing of drawings) {
    const selected = drawing.id === options.selectedId
    ctx.save()
    ctx.strokeStyle = drawing.style.color
    ctx.lineWidth = drawing.style.lineWidth
    ctx.setLineDash([])

    switch (drawing.kind) {
      case 'horizontal-line':
        drawHorizontalLine(ctx, drawing, projection, options)
        break
      case 'trend-line':
        drawTrendLine(ctx, drawing, projection)
        break
      case 'rectangle':
        drawRectangle(ctx, drawing, projection)
        break
      case 'position':
        drawPosition(ctx, drawing, projection, options)
        break
      case 'fibonacci':
        drawFibonacci(ctx, drawing, projection, options)
        break
      case 'zone':
        drawZone(ctx, drawing, projection, options)
        break
      case 'label':
        drawLabel(ctx, drawing, projection)
        break
    }

    ctx.restore()

    if (selected) {
      drawHandles(ctx, drawing, projection)
    }
  }
}

function drawHorizontalLine(
  ctx: CanvasRenderingContext2D,
  drawing: Drawing,
  projection: Projection,
  options: RenderOptions,
): void {
  const p = project(drawing.anchors[0], projection)
  if (!p) return

  ctx.beginPath()
  ctx.moveTo(0, p.y)
  ctx.lineTo(options.width, p.y)
  ctx.stroke()
}

function drawTrendLine(
  ctx: CanvasRenderingContext2D,
  drawing: Drawing,
  projection: Projection,
): void {
  const a = project(drawing.anchors[0], projection)
  const b = project(drawing.anchors[1], projection)
  if (!a || !b) return

  ctx.beginPath()
  ctx.moveTo(a.x, a.y)
  ctx.lineTo(b.x, b.y)
  ctx.stroke()
}

function drawRectangle(
  ctx: CanvasRenderingContext2D,
  drawing: Drawing,
  projection: Projection,
): void {
  const a = project(drawing.anchors[0], projection)
  const b = project(drawing.anchors[1], projection)
  if (!a || !b) return

  const x = Math.min(a.x, b.x)
  const y = Math.min(a.y, b.y)
  const w = Math.abs(b.x - a.x)
  const h = Math.abs(b.y - a.y)

  if (drawing.style.fill) {
    ctx.fillStyle = drawing.style.fill
    ctx.fillRect(x, y, w, h)
  }
  ctx.strokeRect(x, y, w, h)
}

/**
 * The risk/reward tool: green above the entry toward the target, red below toward the stop.
 *
 * Colour encodes outcome, not direction — on a short the green zone is *below* the entry, because
 * that is where the profit is. A trader reading the chart sees "money" and "loss" without having to
 * work out which way round the trade is.
 */
function drawPosition(
  ctx: CanvasRenderingContext2D,
  drawing: Drawing,
  projection: Projection,
  options: RenderOptions,
): void {
  const entry = project(drawing.anchors[0], projection)
  const stop = project(drawing.anchors[1], projection)
  const target = project(drawing.anchors[2], projection)
  if (!entry || !stop || !target) return

  const left = entry.x
  const right = positionRight(drawing, projection, options.width)
  const w = Math.max(right - left, 0)

  ctx.fillStyle = 'rgba(63, 185, 80, 0.14)'
  ctx.fillRect(left, Math.min(entry.y, target.y), w, Math.abs(target.y - entry.y))

  ctx.fillStyle = 'rgba(248, 81, 73, 0.14)'
  ctx.fillRect(left, Math.min(entry.y, stop.y), w, Math.abs(stop.y - entry.y))

  const levels: Array<[number, string]> = [
    [entry.y, THEME.ink],
    [stop.y, THEME.down],
    [target.y, THEME.up],
  ]
  for (const [y, color] of levels) {
    ctx.strokeStyle = color
    ctx.beginPath()
    ctx.moveTo(left, y)
    ctx.lineTo(right, y)
    ctx.stroke()
  }
  const partial = drawing.partialTicks === undefined ? null : projection.priceToY(drawing.partialTicks)
  if (partial !== null) {
    ctx.strokeStyle = THEME.up
    ctx.setLineDash([4, 3])
    ctx.beginPath()
    ctx.moveTo(left, partial)
    ctx.lineTo(right, partial)
    ctx.stroke()
    ctx.setLineDash([])
  }

  const metrics = positionMetrics(drawing.anchors)
  const riskCents = metrics.riskTicks * options.tickValueCents
  const rewardCents = metrics.rewardTicks * options.tickValueCents

  const label =
    metrics.ratio === null
      ? `${metrics.direction} · no risk set`
      : `${metrics.direction} · ${metrics.ratio.toFixed(2)}R · ` +
        `risk $${(riskCents / 100).toFixed(2)} · reward $${(rewardCents / 100).toFixed(2)}`

  ctx.fillStyle = THEME.ink
  ctx.font = '11px system-ui, sans-serif'
  ctx.fillText(label, left + 6, Math.min(entry.y, stop.y, target.y) - 6)
}

/**
 * ICT's Fibonacci (ICT-43): every level from the leg's earlier end to the right edge — the targets lie
 * in the future, and a replay walks into them — with the OTE band (ICT-42) shaded and its 0.705
 * drawn heavier. The leg itself is a faint dashed diagonal, so it is clear which way it was drawn.
 */
function drawFibonacci(
  ctx: CanvasRenderingContext2D,
  drawing: Drawing,
  projection: Projection,
  options: RenderOptions,
): void {
  const a = project(drawing.anchors[0], projection)
  const b = project(drawing.anchors[1], projection)
  if (!a || !b) return
  const left = Math.min(a.x, b.x)
  const right = options.width
  const y = (ratio: number) => projection.priceToY(fibLevelTicks(drawing.anchors, ratio))

  const oteTop = y(OTE.from)
  const oteBottom = y(OTE.to)
  if (oteTop !== null && oteBottom !== null) {
    ctx.fillStyle = 'rgba(88, 166, 255, 0.10)'
    ctx.fillRect(left, Math.min(oteTop, oteBottom), right - left, Math.abs(oteBottom - oteTop))
  }

  ctx.setLineDash([3, 4])
  ctx.strokeStyle = 'rgba(139, 148, 158, 0.6)'
  ctx.beginPath()
  ctx.moveTo(a.x, a.y)
  ctx.lineTo(b.x, b.y)
  ctx.stroke()

  ctx.font = '11px system-ui, sans-serif'
  const labels: Array<{ ratio: number; text: string; y: number; color: string }> = []
  for (const level of ICT_FIB_LEVELS) {
    const ly = y(level.ratio)
    if (ly === null) continue
    const { color, dash, width } = fibStyle(level.ratio)
    ctx.strokeStyle = color
    ctx.lineWidth = width
    ctx.setLineDash(dash)
    ctx.beginPath()
    ctx.moveTo(left, ly)
    ctx.lineTo(right, ly)
    ctx.stroke()
    const price = formatFibLevel(fibLevelTicks(drawing.anchors, level.ratio), options.tickSize)
    labels.push({ ratio: level.ratio, text: `${level.label}  ${price}`, y: ly, color })
  }
  ctx.setLineDash([])

  // On a short leg the levels crowd together; the ones that matter most keep their words (BUG-048).
  const space = new LabelSpace()
  labels.sort((a, b) => FIB_LABEL_PRIORITY.indexOf(a.ratio) - FIB_LABEL_PRIORITY.indexOf(b.ratio))
  for (const label of labels) {
    const baseline = space.place(left + 4, label.y - 3, ctx.measureText(label.text).width, 0)
    if (baseline === null) continue
    ctx.fillStyle = label.color
    ctx.fillText(label.text, left + 4, baseline)
  }
}

/** Which levels keep their labels when there is room for only some: the OTE's heart first. */
const FIB_LABEL_PRIORITY = [0.705, 0.5, 1, 0, -1, -2, 0.62, 0.79, -0.27, -0.62]

function fibStyle(ratio: number): { color: string; dash: number[]; width: number } {
  if (ratio < 0) return { color: THEME.up, dash: [6, 3], width: 1 } // targets
  if (ratio === OTE.sweetSpot) return { color: THEME.accent, dash: [], width: 2 }
  if (ratio >= OTE.from && ratio <= OTE.to) return { color: THEME.accent, dash: [], width: 1 }
  if (ratio === 0.5) return { color: THEME.ink, dash: [4, 4], width: 1 } // equilibrium
  return { color: THEME.subtle, dash: [], width: 1 } // 0 and 1, the leg's ends
}

/**
 * A zone — a gap or a block — with its consequent encroachment (ICT-21): the exact middle, dashed,
 * and faint quarter lines. Its name, if it has one, sits inside the top-left corner, and the CE's
 * price at the right end of its line, to the half-tick.
 */
function drawZone(
  ctx: CanvasRenderingContext2D,
  drawing: Drawing,
  projection: Projection,
  options: RenderOptions,
): void {
  drawRectangle(ctx, drawing, projection)
  const a = project(drawing.anchors[0], projection)
  const b = project(drawing.anchors[1], projection)
  if (!a || !b) return
  const left = Math.min(a.x, b.x)
  const right = Math.max(a.x, b.x)
  const top = Math.min(a.y, b.y)
  const halfTicks = ceHalfTicks(drawing.anchors)

  const line = (y: number | null, dash: number[], alpha: number) => {
    if (y === null) return
    ctx.globalAlpha = alpha
    ctx.setLineDash(dash)
    ctx.beginPath()
    ctx.moveTo(left, y)
    ctx.lineTo(right, y)
    ctx.stroke()
    ctx.globalAlpha = 1
  }
  const low = Math.min(drawing.anchors[0].priceTicks, drawing.anchors[1].priceTicks)
  const high = Math.max(drawing.anchors[0].priceTicks, drawing.anchors[1].priceTicks)
  line(projection.priceToY(low + (high - low) * 0.25), [2, 4], 0.5)
  line(projection.priceToY(low + (high - low) * 0.75), [2, 4], 0.5)
  const ce = projection.priceToY(halfTicks / 2)
  line(ce, [6, 4], 1)
  ctx.setLineDash([])

  ctx.font = '11px system-ui, sans-serif'
  ctx.fillStyle = drawing.style.color
  if (drawing.text) ctx.fillText(drawing.text, left + 4, top + 13)
  if (ce !== null) {
    const text = `CE ${formatHalfTicks(halfTicks, options.tickSize)}`
    const width = ctx.measureText(text).width
    if (right - left > width + 8) ctx.fillText(text, right - width - 4, ce - 3)
  }
}

/** A note at a point: a dot, and the words on a dark backing so they read over candles. */
function drawLabel(ctx: CanvasRenderingContext2D, drawing: Drawing, projection: Projection): void {
  const p = project(drawing.anchors[0], projection)
  if (!p) return
  const text = drawing.text ?? ''
  ctx.font = `${LABEL_FONT_PX}px system-ui, sans-serif`
  const width = ctx.measureText(text).width
  ctx.fillStyle = 'rgba(13, 17, 23, 0.8)'
  ctx.fillRect(p.x + 6, p.y - LABEL_FONT_PX / 2 - 4, width + 10, LABEL_FONT_PX + 8)
  ctx.fillStyle = drawing.style.color
  ctx.beginPath()
  ctx.arc(p.x, p.y, 3, 0, Math.PI * 2)
  ctx.fill()
  ctx.textBaseline = 'middle'
  ctx.fillText(text, p.x + 11, p.y)
}

function drawHandles(
  ctx: CanvasRenderingContext2D,
  drawing: Drawing,
  projection: Projection,
): void {
  ctx.save()
  ctx.fillStyle = THEME.canvas
  ctx.strokeStyle = THEME.accent
  ctx.lineWidth = 1.5

  for (const anchor of drawing.anchors) {
    const p = project(anchor, projection)
    if (!p) continue
    const half = HANDLE_SIZE / 2
    ctx.fillRect(p.x - half, p.y - half, HANDLE_SIZE, HANDLE_SIZE)
    ctx.strokeRect(p.x - half, p.y - half, HANDLE_SIZE, HANDLE_SIZE)
  }

  ctx.restore()
}
