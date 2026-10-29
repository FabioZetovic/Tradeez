import { useSyncExternalStore } from 'react'

/** Below Tailwind's `md`: the phone layout of T53. */
const PHONE = '(max-width: 767.98px)'

const query = () => (typeof window !== 'undefined' && typeof window.matchMedia === 'function' ? window.matchMedia(PHONE) : null)

function subscribe(onChange: () => void) {
  const list = query()
  list?.addEventListener('change', onChange)
  return () => list?.removeEventListener('change', onChange)
}

/**
 * Whether the screen is a phone's (T53). Decided in code rather than only in CSS wherever the phone and the desktop
 * differ in what is on the page — the tab bar, the bars, the sheets — so the page holds one or the other, never both
 * hidden behind a class. Without a media query (the tests) it is the desktop.
 */
export function useIsPhone(): boolean {
  return useSyncExternalStore(subscribe, () => query()?.matches ?? false, () => false)
}
