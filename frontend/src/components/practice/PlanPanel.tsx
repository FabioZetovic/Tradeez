import { useState } from 'react'
import type { Direction, PlanRequest } from '@/lib/api/practice'
import { formatPrice, priceToTicks } from '@/lib/chart/priceFormat'
import { formatCents, planNumbers, planProblem } from '@/lib/practice/plan'

interface PlanPanelProps {
  /** The plan as currently drawn, or null if no bracket has been placed yet. */
  plan: PlanRequest | null
  referenceTicks: number
  tickSize: number
  tickValueCents: number
  busy: boolean
  onPlace: (direction: Direction) => void
  onFlip: () => void
  onChangeLevel: (level: 'stop' | 'target', ticks: number) => void
  onChangeContracts: (contracts: number) => void
  onLock: (plan: PlanRequest) => void
}

/**
 * Building the trade before committing to it.
 *
 * The chart is where the bracket is shaped — drag the stop and target handles — and this panel is
 * where it is read back in numbers and locked. Both edit the same drawing, so they cannot disagree.
 */
export function PlanPanel({
  plan,
  referenceTicks,
  tickSize,
  tickValueCents,
  busy,
  onPlace,
  onFlip,
  onChangeLevel,
  onChangeContracts,
  onLock,
}: PlanPanelProps) {
  if (!plan) {
    return (
      <section className="panel" aria-label="Plan">
        <h3>Plan a trade</h3>
        <p className="muted">
          Price is <strong>{formatPrice(referenceTicks, tickSize)}</strong>. Place a bracket, then
          drag its stop and target on the chart. Or step the replay forward and wait for a setup.
        </p>
        <div className="button-row">
          <button type="button" className="buy" onClick={() => onPlace('LONG')}>
            Place long
          </button>
          <button type="button" className="sell" onClick={() => onPlace('SHORT')}>
            Place short
          </button>
        </div>
      </section>
    )
  }

  const problem = planProblem(plan, referenceTicks)
  const numbers = planNumbers(plan, referenceTicks, tickValueCents)

  return (
    <section className="panel" aria-label="Plan">
      <h3>
        {plan.direction === 'LONG' ? 'Long' : 'Short'} at market{' '}
        <button type="button" className="link" onClick={onFlip}>
          flip to {plan.direction === 'LONG' ? 'short' : 'long'}
        </button>
      </h3>

      <div className="plan-grid">
        <span>Entry</span>
        <span className="muted">
          next price after {formatPrice(referenceTicks, tickSize)}
        </span>
        <PriceField
          label="Stop"
          ticks={plan.stopTicks}
          tickSize={tickSize}
          onCommit={(t) => onChangeLevel('stop', t)}
        />
        <PriceField
          label="Target"
          ticks={plan.targetTicks}
          tickSize={tickSize}
          onCommit={(t) => onChangeLevel('target', t)}
        />
        <label htmlFor="plan-contracts">Contracts</label>
        <input
          id="plan-contracts"
          type="number"
          min={1}
          max={100}
          step={1}
          value={plan.contracts}
          onChange={(e) => onChangeContracts(Number(e.target.value))}
        />
      </div>

      <dl className="kv small" data-testid="plan-numbers">
        <dt>Risk</dt>
        <dd>
          {numbers.riskTicks} ticks · {formatCents(numbers.riskCents)}
        </dd>
        <dt>Reward</dt>
        <dd>
          {numbers.rewardTicks} ticks · {formatCents(numbers.rewardCents)}
        </dd>
        <dt>Reward : risk</dt>
        <dd>{numbers.ratio === null ? '—' : numbers.ratio.toFixed(2)}</dd>
      </dl>

      {problem && (
        <p className="error" role="alert">
          {problem}
        </p>
      )}

      <p className="muted small">
        Locking is final: the plan cannot be changed afterwards, and the outcome is decided the moment
        you lock it. The replay only reveals it.
      </p>
      <button
        type="button"
        className="primary"
        disabled={problem !== null || busy}
        onClick={() => onLock(plan)}
      >
        Lock plan
      </button>
    </section>
  )
}

/**
 * A price box that commits on Enter or blur.
 *
 * Committing on every keystroke would round "20012." to a tick while it is still being typed, and
 * the learner would watch their input rewrite itself.
 */
function PriceField({
  label,
  ticks,
  tickSize,
  onCommit,
}: {
  label: string
  ticks: number
  tickSize: number
  onCommit: (ticks: number) => void
}) {
  // What the learner is typing, or null when they are not. While null the box simply shows the
  // plan's price, so a bracket dragged on the chart updates it with no syncing effect.
  const [draft, setDraft] = useState<string | null>(null)
  const text = draft ?? formatPrice(ticks, tickSize)

  const id = `plan-${label.toLowerCase()}`
  const commit = () => {
    if (draft === null) return
    const price = Number(draft)
    if (draft.trim() !== '' && Number.isFinite(price) && price > 0) {
      onCommit(priceToTicks(price, tickSize))
    }
    // Valid or not, typing is over: show the plan's price, which is the committed value or,
    // for nonsense input, the one that was there before.
    setDraft(null)
  }

  return (
    <>
      <label htmlFor={id}>{label}</label>
      <input
        id={id}
        inputMode="decimal"
        value={text}
        onChange={(e) => setDraft(e.target.value)}
        onBlur={commit}
        onKeyDown={(e) => {
          if (e.key === 'Enter') commit()
        }}
      />
    </>
  )
}
