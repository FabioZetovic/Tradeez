import '@testing-library/jest-dom/vitest'

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

if (typeof HTMLCanvasElement !== 'undefined' && !HTMLCanvasElement.prototype.getContext) {
  // Returning null is what jsdom would do for an unsupported context type, and the overlay already
  // has to handle that — a real browser returns null too when the context is unavailable.
  HTMLCanvasElement.prototype.getContext = () => null
}
