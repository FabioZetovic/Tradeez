import type { PracticeState, ResultView, TradeView } from '@/lib/api/practice'
import { formatPrice } from '@/lib/chart/priceFormat'
import { formatSessionTime } from '@/lib/chart/timeAxis'
import { formatCents, formatR, outcomeLabel } from '@/lib/practice/plan'
import { Notice, Panel } from '../ui'
import { keyValueClass, panelTitle } from '@/lib/styles'

/**
 * One finished trade, and everything needed to check it.
 *
 * The assumption line is not decoration: an AMBIGUOUS result is the engine's worst-case guess, and
 * it is shown as a guess (INVARIANT 6). What reproduces the result — the seed of a simulated market,
 * the date of a real one — belongs to the whole session and is shown when it ends ({@link TradeLog}).
 */
export function ResultCard({
  state,
  trade,
  result,
}: {
  state: PracticeState
  trade: TradeView
  result: ResultView
}) {
  const plan = trade.plan
  const risk = Math.abs(plan.referenceTicks - plan.stopTicks)
  const tz = state.exchangeTimezone
  const basis = state.timeBasis
  const resolution = result.resolution === 'M1' ? 'one-minute' : 'one-second'
  const tone = result.netPnlCents > 0 ? 'text-up' : result.netPnlCents < 0 ? 'text-down' : 'text-ink'

  return (
    <Panel aria-label="Result">
      <h3 className={panelTitle} data-testid="result-outcome">
        Trade {trade.number} · {outcomeLabel(result.outcome)}
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
        <dt>{plan.direction === 'LONG' ? 'Long' : 'Short'}</dt>
        <dd>
          {plan.contracts} {plan.contracts === 1 ? 'contract' : 'contracts'}
        </dd>
        <dt>Entry</dt>
        <dd>
          {formatPrice(result.entryTicks, state.tickSize)} · {formatSessionTime(result.entryTime, tz, basis)}
        </dd>
        <dt>Exit</dt>
        <dd>
          {formatPrice(result.exitTicks, state.tickSize)} · {formatSessionTime(result.exitTime, tz, basis)}
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
          Resolved on {resolution} bars ({result.resolution}) by {result.engineVersion} with{' '}
          {result.fillModelVersion}: stops slip one tick, commission per contract per round trip,
          flat at the session close.
        </p>
      </details>
    </Panel>
  )
}
