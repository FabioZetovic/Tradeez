import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import { motion } from 'motion/react'
import { Check, Clock, Lock } from 'lucide-react'
import { getCurriculum, type Curriculum } from '@/lib/api/concepts'
import { getLearning, type ConceptProgress, type Learning } from '@/lib/api/learning'
import { topicIcon } from '@/lib/topics'
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
          const Icon = topicIcon(topic.title)
          const id = `topic-${topic.title.toLowerCase().replace(/[^a-z]+/g, '-')}`
          return (
            <section key={topic.title} aria-labelledby={id}>
              <div className="mb-4 flex items-baseline justify-between gap-4">
                <h2 id={id} className="flex items-center gap-2.5 font-display text-xl font-bold tracking-tight text-ink">
                  <Icon aria-hidden className="size-4 text-subtle" />
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
}: {
  index: number
  progress: ConceptProgress
  name: string
  next: boolean
  startHere: boolean
  lockedBy?: string
}) {
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
          next ? 'border-accent' : 'border-line hover:border-accent/40',
        )}
      >
        {ring > 0 && <Ring value={ring} size={40} label={done ? 'Complete' : `${Math.round(ring * 100)}% recently`} />}
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
