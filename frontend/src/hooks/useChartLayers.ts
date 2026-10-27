import { useCallback, useEffect, useMemo, useState } from 'react'
import { getCurriculum, type Curriculum } from '@/lib/api/concepts'
import { CLOCK_LAYERS, DEFAULT_CLOCK_LAYERS, type ClockLayerKey } from '@/lib/clock/layers'
import { arrangeLayers, isClockLayer, TWO_MARKET_RULES } from '@/lib/layers'
import { useToggles, type ToggleConfig } from './useToggles'

/** Button text where the detector's id would read badly; otherwise the id itself (FVG, BPR, VI). */
const LABELS: Record<string, string> = {
  FVG_STATE: 'FVG state',
  OPENING_GAP: 'NWOG/NDOG',
  VOID: 'Gaps/voids',
  SWING: 'Swings',
  SWING_TIER: 'ITH/LTH',
  STRUCTURE: 'HH/HL',
  BOS_CHOCH: 'BOS/CHoCH',
  DISPLACEMENT: 'Displacement',
  LIQUIDITY: 'BSL/SSL',
  EQHL: 'EQH/EQL',
  SWEEP: 'Sweep/run',
  TURTLE_SOUP: 'Turtle soup',
  BREAKER: 'Breaker',
  MITIGATION: 'Mitigation',
  REJECTION: 'Rejection',
  UNICORN: 'Unicorn',
  DEALING_RANGE: 'Dealing range',
  PD: 'Premium/discount',
  STDEV: 'Std dev',
  PO3: 'Po3',
  DAILY_PROFILE: 'Daily profile',
  WEEKLY_PROFILE: 'Weekly profile',
  JUDAS: 'Judas',
}

const isClockKey = (key: string): key is ClockLayerKey => CLOCK_LAYERS.some((layer) => layer.key === key)

/**
 * The Chart tab and a practice replay each keep their own choice (T42). The Chart tab opens on the killzones, the
 * opens and PDH/PDL; practice on nothing at all — Fabio: "45 indicators, chart is flashing" — so a learner reads the
 * candles first. Every rule starts off everywhere: a learner should find a gap before being shown one.
 */
const TOGGLES = {
  chart: {
    clock: { storageKey: 'tradeez.clockLayers', defaults: DEFAULT_CLOCK_LAYERS, isKnown: isClockKey },
    setups: { storageKey: 'tradeez.setupLayers', defaults: [], isKnown: (key): key is string => key.length > 0 },
  },
  practice: {
    clock: { storageKey: 'tradeez.practice.clockLayers', defaults: [], isKnown: isClockKey },
    setups: { storageKey: 'tradeez.practice.setupLayers', defaults: [], isKnown: (key): key is string => key.length > 0 },
  },
} satisfies Record<string, { clock: ToggleConfig<ClockLayerKey>; setups: ToggleConfig<string> }>

/**
 * The chart's layers (T38): the New York clock's parts (T7) and one rule per detector the server runs (T9), named
 * and explained from the curriculum (S-2) — which are on, and of the rules, the ones to ask the server for —
 * remembered in this browser for the Chart tab and for practice apart (T42). A failure to read the curriculum offers
 * the clock alone; the chart itself is unaffected.
 */
export function useChartLayers(scope: keyof typeof TOGGLES = 'chart') {
  const [rules, setRules] = useState<Array<{ id: string; label: string; title: string }>>([])
  const clock = useToggles(TOGGLES[scope].clock)
  const setups = useToggles(TOGGLES[scope].setups)

  useEffect(() => {
    let cancelled = false
    getCurriculum()
      .then((curriculum) => {
        if (!cancelled) setRules(rulesOf(curriculum))
      })
      .catch(() => {})
    return () => {
      cancelled = true
    }
  }, [])

  // The Chart tab draws the other market under the chart (T47); a practice replay shows one market only.
  const twoMarkets = scope === 'chart'
  const layers = useMemo(() => arrangeLayers(rules, twoMarkets), [rules, twoMarkets])
  const setupsOn = useMemo(
    () => rules.filter((r) => setups.shown.has(r.id) && (twoMarkets || !TWO_MARKET_RULES.has(r.id))).map((r) => r.id),
    [rules, setups.shown, twoMarkets],
  )

  const { toggle: toggleClock, set: setClock } = clock
  const { toggle: toggleSetup, set: setSetups } = setups
  const toggle = useCallback(
    (id: string) => (isClockLayer(id) ? toggleClock(id) : toggleSetup(id)),
    [toggleClock, toggleSetup],
  )
  /** Exactly these on: a preset, or [] to clear all. */
  const setOnly = useCallback(
    (ids: readonly string[]) => {
      setClock(ids.filter(isClockLayer))
      setSetups(ids.filter((id) => !isClockLayer(id)))
    },
    [setClock, setSetups],
  )
  const isOn = useCallback(
    (id: string) => (isClockLayer(id) ? clock.shown.has(id) : setups.shown.has(id)),
    [clock.shown, setups.shown],
  )

  return { layers, isOn, toggle, setOnly, clockShown: clock.shown, setupsShown: setups.shown, setupsOn }
}

export type ChartLayers = ReturnType<typeof useChartLayers>

function rulesOf(curriculum: Curriculum) {
  return curriculum.concepts.flatMap(({ concept, detectors }) =>
    detectors.map((rule) => ({
      id: rule.detector,
      label: LABELS[rule.detector] ?? rule.detector,
      title: `${concept.name} (${concept.id}), rule ${rule.version}. ${concept.summary}`,
    })),
  )
}
