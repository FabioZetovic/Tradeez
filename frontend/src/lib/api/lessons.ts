import { apiFetch } from './client'
import type { DomainBar } from '../chart/types'
import type { Rule, Setup } from './setups'

/**
 * Lesson cards (T14, DEC-055): a concept from ICT_CONCEPTS.md, its rules in words, the drills that
 * practise it, and real NQ examples its rule found at random.
 *
 * DEC-007 — mirrors the OpenAPI document for /api/concepts/{id}/lesson. The server leaves null fields
 * out of the JSON, so those are optional here.
 */

interface ConceptRef {
  id: string
  name: string
}

interface RuleCard {
  detector: string
  version: string
  parameters: Record<string, unknown>
  /** The rule in words, generated server-side from its parameters. */
  definition: string
  methodologySource: string
}

interface DrillRef {
  kind: string
  mode: 'IDENTIFY' | 'CLASSIFY' | 'PREDICT'
  title: string
}

export interface Example {
  detector: string
  label: string
  /** Why it qualified, rendered by the server from the rule's evidence. */
  explanation: string[]
  timeframe: string
  bars: DomainBar[]
  setup: Setup
  rule: Rule
}

export interface Lesson {
  id: string
  name: string
  level: number
  /** D, R, J or a combination. */
  detectability: string
  status: 'IMPLEMENTED' | 'DRAFTED' | 'DESCRIBED' | 'OUT_OF_SCOPE'
  statusNote: string
  summary: string
  prerequisites: ConceptRef[]
  unlocks: ConceptRef[]
  rules: RuleCard[]
  /** Where the app draws it. */
  shownOn?: 'SETUPS' | 'CLOCK'
  drills: DrillRef[]
  /** The market the examples come from. */
  instrument: string
  tickSize: number
  exchangeTimezone: string
  timeframe: string
  examples: Example[]
  /** Why there are no examples, or fewer than three. */
  examplesNote?: string
}

export const LESSON_TIMEFRAMES = ['1m', '5m', '15m'] as const

export const getLesson = (id: string, timeframe: string) =>
  apiFetch<Lesson>(`/api/concepts/${encodeURIComponent(id)}/lesson?timeframe=${encodeURIComponent(timeframe)}`)
