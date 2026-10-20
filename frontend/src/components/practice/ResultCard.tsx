import type { PracticeState, ResultView, ScoreLine, TradeView } from '@/lib/api/practice'
import { formatPrice } from '@/lib/chart/priceFormat'
import { formatSessionTime } from '@/lib/chart/timeAxis'
import { formatCents, formatR, outcomeLabel } from '@/lib/practice/plan'
import { Notice, Panel } from '../ui'
import { cx, keyValueClass, panelTitle } from '@/lib/styles'

const sideClass = 'flex flex-col gap-3 rounded-md border border-line/70 p-4'
const sideTitle = 'text-xs font-semibold tracking-wide text-subtle uppercase'

/**
 * One finished trade, scored (T23, DEC-067): what it did and how it was made, side by side and at equal
 * weight, each line a statement with its reasons — nothing added up, because a good process can lose and a
 * bad one can win (DEC-027).
 *
 * The assumption line is not decoration: an AMBIGUOUS result is the engine's worst-case guess, and it is
 * shown as a guess (INVARIANT 6). What reproduces the result — the seed of a simulated market, the date of a
 * real one — belongs to the whole session and is shown when it ends ({@link TradeLog}).
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
  const { plan, scorecard } = trade
  const tz = state.exchangeTimezone
  const basis = state.timeBasis
  const resolution = result.resolution === 'M1' ? 'one-minute' : 'one-second'
  const tone = result.netPnlCents > 0 ? 'text-up' : result.netPnlCents < 0 ? 'text-down' : 'text-ink'
  // The learner's own rules: the only lines that carry a verdict.
  const rules = scorecard?.process.filter((l) => l.verdict) ?? []
  const broken = rules.filter((l) => l.verdict === 'BROKEN').length

  return (
    <Panel aria-label="Result" className="@container">
      <h3 className={panelTitle} data-testid="result-outcome">
        Trade {trade.number} · {outcomeLabel(result.outcome)}
      </h3>

      {/* INVARIANT 6: an assumed outcome is labelled as one, where it cannot be missed. */}
      {result.assumption && (
        <Notice role="note" className="mb-3">
          {result.assumption}
        </Notice>
      )}

      <div className="grid gap-4 @xl:grid-cols-2">
        <section aria-label="Outcome" className={sideClass}>
          <h4 className={sideTitle}>Outcome · what it did</h4>
          <p className={`text-2xl font-semibold tabular-nums ${tone}`}>
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
          </dl>
          {scorecard && <Lines lines={scorecard.outcome} />}

          {/* BUG-059: a gap is bad news for a stop and good news for a target — say which this was. */}
          {result.gapExit && result.outcome === 'STOP' && (
            <p className="text-xs text-subtle" data-testid="gap-note">
              The market opened beyond your stop, so it filled at the opening price — worse than your stop. Real
              stops do this too.
            </p>
          )}
          {result.gapExit && result.outcome === 'TARGET' && (
            <p className="text-xs text-subtle" data-testid="gap-note">
              The market opened beyond your target, so it filled at the opening price — better than your target. A
              resting limit fills at the open when the market opens past it.
            </p>
          )}
        </section>

        <section aria-label="Process" className={sideClass}>
          <h4 className={sideTitle}>Process · how it was made</h4>
          {/* As prominent as the money: the learner's own rules, counted — never a weighted score. */}
          <p
            className={cx('text-2xl font-semibold', rules.length === 0 ? 'text-subtle' : broken > 0 ? 'text-down' : 'text-up')}
            data-testid="result-rules"
          >
            {rules.length === 0
              ? 'Your rules: none to check'
              : broken > 0
                ? `Your rules: ${broken} of ${rules.length} broken`
                : `Your rules: ${rules.length} of ${rules.length} kept`}
          </p>
          {scorecard && <Lines lines={scorecard.process} />}

          {/* T22: how the trade followed the model named — checked when it was placed. */}
          {plan.checklist && (
            <details className="text-xs" data-testid="result-checklist">
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
                Following a model is not the same as being right, and breaking it is not the same as being wrong:
                this is adherence, scored apart from the result. {plan.checklist.version}.
              </p>
            </details>
          )}
        </section>
      </div>

      {scorecard && (
        <p className="mt-3 text-xs text-subtle">
          Each side stands alone: a well-made trade can lose and a careless one can win, so nothing here is added up
          into one score. A tick marks only your own rules — your risk limit and your bias; the rest is stated, not
          judged, because what is right depends on the method. {scorecard.version}.
        </p>
      )}

      <details className="mt-3 text-xs">
        <summary className="cursor-pointer text-subtle">How this was decided</summary>
        <p className="mt-2">
          Resolved on {resolution} bars ({result.resolution}) by {result.engineVersion} with the costs it was placed under,{' '}
          {result.fillModelVersion} — the slippage and fees in the Costs line — and flat at the session close.
          {result.outcome === 'CLOSED' &&
            ' You closed it at market: it filled at the open of the next bar, slipping as the entry did.'}
        </p>
      </details>
    </Panel>
  )
}

/** A side's lines: the label, then the statement — ticked or crossed only when it is the learner's own rule. */
function Lines({ lines }: { lines: ScoreLine[] }) {
  return (
    <ul className="flex flex-col gap-1.5 text-sm">
      {lines.map((line) => (
        <li key={line.id} data-testid={`score-${line.id}`} className="grid grid-cols-[8rem_1fr] gap-x-3">
          <span className="text-subtle">{line.label}</span>
          <span className={cx(line.verdict === 'KEPT' && 'text-up', line.verdict === 'BROKEN' && 'text-down')}>
            {line.verdict && <span aria-hidden="true">{line.verdict === 'KEPT' ? '✓ ' : '✗ '}</span>}
            {line.text}
            {line.verdict && <span className="sr-only">{line.verdict === 'KEPT' ? ' Kept.' : ' Broken.'}</span>}
          </span>
        </li>
      ))}
    </ul>
  )
}
