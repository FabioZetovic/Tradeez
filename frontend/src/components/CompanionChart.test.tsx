import { act, render, screen, waitFor } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { AdapterOut, CompanionChart } from './CompanionChart'
import { FakeChartAdapter } from '@/test/fakeChartAdapter'

const BARS = [0, 1, 2].map((i) => ({
  time: 1_710_338_400 + i * 300,
  open: 23_000,
  high: 23_004,
  low: 22_996,
  close: 23_002,
  volume: 10,
}))

function companion(leader: FakeChartAdapter | null, follower: FakeChartAdapter) {
  return (
    <CompanionChart
      companion={{ instrument: 'ES', tickSize: 0.25, bars: BARS }}
      leader={leader}
      exchangeTimezone="America/Chicago"
      axisTimezone="exchange"
      adapterFactory={() => follower}
    />
  )
}

describe('CompanionChart (DEC-075)', () => {
  it('draws the other market, and keeps to the minutes the chart above shows — at first and as it moves', async () => {
    const leader = new FakeChartAdapter()
    leader.visibleRange = { from: 1000, to: 2000 }
    const follower = new FakeChartAdapter()
    render(companion(leader, follower))
    expect(screen.getByRole('figure', { name: 'ES over the same minutes' })).toBeInTheDocument()
    await waitFor(() => expect(follower.bars).toEqual(BARS))
    await waitFor(() => expect(follower.shownRange).toEqual({ from: 1000, to: 2000 }))

    leader.visibleRange = { from: 1500, to: 2500 }
    act(() => leader.emitViewportChange())
    expect(follower.shownRange).toEqual({ from: 1500, to: 2500 })
    // BUG-092: moved by the chart above alone, so the two never show different minutes.
    expect(follower.mountOptions?.followsOnly).toBe(true)
    expect(screen.getByRole('figure')).toHaveTextContent('move the chart above to move both')
  })

  it('stands on its own until the chart above is there, and lets go of it when unmounted', async () => {
    const follower = new FakeChartAdapter()
    const { rerender, unmount } = render(companion(null, follower))
    await waitFor(() => expect(follower.bars).toEqual(BARS))
    expect(follower.setVisibleTimeRange).not.toHaveBeenCalled()

    const leader = new FakeChartAdapter()
    rerender(companion(leader, follower))
    await waitFor(() => expect(leader.viewportListeners).toHaveLength(1))
    unmount()
    expect(leader.viewportListeners).toHaveLength(0)
  })

  it("hands a chart's adapter out of its overlay", () => {
    const adapter = new FakeChartAdapter()
    const onAdapter = vi.fn()
    render(<AdapterOut adapter={adapter} onAdapter={onAdapter} />)
    expect(onAdapter).toHaveBeenLastCalledWith(adapter)
  })
})
