/**
 * How things arrive on screen (REDESIGN §4, R-5), one timing for the whole app.
 *
 * Fabio, 2026-10-28: "animations load too early, you cant even see them". Measured: every entrance was over within
 * 0.2–0.25 s of the first paint, moving 8 px — while the browser was still busy finishing the page and the eye was still
 * landing on it. So an entrance now waits a beat, lasts half a second and travels far enough to be seen.
 */
export const EASE_OUT = [0.22, 1, 0.36, 1] as const

/** The beat before anything moves: past the first paint and the work that follows it. */
export const SETTLE = 0.15

/**
 * An element rising into place, `order` places after the first of its group. A long list caps its stagger, so its
 * last item never waits more than `max` steps.
 */
export function enter(order = 0, { rise = 14, step = 0.06, max = 10 }: { rise?: number; step?: number; max?: number } = {}) {
  return {
    initial: { opacity: 0, y: rise },
    animate: { opacity: 1, y: 0 },
    transition: { duration: 0.5, ease: EASE_OUT, delay: SETTLE + Math.min(order, max) * step },
  }
}
