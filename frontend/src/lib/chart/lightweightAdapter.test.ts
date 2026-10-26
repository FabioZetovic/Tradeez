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
    attachPrimitive: vi.fn(),
    detachPrimitive: vi.fn(),
  }
  const timeScale = {
    fitContent: vi.fn(),
    setVisibleLogicalRange: vi.fn(),
    getVisibleLogicalRange: vi.fn(() => null as { from: number; to: number } | null),
    subscribeVisibleLogicalRangeChange: vi.fn(),
    unsubscribeVisibleLogicalRangeChange: vi.fn(),
    // Candle n at x = 100 + 10n, the empty space on either side included.
    logicalToCoordinate: vi.fn((logical: number) => 100 + logical * 10),
    coordinateToLogical: vi.fn((x: number) => (x - 100) / 10),
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

describe('LightweightChartAdapter — BUG-092', () => {
  it('a chart that follows another neither scrolls nor zooms its time on its own; its price axis still scales', () => {
    new LightweightChartAdapter().mount(document.createElement('div'), {
      tickSize: 0.25,
      exchangeTimezone: 'America/Chicago',
      axisTimezone: 'exchange',
      followsOnly: true,
    })
    expect(created[0].options).toMatchObject({
      handleScroll: false,
      handleScale: { mouseWheel: false, pinch: false, axisPressedMouseMove: { time: false, price: true } },
    })
  })

  it('a chart of its own keeps the library’s scrolling and zooming', () => {
    mountedOptions('exchange')
    expect(created[0].options).not.toHaveProperty('handleScroll')
    expect(created[0].options).not.toHaveProperty('handleScale')
  })
})

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

describe('converting between time and the screen (BUG-097)', () => {
  it("goes on past the last candle, a candle's step at a time, so a drawing can be put in the empty space ahead", async () => {
    const { createChart } = await import('lightweight-charts')
    const adapter = new LightweightChartAdapter()
    adapter.mount(document.createElement('div'), { tickSize: 0.25, exchangeTimezone: 'America/Chicago', axisTimezone: 'exchange' })
    const chart = vi.mocked(createChart).mock.results.at(-1)!.value
    const series = vi.mocked(chart.addSeries).mock.results[0].value
    // Five-minute candles with the weekend's gap between the third and the fourth.
    vi.mocked(series.data).mockReturnValue([0, 300, 600, 90_000].map((time) => ({ time: time as never })))
    const projection = adapter.getProjection()!

    // On the candles: exactly where the chart draws them, gap or no gap; a click lands on the nearest one.
    expect(projection.timeToX(600)).toBe(120)
    expect(projection.timeToX(90_000)).toBe(130)
    expect(projection.xToTime(126)).toBe(90_000)
    expect(projection.xToTime(104)).toBe(0)
    // Ahead of the last candle — where the replay has not got to — and behind the first.
    expect(projection.xToTime(150)).toBe(90_600)
    expect(projection.timeToX(90_600)).toBe(150)
    expect(projection.xToTime(80)).toBe(-600)
    vi.mocked(series.data).mockReturnValue([])
    expect(adapter.getProjection()!.xToTime(150)).toBeNull()
    expect(adapter.getProjection()!.timeToX(0)).toBeNull()
  })
})

describe('layers painted by the chart (T37, BUG-095)', () => {
  const mount = (adapter: LightweightChartAdapter) =>
    adapter.mount(document.createElement('div'), { tickSize: 0.25, exchangeTimezone: 'America/Chicago', axisTimezone: 'exchange' })

  async function lastSeries() {
    const { createChart } = await import('lightweight-charts')
    const chart = vi.mocked(createChart).mock.results.at(-1)!.value
    return vi.mocked(chart.addSeries).mock.results.at(-1)!.value
  }

  it('attaches a layer to the candles, again after a remount, and no more once removed', async () => {
    const adapter = new LightweightChartAdapter()
    const paint = vi.fn()
    const handle = adapter.addLayer({ zOrder: 'bottom', paint }) // before the chart exists
    mount(adapter)
    const series = await lastSeries()
    expect(series.attachPrimitive).toHaveBeenCalledTimes(1)
    const primitive = series.attachPrimitive.mock.calls[0][0]

    // Painted in the pane's CSS pixels, in the chart's paint, at the z-order asked for.
    const [view] = primitive.paneViews()
    expect(view.zOrder()).toBe('bottom')
    const context = { save: vi.fn(), restore: vi.fn() }
    view.renderer().draw({ useMediaCoordinateSpace: (f: (scope: unknown) => void) => f({ context, mediaSize: { width: 640, height: 300 } }) })
    expect(paint).toHaveBeenCalledWith(context, { width: 640, height: 300 })

    series.attachPrimitive.mockClear()
    mount(adapter)
    expect(series.attachPrimitive).toHaveBeenCalledWith(primitive)

    handle.remove()
    expect(series.detachPrimitive).toHaveBeenCalledWith(primitive)
    series.attachPrimitive.mockClear()
    mount(adapter)
    expect(series.attachPrimitive).not.toHaveBeenCalled()
  })

  it('asks for a paint when attached, and when the layer asks to be redrawn', async () => {
    // A lesson's example never moves: a layer attached after its first paint was never painted at all.
    const adapter = new LightweightChartAdapter()
    mount(adapter)
    const series = await lastSeries()
    series.attachPrimitive.mockClear()
    const handle = adapter.addLayer({ zOrder: 'top', paint: () => {} })
    const primitive = series.attachPrimitive.mock.calls[0][0]
    const requestUpdate = vi.fn()

    primitive.attached({ requestUpdate })
    expect(requestUpdate).toHaveBeenCalledTimes(1)
    handle.redraw()
    expect(requestUpdate).toHaveBeenCalledTimes(2)
    primitive.detached()
    handle.redraw()
    expect(requestUpdate).toHaveBeenCalledTimes(2)
  })
})

describe('a still chart (T39, R-12)', () => {
  it('neither scrolls nor zooms; an ordinary chart does both', () => {
    const mount = (still?: boolean) => {
      new LightweightChartAdapter().mount(document.createElement('div'), {
        tickSize: 0.25,
        exchangeTimezone: 'America/Chicago',
        axisTimezone: 'exchange',
        still,
      })
      return created.at(-1)!.options
    }
    expect(mount(true)).toMatchObject({ handleScroll: false, handleScale: false })
    const ordinary = mount()
    expect(ordinary.handleScroll).toBeUndefined()
    expect(ordinary.handleScale).toBeUndefined()
  })
})
