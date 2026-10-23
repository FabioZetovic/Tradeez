import { useEffect, type ReactNode } from 'react'
import { PriceChart } from './PriceChart'
import type { ChartAdapter, Companion, DomainBar } from '@/lib/chart/types'
import type { AxisTimezone } from '@/lib/chart/timeAxis'

interface CompanionChartProps {
  companion: Companion
  /** The chart above, whose stretch of time this one keeps to; null until it has mounted. */
  leader: ChartAdapter | null
  exchangeTimezone: string
  axisTimezone: AxisTimezone
  daily?: boolean
  adapterFactory?: () => ChartAdapter
  heightClass?: string
  overlay?: (adapter: ChartAdapter | null) => ReactNode
}

/**
 * The market a rule compares with, under the chart being read (DEC-075): SMT reads NQ beside ES, and a divergence
 * is seen by looking from one to the other. It keeps to the minutes the chart above shows as that one is panned or
 * zoomed, so a swing on one sits over the same moment on the other — and it is not moved on its own (BUG-092): a pan
 * here alone showed two moments as one.
 */
export function CompanionChart({
  companion,
  leader,
  exchangeTimezone,
  axisTimezone,
  daily,
  adapterFactory,
  heightClass = 'h-56',
  overlay,
}: CompanionChartProps) {
  return (
    <figure className="flex min-w-0 flex-col gap-1" aria-label={`${companion.instrument} over the same minutes`}>
      <figcaption className="text-xs text-subtle">
        <strong className="text-ink">{companion.instrument}</strong> over the same minutes — move the chart above to
        move both
      </figcaption>
      <PriceChart
        bars={companion.bars}
        tickSize={companion.tickSize}
        exchangeTimezone={exchangeTimezone}
        axisTimezone={axisTimezone}
        daily={daily}
        adapterFactory={adapterFactory}
        heightClass={heightClass}
        followsOnly
        overlay={(adapter) => (
          <>
            <FollowRange leader={leader} follower={adapter} bars={companion.bars} />
            {overlay?.(adapter)}
          </>
        )}
      />
    </figure>
  )
}

/**
 * Keeps one chart on the minutes another shows: once its own candles are in — it frames them itself when they
 * arrive, so it follows a moment later — and whenever the other is panned or zoomed.
 */
function FollowRange({
  leader,
  follower,
  bars,
}: {
  leader: ChartAdapter | null
  follower: ChartAdapter | null
  bars: DomainBar[]
}) {
  useEffect(() => {
    if (!leader || !follower) return
    const follow = () => {
      const range = leader.getVisibleTimeRange()
      if (range) follower.setVisibleTimeRange(range)
    }
    const framed = setTimeout(follow, 0)
    const off = leader.onViewportChange(follow)
    return () => {
      clearTimeout(framed)
      off()
    }
  }, [leader, follower, bars])
  return null
}

/** Hands a chart's adapter out of its overlay, for a companion chart to follow. */
export function AdapterOut({
  adapter,
  onAdapter,
}: {
  adapter: ChartAdapter | null
  onAdapter: (adapter: ChartAdapter | null) => void
}) {
  useEffect(() => onAdapter(adapter), [adapter, onAdapter])
  return null
}
