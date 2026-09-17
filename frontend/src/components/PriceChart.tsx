import { useEffect, useRef } from 'react'
import type { ChartAdapter, DomainBar } from '@/lib/chart/types'
import { LightweightChartAdapter } from '@/lib/chart/lightweightAdapter'

interface PriceChartProps {
  bars: DomainBar[]
  tickSize: number
  /**
   * Injectable for tests. jsdom has no working canvas, so a component that constructed the renderer
   * itself could not be tested at all — this is DEC-004's abstraction earning its keep immediately
   * rather than only if the renderer is ever swapped.
   */
  adapterFactory?: () => ChartAdapter
}

export function PriceChart({ bars, tickSize, adapterFactory }: PriceChartProps) {
  const containerRef = useRef<HTMLDivElement>(null)
  const adapterRef = useRef<ChartAdapter | null>(null)

  // The factory is read at mount and never again, so it is held in a ref rather than listed as a
  // dependency. Depending on it directly meant any caller passing an inline arrow — the natural way
  // to write it — remounted the chart on every parent render. During a replay the parent re-renders
  // on every bar, so the user's zoom and pan would be discarded continuously. A test caught this.
  const factoryRef = useRef(adapterFactory)
  factoryRef.current = adapterFactory

  // Remount only when the instrument's tick size changes: that is a different product and needs a
  // different price scale.
  useEffect(() => {
    const container = containerRef.current
    if (!container) return

    const adapter = (factoryRef.current ?? (() => new LightweightChartAdapter()))()
    adapter.mount(container, { tickSize })
    adapterRef.current = adapter

    return () => {
      adapter.destroy()
      adapterRef.current = null
    }
  }, [tickSize])

  // Data updates reuse the mounted chart.
  useEffect(() => {
    const adapter = adapterRef.current
    if (!adapter) return
    adapter.setBars(bars)
    adapter.fitContent()
  }, [bars])

  return <div ref={containerRef} className="chart" data-testid="price-chart" />
}
