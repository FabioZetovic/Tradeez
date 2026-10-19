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

      {/* BUG-059: a gap is bad news for a stop and good news for a target — say which this was. */}
      {result.gapExit && result.outcome === 'STOP' && (
        <p className="mt-3 text-xs text-subtle" data-testid="gap-note">
          The market opened beyond your stop, so it filled at the opening price — worse than your stop.
          Real stops do this too.
        </p>
      )}
      {result.gapExit && result.outcome === 'TARGET' && (
        <p className="mt-3 text-xs text-subtle" data-testid="gap-note">
          The market opened beyond your target, so it filled at the opening price — better than your
          target. A resting limit fills at the open when the market opens past it.
        </p>
      )}

      {/* T22: how the trade followed the model named — checked when it was placed, apart from how it ended. */}
      {plan.checklist && (
        <details className="mt-4 text-xs" data-testid="result-checklist">
          <summary className="cursor-pointer text-subtle">
            {plan.checklist.title}: {plan.checklist.met} of {plan.checklist.total} checks met when placed
          </summary>
          <ul className="mt-2 flex flex-col gap-0.5">
            {plan.checklist.items.map((item) => (
              <li key={item.id} className={item.met ? 'text-up' : 'text-subtle'}>
                {item.met ? '✓' : '✗'} {item.text}
              </li>
            ))}
          </ul>
          <p className="mt-2 text-subtle">
            Following a model is not the same as being right, and breaking it is not the same as being wrong: this
            is adherence, scored apart from the result. {plan.checklist.version}.
          </p>
        </details>
      )}

      <details className="mt-4 text-xs">
        <summary className="cursor-pointer text-subtle">How this was decided</summary>
        <p className="mt-2">
          Resolved on {resolution} bars ({result.resolution}) by {result.engineVersion} with{' '}
          {result.fillModelVersion}: stops slip one tick, commission per contract per round trip,
          flat at the session close.
          {result.outcome === 'CLOSED' &&
            ' You closed it at market: it filled at the open of the next bar, without slippage, as the entry did.'}
        </p>
      </details>
    </Panel>
  )
}
