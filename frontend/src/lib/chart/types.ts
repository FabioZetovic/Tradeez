import type { AxisTimezone } from './timeAxis'

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
  /** IANA zone the axis labels in, e.g. "America/Chicago" (BUG-014). */
  exchangeTimezone: string
  /** 'exchange' labels in the exchange's local time; 'utc' shows the stored value. */
  axisTimezone: AxisTimezone
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

  /**
   * Frames the last `count` bars, leaving room on the right.
   *
   * For practice: four days of history fitted into one screen is unreadable, and the learner's
   * decision is about the most recent price action. The history stays scrollable to the left.
   */
  showRecent(count: number): void

  resize(width: number, height: number): void
  destroy(): void

  /**
   * Converts between market space and screen space, for the drawing overlay.
   *
   * Exposed here rather than letting the overlay reach into the charting library, so the drawing
   * layer depends on four functions instead of on Lightweight Charts (DEC-004). Returns null if the
   * chart is not mounted.
   */
  getProjection(): import('../drawing/types').Projection | null

  /**
   * Fires whenever the visible range changes — pan, zoom, or a resize.
   *
   * The overlay is a separate canvas, so nothing redraws it automatically; without this the
   * drawings would stay put while the candles moved underneath them.
   *
   * @returns an unsubscribe function
   */
  onViewportChange(listener: () => void): () => void
}
