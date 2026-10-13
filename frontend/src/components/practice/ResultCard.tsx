import type { PracticeState, ResultView } from '@/lib/api/practice'
import { formatPrice } from '@/lib/chart/priceFormat'
import { formatSessionTime } from '@/lib/chart/timeAxis'
import { formatCents, formatR, outcomeLabel } from '@/lib/practice/plan'
import { Notice, Panel } from '../ui'
import { keyValueClass, panelTitle } from '@/lib/styles'

/**
 * What happened, and everything needed to check it.
 *
 * Two things on this card are not decoration. The assumption line: an AMBIGUOUS result is the
 * engine's worst-case guess, and it is shown as a guess (INVARIANT 6). And the provenance at the
 * bottom: the seed and versions that reproduce this exact result (DEC-012) — revealed only now,
 * because until the trade was over the seed was the answer (DEC-038).
 */
export function ResultCard({ state, result }: { state: PracticeState; result: ResultView }) {
  const plan = state.plan
  const risk = plan ? Math.abs(plan.referenceTicks - plan.stopTicks) : 0
  const tz = state.exchangeTimezone
  const tone = result.netPnlCents > 0 ? 'text-up' : result.netPnlCents < 0 ? 'text-down' : 'text-ink'

  return (
    <Panel aria-label="Result">
      <h3 className={panelTitle} data-testid="result-outcome">
        {outcomeLabel(result.outcome)}
      </h3>

      {/* INVARIANT 6: an assumed outcome is labelled as one, where it cannot be missed. */}
      {result.assumption && (
        <Notice role="note" className="mb-3">
          {result.assumption}
        </Notice>
      )}

      <p className={`mb-3 text-2xl font-semibold tabular-nums ${tone}`}>
        <span data-testid="result-net">{formatCents(result.netPnlCents)}</span>
        <span className="text-base font-normal text-subtle"> net · </span>
        <span data-testid="result-r">{formatR(result.rMultiple)}</span>
      </p>

      <dl className={keyValueClass}>
        <dt>Entry</dt>
        <dd>
          {formatPrice(result.entryTicks, state.tickSize)} · {formatSessionTime(result.entryTime, tz)}
        </dd>
        <dt>Exit</dt>
        <dd>
          {formatPrice(result.exitTicks, state.tickSize)} · {formatSessionTime(result.exitTime, tz)}
        </dd>
        <dt>Per contract</dt>
        <dd>{result.pnlTicks} ticks</dd>
        <dt>Commission</dt>
        <dd>{formatCents(result.commissionCents)}</dd>
        <dt title="Maximum adverse excursion: the furthest the trade went against you">
          Worst point
        </dt>
        <dd>
          {result.maeTicks} ticks{risk > 0 && ` (${(result.maeTicks / risk).toFixed(2)}R)`}
        </dd>
        <dt title="Maximum favourable excursion: the furthest the trade went in your favour">
          Best point
        </dt>
        <dd>
          {result.mfeTicks} ticks{risk > 0 && ` (${(result.mfeTicks / risk).toFixed(2)}R)`}
        </dd>
      </dl>

      {result.gapExit && (
        <p className="mt-3 text-xs text-subtle">
          The market opened beyond your order, so it filled at the opening price rather than at your
          level. Real stops do this too.
        </p>
      )}

      <details className="mt-4 text-xs">
        <summary className="cursor-pointer text-subtle">How this was decided</summary>
        <p className="mt-2">
          Resolved on one-second bars ({result.resolution}) by {result.engineVersion} with{' '}
          {result.fillModelVersion}: stops slip one tick, commission per contract per round trip,
          flat at the session close. Market {state.generatorVersion}, seed{' '}
          <code data-testid="result-seed" className="rounded border border-line bg-canvas px-1.5 py-0.5 font-mono">
            {state.seed}
          </code>
          .
        </p>
        <p className="mt-2 text-subtle">
          Simulated data. This is practice at reading a chart and committing to a plan, not evidence
          about any real market.
        </p>
      </details>
    </Panel>
  )
}
