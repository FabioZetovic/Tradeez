import { useEffect, useRef } from 'react'
import type { ChartAdapter, ChartLayer, LayerHandle } from '@/lib/chart/types'
import type { Projection } from '@/lib/drawing/types'

type Paint = (ctx: CanvasRenderingContext2D, projection: Projection, pane: { width: number; height: number }) => void

/**
 * Paints with the chart, in the same frame as its candles (T37, BUG-095).
 *
 * The chart repaints the layer itself on every pan, zoom and resize; a new `paint` — the layer's data changed —
 * asks it for one more. The projection is read at paint time, so it is always the frame being painted.
 */
export function useChartLayer(adapter: ChartAdapter | null, zOrder: ChartLayer['zOrder'], paint: Paint): void {
  const paintRef = useRef(paint)
  const handle = useRef<LayerHandle | null>(null)

  useEffect(() => {
    if (!adapter) return
    const added = adapter.addLayer({
      zOrder,
      paint: (ctx, pane) => {
        const projection = adapter.getProjection()
        if (projection) paintRef.current(ctx, projection, pane)
      },
    })
    handle.current = added
    return () => {
      added.remove()
      handle.current = null
    }
  }, [adapter, zOrder])

  useEffect(() => {
    paintRef.current = paint
    handle.current?.redraw()
  }, [paint])
}
