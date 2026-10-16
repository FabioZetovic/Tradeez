import { useCallback, useEffect, useMemo, useState } from 'react'
import { PriceChart } from '../PriceChart'
import { SimulatedDataBanner } from '../SimulatedDataBanner'
import { DrillOverlay } from './DrillOverlay'
import { ApiError } from '@/lib/api/client'
import {
  answerDrill,
  DRILL_TIMEFRAMES,
  listDrillKinds,
  startDrill,
  type DrillKindView,
  type DrillResponse,
  type ResultView,
  type TargetView,
} from '@/lib/api/drills'
import type { ChartAdapter } from '@/lib/chart/types'
import { formatSessionTime } from '@/lib/chart/timeAxis'
import { toggleMark } from '@/lib/drills/marks'
import { Button, Chip, Notice, Panel, Segmented } from '../ui'
import { errorClass, fieldClass, panelTitle } from '@/lib/styles'

interface DrillViewProps {
  /** The drill to offer first: the one a lesson card sent the learner to practise. */
  initialKind?: string
  adapterFactory?: () => ChartAdapter
}

const MODE_GROUPS = [
  { mode: 'IDENTIFY', label: 'Spot — mark every one' },
  { mode: 'CLASSIFY', label: 'Name — say what the marked one is' },
  { mode: 'PREDICT', label: 'Predict — say what price reaches first' },
] as const

/**
 * The Drills tab (DEC-023, DEC-054): short, single-concept exercises, done by the dozen, on real NQ.
 *
 * Three kinds. Spot (mode A): mark every instance of a pattern in a stretch of chart. Name (mode B):
 * one structure is drawn — say what the rule calls it. Both are graded against the versioned rule
 * named on screen, and each hit is explained from the rule's own numbers. Predict (mode C, DEC-058):
 * the chart stops and two targets are drawn — say which price reaches first; the candles after the
 * moment arrive only with the answer, and grade it. On simulated data the one drill left is a
 * developer fixture (DEC-034), and the screen says so before anything else.
 */
export function DrillView({ initialKind, adapterFactory }: DrillViewProps) {
  const [kinds, setKinds] = useState<DrillKindView[]>([])
  const [kind, setKind] = useState(initialKind ?? 'FVG_IDENTIFICATION')
  const [timeframe, setTimeframe] = useState<string>('5m')
  const [drill, setDrill] = useState<DrillResponse | null>(null)
  const [marks, setMarks] = useState<number[]>([])
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const answered = drill?.result !== undefined
  const shownMarks = answered ? (drill?.marks ?? []) : marks

  // A prediction's chart grows by the candles after the moment once it is answered.
  const chartBars = useMemo(() => (drill ? [...drill.bars, ...(drill.revealed ?? [])] : []), [drill])
  const moment = useMemo(() => {
    if (drill?.mode !== 'PREDICT' || drill.bars.length < 2) return undefined
    const last = drill.bars[drill.bars.length - 1].time
    return last + (last - drill.bars[drill.bars.length - 2].time)
  }, [drill])

  useEffect(() => {
    listDrillKinds()
      .then(setKinds)
      .catch(() => setError('Could not load the list of drills. Reload to try again.'))
  }, [])

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
      const next = await startDrill({ kind, instrument: 'NQ', timeframe })
      setDrill(next)
      setMarks([])
    })

  const check = () =>
    run(async () => {
      if (!drill) return
      setDrill(await answerDrill(drill.id, { marks }))
    })

  const choose = (choice: string) =>
    run(async () => {
      if (!drill) return
      setDrill(await answerDrill(drill.id, { choice }))
    })

  // Stable, because the overlay subscribes to clicks with it.
  const toggle = useCallback((time: number) => setMarks((current) => toggleMark(current, time)), [])

  const identify = drill?.mode === 'IDENTIFY'
  const predict = drill?.mode === 'PREDICT'

  return (
    <section className="flex flex-col gap-4">
      <Panel aria-label="Drills">
        <h2 className="mb-2 text-lg font-semibold">Drills</h2>
        <p className="max-w-3xl text-sm text-subtle">
          Short exercises on one idea at a time, on real NQ. <strong>Spot</strong>: mark every instance
          of a pattern in a stretch of chart. <strong>Name</strong>: one structure is drawn — say what it
          is. Each is graded against the versioned rule named on screen: the rule, not a claim that the
          pattern works. <strong>Predict</strong>: the chart stops and two targets are drawn — say which
          price reaches first. What real price did next decides.
        </p>
        <div className="mt-4 flex flex-wrap items-center gap-3">
          <label className="flex items-center gap-2 text-xs font-medium text-subtle">
            Drill
            <select
              className={fieldClass}
              value={kind}
              disabled={kinds.length === 0}
              onChange={(e) => setKind(e.target.value)}
            >
              {MODE_GROUPS.map((group) => (
                <optgroup key={group.mode} label={group.label}>
                  {kinds
                    .filter((k) => k.mode === group.mode)
                    .map((k) => (
                      <option key={k.kind} value={k.kind}>
                        {k.title}
                      </option>
                    ))}
                </optgroup>
              ))}
            </select>
          </label>
          <Segmented<string>
            options={DRILL_TIMEFRAMES}
            value={timeframe}
            onChange={setTimeframe}
            label="Drill timeframe"
          />
          <Button variant="primary" disabled={busy || kinds.length === 0} onClick={() => void start()}>
            {busy && !drill ? 'Finding a chart…' : drill ? 'Next drill' : 'Start a drill'}
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
            <h3 className={`${panelTitle} flex flex-wrap items-center gap-2`}>
              {drill.title} · {drill.instrument} · {drill.timeframe}
              {drill.graded && (
                <Chip data-testid="graded">
                  {predict ? `Graded by what price did · ${drill.question?.version}` : `Graded against ${drill.rule.version}`}
                </Chip>
              )}
            </h3>
            <p className="text-sm">{drill.instructions}</p>
            <details className="mt-3 text-xs">
              <summary className="cursor-pointer text-subtle">
                The rule ({predict ? `${drill.question?.version}, on ${drill.rule.version}` : drill.rule.version})
              </summary>
              {predict && <p className="mt-2 max-w-4xl">{drill.question?.definition}</p>}
              <p className="mt-2 max-w-4xl">{drill.rule.definition}</p>
              <p className="mt-2 text-subtle">
                {drill.rule.methodologySource}. Reproducible under this rule — not proof that a pattern
                matters, or that the method it comes from is right.
              </p>
            </details>
          </Panel>

          <div className="flex min-w-0 items-start gap-2">
            <PriceChart
              // A prediction's answer is the whole story — the chart and what came after — so it is
              // framed afresh rather than scrolled along like a replay.
              key={predict && answered ? `${drill.id}-answered` : drill.id}
              bars={chartBars}
              tickSize={drill.tickSize}
              exchangeTimezone={drill.exchangeTimezone}
              axisTimezone="exchange"
              adapterFactory={adapterFactory}
              overlay={(adapter) => (
                <DrillOverlay
                  adapter={adapter}
                  bars={chartBars}
                  marks={shownMarks}
                  onToggle={identify ? toggle : undefined}
                  subject={drill.subject}
                  result={drill.result}
                  question={drill.question}
                  moment={moment}
                />
              )}
            />
          </div>

          {!answered && identify && (
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

          {!answered && predict && drill.question && (
            <Panel aria-label="Answer">
              <p className="mb-3 text-sm text-subtle">Which does price reach first?</p>
              <div className="flex flex-wrap items-start gap-3">
                {drill.question.targets.map((target) => (
                  <div key={target.value} className="flex flex-col gap-1">
                    <Button disabled={busy} onClick={() => void choose(target.value)}>
                      {target.label}
                    </Button>
                    <span className="text-xs text-subtle" data-testid="target-facts">
                      {facts(target)}
                    </span>
                  </div>
                ))}
              </div>
            </Panel>
          )}

          {!answered && !identify && !predict && (
            <Panel aria-label="Answer">
              <p className="mb-3 text-sm text-subtle">The marked {drill.subject?.side === 'HIGH' ? 'high' : 'low'} is…</p>
              <div className="flex flex-wrap items-center gap-3">
                {drill.options?.map((option) => (
                  <Button key={option.value} disabled={busy} onClick={() => void choose(option.value)}>
                    {option.label}
                  </Button>
                ))}
              </div>
            </Panel>
          )}

          {drill.result && (
            <Panel aria-label="Result">
              {identify ? (
                <SpotResult drill={drill} result={drill.result} />
              ) : predict ? (
                <PredictResult drill={drill} result={drill.result} />
              ) : (
                <NameResult drill={drill} result={drill.result} />
              )}
            </Panel>
          )}
        </>
      )}
    </section>
  )
}

function SpotResult({ drill, result }: { drill: DrillResponse; result: ResultView }) {
  const found = result.found ?? 0
  const missed = result.missed ?? 0
  const extra = result.extra ?? 0
  const time = (t: number) => formatSessionTime(t, drill.exchangeTimezone)
  return (
    <>
      <h3 className={panelTitle} data-testid="drill-summary">
        Found {found} of {found + missed}
        {extra > 0 && ` · ${extra} extra ${extra === 1 ? 'mark' : 'marks'}`}
      </h3>
      <p className="mb-3 text-xs text-subtle" data-testid="drill-grade">
        {drill.graded
          ? `Recall ${percent(found, found + missed)} · precision ${percent(found, found + extra)}, against ${drill.rule.version}.`
          : 'Feedback on this chart, not a grade.'}
      </p>
      <ol className="list-decimal space-y-3 pl-5 text-sm">
        {result.setups.map((setup) => (
          <li key={setup.id} data-testid="drill-gap">
            <strong>
              {setup.label} · {time(setup.anchorTime)}
            </strong>{' '}
            <Chip tone={setup.found ? 'good' : 'caution'}>{setup.found ? 'found' : 'missed'}</Chip>
            <Explanation lines={setup.explanation} />
          </li>
        ))}
      </ol>
      {(result.extraMarks?.length ?? 0) > 0 && (
        <p className="mt-3 text-xs" data-testid="drill-extra">
          Marked, but not {drill.markName} under this rule: {result.extraMarks!.map(time).join(', ')}.
        </p>
      )}
      {(result.excusedMarks?.length ?? 0) > 0 && (
        <p className="mt-2 text-xs text-subtle" data-testid="drill-excused">
          Not counted either way: {result.excusedMarks!.map(time).join(', ')} — the rule needs candles outside
          this chart to judge {result.excusedMarks!.length === 1 ? 'it' : 'them'}.
        </p>
      )}
    </>
  )
}

function NameResult({ drill, result }: { drill: DrillResponse; result: ResultView }) {
  const name = (value?: string) => drill.options?.find((o) => o.value === value)?.label ?? value
  const subject = result.setups[0]
  return (
    <>
      <h3 className={panelTitle} data-testid="drill-summary">
        {result.correct
          ? `Correct — ${name(result.answer)}`
          : `Not quite — the rule says ${name(result.answer)}; you chose ${name(drill.choice)}`}
      </h3>
      <p className="mb-3 text-xs text-subtle" data-testid="drill-grade">
        {drill.graded ? `Graded against ${drill.rule.version}.` : 'Feedback on this chart, not a grade.'}
      </p>
      {subject && (
        <div data-testid="drill-gap" className="text-sm">
          <strong>
            {subject.label} · {formatSessionTime(subject.anchorTime, drill.exchangeTimezone)}
          </strong>
          <Explanation lines={subject.explanation} />
        </div>
      )}
    </>
  )
}

/** What the chart says about a target before the answer: how far, and what lies on the way (ICT-17). */
function facts(target: TargetView): string {
  const away = `${target.distanceTicks} ${target.distanceTicks === 1 ? 'tick' : 'ticks'} away`
  if (target.obstacleSwings === undefined || target.obstacleGaps === undefined) return away
  const total = target.obstacleSwings + target.obstacleGaps
  return total === 0
    ? `${away} · nothing on the way`
    : `${away} · on the way: ${count(target.obstacleSwings, 'swing point')}, ${count(target.obstacleGaps, 'open gap')}`
}

function count(n: number, what: string): string {
  return `${n} ${what}${n === 1 ? '' : 's'}`
}

function PredictResult({ drill, result }: { drill: DrillResponse; result: ResultView }) {
  const name = (value?: string) => drill.options?.find((o) => o.value === value)?.label ?? value
  const when = result.outcomeTime === undefined ? '' : formatSessionTime(result.outcomeTime, drill.exchangeTimezone)
  return (
    <>
      <h3 className={panelTitle} data-testid="drill-summary">
        {result.correct
          ? `Yes — ${name(result.answer)} came first`
          : `Not this time — ${name(result.answer)} came first; you chose ${name(drill.choice)}`}
      </h3>
      <p className="mb-3 text-xs text-subtle" data-testid="drill-grade">
        Decided at {when}, {result.candlesAfter === 1 ? 'the next candle' : `${result.candlesAfter} candles after the chart`}.
        Graded by what real price did — the candles after the line are the ones that were hidden.
      </p>
      <Explanation lines={result.explanation ?? []} />
    </>
  )
}

/** The server's sentences, from the rule's evidence (DEC-026). Shown as they are. */
function Explanation({ lines }: { lines: string[] }) {
  return lines.map((line) => (
    <p key={line} className="mt-1 text-xs text-subtle">
      {line}
    </p>
  ))
}

function percent(part: number, whole: number): string {
  return whole === 0 ? '—' : `${Math.round((100 * part) / whole)}%`
}
