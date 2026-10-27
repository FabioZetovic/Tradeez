import { useCallback, useEffect, useState } from 'react'
import { PriceChart } from '../PriceChart'
import { SimulatedDataBanner } from '../SimulatedDataBanner'
import {
  getBars,
  listInstruments,
  TIMEFRAMES,
  type BarSeries,
  type HiddenStretch,
  type Instrument,
  type TimeframeCode,
} from '@/lib/api/marketData'
import { getChartClock, type ClockLayer } from '@/lib/api/clock'
import { getChartSetups, type SetupLayer } from '@/lib/api/setups'
import type { ChartAdapter } from '@/lib/chart/types'
import { formatPrice } from '@/lib/chart/priceFormat'
import { nextAxisTimezone, timezoneLabel, type AxisTimezone } from '@/lib/chart/timeAxis'
import {
  chartWindow,
  clampEnd,
  JUMPS,
  newYorkDate,
  previousSessionClose,
  sessionEndOn,
  windowDays,
} from '@/lib/chart/dateNavigation'
import { ClockOverlay } from '../ClockOverlay'
import { DrawingOverlay } from '../DrawingOverlay'
import { DrawingToolbar } from '../DrawingToolbar'
import { SetupOverlay } from '../SetupOverlay'
import { LayerChips, LayersMenu } from './LayersMenu'
import { drawsClock } from '@/lib/clock/layers'
import { useChartLayers } from '@/hooks/useChartLayers'
import { useDrawings } from '@/hooks/useDrawings'
import { useDrawingShortcuts } from '@/hooks/useDrawingShortcuts'
import type { SnapMode } from '@/lib/drawing/snap'
import type { DrawingKind } from '@/lib/drawing/types'
import { Button, Notice, Segmented } from '../ui'
import { cx, errorClass } from '@/lib/styles'

/** Why candles are missing from the window, and what brings them back (BUG-085, BUG-086). */
function hiddenNotice(hidden: HiddenStretch[]): string {
  const practice = hidden.some((s) => s.why === 'PRACTICE')
  const drill = hidden.some((s) => s.why === 'DRILL')
  if (practice && drill) {
    return 'Some candles are held back while you are graded by them: the practice session you are in replays them, and your unanswered drill is decided by them. End the session in Practice and answer the drill to see them here.'
  }
  return practice
    ? 'Some candles are held back: the practice session you are in replays them, and seeing them here would show its future. End the session in Practice to see them here.'
    : 'Some candles are held back: the drill you have not answered yet is decided by them. Answer it to see them here.'
}

/** Candles in view when a window loads; the rest of the window is a scroll away. */
const RECENT_BARS = 200

const DAY = 86_400

/** NQ only (R-1): ES is read beside NQ in the SMT lesson and drill, not on this tab (R-2). */
const SYMBOL = 'NQ'

interface ChartPageProps {
  /** Injected in tests so the chart can render without a canvas. */
  adapterFactory?: () => ChartAdapter
}

/**
 * The Chart tab (T38): two years of real NQ filling the window — the slim drawing bar on the left, the chart card
 * with its top bar, the layers on as chips, and a status line. Every layer is one click away in the glass menu.
 */
export function ChartPage({ adapterFactory }: ChartPageProps) {
  const [instrument, setInstrument] = useState<Instrument | null>(null)
  const [instrumentError, setInstrumentError] = useState<string | null>(null)
  const [timeframe, setTimeframe] = useState<TimeframeCode>('5m')
  // The latest bars response, tagged with the request it answers (see `requestKey` below).
  const [loaded, setLoaded] = useState<{ key: string; series?: BarSeries; error?: string } | null>(null)
  // ICT states every time in New York time (DEC-043).
  const [axisTimezone, setAxisTimezone] = useState<AxisTimezone>('new-york')
  // Where the window ends: null follows the newest imported minute.
  const [end, setEnd] = useState<number | null>(null)
  const [activeTool, setActiveTool] = useState<DrawingKind | null>(null)
  const [snapMode, setSnapMode] = useState<SnapMode>('magnet')

  const { state: drawingState, dispatch, commit, retry, syncError, canUndo, canRedo } = useDrawings(SYMBOL)

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
        const nq = list.find((i) => i.symbol === SYMBOL)
        if (nq) setInstrument(nq)
        else setInstrumentError('NQ is not among the instruments.')
      })
      .catch(() => setInstrumentError('Could not load instruments.'))
  }, [])

  const dataFrom = instrument?.dataFrom
  const dataTo = instrument?.dataTo
  // Real history is a bounded window; nothing to load until the instrument says where it ends.
  const windowEnd =
    dataFrom === undefined || dataTo === undefined
      ? null
      : clampEnd(end ?? dataTo, dataFrom, dataTo, windowDays(timeframe) * DAY)
  const noHistory = instrument !== null && dataTo === undefined

  // Loading and error are derived, not stored: a response counts only for the request it answers, so
  // "loading" is simply "the newest response is not for the current request yet". Nothing has to be
  // reset when a request starts (BUG-035: no synchronous setState inside an effect).
  const requestKey = windowEnd === null ? null : `${timeframe}|${windowEnd}`
  useEffect(() => {
    if (windowEnd === null || requestKey === null) return
    let cancelled = false
    const { from, to } = chartWindow(timeframe, windowEnd)
    getBars({ symbol: SYMBOL, timeframe, from, to })
      .then((result) => {
        if (!cancelled) setLoaded({ key: requestKey, series: result })
      })
      .catch(() => {
        if (!cancelled) setLoaded({ key: requestKey, error: 'Could not load bars.' })
      })
    return () => {
      cancelled = true
    }
  }, [timeframe, windowEnd, requestKey])

  const layers = useChartLayers()
  const { clockShown, setupsShown, setupsOn } = layers

  // The New York clock for the same window (T7). A failure costs only the overlay, never the chart.
  const [clock, setClock] = useState<{ key: string; layer: ClockLayer | null } | null>(null)
  useEffect(() => {
    if (windowEnd === null || requestKey === null || !drawsClock(timeframe)) return
    let cancelled = false
    const { from, to } = chartWindow(timeframe, windowEnd)
    getChartClock(SYMBOL, from, to)
      .then((layer) => {
        if (!cancelled) setClock({ key: requestKey, layer })
      })
      .catch(() => {
        if (!cancelled) setClock({ key: requestKey, layer: null })
      })
    return () => {
      cancelled = true
    }
  }, [timeframe, windowEnd, requestKey])

  // Detected setups for the same window (T9), asked for only while a rule is switched on. A layer
  // is drawn only over the timeframe it was found on.
  const [setups, setSetups] = useState<{ key: string; layer: SetupLayer | null } | null>(null)
  useEffect(() => {
    if (windowEnd === null || setupsOn.length === 0) return
    let cancelled = false
    const { from, to } = chartWindow(timeframe, windowEnd)
    getChartSetups(SYMBOL, timeframe, from, to, setupsOn)
      .then((layer) => {
        if (!cancelled) setSetups({ key: timeframe, layer })
      })
      .catch(() => {
        if (!cancelled) setSetups({ key: timeframe, layer: null })
      })
    return () => {
      cancelled = true
    }
  }, [timeframe, windowEnd, setupsOn])

  const current = loaded !== null && loaded.key === requestKey ? loaded : null
  const loading = requestKey === null ? !noHistory && instrumentError === null : current === null
  const error = instrumentError ?? current?.error ?? null
  // The previous chart stays up while the next window loads, instead of flashing empty.
  const series = current ? (current.series ?? null) : (loaded?.series ?? null)

  const lastBar = series?.bars.at(-1)
  const before = series && lastBar ? previousSessionClose(series.bars, lastBar.time) : null
  // The zone is named for the candles on screen — a January chart is EST, whenever it is viewed — and,
  // before any have loaded, for the moment the chart opened.
  const [openedAt] = useState(() => new Date())
  const zoneAt = lastBar ? new Date(lastBar.time * 1000) : openedAt
  const clockAvailable = drawsClock(timeframe)
  const ready = instrument !== null && series !== null && error === null

  return (
    <section aria-label="Chart" className="flex flex-col gap-3 lg:h-[calc(100svh-8.25rem)] lg:min-h-[32rem] lg:flex-row">
      {ready && (
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
      )}

      {/* Stacked under the drawing bar on a phone or tablet (T46), the chart keeps most of the screen. */}
      <div className="flex h-[75svh] min-w-0 flex-col lg:flex-1 gap-3 rounded-card border border-line bg-surface p-3 shadow-panel lg:h-auto">
        <div className="flex flex-wrap items-center gap-x-4 gap-y-2">
          <div className="flex items-baseline gap-3 pr-1">
            <h1 className="font-display text-xl font-bold tracking-tight text-ink">{SYMBOL}</h1>
            {instrument && lastBar && (
              <span className="num text-lg font-semibold text-ink" data-testid="last-price">
                {formatPrice(lastBar.close, instrument.tickSize)}
              </span>
            )}
            {instrument && lastBar && before !== null && (
              <Change ticks={lastBar.close - before} base={before} tickSize={instrument.tickSize} />
            )}
          </div>

          <Segmented options={TIMEFRAMES} value={timeframe} onChange={setTimeframe} label="Timeframe" />

          {dataFrom !== undefined && dataTo !== undefined && windowEnd !== null && (
            <>
              <Segmented
                options={JUMPS.map((jump) => jump.label)}
                value={end === null ? 'Latest' : null}
                onChange={(label) => {
                  const jump = JUMPS.find((j) => j.label === label)!
                  setEnd(jump.days === 0 ? null : dataTo - jump.days * DAY)
                }}
                label="Go back"
              />
              <label className="flex h-8 items-center gap-2 rounded-lg bg-sunken pr-1 pl-3 text-xs text-subtle">
                Date
                <input
                  type="date"
                  data-testid="go-to-date"
                  className="num h-7 rounded-md bg-transparent px-1 text-xs text-ink focus:ring-2 focus:ring-accent/40 focus:outline-none"
                  min={newYorkDate(dataFrom)}
                  max={newYorkDate(dataTo)}
                  value={newYorkDate(windowEnd)}
                  onChange={(e) => {
                    if (e.target.value) setEnd(sessionEndOn(e.target.value))
                  }}
                />
              </label>
            </>
          )}

          {instrument && (
            <Button
              size="sm"
              data-testid="timezone-toggle"
              title="Time axis: New York → exchange → UTC"
              onClick={() => setAxisTimezone(nextAxisTimezone(axisTimezone))}
            >
              <span className="num">{timezoneLabel(instrument.exchangeTimezone, axisTimezone, zoneAt)}</span>
            </Button>
          )}

          <div className="ml-auto">
            <LayersMenu layers={layers} clockAvailable={clockAvailable} />
          </div>
        </div>

        <LayerChips layers={layers} clockAvailable={clockAvailable} />

        {/* The banner reads the data's own dataClass rather than assuming, and waits for the data:
            real NQ must not flash a "simulated" warning while it loads. */}
        {series && <SimulatedDataBanner dataClass={series.dataClass} />}

        {series && series.hidden.length > 0 && (
          <Notice role="status" data-testid="hidden-stretches">
            {hiddenNotice(series.hidden)}
          </Notice>
        )}

        {noHistory && (
          <Notice role="status" data-testid="no-history">
            No real {SYMBOL} history has been imported yet. Run the import described in docs/PATH.md (task T2).
          </Notice>
        )}

        {error && (
          <p role="alert" className={errorClass}>
            {error}
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

        {loading && (
          <p role="status" className="text-sm text-subtle">
            Loading…
          </p>
        )}

        {instrument && series && error === null && (
          <div className="flex min-h-0 flex-1 flex-col">
            <PriceChart
              heightClass="h-full"
              bars={series.bars}
              tickSize={instrument.tickSize}
              exchangeTimezone={instrument.exchangeTimezone}
              axisTimezone={axisTimezone}
              daily={timeframe === '1d'}
              adapterFactory={adapterFactory}
              recentBars={RECENT_BARS}
              overlay={(adapter) => (
                <>
                  {drawsClock(series.timeframe) && (
                    <ClockOverlay
                      adapter={adapter}
                      layer={clock?.layer ?? null}
                      bars={series.bars}
                      timeframe={series.timeframe}
                      tickSize={instrument.tickSize}
                      shown={clockShown}
                    />
                  )}
                  <SetupOverlay
                    adapter={adapter}
                    layer={setups?.key === timeframe ? setups.layer : null}
                    bars={series.bars}
                    timeframe={series.timeframe}
                    shown={setupsShown}
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

        {series && instrument && (
          <dl className="num flex flex-wrap gap-x-6 gap-y-1 text-xs text-subtle [&_dd]:ml-1.5 [&_dd]:inline [&_dd]:text-ink [&_dt]:inline">
            <div>
              <dt>Bars</dt>
              <dd>{series.bars.length.toLocaleString('en-US')}</dd>
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
      </div>
    </section>
  )
}

/** The move since the previous session's close, in points and percent, green up and red down. */
function Change({ ticks, base, tickSize }: { ticks: number; base: number; tickSize: number }) {
  const sign = ticks > 0 ? '+' : ticks < 0 ? '−' : ''
  const percent = base === 0 ? 0 : (Math.abs(ticks) / base) * 100
  return (
    <span
      data-testid="price-change"
      title="Since the previous session's close (17:00 New York)"
      className={cx('num text-sm font-medium', ticks > 0 ? 'text-up' : ticks < 0 ? 'text-down' : 'text-subtle')}
    >
      {sign}
      {formatPrice(Math.abs(ticks), tickSize)} ({sign}
      {percent.toFixed(2)}%)
    </span>
  )
}
