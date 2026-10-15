import { useEffect, useMemo, useState } from 'react'
import { getCurriculum, type Curriculum } from '@/lib/api/concepts'
import { useToggles, type ToggleConfig } from './useToggles'

/** One switch per detector the server runs, named and explained from the curriculum (S-2). */
export interface SetupRule {
  id: string
  label: string
  title: string
}

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
}

/** Every rule starts off: a learner should find a gap before being shown one. */
const SETUP_TOGGLES: ToggleConfig<string> = {
  storageKey: 'tradeez.setupLayers',
  defaults: [],
  isKnown: (key): key is string => key.length > 0,
}

/**
 * The detected-setup overlay's switches (T9): the rules on offer, which are on, and — of those —
 * the ones to ask the server for. A failure to read the curriculum offers no switches; the chart
 * itself is unaffected.
 */
export function useSetupLayers() {
  const [rules, setRules] = useState<SetupRule[]>([])
  const { shown, toggle } = useToggles(SETUP_TOGGLES)

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

  const on = useMemo(() => rules.filter((r) => shown.has(r.id)).map((r) => r.id), [rules, shown])
  return { rules, shown, toggle, on }
}

function rulesOf(curriculum: Curriculum): SetupRule[] {
  return curriculum.concepts.flatMap(({ concept, detectors }) =>
    detectors.map((rule) => ({
      id: rule.detector,
      label: LABELS[rule.detector] ?? rule.detector,
      title: `${concept.name} (${concept.id}), rule ${rule.version}. ${concept.summary}`,
    })),
  )
}
