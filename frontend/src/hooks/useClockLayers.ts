import { useCallback, useEffect, useState } from 'react'
import { CLOCK_LAYERS, DEFAULT_CLOCK_LAYERS, type ClockLayerKey } from '@/lib/clock/layers'

const STORAGE_KEY = 'tradeez.clockLayers'

/**
 * Which parts of the New York clock are shown — one choice for the Chart tab and practice alike,
 * remembered in this browser. A convenience only: storage that is blocked or cleared just means the
 * defaults again.
 */
export function useClockLayers() {
  const [shown, setShown] = useState<ReadonlySet<ClockLayerKey>>(load)

  useEffect(() => {
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify([...shown]))
    } catch {
      // Private window or blocked storage: the choice lasts until the page closes.
    }
  }, [shown])

  const toggle = useCallback((key: ClockLayerKey) => {
    setShown((current) => {
      const next = new Set(current)
      if (next.has(key)) next.delete(key)
      else next.add(key)
      return next
    })
  }, [])

  return { shown, toggle }
}

function load(): ReadonlySet<ClockLayerKey> {
  try {
    const stored = JSON.parse(localStorage.getItem(STORAGE_KEY) ?? 'null') as unknown
    if (Array.isArray(stored)) {
      const known = new Set<string>(CLOCK_LAYERS.map((l) => l.key))
      return new Set(stored.filter((k): k is ClockLayerKey => known.has(k)))
    }
  } catch {
    // Unreadable or blocked: fall through to the defaults.
  }
  return new Set(DEFAULT_CLOCK_LAYERS)
}
