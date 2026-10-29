import { vi } from 'vitest'

/**
 * The test runs on a phone's screen (T53): the media query useIsPhone asks matches. Undone by vi.unstubAllGlobals,
 * which every file using it calls after each test.
 */
export function onPhone() {
  vi.stubGlobal('matchMedia', (query: string) => ({
    matches: query.includes('max-width'),
    media: query,
    addEventListener: () => {},
    removeEventListener: () => {},
  }))
}
