import { apiFetch } from './client'

/**
 * The learner's progress through the curriculum (T16, DEC-059), worked out by the server from graded
 * drills on real data and the cards marked read, under `learning-1.0.0`.
 *
 * DEC-007 — mirrors the OpenAPI document for /api/learning.
 */

type ConceptState = 'NEW' | 'LEARNING' | 'SOLID' | 'UNREAD' | 'DONE'

export interface ConceptProgress {
  id: string
  /** A marking or naming drill grades it against a rule. */
  rated: boolean
  state: ConceptState
  attempts: number
  /** How many attempts the recent accuracy is over: the last 20 at most. */
  recentCount: number
  /** 0 to 1; absent before the first graded attempt. */
  recentAccuracy?: number
  /** Prediction drills answered on it: practice, never a rating. */
  practised: number
  readAt?: number
  lastPractised?: number
  /** ISO day (UTC) it is next due for review. */
  nextReview?: string
  due: boolean
  /** Suggested now: its level is open. The ladder never locks a card. */
  open: boolean
}

export interface LevelProgress {
  level: number
  theme: string
  open: boolean
  complete: boolean
  unlocksWhen: string
}

export interface Learning {
  version: string
  /** The rule in words. */
  rule: string
  summary: {
    concepts: number
    complete: number
    rated: number
    solid: number
    due: number
    ratedAttempts: number
    predictions: number
  }
  levels: LevelProgress[]
  concepts: ConceptProgress[]
  suggested?: string
  due: string[]
}

export const getLearning = () => apiFetch<Learning>('/api/learning')

export const markRead = (conceptId: string) =>
  apiFetch<Learning>(`/api/learning/concepts/${encodeURIComponent(conceptId)}/read`, { method: 'POST' })
