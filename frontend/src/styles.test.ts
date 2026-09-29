/// <reference types="node" />
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

// Read from disk: the test config sets `css: false`, which turns every CSS import — even `?raw` —
// into an empty string. (`import.meta.url` is an http URL under jsdom, hence `__dirname`.)
const appCss = readFileSync(resolve(__dirname, 'App.css'), 'utf8')

/**
 * Guards for two layout fixes that jsdom cannot see.
 *
 * jsdom does no layout and ignores stylesheets, so it cannot render a colour or measure an overflow.
 * Both fixes were verified in a browser (session 015). These tests only stop the rules that fixed
 * them from being deleted by someone tidying the CSS who does not know why they are there.
 */
function rule(selector: string): string {
  const escaped = selector.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
  const pattern = new RegExp(`(^|\\n)${escaped}\\s*\\{([^}]*)\\}`, 'g')
  return [...appCss.matchAll(pattern)].map((m) => m[2]).join('\n')
}

describe('App.css', () => {
  it('BUG-031: owns --text-h, so headings and code stay light whatever the OS theme', () => {
    expect(rule(':root')).toMatch(/--text-h:\s*#e6edf3/)
  })

  it('BUG-032: gives the shell a definite width, so the chart cannot hold it wider than the page', () => {
    const shell = rule('.shell')
    expect(shell).toMatch(/width:\s*100%/)
    expect(shell).toMatch(/min-width:\s*0/)
  })
})
