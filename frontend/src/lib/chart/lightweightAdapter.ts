import {
  CandlestickSeries,
  createChart,
  type CandlestickData,
  type IChartApi,
  type ISeriesApi,
  type Time,
} from 'lightweight-charts'
import type { ChartAdapter, ChartMountOptions, DomainBar } from './types'
import { decimalsFor, ticksToPrice } from './priceFormat'

/**
 * {@link ChartAdapter} backed by TradingView Lightweight Charts v5 (Apache 2.0).
 *
 * This is the only file in the project that imports the charting library. Everything else talks to
 * the interface, which is what makes DEC-004's "swap the renderer later" a real option rather than
 * an aspiration.
 */
export class LightweightChartAdapter implements ChartAdapter {
  private chart: IChartApi | null = null
  private series: ISeriesApi<'Candlestick'> | null = null
  private tickSize = 0.25

  mount(container: HTMLElement, options: ChartMountOptions): void {
    this.destroy()
    this.tickSize = options.tickSize

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
      },
      localization: {
        // The exchange session crosses midnight and the user's machine could be in any timezone.
        // Showing UTC keeps the chart consistent with the data, which is stored in UTC throughout
        // (INVARIANT 4). Exchange-local display comes with the session tooling.
        timeFormatter: (time: number) =>
          new Date(time * 1000).toISOString().slice(11, 16) + ' UTC',
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

  fitContent(): void {
    this.chart?.timeScale().fitContent()
  }

  resize(width: number, height: number): void {
    this.chart?.resize(width, height)
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
