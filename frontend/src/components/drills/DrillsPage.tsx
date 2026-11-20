import { useEffect, useState, type ReactNode } from 'react'
import { Link } from 'react-router'
import { motion } from 'motion/react'
import { ArrowRight, Clock } from 'lucide-react'
import { listDrillKinds, listRiskKinds, RISK_KINDS, type DrillKindView, type RiskKindView } from '@/lib/api/drills'
import { MODE_NAMES } from '@/lib/drills/modes'
import { getLearning, type ConceptProgress } from '@/lib/api/learning'
import { getCurriculum, type Curriculum } from '@/lib/api/concepts'
import { topicTone } from '@/lib/topics'
import { TopicGlyph } from '../TopicGlyph'
import { cx, errorClass } from '@/lib/styles'
import { drillMarket, MARKETS, setDrillMarket, type Market } from '@/lib/markets'
import { Chip, NextStep, PageHeader, Segmented } from '../ui'
import { enter } from '@/lib/motion'
import { useIsPhone } from '@/hooks/useIsPhone'
import type { DrillMode } from '@/lib/api/drills'

/**
 * Drills (T43, Lovable's §5.5): sections of cards, one click to start. Each card carries the learner's real record — from
 * the curriculum's progress for a chart drill, from the server's tally for a risk one. The sections are Learn's topics
 * (T45), each drill under its lesson's; the risk questions go with Risk & management. A drill whose concept no topic
 * holds still gets a card, under "More drills": every drill stays reachable.
 */
export function DrillsPage() {
  const [kinds, setKinds] = useState<DrillKindView[] | null>(null)
  const [risk, setRisk] = useState<RiskKindView[]>([])
  const [concepts, setConcepts] = useState<Map<string, ConceptProgress>>(new Map())
  const [suggested, setSuggested] = useState<string | undefined>()
  const phone = useIsPhone()
  const [curriculum, setCurriculum] = useState<Curriculum | null>(null)
  // T47: the market the drills are asked on, kept for the drill screens too.
  const [market, setMarket] = useState<Market>(drillMarket)
  const chooseMarket = (next: Market) => {
    setMarket(next)
    setDrillMarket(next)
  }
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    listDrillKinds()
      .then(setKinds)
      .catch(() => setError('Could not load the list of drills. Reload to try again.'))
    // The records are extra: without them the cards still start their drills.
    listRiskKinds()
      .then(setRisk)
      .catch(() => {})
    getLearning()
      .then((learning) => {
        setConcepts(new Map(learning.concepts.map((c) => [c.id, c])))
        setSuggested(learning.suggested)
      })
      .catch(() => {})
    getCurriculum()
      .then(setCurriculum)
      .catch(() => {})
  }, [])

  // Each topic's drills in the order its lessons are taught; then the drills of concepts no topic holds.
  const order = new Map((curriculum?.topics ?? []).flatMap((t) => t.concepts).map((id, i) => [id, i]))
  const topicOf = (conceptId: string) => curriculum?.topics.find((t) => t.concepts.includes(conceptId))?.title
  const sections = [
    ...(curriculum?.topics ?? []).map((topic) => ({
      title: topic.title,
      drills: (kinds ?? [])
        .filter((k) => topicOf(k.conceptId) === topic.title)
        .sort((x, y) => (order.get(x.conceptId) ?? 0) - (order.get(y.conceptId) ?? 0)),
    })),
    { title: 'More drills', drills: (kinds ?? []).filter((k) => topicOf(k.conceptId) === undefined) },
  ].filter((section) => section.drills.length > 0 || (kinds !== null && section.title === RISK_TOPIC))
  if (kinds !== null && !sections.some((s) => s.title === RISK_TOPIC)) sections.push({ title: RISK_TOPIC, drills: [] })

  if (phone) {
    return (
      <PhoneDrills
        sections={sections}
        concepts={concepts}
        risk={risk}
        next={(kinds ?? []).find((k) => k.conceptId === suggested)}
        market={market}
        onMarket={chooseMarket}
        error={error}
        loading={kinds === null && !error}
      />
    )
  }

  return (
    <section aria-label="Drills">
      <PageHeader
        title="Drills"
        description={`Short exercises on one idea at a time, on real ${market}.`}
        right={<Segmented<Market> options={MARKETS} value={market} onChange={chooseMarket} label="Market" />}
      />
      {error && (
        <p role="alert" className={errorClass}>
          {error}
        </p>
      )}
      {kinds === null && !error && (
        <p role="status" className="text-sm text-subtle">
          Loading the drills…
        </p>
      )}

      {/* One panel, as on Home (Fabio, 2026-11-20): the topics are its rows, the drills its cells, divided by hairlines
          on the page's own background. */}
      <div data-testid="drills-panel" className="divide-y divide-line overflow-hidden rounded-xl border border-line">
        {sections.map(({ title, drills }) => (
          <Topic key={title} title={title}>
            {drills.map((kind, i) => (
              <Card
                key={kind.kind}
                index={i}
                to={`/drills/${kind.kind}`}
                title={kind.title}
                line={kind.instructions}
                badge={MODE_NAMES[kind.mode]}
                time="~2–3 min"
                topic={title}
                record={record(concepts.get(kind.conceptId))}
              />
            ))}
            {title === RISK_TOPIC &&
              RISK_KINDS.map((kind, i) => {
              const tally = risk.find((r) => r.kind === kind.value)
              return (
                <Card
                  key={kind.value}
                  index={i}
                  to={`/drills/risk/${kind.slug}`}
                  title={kind.label}
                  line={kind.line}
                  badge="Sums"
                  time={tally ? `${tally.limitSeconds} s each` : undefined}
                  topic={title}
                  record={tally && (tally.answered === 0 ? 'Not tried yet' : `${tally.right} of ${tally.answered} right`)}
                />
              )
            })}
          </Topic>
        ))}
      </div>

      <NextStep to="/practice" title="Put it together in Practice" line="Plan a trade on a replayed day." />
    </section>
  )
}

/** Where the risk questions live: with the lesson they practise, risk rules and drawdown (DEC-081). */
const RISK_TOPIC = 'Risk & management'

/**
 * A chart drill's record is its lesson's (S-2, learning-1.1.0): every drill of the lesson answered, and the recent
 * accuracy over them — what passing the lesson is measured on.
 */
function record(concept?: ConceptProgress): string | undefined {
  if (!concept) return undefined
  if (concept.attempts === 0) return 'Not tried yet'
  const tries = `${concept.attempts} ${concept.attempts === 1 ? 'try' : 'tries'}`
  return concept.recentAccuracy === undefined ? tries : `${tries} · ${Math.round(concept.recentAccuracy * 100)}% recently`
}

function Topic({ title, children }: { title: string; children: ReactNode }) {
  const id = `topic-${title.toLowerCase().replace(/[^a-z]+/g, '-')}`
  return (
    <section aria-labelledby={id}>
      <h2 id={id} className="flex items-center gap-2.5 px-7 py-5 font-display text-xl font-bold tracking-tight text-ink">
        <TopicGlyph topic={title} className={cx('h-5 w-7', topicTone(title).text)} />
        {title}
      </h2>
      {/* Each drill draws the line on its right and below; the pixel the list reaches past the panel hides the last
          column's and last row's, so a row left short ends in blank page, not a grey block. */}
      <ul className="-mr-px -mb-px grid grid-cols-1 border-t border-line md:grid-cols-2 xl:grid-cols-3">{children}</ul>
    </section>
  )
}

function Card({
  index,
  to,
  title,
  line,
  badge,
  time,
  record,
  topic,
}: {
  index: number
  to: string
  title: string
  line: string
  badge: string
  time?: string
  record?: string
  /** Its lesson's topic, whose colour and picture it carries (T55). */
  topic: string
}) {
  const tone = topicTone(topic)
  return (
    <motion.li
      {...enter(index, { step: 0.04 })}
      className="border-r border-b border-line"
    >
      <Link
        to={to}
        className="flex h-full flex-col bg-canvas p-7 transition-colors duration-150 hover:bg-surface focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-accent"
      >
        <span className="flex items-center gap-2.5">
          <span aria-hidden className={cx('grid size-9 shrink-0 place-items-center rounded-lg', tone.soft, tone.text)}>
            <TopicGlyph topic={topic} className="h-4 w-6" />
          </span>
          <Chip tone="accent">{badge}</Chip>
          {time && (
            <span className="ml-auto flex items-center gap-1 text-xs text-subtle">
              <Clock aria-hidden className="size-3.5" />
              {time}
            </span>
          )}
        </span>
        <span className="mt-4 font-display text-lg font-semibold tracking-tight text-ink">{title}</span>
        <span className="mt-1 line-clamp-2 text-sm text-subtle" title={line}>
          {line}
        </span>
        <span className="mt-auto pt-4 text-xs text-subtle" data-testid="drill-record">
          {record ?? ' '}
        </span>
      </Link>
    </motion.li>
  )
}

/** The phone's filter: a drill's kind, or the sums. */
type Filter = 'All' | DrillMode | 'SUMS'
const FILTERS: { value: Filter; label: string }[] = [
  { value: 'IDENTIFY', label: MODE_NAMES.IDENTIFY },
  { value: 'CLASSIFY', label: MODE_NAMES.CLASSIFY },
  { value: 'PREDICT', label: MODE_NAMES.PREDICT },
  { value: 'JUDGE', label: MODE_NAMES.JUDGE },
  { value: 'MANAGE', label: MODE_NAMES.MANAGE },
  { value: 'SUMS', label: 'Sums' },
]

/** Passed, as a lesson is (learning-1.1.0): its drills answered enough, at 70% or better recently. */
const passed = (concept?: ConceptProgress) => concept?.state === 'SOLID' || concept?.state === 'DONE'

/** A recent accuracy in the colour of where it stands against the 70% a lesson needs. */
function Percent({ value }: { value?: number }) {
  if (value === undefined) return <span className="text-xs text-subtle">New</span>
  const percent = Math.round(value * 100)
  return (
    <span className={cx('num text-sm', percent >= 70 ? 'text-up' : percent >= 40 ? 'text-warn' : 'text-down')}>{percent}%</span>
  )
}

/**
 * Drills on a phone (T53, Claude Design): chips to filter by kind, the drill that opens the next lesson first, then a row
 * per drill under its topic — its kind, its name, the record, the recent accuracy.
 */
function PhoneDrills({
  sections,
  concepts,
  risk,
  next,
  market,
  onMarket,
  error,
  loading,
}: {
  sections: { title: string; drills: DrillKindView[] }[]
  concepts: Map<string, ConceptProgress>
  risk: RiskKindView[]
  next?: DrillKindView
  market: Market
  onMarket: (market: Market) => void
  error: string | null
  loading: boolean
}) {
  const [filter, setFilter] = useState<Filter>('All')
  const total = sections.reduce((n, s) => n + s.drills.length, 0) + RISK_KINDS.length
  const shows = (mode: Filter) => filter === 'All' || filter === mode
  const nextConcept = next && concepts.get(next.conceptId)

  return (
    <section aria-label="Drills" className="flex flex-col">
      <div className="flex items-center justify-between gap-3">
        <h1 className="sr-only">Drills</h1>
        <p className="text-sm text-subtle">Short exercises on one idea at a time, on real {market}. About 2–3 minutes each.</p>
        <div className="shrink-0">
          <Segmented<Market> options={MARKETS} value={market} onChange={onMarket} label="Market" />
        </div>
      </div>
      <nav aria-label="Drill kind" className="-mx-4 mt-3 flex gap-2 overflow-x-auto border-y border-line px-4 py-2.5 [scrollbar-width:none]">
        {[{ value: 'All' as Filter, label: `All ${total}` }, ...FILTERS].map((option) => (
          <button
            key={option.value}
            type="button"
            aria-pressed={filter === option.value}
            onClick={() => setFilter(option.value)}
            className={cx(
              'h-9 shrink-0 cursor-pointer rounded-full border px-3.5 text-[13px] font-medium whitespace-nowrap',
              'focus-visible:outline-2 focus-visible:outline-accent',
              filter === option.value ? 'border-accent/60 bg-accent/20 text-ink' : 'border-line bg-surface text-subtle',
            )}
          >
            {option.label}
          </button>
        ))}
      </nav>
      {error && (
        <p role="alert" className={cx(errorClass, 'mt-4')}>
          {error}
        </p>
      )}
      {loading && (
        <p role="status" className="mt-4 text-sm text-subtle">
          Loading the drills…
        </p>
      )}

      <div className="mt-5 flex flex-col gap-6">
        {next && shows(next.mode) && (
          <Link
            to={`/drills/${next.kind}`}
            className="flex items-center gap-3.5 rounded-card border border-accent/30 bg-accent/10 p-4 focus-visible:outline-2 focus-visible:outline-accent"
          >
            <span className="min-w-0 flex-1">
              <span className="block text-xs font-semibold tracking-wide text-accent uppercase">Unlocks your next lesson</span>
              <span className="mt-1.5 block text-lg font-semibold text-ink">{next.title}</span>
              <span className="mt-0.5 block text-sm text-subtle">
                {MODE_NAMES[next.mode]} · {record(nextConcept) ?? 'Not tried yet'} · need 70%
              </span>
            </span>
            <span aria-hidden className="grid size-12 shrink-0 place-items-center rounded-full bg-accent text-accent-ink">
              <ArrowRight className="size-5" />
            </span>
          </Link>
        )}

        {sections.map(({ title, drills }) => {
          const rows = drills.filter((d) => shows(d.mode))
          const sums = title === RISK_TOPIC && shows('SUMS') ? RISK_KINDS : []
          if (rows.length + sums.length === 0) return null
          const id = `phone-topic-${title.toLowerCase().replace(/[^a-z]+/g, '-')}`
          return (
            <section key={title} aria-labelledby={id}>
              <div className="mb-2.5 flex items-baseline justify-between">
                <h2 id={id} className="flex items-center gap-2 text-[17px] font-semibold text-ink">
                  <TopicGlyph topic={title} className={cx('h-4 w-6', topicTone(title).text)} />
                  {title}
                </h2>
                <span className="num text-[13px] text-subtle">
                  {drills.filter((d) => passed(concepts.get(d.conceptId))).length} of {drills.length} passed
                </span>
              </div>
              <ul className="divide-y divide-line overflow-hidden rounded-card border border-line bg-surface">
                {rows.map((kind) => {
                  const concept = concepts.get(kind.conceptId)
                  return (
                    <DrillRow
                      key={kind.kind}
                      to={`/drills/${kind.kind}`}
                      badge={MODE_NAMES[kind.mode]}
                      title={kind.title}
                      line={record(concept) ?? ' '}
                      value={concept && concept.attempts > 0 ? concept.recentAccuracy : undefined}
                    />
                  )
                })}
                {sums.map((kind) => {
                  const tally = risk.find((t) => t.kind === kind.value)
                  return (
                    <DrillRow
                      key={kind.value}
                      to={`/drills/risk/${kind.slug}`}
                      badge="Sums"
                      sums
                      title={kind.label}
                      line={
                        tally
                          ? `${tally.limitSeconds} s each · ${tally.answered === 0 ? 'not tried yet' : `${tally.right} of ${tally.answered} right`}`
                          : kind.line
                      }
                      value={tally && tally.answered > 0 ? tally.right / tally.answered : undefined}
                    />
                  )
                })}
              </ul>
            </section>
          )
        })}
      </div>
    </section>
  )
}

function DrillRow({
  to,
  badge,
  sums = false,
  title,
  line,
  value,
}: {
  to: string
  badge: string
  sums?: boolean
  title: string
  line: string
  value?: number
}) {
  return (
    <li>
      <Link
        to={to}
        className="flex min-h-14 items-center gap-3 py-2.5 pr-3.5 pl-4 focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-accent"
      >
        <span
          className={cx(
            'w-[62px] shrink-0 rounded-md py-0.5 text-center text-[11px] font-semibold',
            sums ? 'bg-sky-400/10 text-sky-300' : 'bg-accent/15 text-accent',
          )}
        >
          {badge}
        </span>
        <span className="min-w-0 flex-1">
          <span className="block text-[15px] leading-snug font-medium text-ink">{title}</span>
          <span className="mt-0.5 block text-[13px] text-subtle" data-testid="drill-record">
            {line}
          </span>
        </span>
        <Percent value={value} />
      </Link>
    </li>
  )
}
