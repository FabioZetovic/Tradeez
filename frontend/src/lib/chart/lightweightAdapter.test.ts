import { afterEach, describe, expect, it, vi } from 'vitest'
import { TickMarkType } from 'lightweight-charts'
import { LightweightChartAdapter } from './lightweightAdapter'

/**
 * The adapter against a recording stand-in for the renderer.
 *
 * jsdom cannot run Lightweight Charts, so these tests check what the adapter *asks* the library to
 * do. That is exactly the level BUG-029 lived at: the time formatter was correct and tested, and
 * the axis still showed UTC, because the adapter never handed a formatter to the axis at all.
 */
const created: Array<{ options: Record<string, unknown> }> = []

vi.mock('lightweight-charts', async (importOriginal) => {
  const actual = await importOriginal<typeof import('lightweight-charts')>()
  const series = {
    setData: vi.fn(),
    update: vi.fn(),
    data: vi.fn(() => []),
    priceToCoordinate: vi.fn(),
    coordinateToPrice: vi.fn(),
  }
  const timeScale = {
    fitContent: vi.fn(),
    setVisibleLogicalRange: vi.fn(),
    getVisibleLogicalRange: vi.fn(() => null as { from: number; to: number } | null),
    subscribeVisibleLogicalRangeChange: vi.fn(),
    unsubscribeVisibleLogicalRangeChange: vi.fn(),
    timeToCoordinate: vi.fn(),
    coordinateToTime: vi.fn(),
  }
  return {
    ...actual,
    createChart: vi.fn((_container: HTMLElement, options: Record<string, unknown>) => {
      created.push({ options })
      return {
        addSeries: vi.fn(() => series),
        timeScale: () => timeScale,
        remove: vi.fn(),
        resize: vi.fn(),
        subscribeCrosshairMove: vi.fn(),
      }
    }),
  }
})

afterEach(() => {
  created.length = 0
})

type TickFormatter = (time: number, type: TickMarkType) => string | null

function mountedOptions(axisTimezone: 'exchange' | 'utc') {
  new LightweightChartAdapter().mount(document.createElement('div'), {
    tickSize: 0.25,
    exchangeTimezone: 'America/Chicago',
    axisTimezone,
  })
  return created[0].options as {
    timeScale: { tickMarkFormatter?: TickFormatter }
    localization: { timeFormatter: (time: number) => string }
  }
}

describe('LightweightChartAdapter — BUG-029', () => {
  const NINE_CDT = 1710338400 // 2024-03-13 14:00 UTC

  it('hands the axis a tick-mark formatter, not only the crosshair', () => {
    const options = mountedOptions('exchange')
    expect(options.timeScale.tickMarkFormatter).toBeTypeOf('function')
  })

  it('labels axis ticks in the same zone as the crosshair', () => {
    const options = mountedOptions('exchange')
    const tick = options.timeScale.tickMarkFormatter!(NINE_CDT, TickMarkType.Time)
    expect(tick).toBe('09:00')
    expect(options.localization.timeFormatter(NINE_CDT)).toBe('09:00')
  })

  it('switches both to UTC together', () => {
    const options = mountedOptions('utc')
    expect(options.timeScale.tickMarkFormatter!(NINE_CDT, TickMarkType.Time)).toBe('14:00')
    expect(options.localization.timeFormatter(NINE_CDT)).toBe('14:00 UTC')
  })
})

describe('the stretch of time on screen (DEC-075)', () => {
  it('is read and set by logical position, empty space included, and only once the series has candles', async () => {
    const { createChart } = await import('lightweight-charts')
    const adapter = new LightweightChartAdapter()
    expect(adapter.getVisibleTimeRange()).toBeNull()
    adapter.mount(document.createElement('div'), {
      tickSize: 0.25,
      exchangeTimezone: 'America/Chicago',
      axisTimezone: 'exchange',
    })
    const chart = vi.mocked(createChart).mock.results.at(-1)!.value
    const timeScale = chart.timeScale()
    const series = vi.mocked(chart.addSeries).mock.results[0].value

    vi.mocked(timeScale.getVisibleLogicalRange).mockReturnValueOnce({ from: -2, to: 3 } as never)
    expect(adapter.getVisibleTimeRange()).toBeNull()

    adapter.setVisibleTimeRange({ from: 1, to: 2 })
    expect(timeScale.setVisibleLogicalRange).not.toHaveBeenCalled()

    const candles = [0, 300, 600, 900].map((time) => ({ time: time as never }))
    vi.mocked(series.data).mockReturnValue(candles)
    vi.mocked(timeScale.getVisibleLogicalRange).mockReturnValueOnce({ from: -2, to: 5 } as never)
    expect(adapter.getVisibleTimeRange()).toEqual({ from: -600, to: 1500 })
    adapter.setVisibleTimeRange({ from: -300, to: 1200 })
    expect(timeScale.setVisibleLogicalRange).toHaveBeenLastCalledWith({ from: -1, to: 4 })
    vi.mocked(series.data).mockReturnValue([])
  })
})
