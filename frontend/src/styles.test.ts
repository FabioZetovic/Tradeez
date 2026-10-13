/// <reference types="node" />
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

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

  it('defines the palette the chart shares (lightweightAdapter.ts uses the same values)', () => {
    expect(indexCss).toMatch(/--color-ink:\s*#e6edf3/)
    expect(indexCss).toMatch(/--color-subtle:\s*#8b949e/)
    expect(indexCss).toMatch(/--color-line:\s*#30363d/)
    expect(indexCss).toMatch(/--color-up:\s*#3fb950/)
    expect(indexCss).toMatch(/--color-down:\s*#f85149/)
  })

  it('BUG-037: carries nothing from the Vite starter template', () => {
    // The template pinned #root to a 1126px bordered column with centred text; that was the layout
    // Fabio saw on a wide screen in session 017.
    expect(indexCss).not.toMatch(/#root/)
    expect(indexCss).not.toMatch(/text-align:\s*center/)
    expect(indexCss).not.toMatch(/1126px/)
  })
})
