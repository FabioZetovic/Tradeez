import { render } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { PriceChart } from './PriceChart'
import { FakeChartAdapter } from '@/test/fakeChartAdapter'
import type { DomainBar } from '@/lib/chart/types'

const bars: DomainBar[] = [
  { time: 1710338400, open: 80_000, high: 80_010, low: 79_990, close: 80_005, volume: 100 },
  { time: 1710338700, open: 80_005, high: 80_020, low: 80_000, close: 80_015, volume: 120 },
]

describe('PriceChart', () => {
  it('mounts the adapter with the instrument tick size', () => {
    const adapter = new FakeChartAdapter()
    render(<PriceChart bars={bars} tickSize={0.25} exchangeTimezone="America/Chicago" axisTimezone="exchange" adapterFactory={() => adapter} />)

    expect(adapter.mounted).toBe(true)
    expect(adapter.mountOptions?.tickSize).toBe(0.25)
  })

  it('passes bars through and fits the view', () => {
    const adapter = new FakeChartAdapter()
    render(<PriceChart bars={bars} tickSize={0.25} exchangeTimezone="America/Chicago" axisTimezone="exchange" adapterFactory={() => adapter} />)

    expect(adapter.bars).toEqual(bars)
    expect(adapter.fitCalls).toBeGreaterThan(0)
  })

  it('reuses the mounted chart when only the data changes', () => {
    // Remounting on every data change would reset zoom and pan. During a replay that fires on
    // every bar, so the user could never hold a view steady while the market moved.
    const adapter = new FakeChartAdapter()
    const { rerender } = render(
      <PriceChart bars={bars} tickSize={0.25} exchangeTimezone="America/Chicago" axisTimezone="exchange" adapterFactory={() => adapter} />,
    )

    rerender(<PriceChart bars={[...bars, bars[1]]} tickSize={0.25} exchangeTimezone="America/Chicago" axisTimezone="exchange" adapterFactory={() => adapter} />)

    // The assertion that matters is the mount count: the chart instance must survive a data change,
    // or zoom and pan are discarded. How many times setBars was called is incidental — mount now
    // seeds the data too, so counting those would break for a reason nobody cares about.
    expect(adapter.mount).toHaveBeenCalledTimes(1)
    expect(adapter.bars).toHaveLength(3)
  })

  it('re-seeds data after a remount so the chart is never left blank', () => {
    // Toggling the axis timezone remounts the chart, because Lightweight Charts fixes its time
    // formatter at creation. The data effect does not re-run — `bars` did not change — so without
    // seeding at mount the user is left staring at an empty chart.
    //
    // This was caught by clicking the toggle in a browser, after the suite was already green.
    const adapter = new FakeChartAdapter()
    const { rerender } = render(
      <PriceChart
        bars={bars}
        tickSize={0.25}
        exchangeTimezone="America/Chicago"
        axisTimezone="exchange"
        adapterFactory={() => adapter}
      />,
    )
    adapter.setBars.mockClear()

    rerender(
      <PriceChart
        bars={bars}
        tickSize={0.25}
        exchangeTimezone="America/Chicago"
        axisTimezone="utc"
        adapterFactory={() => adapter}
      />,
    )

    expect(adapter.mount).toHaveBeenCalledTimes(2)
    expect(adapter.setBars).toHaveBeenCalledWith(bars)
  })

  it('destroys the adapter on unmount', () => {
    // Lightweight Charts holds a canvas and resize observers. Leaking one per navigation would
    // accumulate silently until the tab slowed to a crawl.
    const adapter = new FakeChartAdapter()
    const { unmount } = render(
      <PriceChart bars={bars} tickSize={0.25} exchangeTimezone="America/Chicago" axisTimezone="exchange" adapterFactory={() => adapter} />,
    )

    unmount()
    expect(adapter.destroyed).toBe(true)
  })
})
