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
  /** Daily candles: labelled by trading day rather than by their 18:00 open (T35). */
  daily?: boolean
  /**
   * Its time is another chart's (BUG-092): no scrolling or zooming of its own, so the two can never show different
   * minutes. Its price axis still scales.
   */
  followsOnly?: boolean
  /** A picture of the market, not a tool (R-12, Home): it neither scrolls nor zooms, in time or price. */
  still?: boolean
  /**
   * Empty bars after the last candle; 12 unless set. A short chart with no position tool — a drill, a lesson's example —
   * asks for a few, or a third of it is empty grid (BUG-104).
   */
  rightOffset?: number
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
   * The plotting area, without the price and time scales, from the container's top-left corner.
   * Shading a time window stops at its edges rather than tinting the axes. Null if not mounted.
   */
  getPaneSize(): { width: number; height: number } | null

  /**
   * Paints a layer inside the chart's own paint, in the same frame as the candles (T37, BUG-095): a layer on a
   * canvas of its own, redrawn once the chart had moved, trailed it by a frame or more. Kept across a remount.
   */
  addLayer(layer: ChartLayer): LayerHandle

  /**
   * Fires whenever the visible range changes — pan, zoom, or a resize. For things that are not painted: another
   * chart following this one, an editor placed over a drawing.
   *
   * @returns an unsubscribe function
   */
  onViewportChange(listener: () => void): () => void

  /**
   * The stretch of time on screen, UTC epoch seconds, or null before there are candles (DEC-075): a chart of
   * another market keeps to it.
   */
  getVisibleTimeRange(): TimeRange | null

  /** Shows this stretch of time; ignored before there are candles. */
  setVisibleTimeRange(range: TimeRange): void
}

/** Something painted on the chart, over its plotting area (T37). */
export interface ChartLayer {
  /** 'bottom' under the candles, 'normal' over them, 'top' over everything else. */
  zOrder: 'bottom' | 'normal' | 'top'
  /** Paints in CSS pixels, from the plotting area's top-left corner; never clears — the candles are on it too. */
  paint(ctx: CanvasRenderingContext2D, pane: { width: number; height: number }): void
}

export interface LayerHandle {
  /** Asks for a repaint: the layer's data changed, not the chart. */
  redraw(): void
  remove(): void
}

/** Another market's candles over the same minutes as the chart they are shown under (DEC-075). */
export interface Companion {
  instrument: string
  tickSize: number
  bars: DomainBar[]
}

/** From and to, UTC epoch seconds. */
export interface TimeRange {
  from: number
  to: number
}
