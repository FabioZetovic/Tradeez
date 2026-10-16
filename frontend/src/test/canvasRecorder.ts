/**
 * A canvas 2D context that records what was painted, for the renderers' tests (jsdom has no canvas).
 *
 * Every method call is kept with the fill and stroke styles in force at the time; `measureText`
 * answers two pixels a character, so labels have a width to be placed by.
 */
export function recorder() {
  const calls: Array<{ op: string; args: unknown[]; fill?: unknown; stroke?: unknown }> = []
  const state: Record<string, unknown> = {}
  const record =
    (op: string) =>
    (...args: unknown[]) =>
      calls.push({ op, args, fill: state.fillStyle, stroke: state.strokeStyle })
  const ctx = new Proxy(state, {
    get: (target, key: string) => {
      if (key === 'measureText') return (text: string) => ({ width: text.length * 2 })
      if (key in target) return target[key]
      return record(key)
    },
    set: (target, key: string, value) => {
      target[key] = value
      return true
    },
  }) as unknown as CanvasRenderingContext2D
  return { ctx, calls }
}
