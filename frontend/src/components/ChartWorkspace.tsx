import { useCallback, useEffect, useState } from 'react'
import { PriceChart } from './PriceChart'
import { SimulatedDataBanner } from './SimulatedDataBanner'
import {
  getBars,
  listInstruments,
  TIMEFRAMES,
  type BarSeries,
  type Instrument,
  type TimeframeCode,
} from '@/lib/api/marketData'
import { getChartClock, type ClockLayer } from '@/lib/api/clock'
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
import { ClockOverlay } from './ClockOverlay'
import { ClockToggles } from './ClockToggles'
import { DrawingOverlay } from './DrawingOverlay'
import { DrawingToolbar } from './DrawingToolbar'
import { useClockLayers } from '@/hooks/useClockLayers'
import { useDrawings } from '@/hooks/useDrawings'
import { useDrawingShortcuts } from '@/hooks/useDrawingShortcuts'
import type { SnapMode } from '@/lib/drawing/snap'
import type { DrawingKind } from '@/lib/drawing/types'
import { Button, Segmented } from './ui'
import { cx, errorClass, fieldClass } from '@/lib/styles'

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
  const [timeframe, setTimeframe] = useState<TimeframeCode>('5m')
  // The latest bars response, tagged with the request it answers (see `requestKey` below).
  const [loaded, setLoaded] = useState<{ key: string; series?: BarSeries; error?: string } | null>(null)
  // ICT states every time in New York time (DEC-043).
  const [axisTimezone, setAxisTimezone] = useState<AxisTimezone>('new-york')
  // Where the window ends: null follows the newest imported minute.
  const [end, setEnd] = useState<number | null>(null)
  const [instrumentsError, setInstrumentsError] = useState<string | null>(null)
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
      .catch(() => setInstrumentsError('Could not load instruments.'))
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

  // Loading and error are derived, not stored: a response counts only for the request it answers, so
  // "loading" is simply "the newest response is not for the current request yet". Nothing has to be
  // reset when a request starts (BUG-035: no synchronous setState inside an effect).
  const requestKey = windowEnd === null ? null : `${symbol}|${timeframe}|${windowEnd}`
  useEffect(() => {
    if (windowEnd === null || requestKey === null) return
    let cancelled = false
    const { from, to } = chartWindow(timeframe, windowEnd)
    getBars({ symbol, timeframe, from, to })
      .then((result) => {
        if (!cancelled) setLoaded({ key: requestKey, series: result })
      })
      .catch(() => {
        if (!cancelled) setLoaded({ key: requestKey, error: 'Could not load bars.' })
      })
    return () => {
      cancelled = true
    }
  }, [symbol, timeframe, windowEnd, requestKey])

  // The New York clock for the same window (T7). A failure costs only the overlay, never the chart.
  const [clock, setClock] = useState<{ key: string; layer: ClockLayer | null } | null>(null)
  const { shown: clockShown, toggle: toggleClock } = useClockLayers()
  useEffect(() => {
    if (windowEnd === null || requestKey === null) return
    let cancelled = false
    const { from, to } = chartWindow(timeframe, windowEnd)
    getChartClock(symbol, from, to)
      .then((layer) => {
        if (!cancelled) setClock({ key: requestKey, layer })
      })
      .catch(() => {
        if (!cancelled) setClock({ key: requestKey, layer: null })
      })
    return () => {
      cancelled = true
    }
  }, [symbol, timeframe, windowEnd, requestKey])

  const current = loaded !== null && loaded.key === requestKey ? loaded : null
  const loading = requestKey === null ? !noHistory : current === null
  const error = instrumentsError ?? current?.error ?? null
  // The previous chart stays up while the next window loads, instead of flashing empty.
  const series = current ? (current.series ?? null) : (loaded?.series ?? null)

  const lastBar = series?.bars.at(-1)

  return (
    <section className="flex flex-col gap-4">
      {/* The banner reads the data's own dataClass rather than assuming, and waits for the data:
          real NQ must not flash a "simulated" warning while it loads. */}
      {series && <SimulatedDataBanner dataClass={series.dataClass} />}

      <div className="flex flex-wrap items-center gap-x-5 gap-y-3 rounded-lg border border-line bg-surface px-4 py-3">
        <label className="flex items-center gap-2 text-xs font-medium text-subtle">
          Instrument
          <select
            className={fieldClass}
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

        <Segmented options={TIMEFRAMES} value={timeframe} onChange={setTimeframe} label="Timeframe" />

        {instrument && dataFrom !== undefined && dataTo !== undefined && windowEnd !== null && (
          <div role="group" aria-label="Go to date" className="flex flex-wrap items-center gap-2">
            <label className="flex items-center gap-2 text-xs font-medium text-subtle">
              Date
              <input
                type="date"
                data-testid="go-to-date"
                className={fieldClass}
                min={newYorkDate(dataFrom)}
                max={newYorkDate(dataTo)}
                value={newYorkDate(windowEnd)}
                onChange={(e) => {
                  if (e.target.value) setEnd(sessionEndOn(e.target.value))
                }}
              />
            </label>
            <div className="inline-flex rounded-md border border-line bg-canvas p-0.5">
              {JUMPS.map((jump) => {
                const latest = jump.days === 0
                return (
                  <button
                    key={jump.label}
                    type="button"
                    aria-pressed={latest ? end === null : undefined}
                    title={latest ? 'The newest imported data' : `${jump.label} before the newest data`}
                    onClick={() => setEnd(latest ? null : dataTo - jump.days * DAY)}
                    className={cx(
                      'h-7 cursor-pointer rounded px-2.5 text-xs font-medium transition-colors',
                      latest && end === null ? 'bg-brand/20 text-brand' : 'text-subtle hover:text-ink',
                    )}
                  >
                    {jump.label}
                  </button>
                )
              })}
            </div>
          </div>
        )}

        {instrument && (
          <Button
            size="sm"
            data-testid="timezone-toggle"
            title="Time axis: New York → exchange → UTC"
            onClick={() => setAxisTimezone(nextAxisTimezone(axisTimezone))}
          >
            {timezoneLabel(instrument.exchangeTimezone, axisTimezone, new Date())}
          </Button>
        )}

        {instrument && lastBar && (
          <span className="ml-auto text-xl font-semibold tabular-nums" data-testid="last-price">
            {formatPrice(lastBar.close, instrument.tickSize)}
          </span>
        )}
      </div>

      {error && (
        <p role="alert" className={errorClass}>
          {error}
        </p>
      )}

      {noHistory && (
        <p role="status" data-testid="no-history" className="text-sm text-subtle">
          No real {instrument.symbol} history has been imported yet. Run the import described in
          docs/PATH.md (task T2).
        </p>
      )}

      {/* BUG-023: a save that failed must be visible, or unsaved work looks exactly like saved work. */}
      {syncError && (
        <p role="alert" className={errorClass} data-testid="drawing-sync-error">
          {syncError}{' '}
          <Button variant="link" onClick={() => void retry()}>
            Retry now
          </Button>
        </p>
      )}

      {instrument && series && !error && (
        <div className="self-start">
          <ClockToggles shown={clockShown} onToggle={toggleClock} />
        </div>
      )}

      {instrument && series && !error && (
        <div className="flex min-w-0 items-start gap-2">
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
            heightClass="h-[calc(100svh-15rem)] min-h-[26rem]"
            bars={series.bars}
            tickSize={instrument.tickSize}
            exchangeTimezone={instrument.exchangeTimezone}
            axisTimezone={axisTimezone}
            adapterFactory={adapterFactory}
            recentBars={RECENT_BARS}
            overlay={(adapter) => (
              <>
                <ClockOverlay
                  adapter={adapter}
                  layer={clock?.layer ?? null}
                  bars={series.bars}
                  timeframe={series.timeframe}
                  tickSize={instrument.tickSize}
                  shown={clockShown}
                />
                <DrawingOverlay
                  adapter={adapter}
                  state={drawingState}
                  dispatch={dispatch}
                  activeTool={activeTool}
                  onToolFinished={() => setActiveTool(null)}
                  bars={series.bars}
                  snapMode={snapMode}
                  tickValueCents={instrument.tickValueCents}
                  tickSize={instrument.tickSize}
                  onCommit={() => void commit()}
                />
              </>
            )}
          />
        </div>
      )}

      {loading && (
        <p role="status" className="text-sm text-subtle">
          Loading…
        </p>
      )}

      {series && instrument && (
        <dl className="flex flex-wrap gap-x-6 gap-y-1 text-xs text-subtle [&_dd]:ml-1.5 [&_dd]:inline [&_dd]:text-ink [&_dd]:tabular-nums [&_dt]:inline">
          <div>
            <dt>Bars</dt>
            <dd>{series.bars.length}</dd>
          </div>
          <div>
            <dt>Tick</dt>
            <dd>
              {instrument.tickSize} = ${(instrument.tickValueCents / 100).toFixed(2)}
            </dd>
          </div>
          <div>
            <dt>Source</dt>
            <dd data-testid="data-source">{series.source}</dd>
          </div>
          {dataFrom !== undefined && dataTo !== undefined && (
            <div>
              <dt>History</dt>
              <dd>
                {newYorkDate(dataFrom)} → {newYorkDate(dataTo)}
              </dd>
            </div>
          )}
        </dl>
      )}
    </section>
  )
}
