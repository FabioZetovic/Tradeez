import { describe, expect, it } from 'vitest'
import { SECTION_TOPICS, topicTone } from './topics'

const TOPICS = [
  'Structure',
  'Liquidity',
  'Gaps & imbalances',
  'Premium & discount',
  'Blocks',
  'Bias',
  'Models',
  'Risk & management',
  'Time & sessions',
]

describe('topicTone (T55)', () => {
  it('gives each of the nine topics a colour of its own', () => {
    const texts = TOPICS.map((t) => topicTone(t).text)
    expect(new Set(texts).size).toBe(TOPICS.length)
    expect(texts).not.toContain('text-subtle')
  })

  it("paints every part of a topic's set in the same colour", () => {
    const { text, soft, edge, stroke, fill } = topicTone('Liquidity')
    for (const cls of [text, soft, edge, stroke, fill]) expect(cls).toContain('t-liquidity')
  })

  it('draws a topic it does not know, or none, in the plain set', () => {
    expect(topicTone('More drills').text).toBe('text-subtle')
    expect(topicTone(undefined).fill).toBe('bg-accent')
  })

  it("names only known topics for Home's sections", () => {
    for (const topic of Object.values(SECTION_TOPICS)) expect(TOPICS).toContain(topic)
  })
})
