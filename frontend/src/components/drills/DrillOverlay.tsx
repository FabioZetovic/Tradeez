import { useCallback, useEffect, useRef } from 'react'
import type { ContextView, QuestionView, ResultView, SetupView, SubjectView, TargetView } from '@/lib/api/drills'
import type { ChartAdapter, DomainBar } from '@/lib/chart/types'
import type { Projection } from '@/lib/drawing/types'
import { pickCandle } from '@/lib/drills/marks'
import { useChartLayer } from '@/hooks/useChartLayer'
import { THEME } from '@/lib/theme'

interface DrillOverlayProps {
  adapter: ChartAdapter | null
  bars: DomainBar[]
  marks: number[]
  /** Identification, until answered: a click on a candle marks or unmarks it. */
  onToggle?: (time: number) => void
  /** Classification: the structure to name. */
  subject?: SubjectView
  /** Present once answered: the rule's hits are drawn and marking stops. */
  result?: ResultView
  /** Prediction: the two targets and the context drawn with them. */
  question?: QuestionView
  /** Prediction: where the chart stopped — the first moment after its last candle. */
  moment?: number
}

/** A click that moved further than this was a pan, not a mark. */
const DRAG_TOLERANCE_PX = 5

const ACCENT = THEME.accent
const TEXT = THEME.ink
const MUTED = THEME.subtle
const FILL: Record<SetupView['direction'], string> = {
  BULLISH: 'rgba(63, 185, 80, 0.22)',
  BEARISH: 'rgba(248, 81, 73, 0.22)',
  NEUTRAL: 'rgba(139, 148, 158, 0.22)',
}

/**
 * Marks, the subject, a prediction's targets and the answer, painted by the drill's chart over everything, in the
 * same frame as its candles (T37).
 *
 * Nothing here takes the pointer. Clicks are read from the chart frame they bubble up to, and a click that travelled
 * is ignored — so the learner can still pan and zoom the chart they are searching, which a pointer-capturing overlay
 * would prevent.
 */
export function DrillOverlay({ adapter, bars, marks, onToggle, subject, result, question, moment }: DrillOverlayProps) {
  // Where clicks are measured from: the chart frame's top-left, as the projection is.
  const areaRef = useRef<HTMLDivElement>(null)

  const paint = useCallback(
    (ctx: CanvasRenderingContext2D, projection: Projection, pane: { width: number; height: number }) => {
    const byTime = new Map(bars.map((b) => [b.time, b]))

    // The answer, once there is one, each hit in its own shape. Identification: found ones outlined
    // solid, missed ones dashed, so the two read apart at a glance.
    for (const setup of result?.setups ?? []) drawSetup(ctx, projection, setup)

    // The question, until it is answered.
    if (subject && !result) drawSubject(ctx, projection, subject, byTime)

    // A prediction: the targets stay drawn after the answer, so the future can be read against them.
    if (question) {
      const lastX = bars.length > 0 ? projection.timeToX(bars[bars.length - 1].time) : null
      const right = lastX === null ? pane.width : lastX + 10
      for (const item of question.context) drawContext(ctx, projection, item, right, moment)
      for (const target of question.targets) drawTarget(ctx, projection, target, right)
      if (result && moment !== undefined) drawMoment(ctx, projection, moment, pane.height)
      const answered = question.targets.find((t) => t.value === result?.answer)
      if (answered && result?.outcomeTime !== undefined) drawDecided(ctx, projection, answered, result.outcomeTime)
    }

    // The learner's marks, above each marked candle.
    const extra = new Set(result?.extraMarks ?? [])
    for (const time of marks) {
      const bar = byTime.get(time)
      const x = projection.timeToX(time)
      const y = bar ? projection.priceToY(bar.high) : null
      if (x === null || y === null) continue
      ctx.save()
      if (extra.has(time)) {
        // Marked, but not in the answer key.
        ctx.strokeStyle = THEME.down
        ctx.lineWidth = 2
        ctx.beginPath()
        ctx.moveTo(x - 5, y - 19)
        ctx.lineTo(x + 5, y - 9)
        ctx.moveTo(x + 5, y - 19)
        ctx.lineTo(x - 5, y - 9)
        ctx.stroke()
      } else {
        ctx.fillStyle = ACCENT
        ctx.beginPath()
        ctx.arc(x, y - 14, 5, 0, Math.PI * 2)
        ctx.fill()
      }
      ctx.restore()
    }
    },
    [bars, marks, subject, result, question, moment],
  )
  useChartLayer(adapter, 'top', paint)

  useEffect(() => {
    const area = areaRef.current
    const frame = area?.parentElement
    if (!area || !frame || result || !onToggle) return

    // A press and its release, not a click (BUG-111): on a touch screen the chart library takes the tap for its own
    // panning and no click follows, so a finger could never mark a candle. A release far from its press was a drag.
    let down: { x: number; y: number } | null = null
    const onDown = (event: PointerEvent) => {
      down = event.button === 0 ? { x: event.clientX, y: event.clientY } : null
    }
    const onUp = (event: PointerEvent) => {
      const start = down
      down = null
      if (!start || Math.hypot(event.clientX - start.x, event.clientY - start.y) > DRAG_TOLERANCE_PX) {
        return
      }
      const projection = adapter?.getProjection()
      if (!projection) return
      const rect = area.getBoundingClientRect()
      const time = pickCandle(event.clientX - rect.left, projection, bars)
      if (time !== null) onToggle(time)
    }

    frame.addEventListener('pointerdown', onDown)
    frame.addEventListener('pointerup', onUp)
    return () => {
      frame.removeEventListener('pointerdown', onDown)
      frame.removeEventListener('pointerup', onUp)
    }
  }, [adapter, bars, onToggle, result])

  return (
    <div
      ref={areaRef}
      aria-hidden="true"
      className="absolute inset-0 z-[2]"
      data-testid="drill-overlay"
      style={{ pointerEvents: 'none', cursor: result || !onToggle ? 'default' : 'pointer' }}
    />
  )
}

/** One hit of the rule: a zone across its candles, a level out to its candle, or a ring on a swing. */
function drawSetup(ctx: CanvasRenderingContext2D, projection: Projection, setup: SetupView) {
  const xStart = projection.timeToX(setup.startTime)
  const xAnchor = projection.timeToX(setup.anchorTime)
  const xEnd = projection.timeToX(setup.endTime)
  const yLow = projection.priceToY(setup.zoneLowTicks)
  const yHigh = projection.priceToY(setup.zoneHighTicks)
  if (xStart === null || xAnchor === null || xEnd === null || yLow === null || yHigh === null) return

  ctx.save()
  ctx.fillStyle = FILL[setup.direction]
  ctx.strokeStyle = setup.found === false ? THEME.warn : THEME.ink
  ctx.lineWidth = 1.5
  ctx.setLineDash(setup.found === false ? [5, 4] : [])
  if (setup.shape === 'ZONE') {
    const x = Math.min(xStart, xEnd) - 4
    const y = Math.min(yLow, yHigh)
    const width = Math.abs(xEnd - xStart) + 8
    const height = Math.max(Math.abs(yLow - yHigh), 2)
    ctx.fillRect(x, y, width, height)
    ctx.strokeRect(x, y, width, height)
  } else if (setup.shape === 'LEVEL') {
    ctx.beginPath()
    ctx.moveTo(xStart, yLow)
    ctx.lineTo(xAnchor, yLow)
    ctx.stroke()
    ring(ctx, xAnchor, yLow, 6)
  } else {
    ring(ctx, xAnchor, yLow, 7)
  }
  ctx.restore()
}

/**
 * The structure a classification drill asks about: its level from the swing it comes from, and an
 * arrow at the candle in question, on the side of price the level is.
 */
function drawSubject(
  ctx: CanvasRenderingContext2D,
  projection: Projection,
  subject: SubjectView,
  byTime: Map<number, DomainBar>,
) {
  const xFrom = projection.timeToX(subject.fromTime)
  const x = projection.timeToX(subject.time)
  const y = projection.priceToY(subject.priceTicks)
  const bar = byTime.get(subject.time)
  if (xFrom === null || x === null || y === null || !bar) return

  ctx.save()
  ctx.strokeStyle = ACCENT
  ctx.fillStyle = ACCENT
  ctx.lineWidth = 1.5
  if (xFrom !== x) {
    ctx.setLineDash([6, 4])
    ctx.beginPath()
    ctx.moveTo(xFrom, y)
    ctx.lineTo(x + 6, y)
    ctx.stroke()
    ctx.setLineDash([])
  } else {
    ctx.fillStyle = 'rgba(88, 166, 255, 0.25)'
    ring(ctx, x, y, 7)
    ctx.fillStyle = ACCENT
  }
  // The arrow points at the candle from outside it: above a high, below a low.
  const high = subject.side === 'HIGH'
  const tip = projection.priceToY(high ? bar.high : bar.low)
  if (tip !== null) {
    const base = high ? tip - 22 : tip + 22
    const end = high ? tip - 6 : tip + 6
    ctx.beginPath()
    ctx.moveTo(x, base)
    ctx.lineTo(x, end)
    ctx.stroke()
    ctx.beginPath()
    ctx.moveTo(x, end + (high ? 2 : -2))
    ctx.lineTo(x - 4, end + (high ? -5 : 5))
    ctx.lineTo(x + 4, end + (high ? -5 : 5))
    ctx.closePath()
    ctx.fill()
  }
  ctx.restore()
}

/**
 * A prediction's target: a level from its swing, or a gap from its first candle, out past the last candle
 * shown — where price would have to go — with its name on it.
 */
function drawTarget(ctx: CanvasRenderingContext2D, projection: Projection, target: TargetView, right: number) {
  const x = projection.timeToX(target.fromTime)
  const yLow = projection.priceToY(target.lowTicks)
  const yHigh = projection.priceToY(target.highTicks)
  if (x === null || yLow === null || yHigh === null) return
  ctx.save()
  ctx.strokeStyle = ACCENT
  ctx.lineWidth = 1.5
  if (target.shape === 'LEVEL') {
    ctx.setLineDash([6, 4])
    ctx.beginPath()
    ctx.moveTo(x, yLow)
    ctx.lineTo(right, yLow)
    ctx.stroke()
  } else {
    ctx.fillStyle = target.side === 'BELOW' ? FILL.BULLISH : FILL.BEARISH
    const top = Math.min(yLow, yHigh)
    const height = Math.max(Math.abs(yLow - yHigh), 2)
    ctx.fillRect(x, top, right - x, height)
    ctx.strokeRect(x, top, right - x, height)
  }
  ctx.setLineDash([])
  label(ctx, target.label, right, target.side === 'ABOVE' ? Math.min(yLow, yHigh) - 5 : Math.max(yLow, yHigh) + 13, TEXT)
  ctx.restore()
}

/** Context: an open gap inside the range, faint; or the level the last candle just took, to that candle. */
function drawContext(
  ctx: CanvasRenderingContext2D,
  projection: Projection,
  item: ContextView,
  right: number,
  moment: number | undefined,
) {
  const x = projection.timeToX(item.fromTime)
  const yLow = projection.priceToY(item.lowTicks)
  const yHigh = projection.priceToY(item.highTicks)
  if (x === null || yLow === null || yHigh === null) return
  ctx.save()
  if (item.what.startsWith('TRADE_')) {
    // A management drill's trade: drawn across the whole chart, so the future after the answer reads against it.
    ctx.strokeStyle = item.what === 'TRADE_TARGET' ? THEME.up : item.what === 'TRADE_STOP' ? THEME.down : TEXT
    ctx.lineWidth = 1.5
    ctx.beginPath()
    ctx.moveTo(x, yLow)
    ctx.lineTo(right, yLow)
    ctx.stroke()
    label(ctx, item.label, right, yLow - 5, item.what === 'TRADE_TARGET' ? THEME.up : item.what === 'TRADE_STOP' ? THEME.down : TEXT)
  } else if (item.shape === 'ZONE') {
    ctx.fillStyle = item.what === 'IRL_BULLISH_GAP' ? 'rgba(63, 185, 80, 0.10)' : 'rgba(248, 81, 73, 0.10)'
    ctx.fillRect(x, Math.min(yLow, yHigh), right - x, Math.max(Math.abs(yLow - yHigh), 1))
    label(ctx, item.label, right, Math.min(yLow, yHigh) - 3, MUTED)
  } else {
    const end = moment === undefined ? right : (projection.timeToX(moment) ?? right)
    ctx.strokeStyle = MUTED
    ctx.setLineDash([2, 3])
    ctx.beginPath()
    ctx.moveTo(x, yLow)
    ctx.lineTo(end, yLow)
    ctx.stroke()
    label(ctx, item.label, end, yLow + (item.what === 'TAKEN_BSL' ? -5 : 13), MUTED)
  }
  ctx.restore()
}

/** Once answered: the line where the chart stopped. Everything right of it was hidden. */
function drawMoment(ctx: CanvasRenderingContext2D, projection: Projection, moment: number, height: number) {
  const x = projection.timeToX(moment)
  if (x === null) return
  ctx.save()
  ctx.strokeStyle = MUTED
  ctx.setLineDash([4, 4])
  ctx.beginPath()
  ctx.moveTo(x, 0)
  ctx.lineTo(x, height)
  ctx.stroke()
  ctx.restore()
  ctx.save()
  ctx.fillStyle = MUTED
  ctx.font = '11px system-ui, sans-serif'
  ctx.fillText('chart ended', x + 4, 14)
  ctx.restore()
}

/** The candle that answered it, ringed where it reached the target. */
function drawDecided(ctx: CanvasRenderingContext2D, projection: Projection, target: TargetView, time: number) {
  const x = projection.timeToX(time)
  const y = projection.priceToY(target.side === 'ABOVE' ? target.lowTicks : target.highTicks)
  if (x === null || y === null) return
  ctx.save()
  ctx.strokeStyle = THEME.warn
  ctx.fillStyle = 'rgba(210, 153, 34, 0.25)'
  ctx.lineWidth = 2
  ring(ctx, x, y, 8)
  ctx.restore()
}

function label(ctx: CanvasRenderingContext2D, text: string, right: number, y: number, color: string) {
  ctx.font = '11px system-ui, sans-serif'
  ctx.fillStyle = color
  ctx.fillText(text, right - ctx.measureText(text).width - 4, y)
}

function ring(ctx: CanvasRenderingContext2D, x: number, y: number, radius: number) {
  ctx.beginPath()
  ctx.arc(x, y, radius, 0, Math.PI * 2)
  ctx.fill()
  ctx.stroke()
}
