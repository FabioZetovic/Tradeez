import { apiFetch } from './client'

/**
 * The learner's progress through Learn (T16, DEC-059; topics and locks T45, DEC-082), worked out by the server from
 * graded drills on real data, risk questions and the cards marked read, under `learning-1.1.0`.
 *
 * DEC-007 — mirrors the OpenAPI document for /api/learning.
 */

/** NEW, LEARNING, SOLID (passed) for a drilled lesson; UNREAD, DONE for a reference card. */
type ConceptState = 'NEW' | 'LEARNING' | 'SOLID' | 'UNREAD' | 'DONE'

export interface ConceptProgress {
  id: string
  kind: 'LESSON' | 'REFERENCE'
  topic: string
  /** A drill practises it: passing that drill completes it. */
  drilled: boolean
  state: ConceptState
  /** Its drills answered. */
  attempts: number
  /** How many attempts the recent accuracy is over: the last 20 at most. */
  recentCount: number
  /** 0 to 1; absent before the first answer. */
  recentAccuracy?: number
  /** Of the attempts, predictions, managed trades and risk questions. */
  practised: number
  readAt?: number
  lastPractised?: number
  /** ISO day (UTC) it is next due for review. */
  nextReview?: string
  due: boolean
  /** Open: a reference card, its topic's first lesson, or the lesson before it is passed. */
  open: boolean
}

interface TopicProgress {
  title: string
  /** Its lessons and reference cards, in the order taught. */
  concepts: string[]
  /** How many of them are passed or done. */
  complete: number
}

export interface Learning {
  version: string
  /** The rule in words. */
  rule: string
  summary: {
    concepts: number
    complete: number
    drilled: number
    solid: number
    due: number
    ratedAttempts: number
    predictions: number
  }
  topics: TopicProgress[]
  concepts: ConceptProgress[]
  suggested?: string
  due: string[]
}

export const getLearning = () => apiFetch<Learning>('/api/learning')

export const markRead = (conceptId: string) =>
  apiFetch<Learning>(`/api/learning/concepts/${encodeURIComponent(conceptId)}/read`, { method: 'POST' })
