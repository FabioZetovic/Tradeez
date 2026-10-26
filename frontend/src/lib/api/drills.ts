import { apiFetch } from './client'
import type { Companion, DomainBar } from '../chart/types'

/**
 * Drill API calls.
 *
 * DEC-007 — mirrors the OpenAPI document for /api/drills. Fields that exist only after the drill is
 * answered, or only in one mode, are optional: the server leaves null fields out of the JSON.
 */

/**
 * IDENTIFY: mark every instance (mode A). CLASSIFY: name the marked structure (mode B). PREDICT: say
 * which of two targets price reaches first after the chart stops (mode C, DEC-058).
 */
export type DrillMode = 'IDENTIFY' | 'CLASSIFY' | 'PREDICT' | 'MANAGE'

export interface DrillKindView {
  kind: string
  mode: DrillMode
  title: string
  conceptId: string
  detector: string
  ruleVersion: string
  instructions: string
}

interface RuleView {
  detector: string
  version: string
  parameters: Record<string, number | string>
  /** The rule in words, generated server-side from the same parameters. */
  definition: string
  methodologySource: string
}

interface OptionView {
  value: string
  label: string
}

/** What a classification drill asks about — where it is, not what it is called. */
export interface SubjectView {
  side: 'HIGH' | 'LOW'
  /** Where the level starts: the swing it comes from. Equal to `time` for a swing itself. */
  fromTime: number
  /** The marked candle. */
  time: number
  priceTicks: number
}

/** One of a prediction's two answers, as drawn on the chart. Built from the candles before the moment only. */
export interface TargetView {
  /** BSL, SSL, IRL or ERL. */
  value: string
  /** ERL: an edge of the range. IRL: inside it (ICT-15). */
  role: 'ERL' | 'IRL'
  /** A level is taken a tick beyond; a zone (a gap) is reached when traded into. */
  shape: 'LEVEL' | 'ZONE'
  side: 'ABOVE' | 'BELOW'
  /** Its swing, or its gap's first candle. */
  fromTime: number
  lowTicks: number
  highTicks: number
  distanceTicks: number
  /** Which is taken first: what lies on the way (ICT-17). */
  obstacleSwings?: number
  obstacleGaps?: number
  label: string
}

/** Drawn for context, not to be chosen: a gap inside the range, or the level just taken. */
export interface ContextView {
  /** TRADE_*: a management drill's open trade (T26). */
  what: 'IRL_BULLISH_GAP' | 'IRL_BEARISH_GAP' | 'TAKEN_BSL' | 'TAKEN_SSL' | 'TRADE_ENTRY' | 'TRADE_STOP' | 'TRADE_TARGET'
  shape: 'LEVEL' | 'ZONE'
  fromTime: number
  lowTicks: number
  highTicks: number
  label: string
}

export interface QuestionView {
  version: string
  /** How the targets are chosen and how what price did answers, in words. */
  definition: string
  closeTicks: number
  targets: TargetView[]
  context: ContextView[]
}

export interface SetupView {
  id: string
  direction: 'BULLISH' | 'BEARISH' | 'NEUTRAL'
  kind?: string
  shape: 'ZONE' | 'LEVEL' | 'POINT'
  startTime: number
  /** The candle to mark. */
  anchorTime: number
  endTime: number
  zoneLowTicks: number
  zoneHighTicks: number
  /** Identification: whether a mark found it. */
  found?: boolean
  /** "Bullish gap", "Higher high (HH)". */
  label: string
  /** Why it qualified, rendered by the server from the rule's evidence. Never written here. */
  explanation: string[]
}

export interface ResultView {
  found?: number
  missed?: number
  extra?: number
  /** Marked candles that are not in the answer key. */
  extraMarks?: number[]
  /** Marks on candles the rule cannot judge from this chart: counted neither way. */
  excusedMarks?: number[]
  /** Classification: the rule's name for the subject. Prediction: the target price reached first. */
  answer?: string
  correct?: boolean
  setups: SetupView[]
  /** Prediction: the candle that decided it. */
  outcomeTime?: number
  /** Prediction: how many candles after the chart that was. */
  candlesAfter?: number
  /** Prediction: what happened, in the server's words (prices, never times). */
  explanation?: string[]
}

export interface DrillResponse {
  id: string
  kind: string
  mode: DrillMode
  title: string
  conceptId: string
  instrument: string
  tickSize: number
  exchangeTimezone: string
  timeframe: string
  dataClass: string
  disclaimer?: string
  /** True on simulated data: a developer fixture, never graded (DEC-034). */
  fixture: boolean
  /** True on real data: graded against the rule. */
  graded: boolean
  fixtureNotice?: string
  instructions: string
  /** Identification: what a correct mark is. */
  markName?: string
  rule: RuleView
  bars: DomainBar[]
  subject?: SubjectView
  options?: OptionView[]
  /** Prediction: the targets and what the chart says about them. */
  question?: QuestionView
  marks?: number[]
  choice?: string
  result?: ResultView
  /** Prediction, once answered: the candles after the chart, to a few past the deciding one. */
  revealed?: DomainBar[]
  /** DEC-075: a rule that reads two markets — the other one's candles over the same minutes. */
  companion?: Companion
}

/** Risk drills, mode F (risk-1.0.0, T26): position size, a losing streak, recovery. */
export const RISK_KINDS = [
  { value: 'CONTRACTS', slug: 'position-size', label: 'Position size', line: 'An account, a risk and a stop: how many contracts?' },
  { value: 'STREAK', slug: 'losing-streak', label: 'Losing streak', line: 'The same share risked loss after loss: how deep does it go?' },
  { value: 'RECOVERY', slug: 'recovery', label: 'Recovery', line: 'An account down so much: what gain brings it back?' },
] as const

export type RiskKind = (typeof RISK_KINDS)[number]['value']

export interface RiskDrill {
  id: string
  kind: RiskKind
  version: string
  text: string
  /** contracts or % */
  unit: string
  /** A clock to answer against; an answer after it still counts. */
  limitSeconds: number
  /** Absent until answered: the answer and its working come only with it. */
  result?: {
    given: number
    answer: number
    correct: boolean
    seconds: number
    inTime: boolean
    working: string[]
    /** This kind's questions answered so far, this one included, and how many right. */
    answered: number
    right: number
  }
}

/** A kind of risk question with its clock and the learner's record at it (T43). */
export interface RiskKindView {
  kind: RiskKind
  limitSeconds: number
  answered: number
  right: number
}

export const listRiskKinds = () => apiFetch<RiskKindView[]>('/api/drills/risk/kinds')

export const startRiskDrill = (kind: RiskKind) =>
  apiFetch<RiskDrill>('/api/drills/risk', { method: 'POST', body: JSON.stringify({ kind }) })

export const answerRiskDrill = (id: string, value: number) =>
  apiFetch<RiskDrill>(`/api/drills/risk/${encodeURIComponent(id)}/answer`, {
    method: 'POST',
    body: JSON.stringify({ value }),
  })

export const listDrillKinds = () => apiFetch<DrillKindView[]>('/api/drills/kinds')

/** No timeframe: the server draws one of 1m, 5m and 15m (T43) — "the time on a chart doesnt matter". */
export const startDrill = (body: { kind: string; instrument?: string }) =>
  apiFetch<DrillResponse>('/api/drills', { method: 'POST', body: JSON.stringify(body) })

export const answerDrill = (id: string, answer: { marks: number[] } | { choice: string }) =>
  apiFetch<DrillResponse>(`/api/drills/${encodeURIComponent(id)}/answer`, {
    method: 'POST',
    body: JSON.stringify(answer),
  })
