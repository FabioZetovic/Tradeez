/**
 * The chart abstraction (DEC-004).
 *
 * Lightweight Charts is the renderer today. It is wrapped rather than used directly so that
 * swapping it — for TradingView Advanced Charts, or KLineChart if the custom drawing layer proves
 * too expensive — is an adapter rather than a rewrite of every feature that touches a chart.
 *
 * There is a second, immediate payoff: a fake adapter makes the React components testable without
 * a canvas. jsdom has no real canvas, so a component that called `createChart` directly could not
 * be tested at all.
 */

/**
 * A bar as the domain holds it: **prices are integer tick counts, not decimals** (INVARIANT 3).
 *
 * This is the shape the API returns. Conversion to a displayable price happens inside the adapter
 * and nowhere else, which is what "convert only at the presentation edge" means in practice.
 */
export interface DomainBar {
  /** Interval start, UTC epoch seconds. */
  time: number
  open: number
  high: number
  low: number
  close: number
  volume: number
}

export interface ChartMountOptions {
  /** Smallest price increment, e.g. 0.25. Drives both conversion and the price scale format. */
  tickSize: number
  /** Shown in the chart's corner so simulated data is never mistaken for market history. */
  watermark?: string
}

/**
 * What the application needs a chart to do. Deliberately small — every method here is something a
 * feature actually requires, so an alternative renderer has a short list to satisfy.
 */
export interface ChartAdapter {
  mount(container: HTMLElement, options: ChartMountOptions): void

  /** Replaces the whole series. */
  setBars(bars: DomainBar[]): void

  /**
   * Adds or updates the most recent bar.
   *
   * This exists for replay (DEC-009): the server releases one bar at a time and the chart must
   * extend rather than redraw, or every tick of a replay would reset the user's zoom and pan.
   */
  updateBar(bar: DomainBar): void

  fitContent(): void
  resize(width: number, height: number): void
  destroy(): void
}
