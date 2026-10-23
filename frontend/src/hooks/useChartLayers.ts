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

const CLOCK_TOGGLES: ToggleConfig<ClockLayerKey> = {
  storageKey: 'tradeez.clockLayers',
  defaults: DEFAULT_CLOCK_LAYERS,
  isKnown: (key): key is ClockLayerKey => CLOCK_LAYERS.some((layer) => layer.key === key),
}

/** Every rule starts off: a learner should find a gap before being shown one. */
const SETUP_TOGGLES: ToggleConfig<string> = {
  storageKey: 'tradeez.setupLayers',
  defaults: [],
  isKnown: (key): key is string => key.length > 0,
}

/**
 * The chart's layers (T38): the New York clock's parts (T7) and one rule per detector the server runs (T9), named
 * and explained from the curriculum (S-2) — which are on, and of the rules, the ones to ask the server for. One
 * choice for the Chart tab and practice alike, remembered in this browser. A failure to read the curriculum offers
 * the clock alone; the chart itself is unaffected.
 */
export function useChartLayers() {
  const [rules, setRules] = useState<Array<{ id: string; label: string; title: string }>>([])
  const clock = useToggles(CLOCK_TOGGLES)
  const setups = useToggles(SETUP_TOGGLES)

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

  const layers = useMemo(() => arrangeLayers(rules), [rules])
  const setupsOn = useMemo(
    () => rules.filter((r) => setups.shown.has(r.id) && !TWO_MARKET_RULES.has(r.id)).map((r) => r.id),
    [rules, setups.shown],
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
