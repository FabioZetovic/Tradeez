import { useCallback, useId, useMemo, useRef, useState } from 'react'
import { Layers, Search, X } from 'lucide-react'
import type { ChartLayers } from '@/hooks/useChartLayers'
import { LAYER_GROUPS, PRESETS, type Layer } from '@/lib/layers'
import { cx } from '@/lib/styles'
import { Glass, Switch } from '../ui'

interface LayersProps {
  layers: ChartLayers
  /** The clock is drawn on intraday charts only (T35): on a daily chart its switches give way to the reason. */
  clockAvailable: boolean
}

/**
 * Everything the chart can draw, one click away (T38, R-10): the Layers button opens a glass panel over the chart —
 * the chart does not shrink — with a search, presets and the layers in five groups, each a switch.
 */
export function LayersMenu({ layers, clockAvailable }: LayersProps) {
  const [open, setOpen] = useState(false)
  const [query, setQuery] = useState('')
  const root = useRef<HTMLDivElement>(null)
  const close = useCallback(() => setOpen(false), [])
  const searchId = useId()

  const offered = layers.layers.filter((layer) => clockAvailable || layer.kind !== 'clock')
  const count = offered.filter((layer) => layers.isOn(layer.id)).length
  const needle = query.trim().toLowerCase()
  const found = needle
    ? layers.layers.filter((l) => l.label.toLowerCase().includes(needle) || l.title.toLowerCase().includes(needle))
    : layers.layers

  return (
    <div ref={root} className="relative">
      <button
        type="button"
        aria-haspopup="dialog"
        aria-expanded={open}
        onClick={() => setOpen((o) => !o)}
        className={cx(
          'inline-flex h-9 cursor-pointer items-center gap-2 rounded-lg px-3 text-sm font-semibold transition-all',
          'focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent',
          open ? 'bg-accent text-accent-ink' : 'bg-sunken text-ink hover:bg-raised',
        )}
      >
        <Layers aria-hidden className="size-4" />
        Layers
        {count > 0 && (
          <span
            data-testid="layer-count"
            className={cx('num rounded-full px-1.5 text-xs', open ? 'bg-white/20' : 'bg-accent/20 text-accent')}
          >
            {count}
          </span>
        )}
      </button>

      <Glass
        open={open}
        onClose={close}
        anchor={root}
        role="dialog"
        aria-label="Layers"
        className="flex max-h-[70vh] w-[22rem] flex-col"
      >
        <div className="flex items-center justify-between px-4 pt-4 pb-3">
          <h2 className="text-base font-semibold text-ink">Layers</h2>
          <button
            type="button"
            onClick={() => layers.setOnly([])}
            className="cursor-pointer text-xs text-subtle underline-offset-2 hover:text-ink hover:underline"
          >
            Clear all
          </button>
        </div>
        <div className="px-4">
          <label htmlFor={searchId} className="sr-only">
            Search layers
          </label>
          <div className="relative">
            <Search aria-hidden className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-subtle" />
            <input
              id={searchId}
              type="search"
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder={`Search ${layers.layers.length} layers`}
              className="h-9 w-full rounded-lg border border-line bg-canvas/60 pr-3 pl-9 text-sm text-ink placeholder:text-subtle focus:border-accent focus:ring-2 focus:ring-accent/30 focus:outline-none"
            />
          </div>
          <div role="group" aria-label="Presets" className="mt-3 flex flex-wrap gap-1.5">
            {PRESETS.map((preset) => (
              <button
                key={preset.name}
                type="button"
                onClick={() => layers.setOnly(preset.layers)}
                className="cursor-pointer rounded-full border border-line px-2.5 py-1 text-xs text-subtle transition-colors hover:border-accent/60 hover:text-ink"
              >
                {preset.name}
              </button>
            ))}
          </div>
        </div>

        <div className="mt-3 min-h-0 flex-1 overflow-y-auto px-2 pb-3">
          {LAYER_GROUPS.map((group) => {
            const rows = found.filter((layer) => layer.group === group)
            if (rows.length === 0) return null
            return (
              <section key={group} aria-label={group} className="mt-2 first:mt-0">
                <h3 className="px-2 py-1.5 text-sm font-medium text-subtle">{group}</h3>
                {rows.map((layer) => (
                  <LayerRow
                    key={layer.id}
                    layer={layer}
                    on={layers.isOn(layer.id)}
                    available={clockAvailable || layer.kind !== 'clock'}
                    onToggle={() => layers.toggle(layer.id)}
                  />
                ))}
              </section>
            )
          })}
          {found.length === 0 && <p className="px-2 py-3 text-sm text-subtle">No layer matches “{query.trim()}”.</p>}
        </div>
      </Glass>
    </div>
  )
}

function LayerRow({
  layer,
  on,
  available,
  onToggle,
}: {
  layer: Layer
  on: boolean
  available: boolean
  onToggle: () => void
}) {
  const nameId = useId()
  return (
    <div className="flex items-center justify-between gap-3 rounded-lg px-2 py-1.5 hover:bg-sunken/60">
      <span id={nameId} title={layer.title} className={cx('text-sm', available ? 'text-ink' : 'text-subtle')}>
        {layer.label}
        {!available && <span className="ml-2 text-xs">intraday charts, up to 4h</span>}
      </span>
      <Switch checked={on && available} disabled={!available} onChange={onToggle} aria-labelledby={nameId} />
    </div>
  )
}

/** The layers that are on, as chips under the chart's top bar; × switches one off. */
export function LayerChips({ layers, clockAvailable }: LayersProps) {
  const on = useMemo(
    () => layers.layers.filter((layer) => layers.isOn(layer.id) && (clockAvailable || layer.kind !== 'clock')),
    [layers, clockAvailable],
  )
  if (on.length === 0) return null
  return (
    <ul aria-label="Layers on" className="flex flex-wrap gap-1.5">
      {on.map((layer) => (
        <li
          key={layer.id}
          title={layer.title}
          className="inline-flex items-center gap-1 rounded-full bg-accent/15 py-0.5 pr-1 pl-2.5 text-xs font-medium text-accent"
        >
          {layer.label}
          <button
            type="button"
            aria-label={`Remove ${layer.label}`}
            onClick={() => layers.toggle(layer.id)}
            className="grid size-4 cursor-pointer place-items-center rounded-full hover:bg-accent/25"
          >
            <X aria-hidden className="size-3" />
          </button>
        </li>
      ))}
    </ul>
  )
}
