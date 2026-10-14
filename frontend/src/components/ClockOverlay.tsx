import { useCallback, useEffect, useRef } from 'react'
import type { ClockLayer } from '@/lib/api/clock'
import type { ChartAdapter, DomainBar } from '@/lib/chart/types'
import type { ClockLayerKey } from '@/lib/clock/layers'
import { renderClock } from '@/lib/clock/render'
import { timeframeSeconds } from '@/lib/chart/dateNavigation'
import { useOverlayCanvas } from '@/hooks/useOverlayCanvas'

interface ClockOverlayProps {
  adapter: ChartAdapter | null
  layer: ClockLayer | null
  bars: DomainBar[]
  timeframe: string
  tickSize: number
  shown: ReadonlySet<ClockLayerKey>
}

/**
 * The New York clock under the drawings (T7): a canvas that never takes the pointer, so panning,
 * zooming and drawing all pass straight through it.
 */
export function ClockOverlay({ adapter, layer, bars, timeframe, tickSize, shown }: ClockOverlayProps) {
  const canvasRef = useRef<HTMLCanvasElement>(null)

  const draw = useCallback(() => {
    const canvas = canvasRef.current
    const projection = adapter?.getProjection()
    const pane = adapter?.getPaneSize()
    const ctx = canvas?.getContext('2d')
    if (!canvas || !projection || !pane || !ctx) return
    renderClock(ctx, layer, bars, projection, {
      width: canvas.width,
      height: canvas.height,
      pane,
      shown,
      step: timeframeSeconds(timeframe),
      tickSize,
    })
  }, [adapter, layer, bars, timeframe, tickSize, shown])

  useOverlayCanvas(canvasRef, adapter, draw)
  useEffect(draw, [draw])

  return (
    <canvas
      ref={canvasRef}
      aria-hidden="true"
      data-testid="clock-overlay"
      className="pointer-events-none absolute inset-0 z-[1]"
    />
  )
}
