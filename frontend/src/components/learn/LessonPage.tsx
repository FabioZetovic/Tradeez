import { useEffect, useState, type ReactNode } from 'react'
import { useIsPhone } from '@/hooks/useIsPhone'
import { Link, Navigate } from 'react-router'
import { ArrowLeft, ArrowRight, BookCheck, Lock } from 'lucide-react'
import { EvidencePanel } from './EvidencePanel'
import { ExampleChart } from './ExampleChart'
import { ApiError } from '@/lib/api/client'
import { getCurriculum, type Curriculum } from '@/lib/api/concepts'
import { RISK_KINDS } from '@/lib/api/drills'
import { getLearning, markRead, type Learning } from '@/lib/api/learning'
import { getLesson, LESSON_TIMEFRAMES, type Lesson } from '@/lib/api/lessons'
import type { ChartAdapter } from '@/lib/chart/types'
import { MODE_NAMES } from '@/lib/drills/modes'
import { cx, errorClass, panelTitle } from '@/lib/styles'
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
  const phone = useIsPhone()
  // How far down the lesson has been read (T53): a line under the phone's bar.
  const [readShare, setReadShare] = useState(0)
  useEffect(() => {
    if (!phone) return
    const onScroll = () => {
      const room = document.documentElement.scrollHeight - window.innerHeight
      setReadShare(room <= 0 ? 1 : Math.min(1, window.scrollY / room))
    }
    onScroll()
    window.addEventListener('scroll', onScroll, { passive: true })
    return () => window.removeEventListener('scroll', onScroll)
  }, [phone, lesson])

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

  if (phone) {
    const place = topic ? `${topic.title} · ${topic.concepts.indexOf(id) + 1} of ${topic.concepts.length}` : (lesson?.topic ?? '')
    const previous = before ? learning?.concepts.find((c) => c.id === before) : undefined
    const drillLinks = [
      ...(lesson?.drills ?? []).map((drill) => ({ to: `/drills/${drill.kind}`, label: `Practise it: ${MODE_NAMES[drill.mode]} ${drill.title}` })),
      ...(id === RISK_CONCEPT ? RISK_KINDS.map((kind) => ({ to: `/drills/risk/${kind.slug}`, label: `Sums: ${kind.label}` })) : []),
    ]
    const practise = lesson && !locked && lesson.kind !== 'REMOVED'
    return (
      // A lesson on a phone (T53, Claude Design): a bar back with where it sits, a line for how far it has been read, the
      // words and the examples to swipe, and its drill at the thumb.
      <article aria-label={lesson ? `Lesson: ${lesson.name}` : 'Lesson'} className={cx('flex min-h-svh flex-col', practise ? 'pb-40' : 'pb-10')}>
        <header className="sticky top-0 z-20 border-b border-line bg-canvas/95 backdrop-blur">
          <div className="flex h-14 items-center gap-1 pr-3 pl-1">
            <Link
              to="/learn"
              aria-label="Back to Learn"
              className="grid size-11 place-items-center rounded-xl text-ink focus-visible:outline-2 focus-visible:outline-accent"
            >
              <ArrowLeft aria-hidden className="size-5" />
            </Link>
            <p className="min-w-0 flex-1 truncate text-[15px] text-subtle">{place}</p>
            {progress?.due && <span className="rounded-full bg-warn/15 px-2 py-0.5 text-[11px] font-semibold text-warn">Review</span>}
          </div>
          <div aria-hidden className="h-[3px] bg-line">
            <div className="h-full bg-accent" style={{ width: `${Math.round(readShare * 100)}%` }} />
          </div>
        </header>

        <div className="flex flex-col gap-6 px-5 pt-5">
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
                <h1 className="font-display text-[30px] leading-tight font-bold tracking-tight text-ink" data-testid="lesson-title">
                  {lesson.name}
                </h1>
                <p className="mt-2.5 text-[17px] text-subtle" data-testid="lesson-summary">
                  {lesson.text?.summary ?? lesson.summary}
                </p>
              </header>

              {lesson.kind === 'REMOVED' ? (
                <Notice role="note">Not taught in Learn any more (DEC-081). Its useful parts are in the lessons around it.</Notice>
              ) : locked ? (
                <section aria-label="Locked" className="flex flex-col items-center gap-3 rounded-card border border-line bg-surface px-4 py-6 text-center">
                  <span className="grid size-14 place-items-center rounded-2xl bg-accent/15 text-accent">
                    <Lock aria-hidden className="size-6" />
                  </span>
                  <h2 className="text-lg font-semibold text-ink">Locked until you pass one drill</h2>
                  <p className="text-sm text-subtle">
                    Pass the <b className="text-ink">{before ? name(before) : 'previous'}</b> drill — 5 answers averaging 70% or better.
                  </p>
                  {previous?.recentAccuracy !== undefined && (
                    <div className="mt-1 w-full" data-testid="locked-average">
                      <p className="mb-1.5 flex justify-between text-xs">
                        <span className="text-subtle">Your recent average</span>
                        <span className="num text-ink">{Math.round(previous.recentAccuracy * 100)}% / 70%</span>
                      </p>
                      <div className="relative h-1.5 overflow-hidden rounded-full bg-line">
                        <div
                          className={cx('h-full rounded-full', previous.recentAccuracy >= 0.7 ? 'bg-up' : 'bg-down')}
                          style={{ width: `${Math.round(previous.recentAccuracy * 100)}%` }}
                        />
                        <div className="absolute inset-y-0 left-[70%] w-0.5 bg-ink/60" />
                      </div>
                    </div>
                  )}
                  {before && (
                    <Link
                      to={`/learn/${before}`}
                      className="mt-2 inline-flex h-13 w-full items-center justify-center gap-2 rounded-full bg-accent text-base font-semibold text-accent-ink focus-visible:outline-2 focus-visible:outline-accent"
                    >
                      Go to {name(before)}
                      <ArrowRight aria-hidden className="size-4" />
                    </Link>
                  )}
                </section>
              ) : (
                <Body
                  lesson={lesson}
                  busy={busy}
                  onTimeframe={setTimeframe}
                  onMore={() => setRound((r) => r + 1)}
                  adapterFactory={adapterFactory}
                  swipe
                />
              )}
            </>
          )}
        </div>

        {practise && (
          <div
            aria-label="Practise it"
            role="group"
            className="fixed inset-x-0 bottom-0 z-30 border-t border-line bg-canvas/95 px-4 pt-2.5 pb-[calc(1rem+env(safe-area-inset-bottom))] backdrop-blur"
          >
            {progress?.drilled && (
              <p className="mb-2 flex justify-between text-xs text-subtle" data-testid="lesson-record">
                <span>
                  {progress.state === 'SOLID'
                    ? 'Drill passed.'
                    : progress.attempts === 0
                      ? 'Drill not tried yet.'
                      : `Drill: ${progress.attempts} ${progress.attempts === 1 ? 'try' : 'tries'} · ${Math.round((progress.recentAccuracy ?? 0) * 100)}% recently`}
                </span>
                {progress.state !== 'SOLID' && <span>Pass: 5 answers ≥ 70%</span>}
              </p>
            )}
            <div className="flex gap-2 overflow-x-auto [scrollbar-width:none]">
              <button
                type="button"
                aria-label={progress?.readAt !== undefined ? 'Read' : done ? 'Mark as reviewed' : 'Mark as read'}
                title={progress?.readAt !== undefined ? 'Read' : done ? 'Mark as reviewed' : 'Mark as read'}
                disabled={marking || progress?.readAt !== undefined}
                onClick={() => void read()}
                className="grid size-12 shrink-0 cursor-pointer place-items-center rounded-full border border-line bg-transparent text-ink focus-visible:outline-2 focus-visible:outline-accent disabled:opacity-40"
              >
                <BookCheck aria-hidden className="size-5" />
              </button>
              {drillLinks.map((link, i) => (
                <Link
                  key={link.to}
                  to={link.to}
                  className={cx(
                    'inline-flex h-12 shrink-0 items-center justify-center gap-2 px-4 text-[15px] font-semibold whitespace-nowrap focus-visible:outline-2 focus-visible:outline-accent',
                    i === 0 ? 'min-w-0 flex-1 rounded-full bg-accent text-accent-ink' : 'rounded-xl border border-line bg-transparent text-ink',
                  )}
                >
                  <span className="truncate">{link.label}</span>
                  {i === 0 && <ArrowRight aria-hidden className="size-4 shrink-0" />}
                </Link>
              ))}
            </div>
          </div>
        )}
      </article>
    )
  }

  return (
    <article aria-label={lesson ? `Lesson: ${lesson.name}` : 'Lesson'} className="mx-auto flex max-w-4xl flex-col gap-6">
      <Link to="/learn" className="inline-flex items-center gap-1.5 self-start text-sm font-medium text-subtle hover:text-ink pointer-coarse:min-h-11">
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
            {/* The topic, not the catalogue id (BUG-105). */}
            <p className="mb-2 text-sm text-subtle">{lesson.topic ?? 'Not taught'}</p>
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
                    className="inline-flex h-9 items-center rounded-lg bg-accent px-4 text-sm font-semibold text-accent-ink hover:brightness-110 pointer-coarse:h-11"
                  >
                    {MODE_NAMES[drill.mode]}: {drill.title}
                  </Link>
                ))}
                {id === RISK_CONCEPT &&
                  RISK_KINDS.map((kind) => (
                    <Link
                      key={kind.value}
                      to={`/drills/risk/${kind.slug}`}
                      className="inline-flex h-9 items-center rounded-lg bg-accent px-4 text-sm font-semibold text-accent-ink hover:brightness-110 pointer-coarse:h-11"
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
  swipe = false,
}: {
  lesson: Lesson
  busy: boolean
  onTimeframe: (timeframe: string) => void
  onMore: () => void
  adapterFactory?: () => ChartAdapter
  /** A phone (T53): the examples in a row to swipe through, one at a time. */
  swipe?: boolean
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
          <div
            className={cx(
              swipe ? '-mx-5 flex snap-x snap-mandatory gap-3 overflow-x-auto px-5 [scrollbar-width:none] [&>*]:w-[85%] [&>*]:shrink-0 [&>*]:snap-center' : 'grid gap-4 lg:grid-cols-2',
            )}
            data-testid="lesson-examples"
          >
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
