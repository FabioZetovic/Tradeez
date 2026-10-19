import { useEffect, useState } from 'react'
import { getPlanContext, type PlanContext, type PlanRequest, type TradingModel } from '@/lib/api/practice'

/** Long enough that dragging a stop asks once, at the end of the drag, not at every pixel. */
const SETTLE_MS = 250

/**
 * What the app sees for the bracket being drafted (T22, DEC-066): the killzone, the daily-bias suggestion
 * and the chosen model's checklist, asked for again when the draft or the model changes and has settled.
 * A failure leaves the panel without it; the trade can still be placed, and is checked then.
 */
export function usePlanContext(
  sessionId: string,
  draft: PlanRequest | null,
  model: TradingModel | undefined,
  cursor: number | undefined,
): PlanContext | null {
  const [answer, setAnswer] = useState<{ key: string; context: PlanContext | null } | null>(null)
  const key = draft ? `${draft.direction}|${draft.stopTicks}|${draft.targetTicks}|${model ?? ''}|${cursor ?? ''}` : null

  useEffect(() => {
    if (key === null) return
    // Everything the request needs is in the key, so it is the only thing the effect depends on: the draft
    // itself is a new object on every render, and a replay at speed renders many times a second.
    const [direction, stopTicks, targetTicks, chosen] = key.split('|')
    let cancelled = false
    const timer = setTimeout(() => {
      getPlanContext(sessionId, {
        direction: direction as PlanRequest['direction'],
        stopTicks: Number(stopTicks),
        targetTicks: Number(targetTicks),
        model: (chosen || undefined) as TradingModel | undefined,
      })
        .then((context) => {
          if (!cancelled) setAnswer({ key, context })
        })
        .catch(() => {
          if (!cancelled) setAnswer({ key, context: null })
        })
    }, SETTLE_MS)
    return () => {
      cancelled = true
      clearTimeout(timer)
    }
  }, [sessionId, key])

  // Only the answer to this draft: a stale one would tick a checklist for a bracket no longer drawn.
  return answer && answer.key === key ? answer.context : null
}
