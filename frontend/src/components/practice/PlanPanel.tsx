import { useState } from 'react'
import type { Direction, PlanContext, PlanIntent as Intent, PlanRequest } from '@/lib/api/practice'
import { formatPrice, priceToTicks } from '@/lib/chart/priceFormat'
import { formatCents, planNumbers, planProblem } from '@/lib/practice/plan'
import { Button, Chip, Panel } from '../ui'
import { PlanIntent } from './PlanIntent'
import { errorClass, fieldClass, keyValueClass, panelTitle } from '@/lib/styles'

interface PlanPanelProps {
  /** The plan as currently drawn, or null if no bracket has been placed yet. */
  plan: PlanRequest | null
  referenceTicks: number
  tickSize: number
  tickValueCents: number
  /** The learner's limit for one trade's risk (T23): shown, never enforced — the scorecard records it. */
  riskLimitCents?: number
  /** What a trade costs the learner (T27): shown as the most fees and slippage can take. */
  costs?: PlanCosts
  busy: boolean
  onPlace: (direction: Direction) => void
  onFlip: () => void
  /** Throws the draft away: nothing has been placed yet. */
  onDiscard: () => void
  onChangeLevel: (level: 'stop' | 'target', ticks: number) => void
  onChangeContracts: (contracts: number) => void
  onLock: (plan: PlanRequest) => void
  /** What the learner means by the trade (T22), and what the app sees for the draft. */
  intent?: Intent
  onIntent?: (intent: Intent) => void
  context?: PlanContext | null
}

/**
 * Building the trade before committing to it.
 *
 * The chart is where the bracket is shaped — drag the stop and target handles — and this panel is
 * where it is read back in numbers and placed. Both edit the same drawing, so they cannot disagree.
 *
 * Until "Place trade" there is no trade, only a draft — and the panel says so in its title, because
 * a bracket on the chart looks like a position: stepping the replay past a draft's stop does nothing,
 * and a learner who thought they were in a trade would think the stop was broken.
 */
export function PlanPanel({
  plan,
  referenceTicks,
  tickSize,
  tickValueCents,
  riskLimitCents,
  costs,
  busy,
  onPlace,
  onFlip,
  onDiscard,
  onChangeLevel,
  onChangeContracts,
  onLock,
  intent = {},
  onIntent = () => {},
  context = null,
}: PlanPanelProps) {
  if (!plan) {
    return (
      <Panel aria-label="Plan">
        <h3 className={panelTitle}>Plan a trade</h3>
        <p className="text-sm text-subtle">
          Price is <strong className="text-ink">{formatPrice(referenceTicks, tickSize)}</strong>. Draft a
          bracket, drag its stop and target on the chart, then place the trade. Or step the replay forward
          and wait for a setup.
        </p>
        <div className="mt-4 flex flex-wrap gap-2">
          <Button variant="buy" onClick={() => onPlace('LONG')}>
            Plan long
          </Button>
          <Button variant="sell" onClick={() => onPlace('SHORT')}>
            Plan short
          </Button>
        </div>
      </Panel>
    )
  }

  const problem = planProblem(plan, referenceTicks)
  const numbers = planNumbers(plan, referenceTicks, tickValueCents)

  return (
    <Panel aria-label="Plan">
      <h3 className={`${panelTitle} flex flex-wrap items-center gap-2`} data-testid="draft-title">
        Draft · {plan.direction === 'LONG' ? 'long' : 'short'} at market
        <Chip tone="caution">not placed</Chip>
        <Button variant="link" className="font-normal" onClick={onFlip}>
          flip to {plan.direction === 'LONG' ? 'short' : 'long'}
        </Button>
      </h3>

      <div className="mb-4 grid grid-cols-[auto_1fr] items-center gap-x-4 gap-y-2 text-sm">
        <span className="text-subtle">Entry</span>
        <span className="text-subtle">
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
        <label htmlFor="plan-contracts" className="text-subtle">
          Contracts
        </label>
        <input
          id="plan-contracts"
          className={`${fieldClass} w-36`}
          type="number"
          min={1}
          max={100}
          step={1}
          value={plan.contracts}
          onChange={(e) => onChangeContracts(Number(e.target.value))}
        />
      </div>

      <PlanIntent intent={intent} onChange={onIntent} context={context} tickSize={tickSize} />

      <dl className={keyValueClass} data-testid="plan-numbers">
        <dt>Risk</dt>
        <dd>
          {numbers.riskTicks} ticks · {formatCents(numbers.riskCents)}
          {riskLimitCents !== undefined && (
            <span
              data-testid="risk-limit"
              className={numbers.riskCents > riskLimitCents ? 'font-medium text-down' : 'text-subtle'}
            >
              {numbers.riskCents > riskLimitCents
                ? ` · over your ${formatCents(riskLimitCents)} limit`
                : ` · within your ${formatCents(riskLimitCents)} limit`}
            </span>
          )}
        </dd>
        <dt>Reward</dt>
        <dd>
          {numbers.rewardTicks} ticks · {formatCents(numbers.rewardCents)}
        </dd>
        <dt>Reward : risk</dt>
        <dd>{numbers.ratio === null ? '—' : numbers.ratio.toFixed(2)}</dd>
        {costs && (
          <>
            <dt>Costs</dt>
            <dd data-testid="plan-costs">{costsText(costs, plan.contracts, tickValueCents)}</dd>
          </>
        )}
      </dl>

      {problem && (
        <p className={`mt-3 ${errorClass}`} role="alert">
          {problem}
        </p>
      )}

      <p className="my-3 text-xs text-subtle">
        Nothing is traded until you place it. Once placed, the stop and target cannot be changed — but you
        can close the trade at market at any time.
      </p>
      <div className="flex flex-wrap gap-2">
        <Button variant="primary" disabled={problem !== null || busy} onClick={() => onLock(plan)}>
          Place trade
        </Button>
        <Button variant="ghost" disabled={busy} onClick={onDiscard}>
          Discard
        </Button>
      </div>
    </Panel>
  )
}

/** The learner's costs (T27), as much of them as a draft needs. */
export interface PlanCosts {
  feesCents: number
  marketSlippageTicks: number
  stopSlippageTicks: number
}

/**
 * "$4.50 fees · slippage 1 tick in, up to 1 out — at most $14.50": the most a round trip can cost, the exit slipping
 * as a stop or a close at market does, whichever is more.
 */
function costsText(costs: PlanCosts, contracts: number, tickValueCents: number): string {
  const out = Math.max(costs.stopSlippageTicks, costs.marketSlippageTicks)
  const most = (costs.feesCents + (costs.marketSlippageTicks + out) * tickValueCents) * contracts
  const ticks = (n: number) => `${n} ${n === 1 ? 'tick' : 'ticks'}`
  return `${formatCents(costs.feesCents * contracts)} fees · slippage ${ticks(costs.marketSlippageTicks)} in, up to ${out} out — at most ${formatCents(most)}`
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
      <label htmlFor={id} className="text-subtle">
        {label}
      </label>
      <input
        id={id}
        className={`${fieldClass} w-36`}
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
