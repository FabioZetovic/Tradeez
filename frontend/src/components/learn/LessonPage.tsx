import { useEffect, useState, type ReactNode } from 'react'
import { Link, Navigate } from 'react-router'
import { ArrowLeft, Lock } from 'lucide-react'
import { EvidencePanel } from './EvidencePanel'
import { ExampleChart } from './ExampleChart'
import { ApiError } from '@/lib/api/client'
import { getCurriculum, type Curriculum } from '@/lib/api/concepts'
import { RISK_KINDS } from '@/lib/api/drills'
import { getLearning, markRead, type Learning } from '@/lib/api/learning'
import { getLesson, LESSON_TIMEFRAMES, type Lesson } from '@/lib/api/lessons'
import type { ChartAdapter } from '@/lib/chart/types'
import { MODE_NAMES } from '@/lib/drills/modes'
import { errorClass, panelTitle } from '@/lib/styles'
import { Button, Notice, Panel, Segmented } from '../ui'

/** The risk questions practise ICT-83 (DEC-081): its lesson offers them as its drills. */
const RISK_CONCEPT = 'ICT-83'

/**
 * One lesson (T45, Lovable's §5.4): its words in the template — what it is, how to spot it, how it is traded — then real
 * NQ examples, the rule folded, what the data says, and the drill that passes it. A locked lesson opened by its
 * address shows its summary and which drill to pass, not the lesson. A merged concept's address leads to the lesson it
 * is part of.
 */
export function LessonPage({ id, adapterFactory }: { id: string; adapterFactory?: () => ChartAdapter }) {
  const [lesson, setLesson] = useState<Lesson | null>(null)
  const [learning, setLearning] = useState<Learning | null>(null)
  const [curriculum, setCurriculum] = useState<Curriculum | null>(null)
  const [timeframe, setTimeframe] = useState('5m')
  const [round, setRound] = useState(0)
  // The lesson asked for; it is loading until the answer to exactly this request is in.
  const asked = `${id}|${timeframe}|${round}`
  const [answered, setAnswered] = useState<string | null>(null)
  const busy = asked !== answered
  const [error, setError] = useState<string | null>(null)
  const [marking, setMarking] = useState(false)

  useEffect(() => {
    getLearning()
      .then(setLearning)
      .catch(() => {})
    getCurriculum()
      .then(setCurriculum)
      .catch(() => {})
  }, [])

  useEffect(() => {
    let current = true
    getLesson(id, timeframe)
      .then((next) => {
        if (!current) return
        setLesson(next)
        setError(null)
        setAnswered(asked)
      })
      .catch((e: unknown) => {
        if (!current) return
        const body = e instanceof ApiError ? (e.body as { message?: string } | null) : null
        setError(body?.message ?? 'Could not load this lesson. Try again.')
        setAnswered(asked)
      })
    return () => {
      current = false
    }
  }, [asked, id, timeframe])

  const read = async () => {
    setMarking(true)
    try {
      setLearning(await markRead(id))
    } catch {
      setError('Could not mark this lesson as read. Try again.')
    } finally {
      setMarking(false)
    }
  }

  if (lesson?.kind === 'MERGED' && lesson.into) return <Navigate to={`/learn/${lesson.into}`} replace />

  const progress = learning?.concepts.find((c) => c.id === id)
  const name = (conceptId: string) => curriculum?.concepts.find((c) => c.concept.id === conceptId)?.concept.name ?? conceptId
  const topic = learning?.topics.find((t) => t.concepts.includes(id))
  const before = topic?.concepts
    .slice(0, topic.concepts.indexOf(id))
    .filter((c) => learning?.concepts.find((p) => p.id === c)?.kind === 'LESSON')
    .at(-1)
  const locked = progress !== undefined && !progress.open
  const done = progress?.state === 'SOLID' || progress?.state === 'DONE'

  return (
    <article aria-label={lesson ? `Lesson: ${lesson.name}` : 'Lesson'} className="mx-auto flex max-w-4xl flex-col gap-6">
      <Link to="/learn" className="inline-flex items-center gap-1.5 self-start text-sm font-medium text-subtle hover:text-ink">
        <ArrowLeft aria-hidden className="size-4" />
        Learn
      </Link>
      {error && (
        <p role="alert" className={errorClass}>
          {error}
        </p>
      )}
      {!lesson && !error && (
        <p role="status" className="text-sm text-subtle">
          Loading…
        </p>
      )}
      {lesson && (
        <>
          <header>
            <p className="num mb-2 text-sm text-accent">
              {lesson.topic ?? 'Not taught'} · {lesson.id}
            </p>
            <h1 className="font-display text-4xl font-bold tracking-tight text-balance text-ink" data-testid="lesson-title">
              {lesson.name}
            </h1>
            <p className="mt-3 max-w-2xl text-lg text-subtle" data-testid="lesson-summary">
              {lesson.text?.summary ?? lesson.summary}
            </p>
          </header>

          {lesson.kind === 'REMOVED' ? (
            <Notice role="note">Not taught in Learn any more (DEC-081). Its useful parts are in the lessons around it.</Notice>
          ) : locked ? (
            <Panel aria-label="Locked" className="flex items-center gap-4">
              <Lock aria-hidden className="size-6 shrink-0 text-subtle" />
              <div>
                <p className="font-semibold text-ink">Pass the {before ? name(before) : 'previous'} drill to unlock this lesson.</p>
                <p className="mt-1 text-sm text-subtle">
                  A drill is passed at 5 answers averaging 70% or better.{' '}
                  {before && (
                    <Link to={`/learn/${before}`} className="font-semibold text-accent hover:underline">
                      Go to {name(before)}
                    </Link>
                  )}
                </p>
              </div>
            </Panel>
          ) : (
            <Body
              lesson={lesson}
              busy={busy}
              onTimeframe={setTimeframe}
              onMore={() => setRound((r) => r + 1)}
              adapterFactory={adapterFactory}
            />
          )}

          {!locked && lesson.kind !== 'REMOVED' && (
            <Panel aria-label="Practise it">
              <h2 className={panelTitle}>Practise it</h2>
              {progress?.drilled && (
                <p className="mb-3 text-sm text-subtle" data-testid="lesson-record">
                  {progress.state === 'SOLID'
                    ? 'Passed.'
                    : progress.attempts === 0
                      ? 'Not tried yet.'
                      : `${progress.attempts} ${progress.attempts === 1 ? 'try' : 'tries'} · ${Math.round(
                          (progress.recentAccuracy ?? 0) * 100,
                        )}% recently.`}{' '}
                  {progress.state !== 'SOLID' && 'Pass it at 5 answers averaging 70% or better to open the next lesson.'}
                </p>
              )}
              <div className="flex flex-wrap items-center gap-3">
                {lesson.drills.map((drill) => (
                  <Link
                    key={drill.kind}
                    to={`/drills/${drill.kind}`}
                    className="inline-flex h-9 items-center rounded-lg bg-accent px-4 text-sm font-semibold text-accent-ink hover:shadow-glow"
                  >
                    {MODE_NAMES[drill.mode]}: {drill.title}
                  </Link>
                ))}
                {id === RISK_CONCEPT &&
                  RISK_KINDS.map((kind) => (
                    <Link
                      key={kind.value}
                      to={`/drills/risk/${kind.slug}`}
                      className="inline-flex h-9 items-center rounded-lg bg-accent px-4 text-sm font-semibold text-accent-ink hover:shadow-glow"
                    >
                      Sums: {kind.label}
                    </Link>
                  ))}
                <Button disabled={marking || progress?.readAt !== undefined} onClick={() => void read()}>
                  {progress?.readAt !== undefined ? 'Read' : done ? 'Mark as reviewed' : 'Mark as read'}
                </Button>
              </div>
            </Panel>
          )}
        </>
      )}
    </article>
  )
}

/** The template's sections (T45): the words, the examples, the rule folded, what the data says. */
function Body({
  lesson,
  busy,
  onTimeframe,
  onMore,
  adapterFactory,
}: {
  lesson: Lesson
  busy: boolean
  onTimeframe: (timeframe: string) => void
  onMore: () => void
  adapterFactory?: () => ChartAdapter
}) {
  return (
    <>
      {lesson.text && (
        <>
          <Section title="What it is">
            <p className="max-w-[65ch] leading-7">{lesson.text.whatItIs}</p>
          </Section>
          <Section title="How to spot it">
            <ul className="max-w-[65ch] list-disc space-y-1.5 pl-5 leading-7">
              {lesson.text.howToSpot.map((line) => (
                <li key={line}>{line}</li>
              ))}
            </ul>
          </Section>
          <Section title="How it is traded">
            <p className="max-w-[65ch] leading-7">{lesson.text.howToTrade}</p>
          </Section>
        </>
      )}

      <Panel aria-label="Examples">
        <div className="mb-3 flex flex-wrap items-center gap-3">
          <h2 className="text-base font-semibold text-ink">Real {lesson.instrument} examples</h2>
          {lesson.rules.length > 0 && (
            <>
              <Segmented<string>
                options={LESSON_TIMEFRAMES}
                value={lesson.timeframe}
                onChange={onTimeframe}
                label="Example timeframe"
              />
              <Button size="sm" disabled={busy} onClick={onMore}>
                Show others
              </Button>
            </>
          )}
        </div>
        {lesson.examplesNote && (
          <Notice role="note" data-testid="examples-note">
            {lesson.examplesNote}
          </Notice>
        )}
        {lesson.examples.length > 0 && (
          <div className="grid gap-4 lg:grid-cols-2">
            {lesson.examples.map((example) => (
              <ExampleChart
                key={`${example.detector}-${example.setup.anchor}-${example.setup.end}-${example.setup.kind ?? ''}`}
                example={example}
                tickSize={lesson.tickSize}
                exchangeTimezone={lesson.exchangeTimezone}
                adapterFactory={adapterFactory}
              />
            ))}
          </div>
        )}
      </Panel>

      {lesson.rules.length > 0 && (
        <details className="rounded-card border border-line bg-surface p-5 shadow-panel" data-testid="lesson-rule">
          <summary className="cursor-pointer font-semibold text-ink">The rule Tradeez uses</summary>
          {lesson.rules.map((rule) => (
            <div key={rule.detector} className="mt-3">
              <p className="num text-xs text-subtle">{rule.version}</p>
              <p className="mt-1 max-w-[70ch] text-sm">{rule.definition}</p>
            </div>
          ))}
          <p className="mt-3 text-xs text-subtle">
            ICT's claim written as a testable rule. It is not proof that the method works.
          </p>
        </details>
      )}

      {lesson.rules.length > 0 && <EvidencePanel lesson={lesson} />}
    </>
  )
}

function Section({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section aria-label={title}>
      <h2 className="mb-2 font-display text-xl font-bold tracking-tight text-ink">{title}</h2>
      <div className="text-base text-ink">{children}</div>
    </section>
  )
}
