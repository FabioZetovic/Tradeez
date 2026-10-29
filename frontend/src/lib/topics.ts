/**
 * A topic's colour as the classes that paint with it (T55). Written out whole, one set a topic, because Tailwind finds
 * its classes by reading the source: a class assembled at run time would never be generated.
 */
export interface Tone {
  /** Text and icons. */
  text: string
  /** A faint wash behind a glyph or a chip. */
  soft: string
  /** A card's border on hover. */
  edge: string
  /** A ring's stroke. */
  stroke: string
  /** A filled bar or dot. */
  fill: string
}

const TONES: Record<string, Tone> = {
  Structure: { text: 'text-t-structure', soft: 'bg-t-structure/16', edge: 'hover:border-t-structure/50', stroke: 'stroke-t-structure', fill: 'bg-t-structure' },
  Liquidity: { text: 'text-t-liquidity', soft: 'bg-t-liquidity/16', edge: 'hover:border-t-liquidity/50', stroke: 'stroke-t-liquidity', fill: 'bg-t-liquidity' },
  'Gaps & imbalances': { text: 'text-t-gaps', soft: 'bg-t-gaps/16', edge: 'hover:border-t-gaps/50', stroke: 'stroke-t-gaps', fill: 'bg-t-gaps' },
  'Premium & discount': { text: 'text-t-premium', soft: 'bg-t-premium/16', edge: 'hover:border-t-premium/50', stroke: 'stroke-t-premium', fill: 'bg-t-premium' },
  Blocks: { text: 'text-t-blocks', soft: 'bg-t-blocks/16', edge: 'hover:border-t-blocks/50', stroke: 'stroke-t-blocks', fill: 'bg-t-blocks' },
  Bias: { text: 'text-t-bias', soft: 'bg-t-bias/16', edge: 'hover:border-t-bias/50', stroke: 'stroke-t-bias', fill: 'bg-t-bias' },
  Models: { text: 'text-t-models', soft: 'bg-t-models/16', edge: 'hover:border-t-models/50', stroke: 'stroke-t-models', fill: 'bg-t-models' },
  'Risk & management': { text: 'text-t-risk', soft: 'bg-t-risk/16', edge: 'hover:border-t-risk/50', stroke: 'stroke-t-risk', fill: 'bg-t-risk' },
  'Time & sessions': { text: 'text-t-time', soft: 'bg-t-time/16', edge: 'hover:border-t-time/50', stroke: 'stroke-t-time', fill: 'bg-t-time' },
}

/** A topic the palette does not know (one the server adds later, "More drills") in the plain one. */
const PLAIN: Tone = { text: 'text-subtle', soft: 'bg-sunken', edge: 'hover:border-accent/40', stroke: 'stroke-accent', fill: 'bg-accent' }

export const topicTone = (topic: string | undefined): Tone => (topic && TONES[topic]) || PLAIN

/** Home's four sections drawn as a topic each (T55), so each step has its own colour and picture. */
export const SECTION_TOPICS = {
  learn: 'Structure',
  drills: 'Liquidity',
  practice: 'Premium & discount',
  chart: 'Gaps & imbalances',
} as const
