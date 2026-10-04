import { useCallback, useEffect, useState } from 'react'
import { PriceChart } from './PriceChart'
import { SimulatedDataBanner } from './SimulatedDataBanner'
import {
  getBars,
  listInstruments,
  TIMEFRAMES,
  type BarSeries,
  type Instrument,
} from '@/lib/api/marketData'
import type { ChartAdapter } from '@/lib/chart/types'
import { formatPrice } from '@/lib/chart/priceFormat'
import { nextAxisTimezone, timezoneLabel, type AxisTimezone } from '@/lib/chart/timeAxis'
import {
  chartWindow,
  clampEnd,
  JUMPS,
  newYorkDate,
  sessionEndOn,
  windowDays,
} from '@/lib/chart/dateNavigation'
import { DrawingOverlay } from './DrawingOverlay'
import { DrawingToolbar } from './DrawingToolbar'
import { useDrawings } from '@/hooks/useDrawings'
import { useDrawingShortcuts } from '@/hooks/useDrawingShortcuts'
import type { SnapMode } from '@/lib/drawing/snap'
import type { DrawingKind } from '@/lib/drawing/types'

/** Candles in view when a window loads; the rest of the window is a scroll away. */
const RECENT_BARS = 200

const DAY = 86_400

interface ChartWorkspaceProps {
  /** Injected in tests so the chart can render without a canvas. */
  adapterFactory?: () => ChartAdapter
}

export function ChartWorkspace({ adapterFactory }: ChartWorkspaceProps) {
  const [instruments, setInstruments] = useState<Instrument[]>([])
  // Real NQ by default (DEC-040); the simulated markets are no longer listed.
  const [symbol, setSymbol] = useState<string>('NQ')
  const [timeframe, setTimeframe] = useState<string>('5m')
  const [series, setSeries] = useState<BarSeries | null>(null)
  // ICT states every time in New York time (DEC-043).
  const [axisTimezone, setAxisTimezone] = useState<AxisTimezone>('new-york')
  // Where the window ends: null follows the newest imported minute.
  const [end, setEnd] = useState<number | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)
  const [activeTool, setActiveTool] = useState<DrawingKind | null>(null)
  const [snapMode, setSnapMode] = useState<SnapMode>('magnet')

  const { state: drawingState, dispatch, commit, retry, syncError, canUndo, canRedo } =
    useDrawings(symbol)

  const deleteSelected = useCallback(() => {
    if (!drawingState.selectedId) return
    dispatch({ type: 'remove', id: drawingState.selectedId })
    void commit()
  }, [drawingState.selectedId, dispatch, commit])

  const cancelTool = useCallback(() => setActiveTool(null), [])
  const persist = useCallback(() => void commit(), [commit])

  useDrawingShortcuts({
    selectedId: drawingState.selectedId,
    dispatch,
    onCancelTool: cancelTool,
    onDeleteSelected: deleteSelected,
    onHistoryChange: persist,
  })

  useEffect(() => {
    listInstruments()
      .then((list) => {
        setInstruments(list)
        // Keep the current choice when it is offered; otherwise take the first instrument.
        setSymbol((current) =>
          list.length === 0 || list.some((i) => i.symbol === current) ? current : list[0].symbol,
        )
      })
      .catch(() => setError('Could not load instruments.'))
  }, [])

  const instrument = instruments.find((i) => i.symbol === symbol)
  const dataFrom = instrument?.dataFrom
  const dataTo = instrument?.dataTo
  // Real history is a bounded window; nothing to load until the instrument says where it ends.
  const windowEnd =
    dataFrom === undefined || dataTo === undefined
      ? null
      : clampEnd(end ?? dataTo, dataFrom, dataTo, windowDays(timeframe) * DAY)
  const noHistory = instrument !== undefined && dataTo === undefined

  useEffect(() => {
    if (windowEnd === null) return
    let cancelled = false
    setLoading(true)
    setError(null)

    const { from, to } = chartWindow(timeframe, windowEnd)
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
  }, [symbol, timeframe, windowEnd])

  const lastBar = series?.bars.at(-1)

  return (
    <section className="workspace">
      {/* The banner reads the data's own dataClass rather than assuming, and waits for the data:
          real NQ must not flash a "simulated" warning while it loads. */}
      {series && <SimulatedDataBanner dataClass={series.dataClass} />}

      <div className="toolbar">
        <label>
          Instrument
          <select
            value={symbol}
            onChange={(e) => {
              setSymbol(e.target.value)
              setEnd(null)
            }}
          >
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

        {instrument && dataFrom !== undefined && dataTo !== undefined && windowEnd !== null && (
          <div className="date-nav" role="group" aria-label="Go to date">
            <label>
              Date
              <input
                type="date"
                data-testid="go-to-date"
                min={newYorkDate(dataFrom)}
                max={newYorkDate(dataTo)}
                value={newYorkDate(windowEnd)}
                onChange={(e) => {
                  if (e.target.value) setEnd(sessionEndOn(e.target.value))
                }}
              />
            </label>
            {JUMPS.map((jump) => (
              <button
                key={jump.label}
                type="button"
                className="tf"
                aria-pressed={jump.days === 0 ? end === null : undefined}
                title={jump.days === 0 ? 'The newest imported data' : `${jump.label} before the newest data`}
                onClick={() => setEnd(jump.days === 0 ? null : dataTo - jump.days * DAY)}
              >
                {jump.label}
              </button>
            ))}
          </div>
        )}

        {instrument && (
          <button
            type="button"
            className="tf"
            data-testid="timezone-toggle"
            title="Time axis: New York → exchange → UTC"
            onClick={() => setAxisTimezone(nextAxisTimezone(axisTimezone))}
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

      {noHistory && (
        <p role="status" data-testid="no-history">
          No real {instrument.symbol} history has been imported yet. Run the import described in
          docs/PATH.md (task T2).
        </p>
      )}

      {/* BUG-023: a save that failed must be visible, or unsaved work looks exactly like saved work. */}
      {syncError && (
        <p role="alert" className="error" data-testid="drawing-sync-error">
          {syncError}{' '}
          <button type="button" className="link" onClick={() => void retry()}>
            Retry now
          </button>
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
            recentBars={RECENT_BARS}
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

      {loading && !noHistory && <p role="status">Loading…</p>}

      {series && instrument && (
        <dl className="kv kv--compact">
          <dt>Bars</dt>
          <dd>{series.bars.length}</dd>
          <dt>Tick</dt>
          <dd>
            {instrument.tickSize} = ${(instrument.tickValueCents / 100).toFixed(2)}
          </dd>
          <dt>Source</dt>
          <dd data-testid="data-source">{series.source}</dd>
          {dataFrom !== undefined && dataTo !== undefined && (
            <>
              <dt>History</dt>
              <dd>
                {newYorkDate(dataFrom)} → {newYorkDate(dataTo)}
              </dd>
            </>
          )}
          {series.seed != null && (
            <>
              <dt>Seed</dt>
              <dd>{series.seed}</dd>
              <dt>Generator</dt>
              <dd>{series.generatorVersion}</dd>
            </>
          )}
        </dl>
      )}
    </section>
  )
}
