import { HANDLE_SIZE, positionMetrics, positionRight, project } from './geometry'
import type { Drawing, Projection } from './types'

/**
 * Paints drawings onto the overlay canvas.
 *
 * Separated from the React component so it is a plain function of (context, drawings, projection) —
 * no hooks, no lifecycle, and straightforward to reason about. The component's only job is to
 * decide *when* to call this.
 */

export interface RenderOptions {
  width: number
  height: number
  selectedId: string | null
  /** Used to label the position tool in currency rather than ticks. */
  tickValueCents: number
}

export function renderDrawings(
  ctx: CanvasRenderingContext2D,
  drawings: Drawing[],
  projection: Projection,
  options: RenderOptions,
): void {
  ctx.clearRect(0, 0, options.width, options.height)

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
    [entry.y, '#e6edf3'],
    [stop.y, '#f85149'],
    [target.y, '#3fb950'],
  ]
  for (const [y, color] of levels) {
    ctx.strokeStyle = color
    ctx.beginPath()
    ctx.moveTo(left, y)
    ctx.lineTo(right, y)
    ctx.stroke()
  }

  const metrics = positionMetrics(drawing.anchors)
  const riskCents = metrics.riskTicks * options.tickValueCents
  const rewardCents = metrics.rewardTicks * options.tickValueCents

  const label =
    metrics.ratio === null
      ? `${metrics.direction} · no risk set`
      : `${metrics.direction} · ${metrics.ratio.toFixed(2)}R · ` +
        `risk $${(riskCents / 100).toFixed(2)} · reward $${(rewardCents / 100).toFixed(2)}`

  ctx.fillStyle = '#e6edf3'
  ctx.font = '11px system-ui, sans-serif'
  ctx.fillText(label, left + 6, Math.min(entry.y, stop.y, target.y) - 6)
}

function drawHandles(
  ctx: CanvasRenderingContext2D,
  drawing: Drawing,
  projection: Projection,
): void {
  ctx.save()
  ctx.fillStyle = '#0d1117'
  ctx.strokeStyle = '#58a6ff'
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
