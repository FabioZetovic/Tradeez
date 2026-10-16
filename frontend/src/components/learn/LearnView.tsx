import { useCallback, useEffect, useState } from 'react'
import { LessonCard } from './LessonCard'
import { LearningSummary, LevelLine, StatusMark } from './LearningProgress'
import { ApiError } from '@/lib/api/client'
import { getCurriculum, type Curriculum } from '@/lib/api/concepts'
import { getLesson, type Lesson } from '@/lib/api/lessons'
import { getLearning, markRead, type Learning } from '@/lib/api/learning'
import type { ChartAdapter } from '@/lib/chart/types'
import { Panel } from '../ui'
import { cx, errorClass } from '@/lib/styles'

interface LearnViewProps {
  /** Opens the Drills tab on this drill. */
  onPractise: (kind: string) => void
  adapterFactory?: () => ChartAdapter
}

/**
 * The Learn tab (T14, DEC-055): the curriculum of ICT_CONCEPTS.md as a ladder of levels, and a lesson
 * card for the concept chosen — the first step of the concept loop, before spotting and naming.
 *
 * With the learner's progress on it (T16, DEC-059): where each concept stands, which levels are
 * suggested now, what is due for review, and the next step. Asked for afresh each time the tab opens,
 * so drills done in between show. The ladder suggests; every card stays open.
 */
export function LearnView({ onPractise, adapterFactory }: LearnViewProps) {
  const [curriculum, setCurriculum] = useState<Curriculum | null>(null)
  const [selected, setSelected] = useState<string | null>(null)
  const [timeframe, setTimeframe] = useState('5m')
  const [round, setRound] = useState(0)
  const [lesson, setLesson] = useState<Lesson | null>(null)
  const [answered, setAnswered] = useState<string | null>(null)
  const [lessonError, setLessonError] = useState<string | null>(null)
  const [curriculumError, setCurriculumError] = useState<string | null>(null)
  const [learning, setLearning] = useState<Learning | null>(null)
  const [marking, setMarking] = useState(false)

  // The lesson asked for; it is loading until the answer to exactly this request is in.
  const asked = selected && `${selected}|${timeframe}|${round}`
  const busy = asked !== answered
  const error = curriculumError ?? lessonError

  useEffect(() => {
    getCurriculum()
      .then((c) => {
        setCurriculum(c)
        // Start at the first concept Tradeez can show on a chart: the ladder's foundations are reading.
        setSelected((current) => current ?? c.concepts.find((x) => x.detectors.length > 0)?.concept.id ?? null)
      })
      .catch(() => setCurriculumError('Could not load the curriculum. Reload to try again.'))
    // Progress is a layer on the ladder: without it the curriculum still reads.
    getLearning()
      .then(setLearning)
      .catch(() => setLearning(null))
  }, [])

  const read = useCallback(async (id: string) => {
    setMarking(true)
    try {
      setLearning(await markRead(id))
    } catch {
      setLessonError('Could not mark this card as read. Try again.')
    } finally {
      setMarking(false)
    }
  }, [])

  const progressOf = (id: string) => learning?.concepts.find((c) => c.id === id)
  const nameOf = (id: string) => curriculum?.concepts.find((c) => c.concept.id === id)?.concept.name ?? id

  useEffect(() => {
    if (!asked || !selected) return
    let current = true
    getLesson(selected, timeframe)
      .then((next) => {
        if (!current) return
        setLesson(next)
        setLessonError(null)
        setAnswered(asked)
      })
      .catch((e: unknown) => {
        if (!current) return
        const body = e instanceof ApiError ? (e.body as { message?: string } | null) : null
        setLessonError(body?.message ?? 'Could not load this lesson. Try again.')
        setAnswered(asked)
      })
    return () => {
      current = false
    }
  }, [asked, selected, timeframe])

  const open = (id: string) => {
    setSelected(id)
    window.scrollTo?.({ top: 0 })
  }

  return (
    <section className="grid min-w-0 gap-4 lg:grid-cols-[19rem_minmax(0,1fr)]">
      <Panel aria-label="Curriculum" className="lg:sticky lg:top-20 lg:max-h-[calc(100svh-7rem)] lg:overflow-y-auto">
        <h2 className="mb-1 text-lg font-semibold">Learn</h2>
        <p className="mb-4 text-xs text-subtle">
          ICT's concepts in the order they build on each other. Each card: what it is, the rule Tradeez uses,
          and real NQ examples.
        </p>
        {learning && <LearningSummary learning={learning} nameOf={nameOf} onOpen={open} />}
        <nav aria-label="Curriculum ladder">
        {curriculum?.levels.map((level) => (
          <div key={level.level} className="mb-4" data-testid={`level-${level.level}`}>
            <h3 className="mb-1 text-xs font-semibold tracking-wide text-subtle uppercase">
              Level {level.level} · {level.theme}
              <LevelLine level={learning?.levels.find((l) => l.level === level.level)} />
            </h3>
            <ul className="flex flex-col">
              {curriculum.concepts
                .filter((c) => c.concept.level === level.level)
                .map(({ concept, detectors }) => (
                  <li key={concept.id}>
                    <button
                      type="button"
                      aria-current={concept.id === selected ? 'true' : undefined}
                      onClick={() => open(concept.id)}
                      className={cx(
                        'flex w-full cursor-pointer items-baseline gap-2 rounded-md px-2 py-1 text-left text-sm',
                        concept.id === selected ? 'bg-brand/15 text-ink' : 'text-subtle hover:bg-raised hover:text-ink',
                      )}
                    >
                      {/* The space gives the button its name, "ICT-20 Fair value gap", not "ICT-20Fair…". */}
                      <span className="w-12 shrink-0 text-xs tabular-nums">{concept.id}</span>{' '}
                      <span className="min-w-0 flex-1">{concept.name}</span>
                      <StatusMark progress={progressOf(concept.id)} />
                      {detectors.length > 0 && (
                        <span className="text-xs text-up" title="Detected on real charts">
                          ●
                        </span>
                      )}
                    </button>
                  </li>
                ))}
            </ul>
          </div>
        ))}
        </nav>
      </Panel>

      <div className="min-w-0">
        {error && (
          <p role="alert" className={`mb-3 ${errorClass}`}>
            {error}
          </p>
        )}
        {lesson && (
          <LessonCard
            lesson={lesson}
            busy={busy}
            onOpen={open}
            onTimeframe={setTimeframe}
            onMoreExamples={() => setRound((r) => r + 1)}
            onPractise={onPractise}
            adapterFactory={adapterFactory}
            progress={progressOf(lesson.id)}
            rule={learning?.rule}
            marking={marking}
            onMarkRead={() => void read(lesson.id)}
          />
        )}
        {!lesson && !error && (
          <p role="status" className="text-sm text-subtle">
            Loading…
          </p>
        )}
      </div>
    </section>
  )
}
