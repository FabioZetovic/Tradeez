import { Link } from 'react-router'
import { ArrowRight } from 'lucide-react'
import { PageHeader, Panel } from './ui'

const STEPS = [
  {
    to: '/learn',
    name: 'Learn',
    text: 'Each ICT concept as a rule, in words, with real examples and how often the rule found it in two years of NQ.',
  },
  {
    to: '/drills',
    name: 'Drills',
    text: 'Find and name a pattern on a real chart, graded against the rule — and predict what came next.',
  },
  {
    to: '/practice',
    name: 'Practice',
    text:
      'A real day replayed bar by bar, the future hidden. Plan a trade with a stop and a target, then read its ' +
      'scorecard: what it did and how it was made, kept apart.',
  },
  {
    to: '/chart',
    name: 'Chart',
    text: 'The whole history, with every rule and ICT drawing tool to switch on.',
  },
] as const

/**
 * How Tradeez works (T29), the /help page since T36: what each section is for, in the order they build on one
 * another, and what the app is not. Opened from "How it works" in the account menu.
 */
export function Guide() {
  return (
    <>
      <PageHeader
        eyebrow="How it works"
        title="How Tradeez works"
        description="Tradeez teaches ICT's method on two years of real NQ futures history. You practise on the past: nothing you do here reaches a market."
      />
      <ol className="mb-8 grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        {STEPS.map((step, i) => (
          <li key={step.to}>
            <Panel className="flex h-full flex-col gap-2">
              <span className="num text-xs font-semibold tracking-widest text-accent uppercase">Step {i + 1}</span>
              <Link
                to={step.to}
                className="text-lg font-semibold tracking-tight text-ink hover:text-accent focus-visible:outline-2 focus-visible:outline-accent"
              >
                {step.name}
              </Link>
              <p className="text-sm text-subtle">{step.text}</p>
            </Panel>
          </li>
        ))}
      </ol>
      <p className="mb-6 max-w-3xl text-sm text-subtle" data-testid="guide-not">
        What it is not: ICT&apos;s ideas are taught as ICT&apos;s claims. The rules say what they found in the data, not
        that the method works, and nothing here is advice.
      </p>
      <Link
        to="/learn"
        className="inline-flex h-11 items-center gap-2 rounded-lg bg-accent px-5 text-sm font-semibold text-accent-ink transition-all hover:shadow-glow focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent"
      >
        Start with Learn
        <ArrowRight aria-hidden className="size-4" />
      </Link>
    </>
  )
}
