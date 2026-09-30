import { useCallback, useEffect, useRef } from 'react'
import type { ResultView } from '@/lib/api/drills'
import type { ChartAdapter, DomainBar } from '@/lib/chart/types'
import { pickCandle } from '@/lib/drills/fvg'

interface DrillOverlayProps {
  adapter: ChartAdapter | null
  bars: DomainBar[]
  marks: number[]
  onToggle: (time: number) => void
  /** Present once answered: the gaps are drawn and marking stops. */
  result?: ResultView
}

/** A click that moved further than this was a pan, not a mark. */
const DRAG_TOLERANCE_PX = 5

/**
 * Marks and answers, painted over the drill's chart.
 *
 * The canvas never takes the pointer. Clicks are read from the chart frame they bubble up to, and a
 * click that travelled is ignored — so the learner can still pan and zoom the chart they are
 * searching, which a pointer-capturing overlay would prevent.
 */
export function DrillOverlay({ adapter, bars, marks, onToggle, result }: DrillOverlayProps) {
  const canvasRef = useRef<HTMLCanvasElement>(null)

  const draw = useCallback(() => {
    const canvas = canvasRef.current
    const projection = adapter?.getProjection()
    const ctx = canvas?.getContext('2d')
    if (!canvas || !projection || !ctx) return

    ctx.clearRect(0, 0, canvas.width, canvas.height)
    const byTime = new Map(bars.map((b) => [b.time, b]))

    // The answer, once there is one: each gap as a zone across its three candles.
    for (const setup of result?.setups ?? []) {
      const x1 = projection.timeToX(setup.startTime)
      const x3 = projection.timeToX(setup.endTime)
      const yTop = projection.priceToY(setup.zoneHighTicks)
      const yBottom = projection.priceToY(setup.zoneLowTicks)
      if (x1 === null || x3 === null || yTop === null || yBottom === null) continue

      const bullish = setup.direction === 'BULLISH'
      ctx.save()
      ctx.fillStyle = bullish ? 'rgba(63, 185, 80, 0.22)' : 'rgba(248, 81, 73, 0.22)'
      ctx.strokeStyle = setup.found ? '#e6edf3' : '#d29922'
      ctx.lineWidth = 1.5
      // Found gaps are outlined solid; missed ones dashed, so the two read apart at a glance.
      ctx.setLineDash(setup.found ? [] : [5, 4])
      const x = Math.min(x1, x3) - 4
      const width = Math.abs(x3 - x1) + 8
      const y = Math.min(yTop, yBottom)
      const height = Math.max(Math.abs(yBottom - yTop), 2)
      ctx.fillRect(x, y, width, height)
      ctx.strokeRect(x, y, width, height)
      ctx.restore()
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
        // Marked, but not the middle of a qualifying gap.
        ctx.strokeStyle = '#f85149'
        ctx.lineWidth = 2
        ctx.beginPath()
        ctx.moveTo(x - 5, y - 19)
        ctx.lineTo(x + 5, y - 9)
        ctx.moveTo(x + 5, y - 19)
        ctx.lineTo(x - 5, y - 9)
        ctx.stroke()
      } else {
        ctx.fillStyle = '#58a6ff'
        ctx.beginPath()
        ctx.arc(x, y - 14, 5, 0, Math.PI * 2)
        ctx.fill()
      }
      ctx.restore()
    }
  }, [adapter, bars, marks, result])

  // Same sizing approach as the drawing overlay: match the frame in pixels, or every mark lands
  // slightly off its candle.
  useEffect(() => {
    const canvas = canvasRef.current
    if (!canvas?.parentElement) return
    const resize = () => {
      const rect = canvas.parentElement!.getBoundingClientRect()
      canvas.width = rect.width
      canvas.height = rect.height
      canvas.style.width = `${rect.width}px`
      canvas.style.height = `${rect.height}px`
      draw()
    }
    resize()
    const observer = new ResizeObserver(resize)
    observer.observe(canvas.parentElement)
    return () => observer.disconnect()
  }, [draw])

  useEffect(() => {
    if (!adapter) return
    return adapter.onViewportChange(draw)
  }, [adapter, draw])

  useEffect(draw, [draw])

  useEffect(() => {
    const canvas = canvasRef.current
    const frame = canvas?.parentElement
    if (!canvas || !frame || result) return

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
      className="drawing-overlay"
      data-testid="drill-overlay"
      style={{ pointerEvents: 'none', cursor: result ? 'default' : 'pointer' }}
    />
  )
}
