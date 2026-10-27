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

  it('extends the series in place when bars are appended, keeping the view', () => {
    // A replay step. Replacing the series and refitting would snap the learner's zoom back to
    // 'everything' on every bar, so appends must go through updateBar and nothing else.
    const adapter = new FakeChartAdapter()
    const { rerender } = render(
      <PriceChart bars={bars} tickSize={0.25} exchangeTimezone="America/Chicago" axisTimezone="exchange" adapterFactory={() => adapter} />,
    )
    const setBarsCalls = adapter.setBars.mock.calls.length
    const fits = adapter.fitCalls

    const next: DomainBar = { time: 1710339000, open: 80_015, high: 80_030, low: 80_010, close: 80_025, volume: 90 }
    const later: DomainBar = { ...next, time: 1710339300 }
    rerender(<PriceChart bars={[...bars, next, later]} tickSize={0.25} exchangeTimezone="America/Chicago" axisTimezone="exchange" adapterFactory={() => adapter} />)

    // The newest bar is passed again first: it may have been a candle still forming (T34).
    expect(adapter.updates).toEqual([bars[bars.length - 1], next, later])
    expect(adapter.setBars.mock.calls.length).toBe(setBarsCalls)
    expect(adapter.fitCalls).toBe(fits)
  })

  it('T34: updates a candle that was still forming in place, keeping the view', () => {
    const adapter = new FakeChartAdapter()
    const { rerender } = render(
      <PriceChart bars={bars} tickSize={0.25} exchangeTimezone="America/Chicago" axisTimezone="exchange" adapterFactory={() => adapter} />,
    )
    const setBarsCalls = adapter.setBars.mock.calls.length
    const fits = adapter.fitCalls

    const formed = { ...bars[bars.length - 1], high: 80_090, close: 80_080 }
    rerender(<PriceChart bars={[...bars.slice(0, -1), formed]} tickSize={0.25} exchangeTimezone="America/Chicago" axisTimezone="exchange" adapterFactory={() => adapter} />)

    expect(adapter.updates).toEqual([formed])
    expect(adapter.setBars.mock.calls.length).toBe(setBarsCalls)
    expect(adapter.fitCalls).toBe(fits)
  })

  it('treats anything that is not a pure append as a new series and refits', () => {
    const adapter = new FakeChartAdapter()
    const { rerender } = render(
      <PriceChart bars={bars} tickSize={0.25} exchangeTimezone="America/Chicago" axisTimezone="exchange" adapterFactory={() => adapter} />,
    )
    const fits = adapter.fitCalls
    const other = bars.map((b) => ({ ...b, time: b.time + 86_400 }))
    rerender(<PriceChart bars={other} tickSize={0.25} exchangeTimezone="America/Chicago" axisTimezone="exchange" adapterFactory={() => adapter} />)

    expect(adapter.bars).toEqual(other)
    expect(adapter.fitCalls).toBe(fits + 1)
    expect(adapter.updates).toEqual([])
  })

  it('frames only the most recent bars when asked, instead of fitting everything', () => {
    const adapter = new FakeChartAdapter()
    render(<PriceChart bars={bars} tickSize={0.25} exchangeTimezone="America/Chicago" axisTimezone="exchange" adapterFactory={() => adapter} recentBars={120} />)

    expect(adapter.recentShown).toBe(120)
    expect(adapter.fitCalls).toBe(0)
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

describe('PriceChart, another market over the same minutes (BUG-094)', () => {
  it('replaces the series and refits: same times, other prices, is never a replay carried on', () => {
    const adapter = new FakeChartAdapter()
    const { rerender } = render(
      <PriceChart bars={bars} tickSize={0.25} exchangeTimezone="America/Chicago" axisTimezone="exchange" adapterFactory={() => adapter} />,
    )
    const fits = adapter.fitCalls
    // ES over NQ's minutes: every time the same, every price a quarter of NQ's.
    const es = bars.map((b) => ({ ...b, open: b.open / 4, high: b.high / 4, low: b.low / 4, close: b.close / 4 }))
    rerender(<PriceChart bars={es} tickSize={0.25} exchangeTimezone="America/Chicago" axisTimezone="exchange" adapterFactory={() => adapter} />)

    expect(adapter.bars).toEqual(es)
    expect(adapter.updates).toEqual([])
    expect(adapter.fitCalls).toBeGreaterThan(fits)
  })
})
