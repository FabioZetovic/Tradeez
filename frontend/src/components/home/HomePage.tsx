import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'
import { useNow } from '@/hooks/useNow'
import { motion } from 'motion/react'
import { Link } from 'react-router'
import { ArrowRight, ChevronRight, Clock } from 'lucide-react'
import { useIsPhone } from '@/hooks/useIsPhone'
import { formatR } from '@/lib/practice/plan'
import type { User } from '@/lib/api/auth'
import { getCurriculum } from '@/lib/api/concepts'
import { listDrillKinds } from '@/lib/api/drills'
import { getLearning } from '@/lib/api/learning'
import { getBars, listInstruments } from '@/lib/api/marketData'
import { getChartClock, type ClockLayer } from '@/lib/api/clock'
import { getStatistics, listSessions } from '@/lib/api/practice'
import { getNews } from '@/lib/api/news'
import { newsDay, newsTime } from '@/lib/news'
import type { ChartAdapter, DomainBar } from '@/lib/chart/types'
import { formatPrice } from '@/lib/chart/priceFormat'
import { previousSessionClose, sessionStart } from '@/lib/chart/dateNavigation'
import type { ClockLayerKey } from '@/lib/clock/layers'
import { cx } from '@/lib/styles'
import { SECTION_TOPICS, topicTone } from '@/lib/topics'
import { TopicGlyph } from '../TopicGlyph'
import { PriceChart } from '../PriceChart'
import { ClockOverlay } from '../ClockOverlay'
import { ProgressBar, Ring } from '../ui'
import { ImpactFolder } from '../news/NewsPage'
import { enter } from '@/lib/motion'

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
  const now = useNow()
  const phone = useIsPhone()

  const name = useCallback(
    (id: string) => curriculum?.concepts.find((c) => c.concept.id === id)?.concept.name ?? id,
    [curriculum],
  )
  const solid = useMemo(
    () => new Set(learning?.concepts.filter((c) => c.state === 'SOLID').map((c) => c.id) ?? []),
    [learning],
  )
  // learning-1.1.0 (T45): every drill counts toward passing its lesson, so a drill is passed when its lesson is.
  const drills = kinds ?? []
  const drillsPassed = drills.filter((k) => solid.has(k.conceptId)).length
  // BUG-159: every finished trade, from the statistics the total R comes from — not a sum over the newest sessions,
  // which stopped counting at fifty and counted a trade still running.
  const trades = statistics?.overall.trades ?? 0

  const suggested = learning?.suggested
  const order = curriculum?.concepts.map((c) => c.concept.id) ?? []
  const topicOf = (id: string) => learning?.topics?.find((t) => t.concepts.includes(id))?.title
  const due = [...(learning?.due ?? [])].sort((a, b) => order.indexOf(a) - order.indexOf(b)).slice(0, 5)

  if (phone) {
    // Nothing done anywhere yet: the path, instead of figures that are all zero.
    const fresh =
      learning !== undefined &&
      learning !== null &&
      learning.summary.complete === 0 &&
      learning.summary.ratedAttempts + learning.summary.predictions === 0 &&
      trades === 0 &&
      (sessions?.length ?? 0) === 0
    const summary = curriculum?.concepts.find((c) => c.concept.id === suggested)?.concept.summary
    const totalR = statistics?.overall.totalR ?? 0
    return (
      // Home on a phone (T53, Claude Design): the next lesson first, three figures, what is due, the news, the market.
      <div className="flex flex-col gap-5">
        <header>
          <p className="text-[13px] text-subtle">{longDate(now)}</p>
          <h1 className="mt-0.5 font-display text-[28px] leading-tight font-bold tracking-tight text-ink">
            {greeting(now.getHours())}, {user.displayName.split(/\s+/)[0]}.
          </h1>
          {fresh && (
            <p className="mt-1.5 text-sm text-subtle">Learn the rule, drill it, then practise it on a day you can&apos;t see ahead of.</p>
          )}
        </header>

        {suggested && (
          <Link
            to={`/learn/${suggested}`}
            aria-label={`Up next: ${name(suggested)}`}
            className="block rounded-card border border-accent/30 bg-accent/10 p-4.5 focus-visible:outline-2 focus-visible:outline-accent"
          >
            <span className="flex items-center justify-between">
              <span className="text-xs font-semibold tracking-wide text-accent uppercase">{fresh ? 'Start here · Lesson 1' : 'Up next · Lesson'}</span>
              {learning?.due.includes(suggested) && (
                <span className="flex items-center gap-1 rounded-full bg-warn/15 px-2 py-0.5 text-[11px] font-semibold text-warn">
                  <Clock aria-hidden className="size-3" /> Review
                </span>
              )}
            </span>
            <span className="mt-2.5 block text-[22px] font-semibold text-ink">{name(suggested)}</span>
            {summary && <span className="mt-1.5 line-clamp-3 block text-sm text-subtle">{summary}</span>}
            <span className="mt-4 flex h-12 items-center justify-center gap-2 rounded-full bg-accent text-[15px] font-semibold text-accent-ink">
              {fresh ? 'Start the first lesson' : 'Continue lesson'}
              <ArrowRight aria-hidden className="size-4" />
            </span>
          </Link>
        )}

        {fresh ? (
          <section aria-labelledby="your-path">
            <h2 id="your-path" className="mb-2.5 text-base font-semibold text-ink">
              Your path
            </h2>
            <ol className="divide-y divide-line overflow-hidden rounded-card border border-line bg-surface">
              {[
                ['Read a lesson', `0 of ${learning?.summary.concepts ?? 0} lessons`],
                ['Pass its drill', '5 answers averaging 70% unlock the next lesson'],
                ['Practise a replayed day', 'Plan, place, get a scorecard'],
              ].map(([title, line], i) => (
                <li key={title} className="flex min-h-14 items-center gap-3 px-4 py-2.5">
                  <span
                    className={cx(
                      'grid size-[30px] shrink-0 place-items-center rounded-full text-[13px] font-bold',
                      i === 0 ? 'border-[2.5px] border-accent text-accent' : 'bg-sunken text-subtle',
                    )}
                  >
                    {i + 1}
                  </span>
                  <span>
                    <span className="block text-[15px] font-medium text-ink">{title}</span>
                    <span className="block text-[13px] text-subtle">{line}</span>
                  </span>
                </li>
              ))}
            </ol>
          </section>
        ) : (
          <section aria-label="Progress" className="grid grid-cols-3 gap-2">
            <Link to="/learn" className={TILE}>
              <TileLabel topic={SECTION_TOPICS.learn}>Learn</TileLabel>
              <span className="num mt-0.5 text-xl font-semibold text-ink">
                {learning?.summary.complete ?? 0}
                <span className="text-[13px] text-subtle">/{learning?.summary.concepts ?? 0}</span>
              </span>
              <span className="mt-2">
                <ProgressBar
                  tone={topicTone(SECTION_TOPICS.learn).fill}
                  value={(learning?.summary.complete ?? 0) / Math.max(1, learning?.summary.concepts ?? 0)}
                  label={`${learning?.summary.complete ?? 0} of ${learning?.summary.concepts ?? 0} lessons complete`}
                />
              </span>
            </Link>
            <Link to="/drills" className={TILE}>
              <TileLabel topic={SECTION_TOPICS.drills}>Drills</TileLabel>
              <span className="num mt-0.5 text-xl font-semibold text-ink">
                {drillsPassed}
                <span className="text-[13px] text-subtle">/{drills.length}</span>
              </span>
              <span className="mt-2">
                <ProgressBar
                  tone={topicTone(SECTION_TOPICS.drills).fill}
                  value={drillsPassed / Math.max(1, drills.length)}
                  label={`${drillsPassed} of ${drills.length} drills passed`}
                />
              </span>
            </Link>
            <Link to="/practice" className={TILE}>
              <TileLabel topic={SECTION_TOPICS.practice}>Practice</TileLabel>
              <span className={cx('num mt-1 text-[17px] font-semibold', totalR > 0 ? 'text-up' : totalR < 0 ? 'text-down' : 'text-ink')} data-testid="total-r">
                {formatR(totalR)}
              </span>
              <span className="mt-1 text-[13px] text-subtle">
                {trades} finished {trades === 1 ? 'trade' : 'trades'}
              </span>
            </Link>
          </section>
        )}

        {due.length > 0 && (
          <section aria-label="Due for review">
            <div className="mb-2.5 flex items-baseline justify-between">
              <h2 className="flex items-center gap-2 text-base font-semibold text-ink">
                <Clock aria-hidden className="size-4 text-warn" /> Due for review
              </h2>
              <Link to="/learn" className="min-h-11 content-center text-[13px] font-semibold text-accent">
                See all {learning?.due.length}
              </Link>
            </div>
            <ul className="divide-y divide-line overflow-hidden rounded-card border border-line bg-surface">
              {due.slice(0, 3).map((id) => (
                <li key={id}>
                  <Link to={`/learn/${id}`} className="flex min-h-14 items-center gap-3 py-2.5 pr-3.5 pl-4 focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-accent">
                    <Ring
                      value={learning?.concepts.find((c) => c.id === id)?.recentAccuracy}
                      size={30}
                      label="Recent accuracy"
                      tone={topicTone(topicOf(id)).stroke}
                    />
                    <span className="min-w-0 flex-1">
                      <span className="block truncate text-[15px] font-medium text-ink">{name(id)}</span>
                      <span className="block text-[13px] text-subtle">{topicOf(id)}</span>
                    </span>
                    <ChevronRight aria-hidden className="size-4 text-subtle" />
                  </Link>
                </li>
              ))}
            </ul>
          </section>
        )}

        <NewsToday />
        <LatestSession adapterFactory={adapterFactory} />
      </div>
    )
  }

  return (
    <div className="flex flex-col gap-6 py-2">
      {/* D-10 (T50): the greeting is a line; what to do next leads the page. */}
      <header className="flex flex-wrap items-baseline gap-x-3">
        <h1 className="font-display text-2xl font-bold tracking-tight text-ink">
          {greeting(now.getHours())}, {user.displayName.split(/\s+/)[0]}.
        </h1>
        <p className="text-sm text-subtle">{longDate(now)}</p>
      </header>

      <div className="grid gap-4 lg:grid-cols-3">
        <section
          aria-label="Up next"
          className="flex flex-col rounded-card border border-accent/30 bg-accent/[0.07] p-7 shadow-panel lg:col-span-2"
        >
          <h2 className="text-sm font-medium text-accent">Up next</h2>
          {learning === undefined ? null : suggested ? (
            <>
              <p className="mt-2 font-display text-3xl font-bold tracking-tight text-ink">{name(suggested)}</p>
              <p className="mt-2.5 line-clamp-3 max-w-2xl text-base text-subtle">
                {curriculum?.concepts.find((c) => c.concept.id === suggested)?.concept.summary}
              </p>
              <Link
                to={`/learn/${suggested}`}
                className="mt-6 inline-flex h-11 items-center gap-2 self-start rounded-full bg-accent px-6 text-sm font-semibold text-accent-ink shadow-[inset_0_1px_0_rgb(255_255_255/0.18)] hover:brightness-110 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent"
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

        <section aria-label="Practice so far" className="flex flex-col rounded-card border border-line bg-surface p-6 shadow-panel">
          <h2 className="text-sm font-medium text-subtle">Practice so far</h2>
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

      <ol className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
        <StepCard n={1} to="/learn" topic={SECTION_TOPICS.learn} title="Learn" line="ICT's ideas by topic, each with real NQ examples.">
          {learning && (
            <Progress
              tone={topicTone(SECTION_TOPICS.learn).fill}
              value={learning.summary.complete / Math.max(1, learning.summary.concepts)}
              text={`${learning.summary.complete} of ${learning.summary.concepts} lessons complete`}
            />
          )}
        </StepCard>
        <StepCard n={2} to="/drills" topic={SECTION_TOPICS.drills} title="Drills" line="Spot and name patterns on real charts, graded by the rule.">
          {learning && drills.length > 0 && (
            <Progress tone={topicTone(SECTION_TOPICS.drills).fill} value={drillsPassed / drills.length} text={`${drillsPassed} of ${drills.length} drills passed`} />
          )}
        </StepCard>
        <StepCard n={3} to="/practice" topic={SECTION_TOPICS.practice} title="Practice" line="A real day replayed bar by bar, the future hidden.">
          {sessions && (
            <Figure>
              {trades} finished {trades === 1 ? 'trade' : 'trades'}
            </Figure>
          )}
        </StepCard>
        <StepCard n={4} to="/chart" topic={SECTION_TOPICS.chart} title="Chart" line="The whole history, every rule and drawing tool.">
          <Figure>Real NQ, every ICT layer</Figure>
        </StepCard>
      </ol>

      <div className="grid gap-4 lg:grid-cols-2">
        <section aria-label="Due for review" className="rounded-card border border-line bg-surface p-6 shadow-panel">
          <h2 className="flex items-center gap-2 text-sm font-medium text-subtle">
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
                        tone={topicTone(topicOf(id)).stroke}
                      />
                      <span className="min-w-0 flex-1 truncate text-sm text-ink">{name(id)}</span>
                      <ArrowRight aria-hidden className="size-4 text-subtle group-hover:text-ink" />
                    </Link>
                  </li>
                )
              })}
            </ul>
          )}
        </section>

        <NewsToday />
      </div>
      <LatestSession adapterFactory={adapterFactory} />
    </div>
  )
}

/** A figure tile on the phone's Home (T53). */
const TILE =
  'flex min-w-0 flex-col rounded-[14px] border border-line bg-surface px-3.5 py-3 focus-visible:outline-2 focus-visible:outline-accent'

/** A tile's name with its section's picture, so the tiles carry colour before there is any progress (T55). */
function TileLabel({ topic, children }: { topic: string; children: ReactNode }) {
  return (
    <span className="flex items-center gap-1.5 text-[13px] text-subtle">
      <TopicGlyph topic={topic} className={cx('h-3.5 w-5', topicTone(topic).text)} />
      {children}
    </span>
  )
}

function StepCard({
  n,
  to,
  topic,
  title,
  line,
  children,
}: {
  n: number
  to: string
  /** The topic whose colour and picture the step wears (T55). */
  topic: string
  title: string
  line: string
  children?: ReactNode
}) {
  const tone = topicTone(topic)
  return (
    <motion.li
      {...enter(n)}
    >
      <Link
        to={to}
        className={cx(
          'flex h-full flex-col rounded-card border border-line bg-surface p-5 shadow-panel transition-all duration-150 hover:-translate-y-0.5 hover:bg-raised focus-visible:outline-2 focus-visible:outline-accent',
          tone.edge,
        )}
      >
        <span aria-hidden className={cx('mb-4 grid size-10 place-items-center rounded-xl', tone.soft, tone.text)}>
          <TopicGlyph topic={topic} className="h-5 w-7" />
        </span>
        <span className="font-display text-2xl font-bold tracking-tight text-ink">{title}</span>
        <span className="mt-1 text-sm text-subtle">{line}</span>
        <span className="mt-auto pt-5">{children}</span>
      </Link>
    </motion.li>
  )
}

function Progress({ value, text, tone }: { value: number; text: string; tone: string }) {
  return (
    <span className="flex flex-col gap-2">
      <ProgressBar value={value} label={text} tone={tone} />
      <span className="text-xs text-subtle">{text}</span>
    </span>
  )
}

function Figure({ children }: { children: ReactNode }) {
  return <span className="text-xs text-subtle">{children}</span>
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
      className="block rounded-card border border-line bg-surface p-5 shadow-panel transition-all duration-150 hover:-translate-y-0.5 hover:border-accent/40"
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

/** Today's and tomorrow's high-impact US releases, New York time (T40); the week is a click away. */
function NewsToday() {
  const news = useLoaded(getNews)
  const now = useNow().getTime() / 1000
  const days = new Set([newsDay(now), newsDay(now + DAY)])
  const soon = news?.events.filter((e) => e.impact === 'HIGH' && days.has(newsDay(e.at))) ?? []

  return (
    // min-w-0: a grid item is otherwise as wide as its longest title, and the page scrolled sideways on a phone (BUG-112).
    <section aria-label="News today" className="flex min-w-0 flex-col rounded-card border border-line bg-surface p-6 shadow-panel">
      <h2 className="text-sm font-medium text-subtle">News today</h2>
      {/* BUG-134: a calendar never loaded is not a day without news. */}
      {news && news.checkedAt == null && soon.length === 0 && (
        <p className="mt-3 text-sm text-subtle">The economic calendar has not been loaded yet.</p>
      )}
      {news && news.checkedAt != null && soon.length === 0 && (
        <p className="mt-3 text-sm text-subtle">No high-impact news today or tomorrow.</p>
      )}
      {news === null && <p className="mt-3 text-sm text-subtle">The calendar could not be loaded.</p>}
      {soon.length > 0 && (
        <ul className="mt-3 flex flex-col gap-2.5">
          {soon.map((event) => (
            <li key={`${event.key}-${event.at}`} className="flex items-center gap-3 text-sm">
              <span className="num w-24 shrink-0 text-xs text-subtle">
                {newsDay(event.at) === newsDay(now) ? 'Today' : 'Tomorrow'} {newsTime(event.at)}
              </span>
              <ImpactFolder impact={event.impact} />
              <span className="min-w-0 truncate text-ink">{event.title}</span>
            </li>
          ))}
        </ul>
      )}
      {news !== undefined && (
        <Link to="/news" className="mt-auto inline-flex items-center gap-1.5 pt-5 text-sm font-semibold text-accent hover:underline">
          Week ahead <ArrowRight aria-hidden className="size-4" />
        </Link>
      )}
    </section>
  )
}
