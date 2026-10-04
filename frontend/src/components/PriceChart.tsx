import { useEffect, useRef, useState, type ReactNode } from 'react'
import type { ChartAdapter, DomainBar } from '@/lib/chart/types'
import type { AxisTimezone } from '@/lib/chart/timeAxis'
import { LightweightChartAdapter } from '@/lib/chart/lightweightAdapter'

interface PriceChartProps {
  bars: DomainBar[]
  tickSize: number
  exchangeTimezone: string
  axisTimezone: AxisTimezone
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
  /**
   * Frame only the most recent bars instead of fitting the whole series. Practice sets this: its
   * history is days long and the decision is about the last hour or two.
   */
  recentBars?: number
}

export function PriceChart({
  bars,
  tickSize,
  exchangeTimezone,
  axisTimezone,
  adapterFactory,
  overlay,
  recentBars,
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

  // What the mounted chart is currently displaying, so a data change can tell an append (a replay
  // step) from a different series (a new instrument or timeframe).
  const shownRef = useRef<DomainBar[]>([])

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
    frame(adapter, recentBars)
    shownRef.current = barsRef.current

    return () => {
      adapter.destroy()
      adapterRef.current = null
      setMountedAdapter(null)
    }
    // recentBars is listed so the effect is honest about what it reads. In practice it is a
    // constant per screen, so it never triggers a remount on its own.
  }, [tickSize, exchangeTimezone, axisTimezone, recentBars])

  // Data updates reuse the mounted chart.
  //
  // A replay appends a bar or a few at a time. Those are fed in with updateBar, which extends the
  // series in place; replacing the series and refitting on every step would snap the view back to
  // "everything" each time a bar arrived, so the learner could never zoom in on the price action
  // they were actually watching. Anything that is not a pure append is a new series and is refitted.
  useEffect(() => {
    const adapter = adapterRef.current
    if (!adapter) return
    const shown = shownRef.current
    if (bars === shown) return

    if (isAppendOf(shown, bars)) {
      for (let i = shown.length; i < bars.length; i++) adapter.updateBar(bars[i])
    } else {
      adapter.setBars(bars)
      frame(adapter, recentBars)
    }
    shownRef.current = bars
  }, [bars, recentBars])

  // The overlay is positioned against this frame, and pointer events on the chart bubble up to it —
  // which is what lets the overlay hit-test while remaining transparent to the pointer.
  return (
    <div className="chart-frame">
      <div ref={containerRef} className="chart" data-testid="price-chart" />
      {overlay?.(mountedAdapter)}
    </div>
  )
}

/** Frames a freshly loaded series: the most recent bars if asked, otherwise all of it. */
function frame(adapter: ChartAdapter, recentBars: number | undefined) {
  if (recentBars) adapter.showRecent(recentBars)
  else adapter.fitContent()
}

/**
 * True when `next` is `shown` with newer bars on the end.
 *
 * Checks the ends rather than every bar: a replay never rewrites what it has revealed (the server
 * guarantees that, and a backend test holds it to it), so matching first and last is enough to know
 * the prefix is the same series. The appended bars must be strictly later, because updateBar on an
 * equal time would overwrite a bar instead of adding one.
 */
function isAppendOf(shown: DomainBar[], next: DomainBar[]): boolean {
  if (shown.length === 0 || next.length <= shown.length) return false
  const last = shown.length - 1
  return (
    next[0].time === shown[0].time &&
    next[last].time === shown[last].time &&
    next[shown.length].time > shown[last].time
  )
}
