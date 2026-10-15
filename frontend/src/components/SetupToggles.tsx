import type { SetupRule } from '@/hooks/useSetupLayers'
import { cx } from '@/lib/styles'

/**
 * Switches for the detected-setup overlay (T9), one per rule the server runs. Each names the rule
 * it draws: a box is "what fvg-1.0.0 found", never "where the gaps are" (INVARIANT 8).
 */
export function SetupToggles({
  rules,
  shown,
  onToggle,
}: {
  rules: readonly SetupRule[]
  shown: ReadonlySet<string>
  onToggle: (id: string) => void
}) {
  if (rules.length === 0) return null
  return (
    <div
      role="group"
      aria-label="Detected setups"
      className="inline-flex flex-wrap items-center gap-1 rounded-md border border-line bg-canvas p-0.5"
    >
      <span className="px-1.5 text-xs font-medium text-subtle">Setups</span>
      {rules.map((rule) => {
        const on = shown.has(rule.id)
        return (
          <button
            key={rule.id}
            type="button"
            aria-pressed={on}
            title={rule.title}
            onClick={() => onToggle(rule.id)}
            className={cx(
              'h-7 cursor-pointer rounded px-2 text-xs font-medium transition-colors',
              on ? 'bg-brand/20 text-brand' : 'text-subtle hover:text-ink',
            )}
          >
            {rule.id}
          </button>
        )
      })}
    </div>
  )
}
