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
  const basis = state.timeBasis
  const resolution = result.resolution === 'M1' ? 'one-minute' : 'one-second'
  const tone = result.netPnlCents > 0 ? 'text-up' : result.netPnlCents < 0 ? 'text-down' : 'text-ink'

  return (
    <Panel aria-label="Result">
      <h3 className={panelTitle} data-testid="result-outcome">
        {outcomeLabel(result.outcome)}
      </h3>

      {/* DEC-025: the date, hidden until now. */}
      {state.revealedStartTime !== undefined && (
        <p className="mb-3 text-sm" data-testid="revealed-date">
          This was <strong>{revealedDate(state.revealedStartTime)}</strong>, New York time.
        </p>
      )}

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
          flat at the session close.{' '}
          {state.seed !== undefined ? (
            <>
              Market {state.generatorVersion}, seed{' '}
              <code
                data-testid="result-seed"
                className="rounded border border-line bg-canvas px-1.5 py-0.5 font-mono"
              >
                {state.seed}
              </code>
              .
            </>
          ) : (
            <>Real {state.instrument} history from {state.dataSource ?? 'an import'}.</>
          )}
        </p>
        <p className="mt-2 text-subtle">
          {state.dataClass === 'SIMULATED'
            ? 'Simulated data. This is practice at reading a chart and committing to a plan, not evidence about any real market.'
            : 'Real market history: this is what the market did after that moment. One trade says nothing about whether a method works.'}
        </p>
      </details>
    </Panel>
  )
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
