import { CLOCK_LAYERS, type ClockLayerKey } from './clock/layers'

/**
 * Every layer the chart can draw, in one list grouped as a trader reads them (T38, docs/REDESIGN.md §5.8): the New
 * York clock's parts (T7) and the rules' detected setups (T9) side by side, so one menu switches either.
 */
export interface Layer {
  /** A clock part's key (camelCase) or a detector's id (UPPER_CASE): the two never collide. */
  id: string
  kind: 'clock' | 'setup'
  label: string
  /** What it draws, and for a rule its concept and version (INVARIANT 8). */
  title: string
  group: LayerGroup
}

export const LAYER_GROUPS = ['Time & sessions', 'Levels', 'Structure', 'Gaps & blocks', 'Models'] as const
type LayerGroup = (typeof LAYER_GROUPS)[number]

/** Lovable's grouping and order; a rule the list does not know yet joins Models at the end. */
const ORDER: Record<LayerGroup, readonly string[]> = {
  'Time & sessions': ['days', 'killzones', 'silverBullet', 'macros', 'sessionLevels', 'ipda'],
  Levels: ['opens', 'previousDay', 'previousWeek', 'LIQUIDITY', 'EQHL', 'OPENING_GAP', 'ORG'],
  Structure: ['SWING', 'SWING_TIER', 'STRUCTURE', 'DISPLACEMENT', 'MSS', 'BOS_CHOCH', 'SWEEP', 'TURTLE_SOUP', 'SMT'],
  'Gaps & blocks': ['FVG', 'FVG_STATE', 'IFVG', 'BPR', 'VI', 'VOID', 'OB', 'BREAKER', 'MITIGATION', 'REJECTION'],
  Models: ['UNICORN', 'DEALING_RANGE', 'PD', 'OTE', 'STDEV', 'PO3', 'JUDAS', 'DAILY_PROFILE', 'WEEKLY_PROFILE'],
}

/**
 * Read on two markets (R-1, R-2): on the Chart tab since T47, with the other market drawn under the chart; still not in
 * a practice replay, which shows one.
 */
export const TWO_MARKET_RULES: ReadonlySet<string> = new Set(['SMT'])

/** One click to a common view. Clean is everything off. */
export const PRESETS: ReadonlyArray<{ name: string; layers: readonly string[] }> = [
  { name: 'Clean', layers: [] },
  { name: 'Session map', layers: ['days', 'killzones', 'opens', 'previousDay'] },
  { name: 'Killzones + FVG', layers: ['killzones', 'FVG', 'opens'] },
  { name: 'Silver Bullet', layers: ['silverBullet', 'FVG', 'LIQUIDITY', 'MSS'] },
]

const CLOCK_KEYS: ReadonlySet<string> = new Set(CLOCK_LAYERS.map((layer) => layer.key))

export function isClockLayer(id: string): id is ClockLayerKey {
  return CLOCK_KEYS.has(id)
}

/**
 * The clock's parts and the given rules as one list, in group order and in order within each group. The two-market rules
 * only where the chart can show the second market (T47).
 */
export function arrangeLayers(
  rules: ReadonlyArray<{ id: string; label: string; title: string }>,
  twoMarkets = false,
): Layer[] {
  const all = [
    ...CLOCK_LAYERS.map((layer) => ({ id: layer.key, kind: 'clock' as const, label: layer.label, title: layer.title })),
    ...rules
      .filter((rule) => twoMarkets || !TWO_MARKET_RULES.has(rule.id))
      .map((rule) => ({ ...rule, kind: 'setup' as const })),
  ]
  const placed = (id: string): [number, number] => {
    for (const [g, group] of LAYER_GROUPS.entries()) {
      const i = ORDER[group].indexOf(id)
      if (i >= 0) return [g, i]
    }
    return [LAYER_GROUPS.length - 1, Number.MAX_SAFE_INTEGER]
  }
  return all
    .map((layer) => ({ layer, at: placed(layer.id) }))
    .sort((a, b) => a.at[0] - b.at[0] || a.at[1] - b.at[1])
    .map(({ layer, at }) => ({ ...layer, group: LAYER_GROUPS[at[0]] }))
}
