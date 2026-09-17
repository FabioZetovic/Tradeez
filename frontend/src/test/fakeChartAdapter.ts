import { vi } from 'vitest'
import type { ChartAdapter, ChartMountOptions, DomainBar } from '@/lib/chart/types'

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

  resize = vi.fn()

  destroy = vi.fn(() => {
    this.destroyed = true
    this.mounted = false
  })
}
