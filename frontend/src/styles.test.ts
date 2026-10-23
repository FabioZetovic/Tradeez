/// <reference types="node" />
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'
import { THEME } from './lib/theme'

// Read from disk: the test config sets `css: false`, which turns every CSS import into an empty
// string. (`import.meta.url` is an http URL under jsdom, hence `__dirname`.)
const indexCss = readFileSync(resolve(__dirname, 'index.css'), 'utf8')

/**
 * Guards for the stylesheet, which jsdom cannot render or measure.
 *
 * Since DEC-044 the styling is Tailwind utilities in the components plus the design tokens in
 * index.css. These tests stop the few rules that fixed real bugs from being deleted by someone tidying
 * who does not know why they are there.
 */
describe('index.css', () => {
  it('BUG-031: the app is dark whatever the OS prefers', () => {
    expect(indexCss).toMatch(/color-scheme:\s*dark/)
    // Nothing switches palettes on the OS setting: that is how the title once vanished on a light-mode machine.
    expect(indexCss).not.toMatch(/prefers-color-scheme/)
  })

  it("T36: the tokens and the canvases' palette (lib/theme.ts) are the same colours", () => {
    const tokens: Record<string, string> = {
      canvas: 'canvas', surface: 'surface', raised: 'raised', sunken: 'sunken', line: 'line', ink: 'ink',
      subtle: 'subtle', accent: 'accent', up: 'up', down: 'down', warn: 'warn', newsHigh: 'news-high',
      newsMedium: 'news-medium',
    }
    for (const [key, token] of Object.entries(tokens)) {
      expect(indexCss, token).toMatch(new RegExp(`--color-${token}:\\s*${THEME[key as keyof typeof THEME]}`, 'i'))
    }
  })

  it('T36: none of the old GitHub palette is left', () => {
    for (const old of ['#0d1117', '#161b22', '#58a6ff', '#30363d']) expect(indexCss).not.toContain(old)
  })

  it('BUG-037: carries nothing from the Vite starter template', () => {
    // The template pinned #root to a 1126px bordered column with centred text; that was the layout
    // Fabio saw on a wide screen in session 017.
    expect(indexCss).not.toMatch(/#root/)
    expect(indexCss).not.toMatch(/text-align:\s*center/)
    expect(indexCss).not.toMatch(/1126px/)
  })
})
