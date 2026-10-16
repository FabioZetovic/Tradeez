import {
  CandlestickSeries,
  createChart,
  TickMarkType,
  type CandlestickData,
  type IChartApi,
  type ISeriesApi,
  type Time,
} from 'lightweight-charts'
import type { ChartAdapter, ChartMountOptions, DomainBar } from './types'
import type { Projection } from '../drawing/types'
import { decimalsFor, ticksToPrice } from './priceFormat'
import { createTickMarkFormatter, createTimeFormatter, type TickKind } from './timeAxis'

/**
 * {@link ChartAdapter} backed by TradingView Lightweight Charts v5 (Apache 2.0).
 *
 * This is the only file in the project that imports the charting library. Everything else talks to
 * the interface, which is what makes DEC-004's "swap the renderer later" a real option rather than
 * an aspiration.
 */
const RIGHT_OFFSET_BARS = 12

const TICK_KIND: Record<TickMarkType, TickKind> = {
  [TickMarkType.Year]: 'year',
  [TickMarkType.Month]: 'month',
  [TickMarkType.DayOfMonth]: 'day',
  [TickMarkType.Time]: 'time',
  [TickMarkType.TimeWithSeconds]: 'seconds',
}

export class LightweightChartAdapter implements ChartAdapter {
  private chart: IChartApi | null = null
  private series: ISeriesApi<'Candlestick'> | null = null
  private tickSize = 0.25

  mount(container: HTMLElement, options: ChartMountOptions): void {
    this.destroy()
    this.tickSize = options.tickSize
    const tickLabel = createTickMarkFormatter(options.exchangeTimezone, options.axisTimezone, options.daily)

    this.chart = createChart(container, {
      autoSize: true,
      layout: {
        background: { color: 'transparent' },
        textColor: '#8b949e',
        attributionLogo: false,
      },
      grid: {
        vertLines: { color: 'rgba(48, 54, 61, 0.5)' },
        horzLines: { color: 'rgba(48, 54, 61, 0.5)' },
      },
      crosshair: { mode: 0 }, // free crosshair: a trader reads levels between candles, not just on them
      rightPriceScale: { borderColor: '#30363d' },
      timeScale: {
        borderColor: '#30363d',
        timeVisible: true,
        secondsVisible: false,
        // Empty space after the last bar. The position tool draws its zones to the right of the
        // entry, and without room there a bracket on the latest bar is a sliver against the axis.
        rightOffset: RIGHT_OFFSET_BARS,
        // BUG-029 — the axis ticks. `localization.timeFormatter` below covers only the crosshair
        // label; without this the axis stayed in UTC while the crosshair showed exchange time.
        tickMarkFormatter: (time: Time, type: TickMarkType) =>
          typeof time === 'number' ? tickLabel(time, TICK_KIND[type]) : null,
      },
      localization: {
        // BUG-014 — labels in the exchange's own timezone by default. The data is stored in UTC
        // (INVARIANT 4) and this is purely a presentation concern, but it is not cosmetic: session
        // times are what an ICT curriculum is built on, and daylight saving means a fixed offset
        // would be wrong for half the year.
        timeFormatter: createTimeFormatter(options.exchangeTimezone, options.axisTimezone, options.daily),
      },
    })

    this.series = this.chart.addSeries(CandlestickSeries, {
      upColor: '#3fb950',
      downColor: '#f85149',
      borderUpColor: '#3fb950',
      borderDownColor: '#f85149',
      wickUpColor: '#3fb950',
      wickDownColor: '#f85149',
      priceFormat: {
        type: 'price',
        precision: decimalsFor(options.tickSize),
        // Snaps the price scale to the instrument's real grid, so the axis shows prices that could
        // actually trade rather than arbitrary interpolations between them.
        minMove: options.tickSize,
      },
    })
  }

  setBars(bars: DomainBar[]): void {
    this.series?.setData(bars.map((bar) => this.toChartBar(bar)))
  }

  updateBar(bar: DomainBar): void {
    this.series?.update(this.toChartBar(bar))
  }

  showRecent(count: number): void {
    const total = this.series?.data().length ?? 0
    if (!this.chart || total === 0) return
    this.chart.timeScale().setVisibleLogicalRange({
      from: Math.max(0, total - count),
      to: total - 1 + RIGHT_OFFSET_BARS,
    })
  }

  fitContent(): void {
    this.chart?.timeScale().fitContent()
  }

  resize(width: number, height: number): void {
    this.chart?.resize(width, height)
  }

  getProjection(): Projection | null {
    const chart = this.chart
    const series = this.series
    if (!chart || !series) return null

    const timeScale = chart.timeScale()
    const tickSize = this.tickSize

    // Ticks are converted to a price on the way in and back on the way out, so the drawing layer
    // works purely in ticks (INVARIANT 3) while the chart works in prices.
    return {
      // null means "outside the visible range" — the overlay treats that as not drawable rather
      // than clamping, because a line pinned to the chart edge would claim a level it is not on.
      timeToX: (time) => timeScale.timeToCoordinate(time as never) as number | null,
      priceToY: (priceTicks) =>
        series.priceToCoordinate(ticksToPrice(priceTicks, tickSize)) as number | null,
      xToTime: (x) => {
        const time = timeScale.coordinateToTime(x)
        return typeof time === 'number' ? time : null
      },
      yToPrice: (y) => {
        const price = series.coordinateToPrice(y)
        return price === null ? null : Math.round((price as number) / tickSize)
      },
    }
  }

  getPaneSize(): { width: number; height: number } | null {
    return this.chart ? this.chart.paneSize() : null
  }

  onViewportChange(listener: () => void): () => void {
    const chart = this.chart
    if (!chart) return () => {}

    const timeScale = chart.timeScale()
    timeScale.subscribeVisibleLogicalRangeChange(listener)
    return () => timeScale.unsubscribeVisibleLogicalRangeChange(listener)
  }

  destroy(): void {
    this.chart?.remove()
    this.chart = null
    this.series = null
  }

  /** The tick-to-price boundary. Nothing downstream of here sees a tick count. */
  private toChartBar(bar: DomainBar): CandlestickData<Time> {
    return {
      time: bar.time as Time,
      open: ticksToPrice(bar.open, this.tickSize),
      high: ticksToPrice(bar.high, this.tickSize),
      low: ticksToPrice(bar.low, this.tickSize),
      close: ticksToPrice(bar.close, this.tickSize),
    }
  }
}
