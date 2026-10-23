import { useCallback, useEffect, useState } from 'react'

/** A set of switches remembered in this browser under one key. */
export interface ToggleConfig<K extends string> {
  storageKey: string
  defaults: readonly K[]
  /** Drops what an older version stored and this one no longer has. */
  isKnown: (key: string) => key is K
}

/**
 * Which overlay parts are shown — the New York clock's (T7) or the detected setups' (T9) — one
 * choice for the Chart tab and practice alike. A convenience only: storage that is blocked or
 * cleared just means the defaults again.
 */
export function useToggles<K extends string>(config: ToggleConfig<K>) {
  const [shown, setShown] = useState<ReadonlySet<K>>(() => load(config))

  useEffect(() => {
    try {
      localStorage.setItem(config.storageKey, JSON.stringify([...shown]))
    } catch {
      // Private window or blocked storage: the choice lasts until the page closes.
    }
  }, [config.storageKey, shown])

  const toggle = useCallback((key: K) => {
    setShown((current) => {
      const next = new Set(current)
      if (next.has(key)) next.delete(key)
      else next.add(key)
      return next
    })
  }, [])

  /** Exactly these on, the rest off: a preset, or clearing all. */
  const set = useCallback((keys: Iterable<K>) => setShown(new Set(keys)), [])

  return { shown, toggle, set }
}

function load<K extends string>({ storageKey, defaults, isKnown }: ToggleConfig<K>): ReadonlySet<K> {
  try {
    const stored = JSON.parse(localStorage.getItem(storageKey) ?? 'null') as unknown
    if (Array.isArray(stored)) {
      return new Set(stored.filter((k): k is K => typeof k === 'string' && isKnown(k)))
    }
  } catch {
    // Unreadable or blocked: fall through to the defaults.
  }
  return new Set(defaults)
}
