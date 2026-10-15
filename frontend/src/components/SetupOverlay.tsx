import { useCallback, useEffect, useRef } from 'react'
import type { SetupLayer } from '@/lib/api/setups'
import type { ChartAdapter, DomainBar } from '@/lib/chart/types'
import { timeframeSeconds } from '@/lib/chart/dateNavigation'
import { renderSetups } from '@/lib/setups/render'
import { useOverlayCanvas } from '@/hooks/useOverlayCanvas'

interface SetupOverlayProps {
  adapter: ChartAdapter | null
  layer: SetupLayer | null
  bars: DomainBar[]
  timeframe: string
  shown: ReadonlySet<string>
}

/**
 * Detected setups over the clock and under the drawings (T9, S-3): a canvas that never takes the
 * pointer, so panning, zooming and drawing all pass straight through it.
 */
export function SetupOverlay({ adapter, layer, bars, timeframe, shown }: SetupOverlayProps) {
  const canvasRef = useRef<HTMLCanvasElement>(null)

  const draw = useCallback(() => {
    const canvas = canvasRef.current
    const projection = adapter?.getProjection()
    const pane = adapter?.getPaneSize()
    const ctx = canvas?.getContext('2d')
    if (!canvas || !projection || !pane || !ctx) return
    renderSetups(ctx, layer, bars, projection, {
      width: canvas.width,
      height: canvas.height,
      pane,
      shown,
      step: timeframeSeconds(timeframe),
    })
  }, [adapter, layer, bars, timeframe, shown])

  useOverlayCanvas(canvasRef, adapter, draw)
  useEffect(draw, [draw])

  return (
    <canvas
      ref={canvasRef}
      aria-hidden="true"
      data-testid="setup-overlay"
      className="pointer-events-none absolute inset-0 z-[1]"
    />
  )
}
