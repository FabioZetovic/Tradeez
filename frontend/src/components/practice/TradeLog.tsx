import type { PracticeState } from '@/lib/api/practice'
import { formatPrice } from '@/lib/chart/priceFormat'
import { formatSessionTime } from '@/lib/chart/timeAxis'
import { formatCents, formatR, outcomeLabel } from '@/lib/practice/plan'
import { cx } from '@/lib/styles'
import { Panel } from '../ui'

const tableClass =
  'w-full border-collapse text-sm tabular-nums [&_td]:border-b [&_td]:border-line/60 [&_td]:px-2 [&_td]:py-1.5 [&_th]:border-b [&_th]:border-line [&_th]:px-2 [&_th]:py-1.5 [&_th]:text-left [&_th]:font-medium [&_th]:text-subtle'

/**
 * The replay's record: every trade, the score so far, and — once the session has ended — what was
 * hidden while trading could still use it.
 *
 * The score counts closed trades only. A running trade's outcome already exists on the server, but
 * it is the future until the replay reaches it (DEC-009), so it shows as "running" and nothing else.
 */
export function TradeLog({
  state,
  selected,
  onSelect,
}: {
  state: PracticeState
  /** The trade whose details are open, by number. */
  selected?: number
  onSelect: (number: number) => void
}) {
  const { score, trades } = state
  const ended = state.status === 'COMPLETE'
  const tz = state.exchangeTimezone

  return (
    <Panel aria-label="Trades">
      <div className="mb-3 flex flex-wrap items-baseline gap-x-6 gap-y-1">
        <h3 className="text-base font-semibold text-ink">{ended ? 'Session over' : 'Trades'}</h3>
        <Stat label="Closed" value={String(score.closedTrades)} testId="score-trades" />
        <Stat
          label="Win rate"
          value={score.closedTrades > 0 ? `${Math.round((100 * score.wins) / score.closedTrades)}%` : '—'}
          testId="score-winrate"
        />
        <Stat label="Total" value={formatR(score.totalR)} tone={score.totalR} testId="score-r" />
        <Stat
          label="Net"
          value={formatCents(score.netPnlCents)}
          tone={score.netPnlCents}
          testId="score-net"
        />
      </div>

      {/* DEC-025: the date, hidden while another trade could still have used it. */}
      {state.revealedStartTime !== undefined && (
        <p className="mb-3 text-sm" data-testid="revealed-date">
          This replay started on <strong>{revealedDate(state.revealedStartTime)}</strong>, New York
          time.
        </p>
      )}

      {trades.length === 0 ? (
        <p className="text-sm text-subtle">
          {ended ? 'No trades were taken.' : 'No trades yet. Place one from the plan panel.'}
        </p>
      ) : (
        <table className={tableClass}>
          <thead>
            <tr>
              <th>#</th>
              <th>Side</th>
              <th>Entry</th>
              <th>Exit</th>
              <th>Outcome</th>
              <th>R</th>
              <th>Net</th>
              <th>Model</th>
            </tr>
          </thead>
          <tbody>
            {trades.map((trade) => {
              const { result, entry, plan } = trade
              return (
                <tr
                  key={trade.number}
                  data-testid="trade-row"
                  className={cx(
                    result && 'cursor-pointer hover:bg-raised',
                    trade.number === selected && 'bg-raised',
                  )}
                  // A click anywhere on the row is a convenience; the button is the control (BUG-042).
                  onClick={() => result && onSelect(trade.number)}
                >
                  <td>
                    {result ? (
                      <button
                        type="button"
                        aria-pressed={trade.number === selected}
                        aria-label={`Show trade ${trade.number}`}
                        className="cursor-pointer font-medium text-brand hover:underline"
                        onClick={(e) => {
                          e.stopPropagation()
                          onSelect(trade.number)
                        }}
                      >
                        {trade.number}
                      </button>
                    ) : (
                      trade.number
                    )}
                  </td>
                  <td>
                    {plan.direction === 'LONG' ? 'Long' : 'Short'} {plan.contracts}
                  </td>
                  <td>
                    {entry
                      ? `${formatPrice(entry.ticks, state.tickSize)} · ${formatSessionTime(entry.time, tz, state.timeBasis)}`
                      : '—'}
                  </td>
                  <td>
                    {result
                      ? `${formatPrice(result.exitTicks, state.tickSize)} · ${formatSessionTime(result.exitTime, tz, state.timeBasis)}`
                      : '—'}
                  </td>
                  {/* An ambiguous outcome is named as one, and its assumption is a hover away. */}
                  <td title={result?.assumption}>{result ? outcomeLabel(result.outcome) : 'Running'}</td>
                  <td className={toneClass(result?.rMultiple)}>{result ? formatR(result.rMultiple) : ''}</td>
                  <td className={toneClass(result?.netPnlCents)}>
                    {result ? formatCents(result.netPnlCents) : ''}
                  </td>
                  {/* Adherence to the model named, as checked when the trade was placed — apart from its outcome (T22). */}
                  <td title={plan.checklist?.title} data-testid="trade-model">
                    {plan.checklist ? `${plan.checklist.met}/${plan.checklist.total}` : '—'}
                  </td>
                </tr>
              )
            })}
          </tbody>
        </table>
      )}

      {ended && (
        <p className="mt-3 text-xs text-subtle" data-testid="provenance">
          {state.seed !== undefined ? (
            <>
              Simulated market {state.generatorVersion}, seed{' '}
              <code
                data-testid="result-seed"
                className="rounded border border-line bg-canvas px-1.5 py-0.5 font-mono text-ink"
              >
                {state.seed}
              </code>
              : practice at reading a chart and committing to a plan, not evidence about any real
              market.
            </>
          ) : (
            <>
              Real {state.instrument} history from {state.dataSource ?? 'an import'}: this is what the
              market did. A handful of trades says nothing about whether a method works.
            </>
          )}
        </p>
      )}
    </Panel>
  )
}

function Stat({
  label,
  value,
  tone,
  testId,
}: {
  label: string
  value: string
  tone?: number
  testId: string
}) {
  return (
    <span className="text-sm">
      <span className="text-subtle">{label} </span>
      <strong className={cx('tabular-nums', toneClass(tone))} data-testid={testId}>
        {value}
      </strong>
    </span>
  )
}

function toneClass(value: number | undefined): string {
  return value === undefined || value === 0 ? '' : value > 0 ? 'text-up' : 'text-down'
}

/**
 * "Monday 7 April 2025, 10:05" — the real moment, in New York time. Assembled from parts: how Intl
 * joins them (a comma, "at") differs between ICU versions.
 */
function revealedDate(epochSeconds: number): string {
  const parts = new Intl.DateTimeFormat('en-GB', {
    timeZone: 'America/New_York',
    weekday: 'long',
    day: 'numeric',
    month: 'long',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    hourCycle: 'h23',
  }).formatToParts(new Date(epochSeconds * 1000))
  const part = (type: Intl.DateTimeFormatPartTypes) => parts.find((p) => p.type === type)?.value
  return `${part('weekday')} ${part('day')} ${part('month')} ${part('year')}, ${part('hour')}:${part('minute')}`
}
