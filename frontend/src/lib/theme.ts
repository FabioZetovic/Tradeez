/**
 * The palette (T36, DEC-078; docs/REDESIGN.md R-3). The canvases — the chart, its overlays, the drill marks —
 * cannot read CSS variables, so the values live here too; `index.css` defines the same ones as tokens, and
 * `styles.test.ts` fails if the two ever disagree.
 *
 * `accent` is the one theme colour: change it here and in index.css, and every "theme coloured" part follows.
 * It never marks a price or a result — green and red mean up and down only.
 */
export const THEME = {
  canvas: '#0e1014',
  surface: '#15181d',
  raised: '#1d2127',
  sunken: '#22262d',
  line: '#262a31',
  ink: '#f0f2f5',
  subtle: '#979ca6',
  accent: '#7c6cff',
  up: '#34d399',
  down: '#f05462',
  warn: '#f0a73a',
  newsHigh: '#e5484d',
  newsMedium: '#f5a524',
} as const

/** Each killzone's own colour: its shading, its strip, its high and low. */
export const KILLZONE_COLORS = {
  Asia: '#b392f0',
  London: '#6ea8fe',
  'NY AM': '#f0a73a',
  'NY PM': '#3fc5c9',
} as const
