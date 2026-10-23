import { useCallback } from 'react'
import type { SetupLayer } from '@/lib/api/setups'
import type { ChartAdapter, DomainBar } from '@/lib/chart/types'
import type { Projection } from '@/lib/drawing/types'
import { timeframeSeconds } from '@/lib/chart/dateNavigation'
import { renderSetups } from '@/lib/setups/render'
import { useChartLayer } from '@/hooks/useChartLayer'

interface SetupOverlayProps {
  adapter: ChartAdapter | null
  layer: SetupLayer | null
  bars: DomainBar[]
  timeframe: string
  shown: ReadonlySet<string>
}

/**
 * Detected setups (T9, S-3), painted by the chart over its candles and under the drawings (T37): they move with the
 * candles in the same frame, and never take the pointer.
 */
export function SetupOverlay({ adapter, layer, bars, timeframe, shown }: SetupOverlayProps) {
  const paint = useCallback(
    (ctx: CanvasRenderingContext2D, projection: Projection, pane: { width: number; height: number }) =>
      renderSetups(ctx, layer, bars, projection, { pane, shown, step: timeframeSeconds(timeframe) }),
    [layer, bars, timeframe, shown],
  )
  useChartLayer(adapter, 'normal', paint)
  return null
}
