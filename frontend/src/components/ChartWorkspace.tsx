import { useCallback, useEffect, useState } from 'react'
import { PriceChart } from './PriceChart'
import { SimulatedDataBanner } from './SimulatedDataBanner'
import {
  demoWindow,
  getBars,
  listInstruments,
  TIMEFRAMES,
  type BarSeries,
  type Instrument,
} from '@/lib/api/marketData'
import type { ChartAdapter } from '@/lib/chart/types'
import { formatPrice } from '@/lib/chart/priceFormat'
import { timezoneLabel, type AxisTimezone } from '@/lib/chart/timeAxis'
import { DrawingOverlay } from './DrawingOverlay'
import { DrawingToolbar } from './DrawingToolbar'
import { useDrawings } from '@/hooks/useDrawings'
import type { SnapMode } from '@/lib/drawing/snap'
import type { DrawingKind } from '@/lib/drawing/types'

interface ChartWorkspaceProps {
  /** Injected in tests so the chart can render without a canvas. */
  adapterFactory?: () => ChartAdapter
}

export function ChartWorkspace({ adapterFactory }: ChartWorkspaceProps) {
  const [instruments, setInstruments] = useState<Instrument[]>([])
  const [symbol, setSymbol] = useState<string>('DEMO-NQ')
  const [timeframe, setTimeframe] = useState<string>('5m')
  const [series, setSeries] = useState<BarSeries | null>(null)
  const [axisTimezone, setAxisTimezone] = useState<AxisTimezone>('exchange')
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)
  const [activeTool, setActiveTool] = useState<DrawingKind | null>(null)
  const [snapMode, setSnapMode] = useState<SnapMode>('magnet')

  const { state: drawingState, dispatch, commit, canUndo, canRedo } = useDrawings(symbol)

  const deleteSelected = useCallback(() => {
    if (!drawingState.selectedId) return
    dispatch({ type: 'remove', id: drawingState.selectedId })
    void commit()
  }, [drawingState.selectedId, dispatch, commit])

  // Keyboard shortcuts. Bound on the window rather than the canvas because a canvas cannot hold
  // focus without a tabindex, and a chart that steals focus from the page is worse than one that
  // shares its shortcuts.
  useEffect(() => {
    function onKeyDown(event: KeyboardEvent) {
      const target = event.target as HTMLElement | null
      if (target && /^(INPUT|TEXTAREA|SELECT)$/.test(target.tagName)) return

      if (event.key === 'Escape') {
        setActiveTool(null)
        dispatch({ type: 'select', id: null })
        return
      }
      if ((event.key === 'Delete' || event.key === 'Backspace') && drawingState.selectedId) {
        event.preventDefault()
        deleteSelected()
        return
      }
      if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'z') {
        event.preventDefault()
        dispatch({ type: event.shiftKey ? 'redo' : 'undo' })
        void commit()
      }
    }

    window.addEventListener('keydown', onKeyDown)
    return () => window.removeEventListener('keydown', onKeyDown)
  }, [dispatch, commit, deleteSelected, drawingState.selectedId])

  useEffect(() => {
    listInstruments()
      .then(setInstruments)
      .catch(() => setError('Could not load instruments.'))
  }, [])

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    setError(null)

    const { from, to } = demoWindow(timeframe)
    getBars({ symbol, timeframe, from, to })
      .then((result) => {
        if (!cancelled) setSeries(result)
      })
      .catch(() => {
        if (!cancelled) setError('Could not load bars.')
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })

    return () => {
      cancelled = true
    }
  }, [symbol, timeframe])

  const instrument = instruments.find((i) => i.symbol === symbol)
  const lastBar = series?.bars.at(-1)

  return (
    <section className="workspace">
      {/* The banner reads the data's own dataClass rather than assuming. When real licensed data
          eventually arrives, this disappears on its own instead of needing to be remembered. */}
      <SimulatedDataBanner dataClass={series?.dataClass ?? 'SIMULATED'} />

      <div className="toolbar">
        <label>
          Instrument
          <select value={symbol} onChange={(e) => setSymbol(e.target.value)}>
            {instruments.map((i) => (
              <option key={i.symbol} value={i.symbol}>
                {i.symbol}
              </option>
            ))}
          </select>
        </label>

        <div className="timeframes" role="group" aria-label="Timeframe">
          {TIMEFRAMES.map((tf) => (
            <button
              key={tf}
              type="button"
              aria-pressed={tf === timeframe}
              className={tf === timeframe ? 'tf tf--active' : 'tf'}
              onClick={() => setTimeframe(tf)}
            >
              {tf}
            </button>
          ))}
        </div>

        {instrument && (
          <button
            type="button"
            className="tf"
            data-testid="timezone-toggle"
            title="Switch the time axis between exchange-local and UTC"
            onClick={() => setAxisTimezone(axisTimezone === 'utc' ? 'exchange' : 'utc')}
          >
            {timezoneLabel(instrument.exchangeTimezone, axisTimezone, new Date())}
          </button>
        )}

        {instrument && lastBar && (
          <span className="last-price" data-testid="last-price">
            {formatPrice(lastBar.close, instrument.tickSize)}
          </span>
        )}
      </div>

      {error && (
        <p role="alert" className="error">
          {error}
        </p>
      )}

      {instrument && series && !error && (
        <div className="chart-area">
          <DrawingToolbar
            activeTool={activeTool}
            onSelectTool={setActiveTool}
            snapMode={snapMode}
            onToggleSnap={() => setSnapMode(snapMode === 'magnet' ? 'off' : 'magnet')}
            canUndo={canUndo}
            canRedo={canRedo}
            onUndo={() => {
              dispatch({ type: 'undo' })
              void commit()
            }}
            onRedo={() => {
              dispatch({ type: 'redo' })
              void commit()
            }}
            hasSelection={drawingState.selectedId !== null}
            onDelete={deleteSelected}
          />

          <PriceChart
            bars={series.bars}
            tickSize={instrument.tickSize}
            exchangeTimezone={instrument.exchangeTimezone}
            axisTimezone={axisTimezone}
            adapterFactory={adapterFactory}
            overlay={(adapter) => (
              <DrawingOverlay
                adapter={adapter}
                state={drawingState}
                dispatch={dispatch}
                activeTool={activeTool}
                onToolFinished={() => setActiveTool(null)}
                bars={series.bars}
                snapMode={snapMode}
                tickValueCents={instrument.tickValueCents}
                onCommit={() => void commit()}
              />
            )}
          />
        </div>
      )}

      {loading && <p role="status">Loading…</p>}

      {series && instrument && (
        <dl className="kv kv--compact">
          <dt>Bars</dt>
          <dd>{series.bars.length}</dd>
          <dt>Tick</dt>
          <dd>
            {instrument.tickSize} = ${(instrument.tickValueCents / 100).toFixed(2)}
          </dd>
          <dt>Seed</dt>
          <dd>{series.seed}</dd>
          <dt>Generator</dt>
          <dd>{series.generatorVersion}</dd>
        </dl>
      )}
    </section>
  )
}
