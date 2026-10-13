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
import { Button, Chip, Notice, Panel, Segmented } from '../ui'
import { errorClass, panelTitle } from '@/lib/styles'

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
    <section className="flex flex-col gap-4">
      <Panel aria-label="Drills">
        <h2 className="mb-2 text-lg font-semibold">Drills</h2>
        <p className="max-w-3xl text-sm text-subtle">
          Short exercises on one idea at a time. The first: find every fair value gap in a stretch of
          chart. Mark the middle candle of each one, then check.
        </p>
        <div className="mt-4 flex flex-wrap items-center gap-3">
          <Segmented<string>
            options={DRILL_TIMEFRAMES}
            value={timeframe}
            onChange={setTimeframe}
            label="Drill timeframe"
          />
          <Button variant="primary" disabled={busy} onClick={() => void start()}>
            {drill ? 'Next drill' : 'Start a drill'}
          </Button>
        </div>
        {error && (
          <p role="alert" className={`mt-3 ${errorClass}`}>
            {error}
          </p>
        )}
      </Panel>

      {drill && (
        <>
          <SimulatedDataBanner dataClass={drill.dataClass} />
          {drill.fixture && (
            <Notice role="note" data-testid="fixture-notice">
              {drill.fixtureNotice}
            </Notice>
          )}

          <Panel aria-label="Task">
            <h3 className={panelTitle}>
              Fair value gaps · {drill.instrument} · {drill.timeframe}
            </h3>
            <p className="text-sm">{drill.instructions}</p>
            <details className="mt-3 text-xs">
              <summary className="cursor-pointer text-subtle">The rule ({drill.rule.version})</summary>
              <p className="mt-2">{drill.rule.definition}</p>
              <p className="mt-2 text-subtle">
                {drill.rule.methodologySource}. Reproducible under this rule — not proof that a gap
                matters, or that the method it comes from is right.
              </p>
            </details>
          </Panel>

          <div className="flex min-w-0 items-start gap-2">
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
            <Panel aria-label="Answer">
              <div className="flex flex-wrap items-center gap-3">
                <span data-testid="mark-count" className="text-sm text-subtle">
                  {marks.length === 1 ? '1 candle marked' : `${marks.length} candles marked`}
                </span>
                <Button disabled={busy || marks.length === 0} onClick={() => setMarks([])}>
                  Clear marks
                </Button>
                <Button variant="primary" disabled={busy} onClick={() => void check()}>
                  Check answer
                </Button>
              </div>
            </Panel>
          )}

          {drill.result && (
            <Panel aria-label="Result">
              <h3 className={panelTitle} data-testid="drill-summary">
                Found {drill.result.found} of {drill.result.found + drill.result.missed}
                {drill.result.extra > 0 &&
                  ` · ${drill.result.extra} extra ${drill.result.extra === 1 ? 'mark' : 'marks'}`}
              </h3>
              <p className="mb-3 text-xs text-subtle">Feedback on this chart, not a grade.</p>
              <ol className="list-decimal space-y-3 pl-5 text-sm">
                {drill.result.setups.map((setup) => (
                  <li key={setup.id} data-testid="drill-gap">
                    <strong>
                      {setup.direction === 'BULLISH' ? 'Bullish' : 'Bearish'} gap ·{' '}
                      {formatSessionTime(setup.anchorTime, drill.exchangeTimezone)}
                    </strong>{' '}
                    <Chip tone={setup.found ? 'good' : 'caution'}>{setup.found ? 'found' : 'missed'}</Chip>
                    {explainFvg(
                      setup,
                      drill.tickSize,
                      drill.rule.parameters.minGapPercentOfAverageRange,
                    ).map((line) => (
                      <p key={line} className="mt-1 text-xs text-subtle">
                        {line}
                      </p>
                    ))}
                  </li>
                ))}
              </ol>
              {drill.result.extraMarks.length > 0 && (
                <p className="mt-3 text-xs" data-testid="drill-extra">
                  Marked, but not the middle candle of a gap under this rule:{' '}
                  {drill.result.extraMarks
                    .map((t) => formatSessionTime(t, drill.exchangeTimezone))
                    .join(', ')}
                  .
                </p>
              )}
            </Panel>
          )}
        </>
      )}
    </section>
  )
}
