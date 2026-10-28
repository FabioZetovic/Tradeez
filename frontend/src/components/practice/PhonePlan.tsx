import { useEffect, useRef, useState, type ReactNode } from 'react'
import { ChevronDown, Minus, Plus } from 'lucide-react'
import type { PlanContext, PlanIntent as Intent, PlanRequest } from '@/lib/api/practice'
import { formatPrice } from '@/lib/chart/priceFormat'
import { formatCents, planNumbers, planProblem } from '@/lib/practice/plan'
import { cx, errorClass } from '@/lib/styles'
import { PlanIntent } from './PlanIntent'
import type { PlanCosts } from './PlanPanel'

/**
 * Planning a trade on a phone (T53, Claude Design): a panel over the chart's foot, the side to switch, − and + for the
 * stop, the target and the contracts — a tick at a press, quicker while held — the risk, the reward and R:R as they
 * change, the plan's four questions behind one row, and Discard or Place. The bracket on the chart moves with it and
 * still drags. A partial is the desktop panel's (T28).
 */
export function PhonePlan({
  plan,
  referenceTicks,
  tickSize,
  tickValueCents,
  riskLimitCents,
  costs,
  busy,
  onFlip,
  onDiscard,
  onChangeLevel,
  onChangeContracts,
  onLock,
  intent,
  onIntent,
  context,
}: {
  plan: PlanRequest
  referenceTicks: number
  tickSize: number
  tickValueCents: number
  riskLimitCents?: number
  costs?: PlanCosts
  busy: boolean
  onFlip: () => void
  onDiscard: () => void
  onChangeLevel: (level: 'stop' | 'target', ticks: number) => void
  onChangeContracts: (contracts: number) => void
  onLock: (plan: PlanRequest) => void
  intent: Intent
  onIntent: (intent: Intent) => void
  context: PlanContext | null
}) {
  const [questions, setQuestions] = useState(false)
  const problem = planProblem(plan, referenceTicks)
  const numbers = planNumbers(plan, referenceTicks, tickValueCents)
  // As the desktop panel judges it (BUG-088): what the stop costs with the slippage and the fees.
  const atStopCents = costs
    ? numbers.riskCents +
      (costs.feesCents + (costs.marketSlippageTicks + costs.stopSlippageTicks) * tickValueCents) * plan.contracts
    : numbers.riskCents
  const long = plan.direction === 'LONG'
  const answered = [intent.model, intent.bias, intent.dolTicks, intent.confidence].filter((v) => v !== undefined).length

  return (
    <section
      aria-label="Plan"
      className="fixed inset-x-0 bottom-0 z-30 max-h-[62svh] overflow-y-auto rounded-t-3xl border-t border-line bg-surface px-4 pt-2 shadow-[0_-16px_48px_rgb(0_0_0/0.55)]"
    >
      <div aria-hidden className="mx-auto mb-2.5 h-1.5 w-10 rounded-full bg-subtle/35" />
      <div className="flex items-center gap-2.5">
        <div role="group" aria-label="Side" className="grid w-40 grid-cols-2 gap-0.5 rounded-xl border border-line bg-canvas p-0.5">
          {(['LONG', 'SHORT'] as const).map((side) => (
            <button
              key={side}
              type="button"
              aria-pressed={plan.direction === side}
              onClick={() => plan.direction !== side && onFlip()}
              className={cx(
                'h-9 cursor-pointer rounded-[10px] text-sm font-semibold focus-visible:outline-2 focus-visible:outline-accent',
                plan.direction !== side ? 'text-subtle' : side === 'LONG' ? 'bg-up/15 text-up' : 'bg-down/15 text-down',
              )}
            >
              {side === 'LONG' ? 'Long' : 'Short'}
            </button>
          ))}
        </div>
        <span className="flex-1 text-right text-[13px] text-subtle" data-testid="draft-title">
          Draft, not placed · entry at the next price
        </span>
      </div>

      <div className="mt-3 flex flex-col gap-2">
        <Stepper
          label="Stop"
          tone="text-down"
          value={formatPrice(plan.stopTicks, tickSize)}
          onStep={(d) => onChangeLevel('stop', plan.stopTicks + d)}
        />
        <Stepper
          label="Target"
          tone="text-up"
          value={formatPrice(plan.targetTicks, tickSize)}
          onStep={(d) => onChangeLevel('target', plan.targetTicks + d)}
        />
        <Stepper
          label="Contracts"
          value={String(plan.contracts)}
          names={['Fewer contracts', 'More contracts']}
          onStep={(d) => onChangeContracts(Math.min(100, Math.max(1, plan.contracts + d)))}
        />
      </div>

      <div className="mt-3 grid grid-cols-3 gap-1.5" data-testid="plan-numbers">
        <Figure label="Risk" tone="text-down">
          {numbers.riskTicks}t · {formatCents(numbers.riskCents)}
        </Figure>
        <Figure label="Reward" tone="text-up">
          {numbers.rewardTicks}t · {formatCents(numbers.rewardCents)}
        </Figure>
        <Figure label="R : R">{numbers.ratio === null ? '—' : numbers.ratio.toFixed(2)}</Figure>
      </div>
      {riskLimitCents !== undefined && (
        <p data-testid="risk-limit" className={cx('mt-2 text-xs', atStopCents > riskLimitCents ? 'font-medium text-down' : 'text-subtle')}>
          {formatCents(atStopCents)} at the stop with costs ·{' '}
          {atStopCents > riskLimitCents ? `over your ${formatCents(riskLimitCents)} limit` : `within your ${formatCents(riskLimitCents)} limit`}
        </p>
      )}

      <button
        type="button"
        aria-expanded={questions}
        onClick={() => setQuestions((q) => !q)}
        className="mt-2.5 flex min-h-12 w-full cursor-pointer items-center gap-2 rounded-xl border border-line bg-raised px-3.5 text-left text-sm focus-visible:outline-2 focus-visible:outline-accent"
      >
        <span className="flex-1">
          <span className="font-semibold text-ink">Your plan</span>
          <span className="text-subtle"> · model, bias, draw, confidence</span>
        </span>
        <span className="rounded-full bg-sunken px-2 py-0.5 text-[11px] font-semibold text-subtle">{answered} of 4</span>
        <ChevronDown aria-hidden className={cx('size-4 text-subtle transition-transform', questions && 'rotate-180')} />
      </button>
      {questions && (
        <div className="mt-2">
          <PlanIntent intent={intent} onChange={onIntent} context={context} tickSize={tickSize} />
        </div>
      )}

      {problem && (
        <p className={cx(errorClass, 'mt-2')} role="alert">
          {problem}
        </p>
      )}
      <div className="sticky bottom-0 -mx-4 mt-3 flex gap-2 bg-surface px-4 pt-2 pb-[calc(1rem+env(safe-area-inset-bottom))]">
        <button
          type="button"
          disabled={busy}
          onClick={onDiscard}
          className="h-12 flex-1 cursor-pointer rounded-xl border border-line bg-raised text-[15px] font-semibold text-ink focus-visible:outline-2 focus-visible:outline-accent disabled:opacity-40"
        >
          Discard
        </button>
        <button
          type="button"
          disabled={problem !== null || busy}
          onClick={() => onLock(plan)}
          className="h-12 flex-[2] cursor-pointer rounded-xl bg-accent text-[15px] font-semibold text-accent-ink focus-visible:outline-2 focus-visible:outline-accent disabled:opacity-40"
        >
          Place {long ? 'long' : 'short'} trade
        </button>
      </div>
    </section>
  )
}

/** A value with − and + either side: a step at a press, then quicker while it is held. */
function Stepper({
  label,
  tone,
  value,
  names,
  onStep,
}: {
  label: string
  tone?: string
  value: string
  /** The two buttons' names; 'Lower stop' and 'Raise stop' unless given. */
  names?: [string, string]
  onStep: (delta: number) => void
}) {
  // The latest onStep, for the repeat to call: each step re-renders with a new plan.
  const latest = useRef(onStep)
  useEffect(() => {
    latest.current = onStep
  })
  const timer = useRef<number | null>(null)
  const stop = () => {
    if (timer.current !== null) window.clearTimeout(timer.current)
    timer.current = null
  }
  useEffect(() => stop, [])
  const hold = (delta: number) => {
    let wait = 380
    const again = () => {
      latest.current(delta)
      wait = Math.max(50, wait * 0.8)
      timer.current = window.setTimeout(again, wait)
    }
    timer.current = window.setTimeout(again, wait)
  }
  const button = (delta: number, name: string, icon: ReactNode) => (
    <button
      type="button"
      aria-label={name}
      onClick={() => onStep(delta)}
      onPointerDown={() => hold(delta)}
      onPointerUp={stop}
      onPointerLeave={stop}
      onPointerCancel={stop}
      className="grid size-11 shrink-0 cursor-pointer touch-none place-items-center rounded-xl border border-line bg-raised text-ink select-none focus-visible:outline-2 focus-visible:outline-accent"
    >
      {icon}
    </button>
  )
  return (
    <div className="flex items-center gap-2.5">
      <span className={cx('w-20 text-sm', tone ?? 'text-ink')}>{label}</span>
      {button(-1, names?.[0] ?? `Lower ${label.toLowerCase()}`, <Minus aria-hidden className="size-4" />)}
      <output aria-label={label} className="num flex-1 text-center text-[17px] font-semibold text-ink">
        {value}
      </output>
      {button(1, names?.[1] ?? `Raise ${label.toLowerCase()}`, <Plus aria-hidden className="size-4" />)}
    </div>
  )
}

function Figure({ label, tone, children }: { label: string; tone?: string; children: ReactNode }) {
  return (
    <div className="rounded-xl border border-line bg-raised px-2.5 py-2">
      <div className="text-xs text-subtle">{label}</div>
      <div className={cx('num text-[13px] font-semibold', tone ?? 'text-ink')}>{children}</div>
    </div>
  )
}
