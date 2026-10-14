import type {
  Direction,
  Outcome,
  PlanRequest,
  PlanView,
  PracticeState,
  TradeView,
} from '../api/practice'
import type { Anchor, Drawing } from '../drawing/types'
import { DEFAULT_STYLES } from '../drawing/types'

/**
 * The practice plan, as the learner builds it on the chart.
 *
 * The position tool from the drawing layer is the trade-entry control: its three anchors are entry,
 * stop and target. The entry anchor is pinned to the last visible close, because the order is at
 * market — the learner chooses *where the bracket goes*, not the price they get in at.
 */

/** The drawing that is the plan. Every other drawing on the practice chart is just analysis. */
export const PLAN_DRAWING_ID = 'practice-plan'

/** Mirrors the server's limit; a bracket further out is a way to hold until the close. */
export const MAX_BRACKET_TICKS = 20_000

/** Starting shape: 10 points of risk for 20 of reward on NQ. A 2R default reads as intentional. */
const DEFAULT_RISK_TICKS = 40
const DEFAULT_REWARD_TICKS = 80

export function defaultBracket(
  direction: Direction,
  referenceTicks: number,
  time: number,
): Drawing {
  const sign = direction === 'LONG' ? 1 : -1
  return {
    id: PLAN_DRAWING_ID,
    kind: 'position',
    anchors: [
      { time, priceTicks: referenceTicks },
      { time, priceTicks: referenceTicks - sign * DEFAULT_RISK_TICKS },
      { time, priceTicks: referenceTicks + sign * DEFAULT_REWARD_TICKS },
    ],
    style: DEFAULT_STYLES.position,
  }
}

/** Keeps the entry anchor on the market price, whatever the learner dragged. */
export function pinEntry(drawing: Drawing, referenceTicks: number, time: number): Drawing {
  const anchors: Anchor[] = [...drawing.anchors]
  anchors[0] = { time, priceTicks: referenceTicks }
  // Stop and target share the entry's time so the zone always starts at the last visible bar.
  anchors[1] = { ...anchors[1], time }
  anchors[2] = { ...anchors[2], time }
  return { ...drawing, anchors }
}

/** The running trade's plan as a position drawing, anchored to the bar last visible at the lock. */
export function lockedBracket(plan: PlanView, time: number): Drawing {
  return {
    ...defaultBracket(plan.direction, plan.referenceTicks, time),
    anchors: [
      { time, priceTicks: plan.referenceTicks },
      { time, priceTicks: plan.stopTicks },
      { time, priceTicks: plan.targetTicks },
    ],
  }
}

/** Drawings of finished trades start with this. They are the record, not analysis: never edited. */
export const TRADE_DRAWING_PREFIX = 'practice-trade-'

/**
 * A finished trade on the chart, from the bar it was entered in to the bar it left in — at the
 * price it was really filled, with the stop and target it was planned with. Null while it runs.
 */
export function closedTradeDrawing(trade: TradeView, timeframe: string): Drawing | null {
  const result = trade.result
  if (!result) return null
  const step = timeframeSeconds(timeframe)
  const start = result.entryTime - (result.entryTime % step)
  return {
    id: `${TRADE_DRAWING_PREFIX}${trade.number}`,
    kind: 'position',
    anchors: [
      { time: start, priceTicks: result.entryTicks },
      { time: start, priceTicks: trade.plan.stopTicks },
      { time: start, priceTicks: trade.plan.targetTicks },
    ],
    style: DEFAULT_STYLES.position,
    endTime: result.exitTime - (result.exitTime % step),
  }
}

/** "5m" → 300. */
export function timeframeSeconds(code: string): number {
  const unit = code.slice(-1)
  const n = Number(code.slice(0, -1))
  return unit === 'm' ? n * 60 : unit === 'h' ? n * 3600 : unit === 's' ? n : n * 86_400
}

/** Mirrors the bracket through the entry: a long becomes the equivalent short, and back. */
export function flipBracket(drawing: Drawing): Drawing {
  const [entry, stop, target] = drawing.anchors
  const mirror = (a: Anchor): Anchor => ({ ...a, priceTicks: 2 * entry.priceTicks - a.priceTicks })
  return { ...drawing, anchors: [entry, mirror(stop), mirror(target)] }
}

/** Long when the target is above the entry. Same rule the position tool labels itself with. */
export function directionOf(drawing: Drawing): Direction {
  return drawing.anchors[2].priceTicks >= drawing.anchors[0].priceTicks ? 'LONG' : 'SHORT'
}

export function planFromDrawing(drawing: Drawing, contracts: number): PlanRequest {
  return {
    direction: directionOf(drawing),
    stopTicks: drawing.anchors[1].priceTicks,
    targetTicks: drawing.anchors[2].priceTicks,
    contracts,
  }
}

/**
 * Why a plan would be refused, or null if it would not.
 *
 * The server checks all of this too and is the authority. Checking here as well is so the button
 * can explain itself before the learner presses it, rather than after.
 */
export function planProblem(plan: PlanRequest, referenceTicks: number): string | null {
  const { direction, stopTicks, targetTicks, contracts } = plan
  if (!Number.isInteger(contracts) || contracts < 1 || contracts > 100) {
    return 'Contracts must be a whole number from 1 to 100.'
  }
  if (direction === 'LONG' && !(stopTicks < referenceTicks && targetTicks > referenceTicks)) {
    return 'A long needs its stop below the current price and its target above it.'
  }
  if (direction === 'SHORT' && !(stopTicks > referenceTicks && targetTicks < referenceTicks)) {
    return 'A short needs its stop above the current price and its target below it.'
  }
  if (
    Math.abs(stopTicks - referenceTicks) > MAX_BRACKET_TICKS ||
    Math.abs(targetTicks - referenceTicks) > MAX_BRACKET_TICKS
  ) {
    return 'Stop and target must be within 5,000 points of the price.'
  }
  return null
}

export interface PlanNumbers {
  riskTicks: number
  rewardTicks: number
  ratio: number | null
  riskCents: number
  rewardCents: number
}

export function planNumbers(
  plan: PlanRequest,
  referenceTicks: number,
  tickValueCents: number,
): PlanNumbers {
  const riskTicks = Math.abs(referenceTicks - plan.stopTicks)
  const rewardTicks = Math.abs(plan.targetTicks - referenceTicks)
  return {
    riskTicks,
    rewardTicks,
    ratio: riskTicks === 0 ? null : rewardTicks / riskTicks,
    // Integer cents throughout; only formatting turns them into dollars (INVARIANT 3).
    riskCents: riskTicks * tickValueCents * plan.contracts,
    rewardCents: rewardTicks * tickValueCents * plan.contracts,
  }
}

/** "$1,234.50" or "−$40.00". Cents in, text out; no float arithmetic on money before this. */
export function formatCents(cents: number): string {
  const sign = cents < 0 ? '−' : ''
  const abs = Math.abs(cents)
  const dollars = Math.floor(abs / 100).toLocaleString('en-US')
  return `${sign}$${dollars}.${String(abs % 100).padStart(2, '0')}`
}

/** "+2.00R" / "−1.02R": the same minus sign as money, so a column of results lines up. */
export function formatR(r: number): string {
  const sign = r > 0 ? '+' : r < 0 ? '−' : ''
  return `${sign}${Math.abs(r).toFixed(2)}R`
}

/** What to call a session's status. Flat is the trader's word for holding nothing. */
export const STATUS_LABEL: Record<PracticeState['status'], string> = {
  ANALYSING: 'Flat',
  IN_TRADE: 'In trade',
  COMPLETE: 'Ended',
}

/**
 * What to call an outcome.
 *
 * AMBIGUOUS is named for what it is. Calling it "stopped out" would present the engine's
 * pessimistic assumption as a fact (INVARIANT 6).
 */
export function outcomeLabel(outcome: Outcome): string {
  switch (outcome) {
    case 'TARGET':
      return 'Target hit'
    case 'STOP':
      return 'Stopped out'
    case 'AMBIGUOUS':
      return 'Ambiguous — worst case assumed'
    case 'TIME_EXIT':
      return 'Closed at the session end'
  }
}
