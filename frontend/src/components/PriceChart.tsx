import { useEffect, useLayoutEffect, useRef, useState, type ReactNode } from 'react'
import type { ChartAdapter, DomainBar } from '@/lib/chart/types'
import type { AxisTimezone } from '@/lib/chart/timeAxis'
import { LightweightChartAdapter } from '@/lib/chart/lightweightAdapter'
import { cx } from '@/lib/styles'

interface PriceChartProps {
  bars: DomainBar[]
  tickSize: number
  exchangeTimezone: string
  axisTimezone: AxisTimezone
  /** Daily candles: the axis and crosshair name trading days, not 18:00 opens (T35). */
  daily?: boolean
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
  /** The chart's height, as Tailwind classes. The renderer fills whatever box it is given. */
  heightClass?: string
  /** Moved only by the chart it keeps to (BUG-092). */
  followsOnly?: boolean
  /** Neither scrolls nor zooms (Home, R-12). */
  still?: boolean
}

export function PriceChart({
  bars,
  tickSize,
  exchangeTimezone,
  axisTimezone,
  daily = false,
  adapterFactory,
  overlay,
  recentBars,
  heightClass = 'h-[30rem]',
  followsOnly = false,
  still = false,
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

  // The latest bars, readable from inside the mount effect. A remount builds a fresh chart with no
  // data, and the data effect below does not re-run because `bars` did not change — so without
  // seeding here, toggling the timezone left the user staring at an empty chart. Found by clicking
  // the toggle, not by the suite; there is a test for it now.
  const barsRef = useRef(bars)

  // Both written at commit, before the mount effect below reads them — layout effects run first
  // (BUG-035: never assign a ref during render).
  useLayoutEffect(() => {
    factoryRef.current = adapterFactory
    barsRef.current = bars
  })

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
    adapter.mount(container, { tickSize, exchangeTimezone, axisTimezone, daily, followsOnly, still })
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
  }, [tickSize, exchangeTimezone, axisTimezone, daily, recentBars, followsOnly, still])

  // Data updates reuse the mounted chart.
  //
  // A replay appends a bar or a few at a time, and may first complete the candle that was still
  // forming (T34). Those are fed in with updateBar, which updates the newest bar or extends the
  // series in place; replacing the series and refitting on every step would snap the view back to
  // "everything" each time a bar arrived, so the learner could never zoom in on the price action
  // they were actually watching. Anything else is a new series and is refitted.
  useEffect(() => {
    const adapter = adapterRef.current
    if (!adapter) return
    const shown = shownRef.current
    if (bars === shown) return

    if (continues(shown, bars)) {
      for (let i = shown.length - 1; i < bars.length; i++) adapter.updateBar(bars[i])
    } else {
      adapter.setBars(bars)
      frame(adapter, recentBars)
    }
    shownRef.current = bars
  }, [bars, recentBars])

  // The overlay is positioned against this frame, and pointer events on the chart bubble up to it —
  // which is what lets the overlay hit-test while remaining transparent to the pointer.
  return (
    <div className="relative min-w-0 flex-1">
      <div
        ref={containerRef}
        className={cx('overflow-hidden rounded-lg border border-line bg-surface', heightClass)}
        data-testid="price-chart"
      />
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
 * True when `next` is `shown` carried on: the same bars, the newest perhaps updated — a candle
 * that was still forming, now further on — and perhaps newer ones after it.
 *
 * Checks the ends rather than every bar: a replay never rewrites a candle that has closed (the
 * server guarantees that, and a backend test holds it to it), so matching first and last times is
 * enough to know the prefix is the same series. Bars after it must be strictly later, because
 * updateBar on an equal time would overwrite a bar instead of adding one.
 */
function continues(shown: DomainBar[], next: DomainBar[]): boolean {
  if (shown.length === 0 || next.length < shown.length) return false
  const last = shown.length - 1
  return (
    next[0].time === shown[0].time &&
    next[last].time === shown[last].time &&
    (next.length === shown.length || next[shown.length].time > shown[last].time)
  )
}
