import { apiFetch } from './client'
import type { Rule } from './setups'

/**
 * The curriculum of ICT_CONCEPTS.md (T9, S-2), read by the server from the document itself.
 *
 * DEC-007 — mirrors the OpenAPI document until `npm run api:types` is wired in.
 */

interface Concept {
  id: string
  name: string
  level: number
  /** D, R, J or a combination, as the document writes it. */
  detectability: string
  status: 'IMPLEMENTED' | 'DRAFTED' | 'DESCRIBED' | 'OUT_OF_SCOPE'
  statusNote: string
  rules: string[]
  summary: string
  prerequisites: string[]
  /** How Learn teaches it (DEC-081). */
  kind: 'LESSON' | 'REFERENCE' | 'MERGED' | 'REMOVED'
  /** Its topic; empty for a removed concept. */
  topic: string
  importance: 'high' | 'medium' | 'low'
  /** For a merged concept, the lesson it is a section of; otherwise empty. */
  into: string
}

export interface Curriculum {
  /** Learn's topics, each with its lessons and reference cards in the order taught. */
  topics: { title: string; concepts: string[] }[]
  concepts: { concept: Concept; detectors: Rule[] }[]
}

let curriculum: Promise<Curriculum> | null = null

/** Fixed for the life of a server, so asked for once per page; a failure is asked for again. */
export function getCurriculum(): Promise<Curriculum> {
  curriculum ??= apiFetch<Curriculum>('/api/concepts').catch((error: unknown) => {
    curriculum = null
    throw error
  })
  return curriculum
}
