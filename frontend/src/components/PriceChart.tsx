import { useEffect, useRef, useState, type ReactNode } from 'react'
import type { ChartAdapter, DomainBar } from '@/lib/chart/types'
import { LightweightChartAdapter } from '@/lib/chart/lightweightAdapter'

interface PriceChartProps {
  bars: DomainBar[]
  tickSize: number
  exchangeTimezone: string
  axisTimezone: 'exchange' | 'utc'
  /**
   * Rendered on top of the chart, given the live adapter.
   *
   * A render prop rather than a fixed child, so the chart knows nothing about drawings. The overlay
   * needs the adapter to project market coordinates onto the canvas, and the adapter only exists
   * after mount — this hands it over at exactly the right moment.
   */
  overlay?: (adapter: ChartAdapter | null) => ReactNode
  /**
   * Injectable for tests. jsdom has no working canvas, so a component that constructed the renderer
   * itself could not be tested at all — this is DEC-004's abstraction earning its keep immediately
   * rather than only if the renderer is ever swapped.
   */
  adapterFactory?: () => ChartAdapter
}

export function PriceChart({
  bars,
  tickSize,
  exchangeTimezone,
  axisTimezone,
  adapterFactory,
  overlay,
}: PriceChartProps) {
  const containerRef = useRef<HTMLDivElement>(null)
  const adapterRef = useRef<ChartAdapter | null>(null)

  // State, not just a ref: the overlay is a child and has to re-render when the adapter appears.
  const [mountedAdapter, setMountedAdapter] = useState<ChartAdapter | null>(null)

  // The factory is read at mount and never again, so it is held in a ref rather than listed as a
  // dependency. Depending on it directly meant any caller passing an inline arrow — the natural way
  // to write it — remounted the chart on every parent render. During a replay the parent re-renders
  // on every bar, so the user's zoom and pan would be discarded continuously. A test caught this.
  const factoryRef = useRef(adapterFactory)
  factoryRef.current = adapterFactory

  // The latest bars, readable from inside the mount effect. A remount builds a fresh chart with no
  // data, and the data effect below does not re-run because `bars` did not change — so without
  // seeding here, toggling the timezone left the user staring at an empty chart. Found by clicking
  // the toggle, not by the suite; there is a test for it now.
  const barsRef = useRef(bars)
  barsRef.current = bars

  // Remount when the instrument changes (a different price scale) or when the axis timezone is
  // toggled — Lightweight Charts fixes its time formatter at creation, so the label format cannot
  // be swapped on a live chart.
  useEffect(() => {
    const container = containerRef.current
    if (!container) return

    const adapter = (factoryRef.current ?? (() => new LightweightChartAdapter()))()
    adapter.mount(container, { tickSize, exchangeTimezone, axisTimezone })
    adapterRef.current = adapter
    setMountedAdapter(adapter)

    // Seed the new instance with whatever is already loaded.
    adapter.setBars(barsRef.current)
    adapter.fitContent()

    return () => {
      adapter.destroy()
      adapterRef.current = null
      setMountedAdapter(null)
    }
  }, [tickSize, exchangeTimezone, axisTimezone])

  // Data updates reuse the mounted chart.
  useEffect(() => {
    const adapter = adapterRef.current
    if (!adapter) return
    adapter.setBars(bars)
    adapter.fitContent()
  }, [bars])

  // The overlay is positioned against this frame, and pointer events on the chart bubble up to it —
  // which is what lets the overlay hit-test while remaining transparent to the pointer.
  return (
    <div className="chart-frame">
      <div ref={containerRef} className="chart" data-testid="price-chart" />
      {overlay?.(mountedAdapter)}
    </div>
  )
}
