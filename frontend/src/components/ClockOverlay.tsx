import { useCallback } from 'react'
import type { ClockLayer } from '@/lib/api/clock'
import type { ChartAdapter, DomainBar } from '@/lib/chart/types'
import type { ClockLayerKey } from '@/lib/clock/layers'
import type { Projection } from '@/lib/drawing/types'
import { renderClock } from '@/lib/clock/render'
import { timeframeSeconds } from '@/lib/chart/dateNavigation'
import { useChartLayer } from '@/hooks/useChartLayer'

interface ClockOverlayProps {
  adapter: ChartAdapter | null
  layer: ClockLayer | null
  bars: DomainBar[]
  timeframe: string
  tickSize: number
  shown: ReadonlySet<ClockLayerKey>
}

/**
 * The New York clock (T7), painted by the chart under its candles (T37): it moves with them in the same frame, and
 * never takes the pointer.
 */
export function ClockOverlay({ adapter, layer, bars, timeframe, tickSize, shown }: ClockOverlayProps) {
  const paint = useCallback(
    (ctx: CanvasRenderingContext2D, projection: Projection, pane: { width: number; height: number }) =>
      renderClock(ctx, layer, bars, projection, { pane, shown, step: timeframeSeconds(timeframe), tickSize }),
    [layer, bars, timeframe, tickSize, shown],
  )
  useChartLayer(adapter, 'bottom', paint)
  return null
}
