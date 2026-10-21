import { useState } from 'react'
import { getTradeReview, type PracticeState, type ReviewMoment, type TradeReviewView } from '@/lib/api/practice'
import type { SetupLayer } from '@/lib/api/setups'
import { formatSessionTime } from '@/lib/chart/timeAxis'
import { cx, errorClass, panelTitle } from '@/lib/styles'
import { Button, Panel } from '../ui'

const PHASES = [
  { phase: 'BEFORE', title: 'Before you placed it' },
  { phase: 'TRADE', title: 'While it ran' },
  { phase: 'AFTER', title: 'After it ended' },
] as const

/** The learner's own moments stand out from the rules' hits. */
const OWN = new Set<ReviewMoment['kind']>(['DECISION', 'ENTRY', 'PARTIAL', 'EXIT', 'HELD', 'DRAW'])

/**
 * A finished trade told as it happened (T24, DEC-068): what the rules had seen before it was placed, what they
 * saw while it ran — each hit explained in its rule's own numbers — how it ended, and what hindsight adds.
 * Read on request, and drawn on the chart on request: the review is the learner's to open.
 */
export function TradeReview({
  state,
  number,
  drawn,
  onDraw,
}: {
  state: PracticeState
  number: number
  /** Whether this review's hits are on the chart. */
  drawn: boolean
  onDraw: (layer: SetupLayer | null) => void
}) {
  const [review, setReview] = useState<TradeReviewView | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const load = async () => {
    setLoading(true)
    setError(null)
    try {
      setReview(await getTradeReview(state.id, number))
    } catch {
      setError('Could not read the review.')
    } finally {
      setLoading(false)
    }
  }

  return (
    <Panel aria-label="Review">
      <h3 className={`${panelTitle} flex flex-wrap items-center gap-3`}>
        Trade {number}, as it happened
        {review && review.layer.setups.length > 0 && (
          <Button
            variant="ghost"
            active={drawn}
            aria-pressed={drawn}
            onClick={() => onDraw(drawn ? null : review.layer)}
          >
            {drawn ? 'Hide its rules on the chart' : 'Show its rules on the chart'}
          </Button>
        )}
      </h3>

      {!review && (
        <div className="flex flex-wrap items-center gap-3">
          <p className="text-sm text-subtle">
            What the rules saw before you placed it and while it ran, how it ended, and what hindsight adds.
          </p>
          <Button disabled={loading} onClick={() => void load()}>
            {loading ? 'Reading…' : 'Read the review'}
          </Button>
        </div>
      )}
      {error && (
        <p role="alert" className={`mt-2 ${errorClass}`}>
          {error}
        </p>
      )}

      {review && (
        <div className="flex flex-col gap-4">
          {PHASES.map(({ phase, title }) => {
            const moments = review.moments.filter((m) => m.phase === phase)
            if (moments.length === 0) return null
            return (
              <section key={phase} aria-label={title}>
                <h4 className="mb-2 text-xs font-semibold tracking-wide text-subtle uppercase">{title}</h4>
                <ol className="flex flex-col gap-2">
                  {moments.map((m, i) => (
                    <li
                      key={`${m.time}-${i}`}
                      data-testid={`review-${m.kind.toLowerCase()}`}
                      className="grid grid-cols-[7rem_1fr] gap-x-3 text-sm"
                    >
                      <span className="text-subtle tabular-nums">
                        {formatSessionTime(m.time, state.exchangeTimezone, state.timeBasis)}
                      </span>
                      <div>
                        <p className={cx(OWN.has(m.kind) && 'font-medium text-ink')}>{m.title}</p>
                        {m.lines.map((line) => (
                          <p key={line} className="text-xs text-subtle">
                            {line}
                          </p>
                        ))}
                      </div>
                    </li>
                  ))}
                </ol>
              </section>
            )
          })}
          {review.summary.map((line) => (
            <p key={line} className="text-sm" data-testid="review-summary">
              {line}
            </p>
          ))}
          <p className="text-xs text-subtle">
            Each hit is listed when its rule could first know it, and nothing comes from past where the replay is
            now. The rules read: {review.rules.join(', ')}. {review.version}.
          </p>
        </div>
      )}
    </Panel>
  )
}
