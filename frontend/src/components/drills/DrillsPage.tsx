import { useEffect, useState, type ComponentType, type ReactNode } from 'react'
import { Link } from 'react-router'
import { motion } from 'motion/react'
import { Briefcase, Calculator, ChartCandlestick, Clock, Droplets, GitCompareArrows, Shapes, Waypoints } from 'lucide-react'
import { listDrillKinds, listRiskKinds, RISK_KINDS, type DrillKindView, type RiskKindView } from '@/lib/api/drills'
import { MODE_NAMES } from '@/lib/drills/modes'
import { getLearning, type ConceptProgress } from '@/lib/api/learning'
import { cx, errorClass } from '@/lib/styles'
import { Chip, NextStep, PageHeader } from '../ui'

/**
 * The topics, in the order they build on one another, each holding its drills (docs/REDESIGN.md T43). A kind the server
 * lists and no topic names still gets a card, under "More drills": every drill stays reachable. T44/T45 may regroup them
 * to Learn's topics.
 */
const TOPICS: Array<{ title: string; icon: ComponentType<{ className?: string }>; kinds: string[] }> = [
  { title: 'Gaps & imbalances', icon: ChartCandlestick, kinds: ['FVG_IDENTIFICATION', 'VI_IDENTIFICATION'] },
  {
    title: 'Structure',
    icon: Waypoints,
    kinds: [
      'SWING_HIGH_IDENTIFICATION',
      'SWING_LOW_IDENTIFICATION',
      'DISPLACEMENT_IDENTIFICATION',
      'STRUCTURE_CLASSIFICATION',
      'BOS_CHOCH_CLASSIFICATION',
    ],
  },
  {
    title: 'Liquidity',
    icon: Droplets,
    kinds: [
      'EQHL_IDENTIFICATION',
      'SWEEP_IDENTIFICATION',
      'SWEEP_RUN_CLASSIFICATION',
      'LIQUIDITY_FIRST_PREDICTION',
      'ERL_IRL_PREDICTION',
    ],
  },
  { title: 'SMT', icon: GitCompareArrows, kinds: ['SMT_IDENTIFICATION'] },
  { title: 'Trade management', icon: Briefcase, kinds: ['OPEN_TRADE_MANAGEMENT'] },
]

/**
 * Drills (T43, Lovable's §5.5): sections of cards, one click to start. Each card carries the learner's real record — from
 * the curriculum's progress for a chart drill, from the server's tally for a risk one.
 */
export function DrillsPage() {
  const [kinds, setKinds] = useState<DrillKindView[] | null>(null)
  const [risk, setRisk] = useState<RiskKindView[]>([])
  const [concepts, setConcepts] = useState<Map<string, ConceptProgress>>(new Map())
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
  }, [])

  const listed = new Set(TOPICS.flatMap((t) => t.kinds))
  const sections = [
    ...TOPICS.map((topic) => ({
      ...topic,
      drills: topic.kinds.flatMap((id) => kinds?.find((k) => k.kind === id) ?? []),
    })),
    { title: 'More drills', icon: Shapes, drills: kinds?.filter((k) => !listed.has(k.kind)) ?? [] },
  ].filter((section) => section.drills.length > 0)

  return (
    <section aria-label="Drills">
      <PageHeader eyebrow="Step 2" title="Drills" description="Short exercises on one idea at a time, on real NQ." />
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
        {sections.map(({ title, icon, drills }) => (
          <Topic key={title} title={title} icon={icon}>
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
          </Topic>
        ))}
        {kinds !== null && (
          <Topic title="Risk" icon={Calculator}>
            {RISK_KINDS.map((kind, i) => {
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
        )}
      </div>

      <NextStep to="/practice" title="Put it together in Practice" line="Plan a trade on a replayed day." />
    </section>
  )
}

/**
 * A chart drill's record is its concept's (S-2): the graded tries and the recent accuracy for a rated one; for a
 * prediction or a managed trade, how many were answered — practice, never a rating (DEC-059).
 */
function record(concept?: ConceptProgress): string | undefined {
  if (!concept) return undefined
  if (concept.rated) {
    if (concept.attempts === 0) return 'Not tried yet'
    const tries = `${concept.attempts} ${concept.attempts === 1 ? 'try' : 'tries'}`
    return concept.recentAccuracy === undefined ? tries : `${tries} · ${Math.round(concept.recentAccuracy * 100)}% recently`
  }
  return concept.practised === 0 ? 'Not tried yet' : `${concept.practised} answered`
}

function Topic({
  title,
  icon: Icon,
  children,
}: {
  title: string
  icon: ComponentType<{ className?: string }>
  children: ReactNode
}) {
  const id = `topic-${title.toLowerCase().replace(/[^a-z]+/g, '-')}`
  return (
    <section aria-labelledby={id}>
      <h2 id={id} className="mb-4 flex items-center gap-2.5 font-display text-xl font-bold tracking-tight text-ink">
        <span aria-hidden className="grid size-8 place-items-center rounded-lg bg-accent/15 text-accent">
          <Icon className="size-4" />
        </span>
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
        <span className="num mt-auto pt-4 text-xs text-subtle" data-testid="drill-record">
          {record ?? ' '}
        </span>
      </Link>
    </motion.li>
  )
}
