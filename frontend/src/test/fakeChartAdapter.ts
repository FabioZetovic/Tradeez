import { vi } from 'vitest'
import type { ChartAdapter, ChartLayer, ChartMountOptions, DomainBar, TimeRange } from '@/lib/chart/types'
import type { Projection } from '@/lib/drawing/types'

/**
 * A {@link ChartAdapter} that records calls instead of drawing.
 *
 * jsdom has no working canvas, so the real renderer cannot run in tests at all. Rather than mocking
 * the charting library — which would couple tests to its internals — the abstraction from DEC-004
 * is used as the seam. That decision was justified as "the renderer might be swapped later"; being
 * able to test the components at all is the benefit it delivered immediately.
 */
export class FakeChartAdapter implements ChartAdapter {
  mounted = false
  mountOptions: ChartMountOptions | null = null
  bars: DomainBar[] = []
  updates: DomainBar[] = []
  fitCalls = 0
  destroyed = false

  mount = vi.fn((_container: HTMLElement, options: ChartMountOptions) => {
    this.mounted = true
    this.mountOptions = options
  })

  setBars = vi.fn((bars: DomainBar[]) => {
    this.bars = bars
  })

  updateBar = vi.fn((bar: DomainBar) => {
    this.updates.push(bar)
  })

  fitContent = vi.fn(() => {
    this.fitCalls += 1
  })

  recentShown: number | null = null

  showRecent = vi.fn((count: number) => {
    this.recentShown = count
  })

  resize = vi.fn()

  destroy = vi.fn(() => {
    this.destroyed = true
    this.mounted = false
  })

  /** A simple linear projection, enough for the overlay to be exercised without a canvas. */
  getProjection = vi.fn((): Projection | null => ({
    timeToX: (time: number) => time - 1000,
    priceToY: (ticks: number) => 500 - (ticks - 80_000),
    xToTime: (x: number) => x + 1000,
    yToPrice: (y: number) => 80_000 + (500 - y),
  }))

  getPaneSize = vi.fn(() => ({ width: 800, height: 400 }))

  /** The layers painted with the chart (T37), and how often each asked to be repainted. */
  layers: ChartLayer[] = []
  redraws = 0

  addLayer = vi.fn((layer: ChartLayer) => {
    this.layers.push(layer)
    return {
      redraw: () => {
        this.redraws += 1
      },
      remove: () => {
        this.layers = this.layers.filter((l) => l !== layer)
      },
    }
  })

  /** Paints every layer, bottom first, as the chart would, onto a context a test records. */
  paintLayers(ctx: CanvasRenderingContext2D) {
    const order = { bottom: 0, normal: 1, top: 2 }
    const pane = this.getPaneSize()
    for (const layer of [...this.layers].sort((a, b) => order[a.zOrder] - order[b.zOrder])) layer.paint(ctx, pane)
  }

  viewportListeners: Array<() => void> = []

  onViewportChange = vi.fn((listener: () => void) => {
    this.viewportListeners.push(listener)
    return () => {
      this.viewportListeners = this.viewportListeners.filter((l) => l !== listener)
    }
  })

  /** What a test says is on screen; what the chart was last told to show. */
  visibleRange: TimeRange | null = null
  shownRange: TimeRange | null = null

  getVisibleTimeRange = vi.fn(() => this.visibleRange)

  setVisibleTimeRange = vi.fn((range: TimeRange) => {
    this.shownRange = range
  })

  /** Simulates a pan or zoom, so redraw-on-viewport-change can be tested. */
  emitViewportChange() {
    this.viewportListeners.forEach((l) => l())
  }
}
