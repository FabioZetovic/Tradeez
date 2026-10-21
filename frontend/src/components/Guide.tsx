import { Button, Panel } from './ui'
import { panelTitle } from '@/lib/styles'

/** Where a step of the guide leads. */
type GuideSection = 'chart' | 'learn' | 'practice' | 'drills'

const STEPS: Array<{ section: GuideSection; name: string; text: string }> = [
  {
    section: 'learn',
    name: 'Learn',
    text: 'Each ICT concept as a rule, in words, with real examples and how often the rule found it in two years of NQ.',
  },
  {
    section: 'drills',
    name: 'Drills',
    text: 'Find and name a pattern on a real chart, graded against the rule — and predict what came next.',
  },
  {
    section: 'practice',
    name: 'Practice',
    text:
      'A real day replayed bar by bar, the future hidden. Plan a trade with a stop and a target, then read its ' +
      'scorecard: what it did and how it was made, kept apart.',
  },
  {
    section: 'chart',
    name: 'Chart',
    text: 'The whole history, with every rule and ICT drawing tool to switch on.',
  },
]

/**
 * How Tradeez works, for a first visit (T29): what each tab is for, in the order they build on one another, and what
 * the app is not. Shown until it is closed, and again from "How it works" in the header.
 */
export function Guide({ onClose, onOpen }: { onClose: () => void; onOpen: (section: GuideSection) => void }) {
  return (
    <Panel aria-label="How Tradeez works" className="mb-5">
      <h2 className={panelTitle}>How Tradeez works</h2>
      <p className="mb-4 max-w-3xl text-sm text-subtle">
        Tradeez teaches ICT&apos;s method on two years of real NQ futures history. You practise on the past: nothing
        you do here reaches a market.
      </p>
      <ol className="mb-4 grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
        {STEPS.map((step, i) => (
          <li key={step.section} className="flex flex-col gap-2 rounded-md border border-line/70 p-4">
            <span className="text-xs font-semibold tracking-wide text-subtle uppercase">Step {i + 1}</span>
            <Button variant="link" className="self-start font-semibold" onClick={() => onOpen(step.section)}>
              {step.name}
            </Button>
            <p className="text-sm text-subtle">{step.text}</p>
          </li>
        ))}
      </ol>
      <p className="mb-4 max-w-3xl text-sm text-subtle" data-testid="guide-not">
        What it is not: ICT&apos;s ideas are taught as ICT&apos;s claims. The rules say what they found in the data, not
        that the method works, and nothing here is advice.
      </p>
      <div className="flex flex-wrap gap-2">
        <Button variant="primary" onClick={() => onOpen('learn')}>
          Start with Learn
        </Button>
        <Button variant="ghost" onClick={onClose}>
          Close
        </Button>
      </div>
    </Panel>
  )
}
