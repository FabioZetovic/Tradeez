import { useCallback, useEffect, useRef } from 'react'
import type { ResultView, SetupView, SubjectView } from '@/lib/api/drills'
import type { ChartAdapter, DomainBar } from '@/lib/chart/types'
import type { Projection } from '@/lib/drawing/types'
import { pickCandle } from '@/lib/drills/marks'
import { useOverlayCanvas } from '@/hooks/useOverlayCanvas'

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
}

/** A click that moved further than this was a pan, not a mark. */
const DRAG_TOLERANCE_PX = 5

const ACCENT = '#58a6ff'
const FILL: Record<SetupView['direction'], string> = {
  BULLISH: 'rgba(63, 185, 80, 0.22)',
  BEARISH: 'rgba(248, 81, 73, 0.22)',
  NEUTRAL: 'rgba(139, 148, 158, 0.22)',
}

/**
 * Marks, the subject and the answer, painted over the drill's chart.
 *
 * The canvas never takes the pointer. Clicks are read from the chart frame they bubble up to, and a
 * click that travelled is ignored — so the learner can still pan and zoom the chart they are
 * searching, which a pointer-capturing overlay would prevent.
 */
export function DrillOverlay({ adapter, bars, marks, onToggle, subject, result }: DrillOverlayProps) {
  const canvasRef = useRef<HTMLCanvasElement>(null)

  const draw = useCallback(() => {
    const canvas = canvasRef.current
    const projection = adapter?.getProjection()
    const ctx = canvas?.getContext('2d')
    if (!canvas || !projection || !ctx) return

    ctx.clearRect(0, 0, canvas.width, canvas.height)
    const byTime = new Map(bars.map((b) => [b.time, b]))

    // The answer, once there is one, each hit in its own shape. Identification: found ones outlined
    // solid, missed ones dashed, so the two read apart at a glance.
    for (const setup of result?.setups ?? []) drawSetup(ctx, projection, setup)

    // The question, until it is answered.
    if (subject && !result) drawSubject(ctx, projection, subject, byTime)

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
        ctx.strokeStyle = '#f85149'
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
  }, [adapter, bars, marks, subject, result])

  useOverlayCanvas(canvasRef, adapter, draw)
  useEffect(draw, [draw])

  useEffect(() => {
    const canvas = canvasRef.current
    const frame = canvas?.parentElement
    if (!canvas || !frame || result || !onToggle) return

    let down: { x: number; y: number } | null = null
    const onDown = (event: PointerEvent) => {
      down = { x: event.clientX, y: event.clientY }
    }
    const onClick = (event: MouseEvent) => {
      const start = down
      down = null
      if (start && Math.hypot(event.clientX - start.x, event.clientY - start.y) > DRAG_TOLERANCE_PX) {
        return
      }
      const projection = adapter?.getProjection()
      if (!projection) return
      const rect = canvas.getBoundingClientRect()
      const time = pickCandle(event.clientX - rect.left, projection, bars)
      if (time !== null) onToggle(time)
    }

    frame.addEventListener('pointerdown', onDown)
    frame.addEventListener('click', onClick)
    return () => {
      frame.removeEventListener('pointerdown', onDown)
      frame.removeEventListener('click', onClick)
    }
  }, [adapter, bars, onToggle, result])

  return (
    <canvas
      ref={canvasRef}
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
  ctx.strokeStyle = setup.found === false ? '#d29922' : '#e6edf3'
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

function ring(ctx: CanvasRenderingContext2D, x: number, y: number, radius: number) {
  ctx.beginPath()
  ctx.arc(x, y, radius, 0, Math.PI * 2)
  ctx.fill()
  ctx.stroke()
}
