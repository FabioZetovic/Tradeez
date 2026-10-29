import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import { motion } from 'motion/react'
import { BookOpen, Check, ChevronRight, Clock, Lock } from 'lucide-react'
import { useIsPhone } from '@/hooks/useIsPhone'
import { getCurriculum, type Curriculum } from '@/lib/api/concepts'
import { getLearning, type ConceptProgress, type Learning } from '@/lib/api/learning'
import { topicTone } from '@/lib/topics'
import { TopicGlyph } from '../TopicGlyph'
import { cx, errorClass } from '@/lib/styles'
import { Chip, NextStep, PageHeader, Ring } from '../ui'
import { enter } from '@/lib/motion'

/**
 * Learn (T45, Lovable's §5.3, DEC-081): ICT's ideas grouped by topic, each topic a short path of lessons in the order
 * they are best learnt, its reference cards with it. Fabio: "trader doesnt even know which one to start" — the first
 * lesson of the recommended topic says "Start here"; "passing the drill unlocks the next session" — a locked lesson
 * says which drill to pass.
 */
export function LearnPage() {
  const [learning, setLearning] = useState<Learning | null>(null)
  const [curriculum, setCurriculum] = useState<Curriculum | null>(null)
  const [error, setError] = useState<string | null>(null)
  const phone = useIsPhone()

  useEffect(() => {
    getLearning()
      .then(setLearning)
      .catch(() => setError('Could not load your lessons. Reload to try again.'))
    getCurriculum()
      .then(setCurriculum)
      .catch(() => setError('Could not load your lessons. Reload to try again.'))
  }, [])

  const name = (id: string) => curriculum?.concepts.find((c) => c.concept.id === id)?.concept.name ?? id
  const progress = new Map(learning?.concepts.map((c) => [c.id, c]))
  // A newcomer: nothing practised or read yet.
  const fresh =
    learning !== null &&
    learning.summary.ratedAttempts + learning.summary.predictions === 0 &&
    learning.concepts.every((c) => c.readAt === undefined)

  if (phone) {
    return <PhoneLearn learning={learning} name={name} fresh={fresh} error={error} />
  }

  return (
    <section aria-label="Learn">
      <PageHeader
        title="Learn"
        description="ICT's ideas grouped by topic — start with the first open lesson."
        right={
          learning && (
            <span className="text-sm text-subtle" data-testid="learn-total">
              <strong className="num text-ink">{learning.summary.complete}</strong> of {learning.summary.concepts} lessons
              done
            </span>
          )
        }
      />
      {error && (
        <p role="alert" className={errorClass}>
          {error}
        </p>
      )}
      {!learning && !error && (
        <p role="status" className="text-sm text-subtle">
          Loading your lessons…
        </p>
      )}

      <div className="flex flex-col gap-10">
        {learning?.topics.map((topic) => {
          const id = `topic-${topic.title.toLowerCase().replace(/[^a-z]+/g, '-')}`
          return (
            <section key={topic.title} aria-labelledby={id}>
              <div className="mb-4 flex items-baseline justify-between gap-4">
                <h2 id={id} className="flex items-center gap-2.5 font-display text-xl font-bold tracking-tight text-ink">
                  <TopicGlyph topic={topic.title} className={cx('h-5 w-7', topicTone(topic.title).text)} />
                  {topic.title}
                </h2>
                <span className="num text-xs text-subtle" data-testid="topic-progress">
                  {topic.complete} of {topic.concepts.length}
                </span>
              </div>
              <ul className="grid grid-cols-1 gap-3 md:grid-cols-2">
                {topic.concepts.map((conceptId, i) => {
                  const p = progress.get(conceptId)
                  if (!p) return null
                  const before = topic.concepts
                    .slice(0, i)
                    .filter((c) => progress.get(c)?.kind === 'LESSON')
                    .at(-1)
                  return (
                    <LessonTile
                      key={conceptId}
                      index={i}
                      progress={p}
                      name={name(conceptId)}
                      next={learning.suggested === conceptId}
                      startHere={fresh && learning.suggested === conceptId}
                      lockedBy={before && name(before)}
                      topic={topic.title}
                    />
                  )
                })}
              </ul>
            </section>
          )
        })}
      </div>

      <NextStep to="/drills" title="Drill what you've learned" line="Spot and name the patterns on real NQ and ES charts." />
    </section>
  )
}

/**
 * One lesson or card (Lovable's): its title, a ring once there is progress (an empty one read as a radio button,
 * BUG-107), and its state — done, due for review, next (an accent border), or locked, saying which drill opens it. No
 * catalogue id on the tile (BUG-105); a locked one stays readable, the lock saying it is locked (BUG-106).
 */
function LessonTile({
  index,
  progress,
  name,
  next,
  startHere,
  lockedBy,
  topic,
}: {
  index: number
  progress: ConceptProgress
  name: string
  next: boolean
  startHere: boolean
  lockedBy?: string
  /** Its topic, whose colour and picture it carries (T55). */
  topic: string
}) {
  const tone = topicTone(topic)
  const done = progress.state === 'SOLID' || progress.state === 'DONE'
  const locked = !progress.open
  const card = progress.kind === 'REFERENCE'
  const ring = done ? 1 : (progress.recentAccuracy ?? 0)
  const why = locked && lockedBy ? `Pass the ${lockedBy} drill to unlock` : undefined
  return (
    <motion.li
      {...enter(index, { step: 0.04 })}
    >
      <Link
        to={`/learn/${progress.id}`}
        title={why}
        data-testid="lesson-tile"
        className={cx(
          'flex h-full items-center gap-4 rounded-card border bg-surface p-4 shadow-panel transition-all duration-150',
          'hover:-translate-y-0.5 hover:bg-raised focus-visible:outline-2 focus-visible:outline-accent',
          next ? 'border-accent' : cx('border-line', tone.edge),
        )}
      >
        {ring > 0 ? (
          <Ring value={ring} size={40} label={done ? 'Complete' : `${Math.round(ring * 100)}% recently`} tone={tone.stroke} />
        ) : (
          // Before any progress, the topic's own picture in its colour (T55) — not an empty ring (BUG-107).
          <span aria-hidden className={cx('grid size-10 shrink-0 place-items-center rounded-xl', tone.soft, tone.text)}>
            <TopicGlyph topic={topic} className="h-5 w-7" />
          </span>
        )}
        <span className="min-w-0 flex-1">
          <span className="block font-semibold text-ink">{name}</span>
          {why && <span className="mt-0.5 block text-xs text-subtle">{why}</span>}
        </span>
        <span className="flex shrink-0 flex-col items-end gap-1">
          {startHere && <Chip tone="accent">Start here</Chip>}
          {card && !done && <Chip title="A reference page without a drill: reading it counts as done">Read only</Chip>}
          {locked ? (
            <Lock aria-label="Locked" className="size-4 text-subtle" />
          ) : progress.due ? (
            <Chip tone="caution">
              <Clock aria-hidden className="mr-1 size-3" /> Review
            </Chip>
          ) : done ? (
            <Chip tone="good" aria-label="Done">
              <Check aria-hidden className="size-3.5" /> Done
            </Chip>
          ) : next && !startHere ? (
            <Chip tone="accent">Next</Chip>
          ) : null}
        </span>
      </Link>
    </motion.li>
  )
}

/** A topic's name short enough for a chip ("Gaps & imbalances" → "Gaps"). */
const short = (title: string) => title.split(/ & | /)[0]

/**
 * Learn on a phone (T53, Claude Design): how far along, chips to keep to one topic, and a row a lesson with a circle
 * saying where it stands — open, under way, done, locked or read only — and what opens it or what is due.
 */
function PhoneLearn({
  learning,
  name,
  fresh,
  error,
}: {
  learning: Learning | null
  name: (id: string) => string
  fresh: boolean
  error: string | null
}) {
  const [topic, setTopic] = useState('All')
  const progress = new Map(learning?.concepts.map((c) => [c.id, c]))
  const total = learning?.summary.concepts ?? 0
  const complete = learning?.summary.complete ?? 0
  const due = learning?.due.length ?? 0
  return (
    <section aria-label="Learn" className="flex flex-col">
      <h1 className="sr-only">Learn</h1>
      {error && (
        <p role="alert" className={errorClass}>
          {error}
        </p>
      )}
      {!learning && !error && (
        <p role="status" className="text-sm text-subtle">
          Loading your lessons…
        </p>
      )}
      {learning && (
        <>
          <div>
            <p className="mb-2 flex justify-between text-[13px]">
              <span data-testid="learn-total">
                <b className="num text-ink">{complete}</b> <span className="text-subtle">of {total} lessons done</span>
              </span>
              {due > 0 && <span className="font-semibold text-warn">{due} due for review</span>}
            </p>
            <div className="h-1.5 overflow-hidden rounded-full bg-line">
              <div className="h-full rounded-full bg-accent" style={{ width: `${(complete / Math.max(1, total)) * 100}%` }} />
            </div>
          </div>
          <nav aria-label="Topics" className="-mx-4 mt-3.5 flex gap-2 overflow-x-auto border-y border-line px-4 py-2.5 [scrollbar-width:none]">
            {['All', ...learning.topics.map((t) => t.title)].map((title) => {
              const t = learning.topics.find((x) => x.title === title)
              return (
                <button
                  key={title}
                  type="button"
                  aria-pressed={topic === title}
                  onClick={() => setTopic(title)}
                  className={cx(
                    'inline-flex h-9 shrink-0 cursor-pointer items-center gap-1.5 rounded-full border px-3.5 text-[13px] font-medium whitespace-nowrap',
                    'focus-visible:outline-2 focus-visible:outline-accent',
                    topic === title ? 'border-accent/60 bg-accent/20 text-ink' : 'border-line bg-surface text-subtle',
                  )}
                >
                  {t ? short(title) : title}
                  {t && (
                    <span className="num text-subtle">
                      {t.complete}/{t.concepts.length}
                    </span>
                  )}
                </button>
              )
            })}
          </nav>
          <div className="mt-5 flex flex-col gap-6">
            {learning.topics
              .filter((t) => topic === 'All' || t.title === topic)
              .map((t) => {
                const id = `phone-topic-${t.title.toLowerCase().replace(/[^a-z]+/g, '-')}`
                return (
                  <section key={t.title} aria-labelledby={id}>
                    <div className="mb-2.5 flex items-baseline justify-between">
                      <h2 id={id} className="flex items-center gap-2 text-[17px] font-semibold text-ink">
                        <TopicGlyph topic={t.title} className={cx('h-4 w-6', topicTone(t.title).text)} />
                        {t.title}
                      </h2>
                      <span className="num text-[13px] text-subtle" data-testid="topic-progress">
                        {t.complete} of {t.concepts.length}
                      </span>
                    </div>
                    <ul className="divide-y divide-line overflow-hidden rounded-card border border-line bg-surface">
                      {t.concepts.map((conceptId, i) => {
                        const p = progress.get(conceptId)
                        if (!p) return null
                        const before = t.concepts
                          .slice(0, i)
                          .filter((c) => progress.get(c)?.kind === 'LESSON')
                          .at(-1)
                        return (
                          <LessonRow
                            key={conceptId}
                            progress={p}
                            name={name(conceptId)}
                            next={learning.suggested === conceptId}
                            startHere={fresh && learning.suggested === conceptId}
                            lockedBy={before && name(before)}
                            topic={t.title}
                          />
                        )
                      })}
                    </ul>
                  </section>
                )
              })}
          </div>
        </>
      )}
    </section>
  )
}

/** One lesson as a row: its circle, its name, one line of where it stands, and a badge when something is due. */
function LessonRow({
  progress: p,
  name,
  next,
  startHere,
  lockedBy,
  topic,
}: {
  progress: ConceptProgress
  name: string
  next: boolean
  startHere: boolean
  lockedBy?: string
  topic: string
}) {
  const tone = topicTone(topic)
  const done = p.state === 'SOLID' || p.state === 'DONE'
  const locked = !p.open
  const card = p.kind === 'REFERENCE'
  const under = !done && p.attempts > 0
  const status = done ? 'Done' : locked ? 'Locked' : card ? 'Read only' : under ? 'In progress' : 'Open'
  const line = locked
    ? `Unlocks after the ${lockedBy ?? 'previous'} drill`
    : p.due
      ? 'Review due'
      : card
        ? 'No drill · reading it counts'
        : under
          ? `${p.attempts} ${p.attempts === 1 ? 'try' : 'tries'} · ${Math.round((p.recentAccuracy ?? 0) * 100)}% recently`
          : done
            ? 'Passed'
            : startHere
              ? 'Start here'
              : next
                ? 'Next'
                : 'Not started'
  return (
    <li>
      <Link
        to={`/learn/${p.id}`}
        data-testid="lesson-tile"
        className={cx(
          'flex min-h-14 items-center gap-3 py-2.5 pr-3.5 pl-4 focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-accent',
          next && 'bg-accent/10',
        )}
      >
        <span
          role="img"
          aria-label={status}
          className={cx(
            'grid size-[30px] shrink-0 place-items-center rounded-full',
            done && cx(tone.fill, 'text-canvas'),
            locked && 'bg-sunken text-subtle',
            card && !locked && !done && 'bg-sunken text-subtle',
            // The circle in its topic's colour (T55): a quarter turn under way, whole when open.
            under && !locked && cx('border-[2.5px] border-line border-t-current border-r-current', tone.text),
            !done && !locked && !card && !under && cx('border-[2.5px] border-current', tone.text),
          )}
        >
          {done && <Check aria-hidden className="size-4" />}
          {locked && <Lock aria-hidden className="size-3.5" />}
          {card && !locked && !done && <BookOpen aria-hidden className="size-3.5" />}
        </span>
        <span className="min-w-0 flex-1">
          <span className="block text-[15px] leading-snug font-medium text-ink">{name}</span>
          <span className="mt-0.5 block text-[13px] text-subtle">{line}</span>
        </span>
        {/* A locked lesson offers no review it cannot open: the lock is the news. */}
        {p.due && !locked ? (
          <span className="rounded-full bg-warn/15 px-2 py-0.5 text-[11px] font-semibold text-warn">Review</span>
        ) : card && !done ? (
          <span className="rounded-full bg-sunken px-2 py-0.5 text-[11px] font-semibold text-subtle">Read only</span>
        ) : (
          !locked && <ChevronRight aria-hidden className="size-4 text-subtle" />
        )}
      </Link>
    </li>
  )
}
