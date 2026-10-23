import '@testing-library/jest-dom/vitest'
import { MotionGlobalConfig } from 'motion'

// Animations finish at once: a test asserts where the page ends up, not how it got there (T36).
MotionGlobalConfig.skipAnimations = true

/**
 * jsdom implements neither ResizeObserver nor the canvas 2D context.
 *
 * Both are stubbed rather than worked around in the components, because a component that avoided
 * them for the sake of its tests would be a worse component. The drawing overlay genuinely needs to
 * track its container's size — without that, its coordinate space drifts from the chart's and every
 * hit test is subtly wrong.
 *
 * Painting is therefore never verified here; it is verified in a browser. These tests are about
 * interaction: which gesture starts, what lands in state, and whether pointer events reach the
 * chart underneath.
 */
class ResizeObserverStub implements ResizeObserver {
  observe() {}
  unobserve() {}
  disconnect() {}
}

globalThis.ResizeObserver ??= ResizeObserverStub

if (typeof HTMLCanvasElement !== 'undefined') {
  // Returning null is what jsdom does anyway, and the overlay already has to handle it — a real
  // browser returns null too when the context is unavailable.
  //
  // Unconditional (BUG-030). This used to be guarded by `!prototype.getContext`, but jsdom *does*
  // define the method — as one that logs "Not implemented" before returning null — so the guard was
  // always false, the stub never installed, and every overlay test printed that line.
  HTMLCanvasElement.prototype.getContext = (() => null) as typeof HTMLCanvasElement.prototype.getContext
}

// jsdom defines scrollTo as a method that only logs "Not implemented" (as with getContext, BUG-030): a quiet one.
window.scrollTo = (() => {}) as typeof window.scrollTo
