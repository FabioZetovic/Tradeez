import { ExampleChart } from './ExampleChart'
import { LESSON_TIMEFRAMES, type Lesson } from '@/lib/api/lessons'
import type { ChartAdapter } from '@/lib/chart/types'
import { Button, Chip, Notice, Panel, Segmented } from '../ui'
import { panelTitle } from '@/lib/styles'

interface LessonCardProps {
  lesson: Lesson
  busy: boolean
  onOpen: (id: string) => void
  onTimeframe: (timeframe: string) => void
  onMoreExamples: () => void
  onPractise: (kind: string) => void
  adapterFactory?: () => ChartAdapter
}

const STATUS: Record<Lesson['status'], { label: string; tone: 'good' | 'neutral' | 'caution' }> = {
  IMPLEMENTED: { label: 'Built into Tradeez', tone: 'good' },
  DRAFTED: { label: 'Rule drafted, not built yet', tone: 'neutral' },
  DESCRIBED: { label: 'Described, no rule yet', tone: 'neutral' },
  OUT_OF_SCOPE: { label: 'Out of scope for now', tone: 'caution' },
}

/** What each detectability letter means (ICT_CONCEPTS.md, master table). */
const DETECTABILITY: Record<string, string> = {
  D: 'detectable',
  R: 'rule-based reading',
  J: 'judgement',
}

const MODE = { IDENTIFY: 'Spot', CLASSIFY: 'Name', PREDICT: 'Predict' } as const

/**
 * One concept's lesson card (T14, DEC-055), in the order of the concept loop: what it is, the exact
 * rule Tradeez uses, real examples the rule found, then the drills that practise it.
 */
export function LessonCard({
  lesson,
  busy,
  onOpen,
  onTimeframe,
  onMoreExamples,
  onPractise,
  adapterFactory,
}: LessonCardProps) {
  const status = STATUS[lesson.status]
  const detectability = lesson.detectability
    .split('/')
    .map((d) => DETECTABILITY[d.trim().charAt(0)] ?? d.trim())
    .join(' / ')

  return (
    <article className="flex min-w-0 flex-col gap-4" aria-label={`Lesson: ${lesson.name}`}>
      <Panel>
        <p className="text-xs font-medium text-subtle">
          {lesson.id} · Level {lesson.level}
        </p>
        <h2 className="mt-1 text-xl font-semibold" data-testid="lesson-title">
          {lesson.name}
        </h2>
        <div className="mt-2 flex flex-wrap items-center gap-2">
          <Chip tone={status.tone}>{status.label}</Chip>
          <Chip title={`Detectability: ${lesson.detectability}`}>{detectability}</Chip>
          {lesson.statusNote && <span className="text-xs text-subtle">{lesson.statusNote}</span>}
        </div>
        <p className="mt-4 max-w-4xl text-sm leading-6" data-testid="lesson-summary">
          {lesson.summary}
        </p>
        <ConceptLinks label="Know first" concepts={lesson.prerequisites} onOpen={onOpen} />
        <ConceptLinks label="Builds toward" concepts={lesson.unlocks} onOpen={onOpen} />
      </Panel>

      {lesson.rules.length > 0 && (
        <Panel aria-label="The rule">
          <h3 className={panelTitle}>The rule Tradeez uses</h3>
          {lesson.rules.map((rule) => (
            <div key={rule.detector} className="mb-3 last:mb-0" data-testid="lesson-rule">
              <p className="text-xs font-medium text-subtle">{rule.version}</p>
              <p className="mt-1 max-w-4xl text-sm">{rule.definition}</p>
            </div>
          ))}
          <p className="mt-3 text-xs text-subtle">
            Reproducible under this rule — not proof that the pattern matters, or that the method it comes
            from is right.
            {lesson.shownOn === 'SETUPS' && ' On the Chart tab, switch it on under Setups to see it on any stretch of history.'}
          </p>
        </Panel>
      )}

      <Panel aria-label="Examples">
        <div className="mb-3 flex flex-wrap items-center gap-3">
          <h3 className="text-base font-semibold text-ink">Real {lesson.instrument} examples</h3>
          {lesson.rules.length > 0 && (
            <>
              <Segmented<string>
                options={LESSON_TIMEFRAMES}
                value={lesson.timeframe}
                onChange={onTimeframe}
                label="Example timeframe"
              />
              <Button size="sm" disabled={busy} onClick={onMoreExamples}>
                Show others
              </Button>
            </>
          )}
        </div>
        {lesson.rules.length > 0 && (
          <p className="mb-3 text-xs text-subtle">
            Found by the rule at random in the imported history — not picked to look good. Each is different
            every time.
          </p>
        )}
        {lesson.examplesNote && (
          <Notice role="note" data-testid="examples-note">
            {lesson.examplesNote}
          </Notice>
        )}
        {lesson.examples.length > 0 && (
          <div className="grid gap-4 xl:grid-cols-3">
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

      {lesson.drills.length > 0 && (
        <Panel aria-label="Practise">
          <h3 className={panelTitle}>Practise it</h3>
          <div className="flex flex-wrap gap-3">
            {lesson.drills.map((drill) => (
              <Button key={drill.kind} variant="primary" onClick={() => onPractise(drill.kind)}>
                {MODE[drill.mode]}: {drill.title}
              </Button>
            ))}
          </div>
        </Panel>
      )}
    </article>
  )
}

function ConceptLinks({
  label,
  concepts,
  onOpen,
}: {
  label: string
  concepts: Lesson['prerequisites']
  onOpen: (id: string) => void
}) {
  if (concepts.length === 0) return null
  return (
    <p className="mt-3 flex flex-wrap items-center gap-x-3 gap-y-1 text-xs">
      <span className="text-subtle">{label}:</span>
      {concepts.map((c) => (
        <Button key={c.id} variant="link" onClick={() => onOpen(c.id)}>
          {c.id} {c.name}
        </Button>
      ))}
    </p>
  )
}
