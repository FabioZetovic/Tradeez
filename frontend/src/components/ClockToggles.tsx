import { CLOCK_LAYERS, type ClockLayerKey } from '@/lib/clock/layers'
import { cx } from '@/lib/styles'

/**
 * Switches for the parts of the New York clock (T7). Several can be on at once. Where the chart
 * draws no clock (a daily chart, T35) they give way to the reason.
 */
export function ClockToggles({
  shown,
  onToggle,
  available = true,
}: {
  shown: ReadonlySet<ClockLayerKey>
  onToggle: (key: ClockLayerKey) => void
  available?: boolean
}) {
  return (
    <div
      role="group"
      aria-label="New York clock"
      className="inline-flex flex-wrap items-center gap-1 rounded-md border border-line bg-canvas p-0.5"
    >
      <span className="px-1.5 text-xs font-medium text-subtle">NY clock</span>
      {!available && <span className="h-7 px-2 text-xs leading-7 text-subtle">On intraday charts, up to 4h</span>}
      {available && CLOCK_LAYERS.map((layer) => {
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
              on ? 'bg-accent/20 text-accent' : 'text-subtle hover:text-ink',
            )}
          >
            {layer.label}
          </button>
        )
      })}
    </div>
  )
}
