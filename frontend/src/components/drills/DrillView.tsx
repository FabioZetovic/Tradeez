import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { motion } from 'motion/react'
import { Link } from 'react-router'
import { ArrowLeft } from 'lucide-react'
import { PriceChart } from '../PriceChart'
import { AdapterOut, CompanionChart } from '../CompanionChart'
import { SimulatedDataBanner } from '../SimulatedDataBanner'
import { DrillOverlay } from './DrillOverlay'
import { MODE_NAMES } from '@/lib/drills/modes'
import { ApiError } from '@/lib/api/client'
import { answerDrill, startDrill, type DrillResponse, type ResultView, type TargetView } from '@/lib/api/drills'
import type { ChartAdapter } from '@/lib/chart/types'
import { formatSessionTime } from '@/lib/chart/timeAxis'
import { toggleMark } from '@/lib/drills/marks'
import { drillMarket } from '@/lib/markets'
import { Button, Chip, Notice, Panel } from '../ui'
import { cx, errorClass, panelTitle } from '@/lib/styles'

/** The answer under the chart; on a phone (T46) held at the screen's foot, so it stays in reach as you scroll. */
const ANSWER_BAR = 'sticky bottom-2 z-10 lg:static'
const DRILL_RIGHT_OFFSET = 2

interface DrillViewProps {
  /** The drill, from its card or a lesson's "Practise it". */
  kind: string
  adapterFactory?: () => ChartAdapter
}

/** "← All drills": back to the cards. */
export function AllDrills({ className }: { className?: string }) {
  return (
    <Link
      to="/drills"
      className={`inline-flex items-center gap-1.5 text-sm font-medium text-subtle hover:text-ink pointer-coarse:min-h-11 ${className ?? ''}`}
    >
      <ArrowLeft aria-hidden className="size-4" />
      All drills
    </Link>
  )
}

/**
 * One drill's screen (DEC-023, DEC-054, T43): short, single-concept exercises, done by the dozen, on real NQ. The card
 * that opened it is the one click: the chart is asked for at once, on a timeframe the server draws.
 *
 * Spot (mode A): mark every instance of a pattern in a stretch of chart. Name (mode B): one structure is drawn — say what
 * the rule calls it. Both are graded against the versioned rule named on screen, and each hit is explained from the
 * rule's own numbers. Predict (mode C, DEC-058): the chart stops and two targets are drawn — say which price reaches
 * first; the candles after the moment arrive only with the answer, and grade it. Manage (T26): an open trade, three
 * choices. On simulated data the one drill left is a developer fixture (DEC-034), and the screen says so before anything
 * else.
 */
export function DrillView({ kind, adapterFactory }: DrillViewProps) {
  const [drill, setDrill] = useState<DrillResponse | null>(null)
  const [marks, setMarks] = useState<number[]>([])
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const answered = drill?.result !== undefined
  const shownMarks = answered ? (drill?.marks ?? []) : marks

  // A prediction's chart grows by the candles after the moment once it is answered.
  const chartBars = useMemo(() => (drill ? [...drill.bars, ...(drill.revealed ?? [])] : []), [drill])
  // DEC-075: the drill's chart, for the other market's chart under it to keep to.
  const [leader, setLeader] = useState<ChartAdapter | null>(null)
  const moment = useMemo(() => {
    if ((drill?.mode !== 'PREDICT' && drill?.mode !== 'MANAGE') || drill.bars.length < 2) return undefined
    const last = drill.bars[drill.bars.length - 1].time
    return last + (last - drill.bars[drill.bars.length - 2].time)
  }, [drill])

  const run = useCallback(async (work: () => Promise<void>) => {
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
  }, [])

  const start = useCallback(
    () =>
      run(async () => {
        const next = await startDrill({ kind, instrument: drillMarket() })
        setDrill(next)
        setMarks([])
      }),
    [run, kind],
  )

  // The card was the click: ask once on arrival — a strict-mode second mount must not ask for a second drill.
  const asked = useRef(false)
  useEffect(() => {
    if (asked.current) return
    asked.current = true
    void start()
  }, [start])

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
  const manage = drill?.mode === 'MANAGE'
  // A question about the chart as it stands, answered by a rule (T45): its lines drawn, its answers offered.
  const judge = drill?.mode === 'JUDGE'
  // Both are answered by what price did after the chart stopped, and show it afterwards.
  const future = predict || manage

  return (
    <section aria-label="Drill" className="mx-auto flex max-w-[1600px] flex-col gap-4">
      <AllDrills />
      {!drill && (
        <Panel aria-label="Finding a chart">
          {busy && (
            <p role="status" className="text-sm text-subtle">
              Finding a chart…
            </p>
          )}
          {error && (
            <div className="flex flex-wrap items-center gap-3">
              <p role="alert" className={errorClass}>
                {error}
              </p>
              <Button disabled={busy} onClick={() => void start()}>
                Try again
              </Button>
            </div>
          )}
        </Panel>
      )}

      {drill && (
        <>
          <header>
            <div className="flex flex-wrap items-baseline gap-3">
              <h1 className="font-display text-3xl font-bold tracking-tight text-ink">{drill.title}</h1>
              <span className="text-sm text-subtle">
                <span data-testid="drill-mode">{MODE_NAMES[drill.mode]}</span> ·{' '}
                <span data-testid="drill-chart">
                  {drill.instrument} · {drill.timeframe}
                </span>
              </span>
            </div>
            <p className="mt-2 max-w-3xl text-base text-subtle">{drill.instructions}</p>
          </header>
          <SimulatedDataBanner dataClass={drill.dataClass} />
          {drill.fixture && (
            <Notice role="note" data-testid="fixture-notice">
              {drill.fixtureNotice}
            </Notice>
          )}

          <div className="rounded-card border border-line bg-surface p-3 shadow-panel">
            <PriceChart
              // A prediction's answer is the whole story — the chart and what came after — so it is
              // framed afresh rather than scrolled along like a replay.
              key={future && answered ? `${drill.id}-answered` : drill.id}
              bars={chartBars}
              tickSize={drill.tickSize}
              exchangeTimezone={drill.exchangeTimezone}
              axisTimezone="exchange"
              // No position tool here: the candles take the width (BUG-104).
              rightOffset={DRILL_RIGHT_OFFSET}
              adapterFactory={adapterFactory}
              overlay={(adapter) => (
                <>
                  {drill.companion && <AdapterOut adapter={adapter} onAdapter={setLeader} />}
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
                </>
              )}
            />
            {drill.companion && (
              <CompanionChart
                companion={drill.companion}
                leader={leader}
                exchangeTimezone={drill.exchangeTimezone}
                axisTimezone="exchange"
                adapterFactory={adapterFactory}
              />
            )}
          </div>

          <details className="text-xs">
            <summary className="flex cursor-pointer flex-wrap items-center gap-2 text-subtle">
              <span className="text-ink">The rule Tradeez uses</span>
              {drill.graded ? (
                <span data-testid="graded">
                  {future ? `Graded by what price did · ${drill.question?.version}, on ${drill.rule.version}`
                    : `Graded against ${judge ? drill.question?.version : drill.rule.version}`}
                </span>
              ) : (
                <span>{predict ? `${drill.question?.version}, on ${drill.rule.version}` : drill.rule.version}</span>
              )}
            </summary>
            {(predict || judge) && <p className="mt-2 max-w-4xl">{drill.question?.definition}</p>}
            <p className="mt-2 max-w-4xl">{drill.rule.definition}</p>
            <p className="mt-2 text-subtle">
              {drill.rule.methodologySource}. Reproducible under this rule — not proof that a pattern
              matters, or that the method it comes from is right.
            </p>
          </details>

          {!answered && identify && (
            <Panel aria-label="Answer" className={cx(ANSWER_BAR, 'lg:border-0 lg:bg-transparent lg:p-0 lg:shadow-none')}>
              <div className="flex flex-wrap items-center justify-between gap-3">
                <span data-testid="mark-count" className="text-sm text-subtle">
                  <span className="num">{marks.length}</span> marked
                </span>
                <span className="flex gap-3">
                  <Button disabled={busy || marks.length === 0} onClick={() => setMarks([])}>
                    Clear
                  </Button>
                  <Button variant="primary" disabled={busy} onClick={() => void check()}>
                    Check answer
                  </Button>
                </span>
              </div>
            </Panel>
          )}

          {!answered && predict && drill.question && (
            <Panel aria-label="Answer" className={ANSWER_BAR}>
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

          {!answered && manage && (
            <Panel aria-label="Answer" className={ANSWER_BAR}>
              <p className="mb-3 text-sm text-subtle">The trade is open and in profit. What do you do with it?</p>
              <div className="flex flex-wrap items-center gap-3">
                {drill.options?.map((option) => (
                  <Button key={option.value} disabled={busy} onClick={() => void choose(option.value)}>
                    {option.label}
                  </Button>
                ))}
              </div>
            </Panel>
          )}

          {!answered && judge && (
            <Panel aria-label="Answer" className={ANSWER_BAR}>
              <div className="flex flex-wrap items-center gap-3">
                {drill.options?.map((option) => (
                  <Button key={option.value} disabled={busy} onClick={() => void choose(option.value)}>
                    {option.label}
                  </Button>
                ))}
              </div>
            </Panel>
          )}

          {!answered && !identify && !future && !judge && (
            <Panel aria-label="Answer" className={ANSWER_BAR}>
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

          {error && (
            <p role="alert" className={errorClass}>
              {error}
            </p>
          )}

          {drill.result && (
            <motion.div
              key={drill.id}
              initial={{ opacity: 0, y: 10 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.25, ease: 'easeOut' }}
            >
              <Panel aria-label="Result">
                {identify ? (
                  <SpotResult drill={drill} result={drill.result} />
                ) : predict ? (
                  <PredictResult drill={drill} result={drill.result} />
                ) : manage ? (
                  <ManageResult drill={drill} result={drill.result} />
                ) : judge ? (
                  <JudgeResult drill={drill} result={drill.result} />
                ) : (
                  <NameResult drill={drill} result={drill.result} />
                )}
                <div className="mt-5 flex flex-wrap items-center gap-4">
                  <Button variant="primary" disabled={busy} onClick={() => void start()}>
                    {busy ? 'Finding a chart…' : 'Next drill'}
                  </Button>
                  <AllDrills />
                </div>
              </Panel>
            </motion.div>
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
        {found}/{found + missed} found
        {extra > 0 && ` · ${extra} extra`}
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

/** What each choice made (T26): the ones that paid most, a tie counting for each, and the story in R. */
function ManageResult({ drill, result }: { drill: DrillResponse; result: ResultView }) {
  // "Hold — leave the stop and the target" is "Hold" in a sentence.
  const name = (value?: string) => drill.options?.find((o) => o.value === value)?.label.split(' — ')[0] ?? value
  const best = (result.answer ?? '').split(',')
  const paid = best.map(name).join(' and ')
  return (
    <>
      <h3 className={panelTitle} data-testid="drill-summary">
        {result.correct
          ? `Yes — ${paid} paid most here`
          : `${paid} paid most here; you chose ${name(drill.choice)}`}
      </h3>
      <p className="mb-3 text-xs text-subtle" data-testid="drill-grade">
        Graded by what real price did with each choice — the candles after the line are the ones that were hidden.
      </p>
      <Explanation lines={result.explanation ?? []} />
    </>
  )
}

/** A judgement's answer (T45): the rule's, against the learner's, and the reasons in prices. */
function JudgeResult({ drill, result }: { drill: DrillResponse; result: ResultView }) {
  const name = (value?: string) => drill.options?.find((o) => o.value === value)?.label ?? value
  return (
    <>
      <h3 className={panelTitle} data-testid="drill-summary">
        {result.correct ? `Right — ${name(result.answer)}` : `Not quite — ${name(result.answer)}; you chose ${name(drill.choice)}`}
      </h3>
      <p className="mb-3 text-xs text-subtle" data-testid="drill-grade">
        Graded against {drill.question?.version}.
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
