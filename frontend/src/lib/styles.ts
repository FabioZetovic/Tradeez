/**
 * Class-name helpers shared by the components (DEC-044). Kept out of components/ui.tsx because a
 * file that exports components should export nothing else — React's fast refresh depends on it.
 */

/** Joins class names, skipping the falsy ones. */
export function cx(...classes: Array<string | false | null | undefined>): string {
  return classes.filter(Boolean).join(' ')
}

/** A panel's title. */
export const panelTitle = 'mb-3 text-lg font-semibold tracking-tight text-ink'

/** Text inputs and selects. */
export const fieldClass =
  'h-10 rounded-lg border border-line bg-canvas/60 px-3.5 text-sm text-ink tabular-nums transition focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/30'

/** A label stacked above its control. */
export const labelClass = 'flex flex-col gap-1.5 text-sm font-medium text-ink'

/** A definition list of label/value pairs. */
export const keyValueClass =
  'grid grid-cols-[auto_1fr] gap-x-6 gap-y-1.5 text-sm [&_dd]:tabular-nums [&_dt]:text-subtle'

/** An error the user should act on. */
export const errorClass = 'text-sm text-down'
