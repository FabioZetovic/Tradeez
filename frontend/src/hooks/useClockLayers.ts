import { CLOCK_LAYERS, DEFAULT_CLOCK_LAYERS, type ClockLayerKey } from '@/lib/clock/layers'
import { useToggles, type ToggleConfig } from './useToggles'

const CLOCK_TOGGLES: ToggleConfig<ClockLayerKey> = {
  storageKey: 'tradeez.clockLayers',
  defaults: DEFAULT_CLOCK_LAYERS,
  isKnown: (key): key is ClockLayerKey => CLOCK_LAYERS.some((layer) => layer.key === key),
}

/** Which parts of the New York clock are shown (T7). */
export function useClockLayers() {
  return useToggles(CLOCK_TOGGLES)
}
