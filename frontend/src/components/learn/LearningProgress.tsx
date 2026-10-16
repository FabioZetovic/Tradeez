import type { ConceptProgress, Learning, LevelProgress } from '@/lib/api/learning'
import { Button, Chip, Panel } from '../ui'
import { panelTitle } from '@/lib/styles'

/**
 * The learner's progress (T16, DEC-059), as the Learn tab shows it: a mark beside each concept of the
 * ladder, a line on each level, a summary with what to do next, and a panel on each card. Every
 * number comes from the server under `learning-1.0.0`; nothing here decides anything.
 */

const percent = (x: number) => `${Math.round(100 * x)}%`

/** The short mark beside a concept in the ladder. */
export function StatusMark({ progress }: { progress?: ConceptProgress }) {
  if (!progress) return null
  const due = progress.due && (
    <span className="rounded bg-warn/20 px-1 text-[10px] font-semibold text-warn" title="Due for review today">
      due
    </span>
  )
  switch (progress.state) {
    case 'SOLID':
      return (
        <span className="flex items-center gap-1 text-xs text-up" data-testid="status-mark" title="Solid">
          ✓{due}
        </span>
      )
    case 'LEARNING':
      return (
        <span className="flex items-center gap-1 text-xs tabular-nums text-ink" data-testid="status-mark"
          title={`Learning: ${percent(progress.recentAccuracy ?? 0)} over the last ${progress.recentCount}`}>
          {percent(progress.recentAccuracy ?? 0)}
          {due}
        </span>
      )
    case 'DONE':
      return (
        <span className="text-xs text-subtle" data-testid="status-mark" title="Read">
          ✓
        </span>
      )
    default:
      return null
  }
}

/** The level's heading line: complete, open, or when it opens. */
export function LevelLine({ level }: { level?: LevelProgress }) {
  if (!level) return null
  if (level.complete) return <span className="text-up normal-case"> · complete</span>
  if (level.open) return null
  return (
    <span className="font-normal normal-case" title={`Opens when: ${level.unlocksWhen}`}>
      {' '}
      · later
    </span>
  )
}

/** The top of the ladder: how far along, what is due, and the next step. */
export function LearningSummary({
  learning,
  nameOf,
  onOpen,
}: {
  learning: Learning
  nameOf: (id: string) => string
  onOpen: (id: string) => void
}) {
  const { summary } = learning
  return (
    <div className="mb-4 rounded-md border border-line p-3 text-xs" data-testid="learning-summary">
      <p>
        <strong className="text-ink">{summary.solid}</strong> of {summary.rated} graded concepts solid ·{' '}
        <strong className="text-ink">{summary.complete}</strong> of {summary.concepts} complete
      </p>
      {learning.suggested && (
        <p className="mt-2 flex flex-wrap items-center gap-2">
          <span className="text-subtle">Next:</span>
          <Button variant="link" onClick={() => onOpen(learning.suggested!)} data-testid="suggested">
            {learning.suggested} {nameOf(learning.suggested)}
          </Button>
        </p>
      )}
      {learning.due.length > 0 && (
        <p className="mt-2 flex flex-wrap items-center gap-x-2 gap-y-1" data-testid="due">
          <span className="text-subtle">Due for review:</span>
          {/* Ids only, to fit the ladder's column: the name is the button's title and its accessible name. */}
          {learning.due.map((id) => (
            <Button key={id} variant="link" title={nameOf(id)} aria-label={`Review ${id} ${nameOf(id)}`} onClick={() => onOpen(id)}>
              {id}
            </Button>
          ))}
        </p>
      )}
    </div>
  )
}

const DATE = new Intl.DateTimeFormat('en-GB', { day: 'numeric', month: 'short', year: 'numeric', timeZone: 'UTC' })

/** The learner's record on one concept, on its card. */
export function ProgressPanel({
  progress,
  rule,
  busy,
  onMarkRead,
}: {
  progress?: ConceptProgress
  rule?: string
  busy: boolean
  onMarkRead: () => void
}) {
  if (!progress) return null
  const read = progress.readAt !== undefined
  return (
    <Panel aria-label="Your progress">
      <h3 className={`${panelTitle} flex flex-wrap items-center gap-2`}>
        Your progress <Chip tone={tone(progress)}>{stateLabel(progress)}</Chip>
      </h3>
      <p className="text-sm" data-testid="progress-line">
        {line(progress)}
      </p>
      {progress.rated && progress.nextReview && (
        <p className="mt-1 text-xs text-subtle" data-testid="review-line">
          {progress.due ? 'Due for review today.' : `Next review: ${DATE.format(new Date(`${progress.nextReview}T00:00:00Z`))}.`}
        </p>
      )}
      {progress.practised > 0 && (
        <p className="mt-1 text-xs text-subtle">
          {progress.practised} prediction {progress.practised === 1 ? 'drill' : 'drills'} — practice, never a rating.
        </p>
      )}
      <div className="mt-3 flex flex-wrap items-center gap-3">
        {read ? (
          <span className="text-xs text-subtle" data-testid="read-on">
            Card read {DATE.format(new Date(progress.readAt! * 1000))}
          </span>
        ) : (
          <Button size="sm" disabled={busy} onClick={onMarkRead}>
            Mark as read
          </Button>
        )}
      </div>
      {rule && (
        <details className="mt-3 text-xs">
          <summary className="cursor-pointer text-subtle">How progress is measured</summary>
          <p className="mt-2 max-w-4xl">{rule}</p>
        </details>
      )}
    </Panel>
  )
}

function stateLabel(p: ConceptProgress): string {
  switch (p.state) {
    case 'NEW':
      return 'Not practised yet'
    case 'LEARNING':
      return 'Learning'
    case 'SOLID':
      return 'Solid'
    case 'UNREAD':
      return 'Not read yet'
    case 'DONE':
      return 'Done'
  }
}

function tone(p: ConceptProgress): 'good' | 'neutral' | 'caution' {
  if (p.state === 'SOLID' || p.state === 'DONE') return 'good'
  return p.due ? 'caution' : 'neutral'
}

function line(p: ConceptProgress): string {
  if (!p.rated) {
    return p.state === 'DONE'
      ? 'No drill grades this concept: reading its card, or practising it, completes it.'
      : 'No drill grades this concept yet: mark its card as read once you have.'
  }
  if (p.attempts === 0) return 'Graded by the drills below. Ten attempts at 80% or more over the last twenty make it solid.'
  const recent = `${percent(p.recentAccuracy ?? 0)} over the last ${p.recentCount === 1 ? 'attempt' : `${p.recentCount} attempts`}`
  return `${p.attempts} graded ${p.attempts === 1 ? 'attempt' : 'attempts'} · ${recent}.`
}
