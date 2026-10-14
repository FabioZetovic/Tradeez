import { CLOCK_LAYERS, type ClockLayerKey } from '@/lib/clock/layers'
import { cx } from '@/lib/styles'

/** Switches for the parts of the New York clock (T7). Several can be on at once. */
export function ClockToggles({
  shown,
  onToggle,
}: {
  shown: ReadonlySet<ClockLayerKey>
  onToggle: (key: ClockLayerKey) => void
}) {
  return (
    <div
      role="group"
      aria-label="New York clock"
      className="inline-flex flex-wrap items-center gap-1 rounded-md border border-line bg-canvas p-0.5"
    >
      <span className="px-1.5 text-xs font-medium text-subtle">NY clock</span>
      {CLOCK_LAYERS.map((layer) => {
        const on = shown.has(layer.key)
        return (
          <button
            key={layer.key}
            type="button"
            aria-pressed={on}
            title={layer.title}
            onClick={() => onToggle(layer.key)}
            className={cx(
              'h-7 cursor-pointer rounded px-2 text-xs font-medium transition-colors',
              on ? 'bg-brand/20 text-brand' : 'text-subtle hover:text-ink',
            )}
          >
            {layer.label}
          </button>
        )
      })}
    </div>
  )
}
