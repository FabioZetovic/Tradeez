import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'
import { Link } from 'react-router'
import { ArrowRight, BookOpen, ChartCandlestick, Clock, Crosshair, Target } from 'lucide-react'
import type { User } from '@/lib/api/auth'
import { getCurriculum } from '@/lib/api/concepts'
import { listDrillKinds } from '@/lib/api/drills'
import { getLearning } from '@/lib/api/learning'
import { getBars, listInstruments } from '@/lib/api/marketData'
import { getChartClock, type ClockLayer } from '@/lib/api/clock'
import { getStatistics, listSessions } from '@/lib/api/practice'
import type { ChartAdapter, DomainBar } from '@/lib/chart/types'
import { formatPrice } from '@/lib/chart/priceFormat'
import { previousSessionClose, sessionStart } from '@/lib/chart/dateNavigation'
import type { ClockLayerKey } from '@/lib/clock/layers'
import { cx } from '@/lib/styles'
import { PriceChart } from '../PriceChart'
import { ClockOverlay } from '../ClockOverlay'
import { ProgressBar, Ring } from '../ui'

const DAY = 86_400

/** Undefined while loading, null when it could not be loaded: every card says only what it knows. */
function useLoaded<T>(load: () => Promise<T>): T | null | undefined {
  const [value, setValue] = useState<T | null | undefined>(undefined)
  useEffect(() => {
    let live = true
    load()
      .then((v) => live && setValue(v))
      .catch(() => live && setValue(null))
    return () => {
      live = false
    }
  }, [load])
  return value
}

/** "Good morning|afternoon|evening", by the browser's own hour. */
function greeting(hour: number): string {
  return hour < 12 ? 'Good morning' : hour < 18 ? 'Good afternoon' : 'Good evening'
}

/** "Friday, 23 October". */
function longDate(at: Date): string {
  const part = (options: Intl.DateTimeFormatOptions) => new Intl.DateTimeFormat('en-GB', options).format(at)
  return `${part({ weekday: 'long' })}, ${part({ day: 'numeric', month: 'long' })}`
}

const STILL_CLOCK: ReadonlySet<ClockLayerKey> = new Set(['killzones'])

/**
 * Home (T39, R-6): the page after sign-in — where the learner stands in each section, what to learn next, what is
 * due for review, the practice so far, and the latest NQ session. Every number is the learner's own, from the
 * server; none is invented, and a new account is told what to do instead.
 */
export function HomePage({ user, adapterFactory }: { user: User; adapterFactory?: () => ChartAdapter }) {
  const learning = useLoaded(getLearning)
  const curriculum = useLoaded(getCurriculum)
  const kinds = useLoaded(listDrillKinds)
  const sessions = useLoaded(listSessions)
  const statistics = useLoaded(getStatistics)
  const [now] = useState(() => new Date())

  const name = useCallback(
    (id: string) => curriculum?.concepts.find((c) => c.concept.id === id)?.concept.name ?? id,
    [curriculum],
  )
  const solid = useMemo(
    () => new Set(learning?.concepts.filter((c) => c.state === 'SOLID').map((c) => c.id) ?? []),
    [learning],
  )
  // Pattern drills: graded against a rule. Predictions are practice, never a rating (DEC-059).
  const pattern = kinds?.filter((k) => k.mode === 'IDENTIFY' || k.mode === 'CLASSIFY') ?? []
  const drillsSolid = pattern.filter((k) => solid.has(k.conceptId)).length
  const trades = sessions?.reduce((sum, s) => sum + s.trades, 0) ?? 0

  const suggested = learning?.suggested
  const order = curriculum?.concepts.map((c) => c.concept.id) ?? []
  const due = [...(learning?.due ?? [])].sort((a, b) => order.indexOf(a) - order.indexOf(b)).slice(0, 5)

  return (
    <div className="flex flex-col gap-6 py-2">
      <header>
        <p className="text-sm text-subtle">{longDate(now)}</p>
        <h1 className="mt-1 font-display text-5xl font-bold tracking-tight text-ink">
          {greeting(now.getHours())}, {user.displayName.split(/\s+/)[0]}.
        </h1>
        <p className="mt-3 max-w-xl text-lg text-subtle">
          Learn the rule, drill it, then practise it on a real day you can&apos;t see ahead of.
        </p>
      </header>

      <ol className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
        <StepCard n={1} to="/learn" icon={<BookOpen />} title="Learn" line="Each ICT concept as a rule, with real NQ examples.">
          {learning && (
            <Progress
              value={learning.summary.complete / Math.max(1, learning.summary.concepts)}
              text={`${learning.summary.complete} of ${learning.summary.concepts} concepts complete`}
            />
          )}
        </StepCard>
        <StepCard n={2} to="/drills" icon={<Crosshair />} title="Drills" line="Spot and name patterns on real charts, graded by the rule.">
          {learning && pattern.length > 0 && (
            <Progress value={drillsSolid / pattern.length} text={`${drillsSolid} of ${pattern.length} drills solid`} />
          )}
        </StepCard>
        <StepCard n={3} to="/practice" icon={<Target />} title="Practice" line="A real day replayed bar by bar, the future hidden.">
          {sessions && (
            <Figure>
              {sessions.length} {sessions.length === 1 ? 'session' : 'sessions'} · {trades} {trades === 1 ? 'trade' : 'trades'}
            </Figure>
          )}
        </StepCard>
        <StepCard n={4} to="/chart" icon={<ChartCandlestick />} title="Chart" line="The whole history, every rule and drawing tool.">
          <Figure>Real NQ, every ICT layer</Figure>
        </StepCard>
      </ol>

      <div className="grid gap-4 lg:grid-cols-3">
        <section aria-label="Up next" className="flex flex-col rounded-card bg-accent/15 p-6 ring-1 ring-accent/30">
          <h2 className="text-xs font-semibold tracking-widest text-accent uppercase">Up next</h2>
          {learning === undefined ? null : suggested ? (
            <>
              <p className="mt-3 font-display text-2xl font-bold tracking-tight text-ink">{name(suggested)}</p>
              <p className="mt-2 line-clamp-4 text-sm text-subtle">
                {curriculum?.concepts.find((c) => c.concept.id === suggested)?.concept.summary}
              </p>
              <Link
                to={`/learn/${suggested}`}
                className="mt-auto inline-flex items-center gap-1.5 pt-5 text-sm font-semibold text-accent hover:underline"
              >
                Continue lesson <ArrowRight aria-hidden className="size-4" />
              </Link>
            </>
          ) : (
            <p className="mt-3 text-sm text-subtle">
              {learning === null ? 'Your progress could not be loaded.' : "Everything open is complete — review what's due."}
            </p>
          )}
        </section>

        <section aria-label="Due for review" className="rounded-card border border-line bg-surface p-6 shadow-panel">
          <h2 className="flex items-center gap-2 text-xs font-semibold tracking-widest text-subtle uppercase">
            <Clock aria-hidden className="size-3.5" /> Due for review
          </h2>
          {learning && due.length === 0 && <p className="mt-3 text-sm text-subtle">Nothing due today.</p>}
          {due.length > 0 && (
            <ul className="mt-3 flex flex-col">
              {due.map((id) => {
                const accuracy = learning?.concepts.find((c) => c.id === id)?.recentAccuracy
                return (
                  <li key={id}>
                    <Link
                      to={`/learn/${id}`}
                      className="group flex items-center gap-3 rounded-lg px-2 py-2 transition-colors hover:bg-sunken"
                    >
                      <Ring
                        value={accuracy}
                        label={accuracy === undefined ? 'Not graded yet' : `${Math.round(accuracy * 100)}% recently`}
                      />
                      <span className="min-w-0 flex-1 truncate text-sm text-ink">{name(id)}</span>
                      <span className="num text-xs text-subtle">{id}</span>
                      <ArrowRight aria-hidden className="size-4 text-subtle group-hover:text-ink" />
                    </Link>
                  </li>
                )
              })}
            </ul>
          )}
        </section>

        <section aria-label="Practice so far" className="flex flex-col rounded-card border border-line bg-surface p-6 shadow-panel">
          <h2 className="text-xs font-semibold tracking-widest text-subtle uppercase">Practice so far</h2>
          {statistics && statistics.overall.trades > 0 && (
            <>
              <p
                data-testid="total-r"
                className={cx('num mt-3 text-4xl font-semibold', statistics.overall.totalR >= 0 ? 'text-up' : 'text-down')}
              >
                {statistics.overall.totalR > 0 ? '+' : statistics.overall.totalR < 0 ? '−' : ''}
                {Math.abs(statistics.overall.totalR).toFixed(2)}R
              </p>
              <p className="mt-1 text-sm text-subtle">
                {statistics.overall.trades} practice {statistics.overall.trades === 1 ? 'trade' : 'trades'}, net of costs
              </p>
            </>
          )}
          {statistics && statistics.overall.trades === 0 && (
            <p className="mt-3 text-sm text-subtle">No trades yet — start a session.</p>
          )}
          {statistics !== undefined && (
            <Link
              to="/practice"
              className="mt-auto inline-flex items-center gap-1.5 pt-5 text-sm font-semibold text-accent hover:underline"
            >
              {statistics && statistics.overall.trades > 0 ? 'Open journal' : 'Start a session'}
              <ArrowRight aria-hidden className="size-4" />
            </Link>
          )}
        </section>
      </div>

      <LatestSession adapterFactory={adapterFactory} />
    </div>
  )
}

function StepCard({
  n,
  to,
  icon,
  title,
  line,
  children,
}: {
  n: number
  to: string
  icon: ReactNode
  title: string
  line: string
  children?: ReactNode
}) {
  return (
    <li>
      <Link
        to={to}
        className="flex h-full flex-col rounded-card border border-line bg-surface p-5 shadow-panel transition-all duration-150 hover:-translate-y-0.5 hover:border-accent/40 hover:bg-raised focus-visible:outline-2 focus-visible:outline-accent"
      >
        <span className="flex items-center justify-between">
          <span className="num text-xs text-subtle">STEP {n}</span>
          <span aria-hidden className="text-accent [&_svg]:size-5">
            {icon}
          </span>
        </span>
        <span className="mt-6 font-display text-2xl font-bold tracking-tight text-ink">{title}</span>
        <span className="mt-1 text-sm text-subtle">{line}</span>
        <span className="mt-auto pt-5">{children}</span>
      </Link>
    </li>
  )
}

function Progress({ value, text }: { value: number; text: string }) {
  return (
    <span className="flex flex-col gap-2">
      <ProgressBar value={value} label={text} />
      <span className="num text-xs text-subtle">{text}</span>
    </span>
  )
}

function Figure({ children }: { children: ReactNode }) {
  return <span className="num text-xs text-subtle">{children}</span>
}

/**
 * The latest NQ session on 5-minute candles with its killzones, still — a picture to read, not a tool (R-12) — and a
 * way into the Chart tab.
 */
function LatestSession({ adapterFactory }: { adapterFactory?: () => ChartAdapter }) {
  const instruments = useLoaded(listInstruments)
  const nq = instruments?.find((i) => i.symbol === 'NQ')
  const dataTo = nq?.dataTo
  const [day, setDay] = useState<{ bars: DomainBar[]; before: number | null; clock: ClockLayer | null } | null>(null)

  useEffect(() => {
    if (dataTo === undefined) return
    let live = true
    // Two days back, so the session before the latest is there to measure the change from.
    getBars({ symbol: 'NQ', timeframe: '5m', from: dataTo - 2 * DAY, to: dataTo })
      .then(async (series) => {
        const last = series.bars.at(-1)
        if (!last) return
        const start = sessionStart(last.time)
        const bars = series.bars.filter((b) => b.time >= start)
        const clock = await getChartClock('NQ', bars[0].time, dataTo).catch(() => null)
        if (live) setDay({ bars, before: previousSessionClose(series.bars, last.time), clock })
      })
      .catch(() => {})
    return () => {
      live = false
    }
  }, [dataTo])

  if (!nq || !day) return null
  const last = day.bars[day.bars.length - 1]
  const change = day.before === null ? null : last.close - day.before

  return (
    <Link
      to="/chart"
      aria-label="NQ, the latest session — open the Chart tab"
      className="block rounded-card border border-line bg-surface p-5 shadow-panel transition-colors hover:border-accent/40"
    >
      <span className="mb-3 flex items-baseline gap-3">
        <span className="text-sm text-subtle">NQ · 5m · latest data</span>
        <span className="num text-xl font-semibold text-ink" data-testid="home-price">
          {formatPrice(last.close, nq.tickSize)}
        </span>
        {change !== null && (
          <span className={cx('num text-sm', change >= 0 ? 'text-up' : 'text-down')} data-testid="home-change">
            {change > 0 ? '+' : change < 0 ? '−' : ''}
            {formatPrice(Math.abs(change), nq.tickSize)}
          </span>
        )}
      </span>
      <PriceChart
        still
        heightClass="h-56"
        bars={day.bars}
        tickSize={nq.tickSize}
        exchangeTimezone={nq.exchangeTimezone}
        axisTimezone="new-york"
        adapterFactory={adapterFactory}
        overlay={(adapter) => (
          <ClockOverlay adapter={adapter} layer={day.clock} bars={day.bars} timeframe="5m" tickSize={nq.tickSize} shown={STILL_CLOCK} />
        )}
      />
    </Link>
  )
}
