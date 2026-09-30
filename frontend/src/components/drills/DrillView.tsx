import { useCallback, useState } from 'react'
import { PriceChart } from '../PriceChart'
import { SimulatedDataBanner } from '../SimulatedDataBanner'
import { DrillOverlay } from './DrillOverlay'
import { ApiError } from '@/lib/api/client'
import {
  answerDrill,
  DRILL_TIMEFRAMES,
  startDrill,
  type DrillResponse,
} from '@/lib/api/drills'
import type { ChartAdapter } from '@/lib/chart/types'
import { formatSessionTime } from '@/lib/chart/timeAxis'
import { explainFvg, toggleMark } from '@/lib/drills/fvg'

interface DrillViewProps {
  adapterFactory?: () => ChartAdapter
}

/**
 * The Drills tab (DEC-023): short, single-concept exercises, done by the dozen.
 *
 * The first is FVG identification — mark the middle candle of every fair value gap. On simulated
 * data it is a developer fixture (DEC-034), and the screen says so before anything else: it shows
 * how the drill works, it is not practice at reading a real market, and nothing about it is scored.
 */
export function DrillView({ adapterFactory }: DrillViewProps) {
  const [timeframe, setTimeframe] = useState<string>('5m')
  const [drill, setDrill] = useState<DrillResponse | null>(null)
  const [marks, setMarks] = useState<number[]>([])
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const answered = drill?.result !== undefined
  const shownMarks = answered ? (drill?.marks ?? []) : marks

  const run = async (work: () => Promise<void>) => {
    setBusy(true)
    setError(null)
    try {
      await work()
    } catch (e) {
      const body = e instanceof ApiError ? (e.body as { message?: string } | null) : null
      setError(body?.message ?? 'Something went wrong. Try again.')
    } finally {
      setBusy(false)
    }
  }

  const start = () =>
    run(async () => {
      const next = await startDrill({ kind: 'FVG_IDENTIFICATION', instrument: 'DEMO-NQ', timeframe })
      setDrill(next)
      setMarks([])
    })

  const check = () =>
    run(async () => {
      if (!drill) return
      setDrill(await answerDrill(drill.id, marks))
    })

  // Stable, because the overlay subscribes to clicks with it.
  const toggle = useCallback((time: number) => setMarks((current) => toggleMark(current, time)), [])

  return (
    <section className="practice">
      <section className="panel" aria-label="Drills">
        <h2>Drills</h2>
        <p className="muted">
          Short exercises on one idea at a time. The first: find every fair value gap in a stretch of
          chart. Mark the middle candle of each one, then check.
        </p>
        <div className="button-row">
          <div className="timeframes" role="group" aria-label="Drill timeframe">
            {DRILL_TIMEFRAMES.map((tf) => (
              <button
                key={tf}
                type="button"
                aria-pressed={tf === timeframe}
                className={tf === timeframe ? 'tf tf--active' : 'tf'}
                onClick={() => setTimeframe(tf)}
              >
                {tf}
              </button>
            ))}
          </div>
          <button type="button" className="primary" disabled={busy} onClick={() => void start()}>
            {drill ? 'Next drill' : 'Start a drill'}
          </button>
        </div>
        {error && (
          <p role="alert" className="error">
            {error}
          </p>
        )}
      </section>

      {drill && (
        <>
          <SimulatedDataBanner dataClass={drill.dataClass} />
          {drill.fixture && (
            <p className="assumption" role="note" data-testid="fixture-notice">
              {drill.fixtureNotice}
            </p>
          )}

          <section className="panel" aria-label="Task">
            <h3>Fair value gaps · {drill.instrument} · {drill.timeframe}</h3>
            <p>{drill.instructions}</p>
            <details className="provenance">
              <summary>The rule ({drill.rule.version})</summary>
              <p className="small">{drill.rule.definition}</p>
              <p className="small muted">
                {drill.rule.methodologySource}. Reproducible under this rule — not proof that a gap
                matters, or that the method it comes from is right.
              </p>
            </details>
          </section>

          <div className="chart-area">
            <PriceChart
              bars={drill.bars}
              tickSize={drill.tickSize}
              exchangeTimezone={drill.exchangeTimezone}
              axisTimezone="exchange"
              adapterFactory={adapterFactory}
              overlay={(adapter) => (
                <DrillOverlay
                  adapter={adapter}
                  bars={drill.bars}
                  marks={shownMarks}
                  onToggle={toggle}
                  result={drill.result}
                />
              )}
            />
          </div>

          {!answered && (
            <section className="panel" aria-label="Answer">
              <div className="button-row">
                <span data-testid="mark-count">
                  {marks.length === 1 ? '1 candle marked' : `${marks.length} candles marked`}
                </span>
                <button type="button" disabled={busy || marks.length === 0} onClick={() => setMarks([])}>
                  Clear marks
                </button>
                <button type="button" className="primary" disabled={busy} onClick={() => void check()}>
                  Check answer
                </button>
              </div>
            </section>
          )}

          {drill.result && (
            <section className="panel" aria-label="Result">
              <h3 data-testid="drill-summary">
                Found {drill.result.found} of {drill.result.found + drill.result.missed}
                {drill.result.extra > 0 &&
                  ` · ${drill.result.extra} extra ${drill.result.extra === 1 ? 'mark' : 'marks'}`}
              </h3>
              <p className="small muted">Feedback on this chart, not a grade.</p>
              <ol className="drill-gaps">
                {drill.result.setups.map((setup) => (
                  <li key={setup.id} data-testid="drill-gap">
                    <strong>
                      {setup.direction === 'BULLISH' ? 'Bullish' : 'Bearish'} gap ·{' '}
                      {formatSessionTime(setup.anchorTime, drill.exchangeTimezone)}
                    </strong>{' '}
                    <span className={setup.found ? 'chip chip--complete' : 'chip chip--in_trade'}>
                      {setup.found ? 'found' : 'missed'}
                    </span>
                    {explainFvg(
                      setup,
                      drill.tickSize,
                      drill.rule.parameters.minGapPercentOfAverageRange,
                    ).map((line) => (
                      <p key={line} className="small">
                        {line}
                      </p>
                    ))}
                  </li>
                ))}
              </ol>
              {drill.result.extraMarks.length > 0 && (
                <p className="small" data-testid="drill-extra">
                  Marked, but not the middle candle of a gap under this rule:{' '}
                  {drill.result.extraMarks
                    .map((t) => formatSessionTime(t, drill.exchangeTimezone))
                    .join(', ')}
                  .
                </p>
              )}
            </section>
          )}
        </>
      )}
    </section>
  )
}
