import { useEffect, useState, type ReactNode } from 'react'
import { Link } from 'react-router'
import { motion } from 'motion/react'
import { Clock, type LucideIcon } from 'lucide-react'
import { listDrillKinds, listRiskKinds, RISK_KINDS, type DrillKindView, type RiskKindView } from '@/lib/api/drills'
import { MODE_NAMES } from '@/lib/drills/modes'
import { getLearning, type ConceptProgress } from '@/lib/api/learning'
import { getCurriculum, type Curriculum } from '@/lib/api/concepts'
import { topicIcon } from '@/lib/topics'
import { cx, errorClass } from '@/lib/styles'
import { drillMarket, MARKETS, setDrillMarket, type Market } from '@/lib/markets'
import { Chip, NextStep, PageHeader, Segmented } from '../ui'

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
      .then((learning) => setConcepts(new Map(learning.concepts.map((c) => [c.id, c]))))
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

      <div className="flex flex-col gap-10">
        {sections.map(({ title, drills }) => (
          <Topic key={title} title={title} icon={topicIcon(title)}>
            {drills.map((kind, i) => (
              <Card
                key={kind.kind}
                index={i}
                to={`/drills/${kind.kind}`}
                title={kind.title}
                line={kind.instructions}
                badge={MODE_NAMES[kind.mode]}
                time="~2–3 min"
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

function Topic({
  title,
  icon: Icon,
  children,
}: {
  title: string
  icon: LucideIcon
  children: ReactNode
}) {
  const id = `topic-${title.toLowerCase().replace(/[^a-z]+/g, '-')}`
  return (
    <section aria-labelledby={id}>
      <h2 id={id} className="mb-4 flex items-center gap-2.5 font-display text-xl font-bold tracking-tight text-ink">
        <Icon aria-hidden className="size-4 text-subtle" />
        {title}
      </h2>
      <ul className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-3">{children}</ul>
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
}: {
  index: number
  to: string
  title: string
  line: string
  badge: string
  time?: string
  record?: string
}) {
  return (
    <motion.li
      initial={{ opacity: 0, y: 8 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.2, ease: 'easeOut', delay: index * 0.04 }}
    >
      <Link
        to={to}
        className={cx(
          'flex h-full flex-col rounded-card border border-line bg-surface p-5 shadow-panel transition-all duration-150',
          'hover:-translate-y-0.5 hover:border-accent/40 hover:bg-raised',
          'focus-visible:outline-2 focus-visible:outline-accent',
        )}
      >
        <span className="flex items-center justify-between gap-2">
          <Chip tone="accent">{badge}</Chip>
          {time && (
            <span className="flex items-center gap-1 text-xs text-subtle">
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
