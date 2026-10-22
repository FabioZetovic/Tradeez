import { useCallback, useEffect, useState } from 'react'
import { PriceChart } from './PriceChart'
import { AdapterOut, CompanionChart } from './CompanionChart'
import { SimulatedDataBanner } from './SimulatedDataBanner'
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
  sessionEndOn,
  windowDays,
} from '@/lib/chart/dateNavigation'
import { ClockOverlay } from './ClockOverlay'
import { ClockToggles } from './ClockToggles'
import { DrawingOverlay } from './DrawingOverlay'
import { DrawingToolbar } from './DrawingToolbar'
import { SetupOverlay } from './SetupOverlay'
import { SetupToggles } from './SetupToggles'
import { useClockLayers } from '@/hooks/useClockLayers'
import { drawsClock } from '@/lib/clock/layers'
import { useSetupLayers } from '@/hooks/useSetupLayers'
import { useDrawings } from '@/hooks/useDrawings'
import { useDrawingShortcuts } from '@/hooks/useDrawingShortcuts'
import type { SnapMode } from '@/lib/drawing/snap'
import type { DrawingKind } from '@/lib/drawing/types'
import { Button, Segmented } from './ui'
import { cx, errorClass, fieldClass } from '@/lib/styles'

/** Candles in view when a window loads; the rest of the window is a scroll away. */
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
  // DEC-075: another market under the chart, over the same minutes — NQ with ES beside it, for SMT.
  const [beside, setBeside] = useState<string | null>(null)
  const [leader, setLeader] = useState<ChartAdapter | null>(null)

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
    if (windowEnd === null || requestKey === null || !drawsClock(timeframe)) return
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

  // Detected setups for the same window (T9), asked for only while a rule is switched on. A layer
  // is drawn only over the market and timeframe it was found on.
  const [setups, setSetups] = useState<{ key: string; layer: SetupLayer | null } | null>(null)
  const setupsKey = `${symbol}|${timeframe}`
  const { rules: setupRules, shown: setupsShown, toggle: toggleSetup, on: setupsOn } = useSetupLayers()
  useEffect(() => {
    if (windowEnd === null || setupsOn.length === 0) return
    let cancelled = false
    const { from, to } = chartWindow(timeframe, windowEnd)
    getChartSetups(symbol, timeframe, from, to, setupsOn)
      .then((layer) => {
        if (!cancelled) setSetups({ key: setupsKey, layer })
      })
      .catch(() => {
        if (!cancelled) setSetups({ key: setupsKey, layer: null })
      })
    return () => {
      cancelled = true
    }
  }, [symbol, timeframe, windowEnd, setupsOn, setupsKey])

  // The market beside, over the same window; while the SMT rule is on, its own divergences too.
  const besideSymbol =
    beside !== null && beside !== symbol && instruments.some((i) => i.symbol === beside) ? beside : null
  const besideInstrument = instruments.find((i) => i.symbol === besideSymbol)
  const besideKey = besideSymbol === null || requestKey === null ? null : `${besideSymbol}|${requestKey}`
  const [besideBars, setBesideBars] = useState<{ key: string; series: BarSeries | null } | null>(null)
  useEffect(() => {
    if (besideSymbol === null || besideKey === null || windowEnd === null) return
    let cancelled = false
    const { from, to } = chartWindow(timeframe, windowEnd)
    getBars({ symbol: besideSymbol, timeframe, from, to })
      .then((result) => {
        if (!cancelled) setBesideBars({ key: besideKey, series: result })
      })
      .catch(() => {
        if (!cancelled) setBesideBars({ key: besideKey, series: null })
      })
    return () => {
      cancelled = true
    }
  }, [besideSymbol, besideKey, timeframe, windowEnd])
  const smtOn = setupsOn.includes('SMT')
  const [besideSetups, setBesideSetups] = useState<{ key: string; layer: SetupLayer | null } | null>(null)
  useEffect(() => {
    if (besideSymbol === null || besideKey === null || windowEnd === null || !smtOn) return
    let cancelled = false
    const { from, to } = chartWindow(timeframe, windowEnd)
    getChartSetups(besideSymbol, timeframe, from, to, ['SMT'])
      .then((layer) => {
        if (!cancelled) setBesideSetups({ key: besideKey, layer })
      })
      .catch(() => {
        if (!cancelled) setBesideSetups({ key: besideKey, layer: null })
      })
    return () => {
      cancelled = true
    }
  }, [besideSymbol, besideKey, timeframe, windowEnd, smtOn])
  const besideSeries = besideBars?.key === besideKey ? besideBars.series : null

  const current = loaded !== null && loaded.key === requestKey ? loaded : null
  const loading = requestKey === null ? !noHistory : current === null
  const error = instrumentsError ?? current?.error ?? null
  // The previous chart stays up while the next window loads, instead of flashing empty.
  const series = current ? (current.series ?? null) : (loaded?.series ?? null)

  const lastBar = series?.bars.at(-1)
  // The zone is named for the candles on screen — a January chart is EST, whenever it is viewed — and,
  // before any have loaded, for the moment the chart opened.
  const [openedAt] = useState(() => new Date())
  const zoneAt = lastBar ? new Date(lastBar.time * 1000) : openedAt

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

        {instruments.length > 1 && (
          <label className="flex items-center gap-2 text-xs font-medium text-subtle">
            Beside
            <select
              className={fieldClass}
              value={besideSymbol ?? ''}
              onChange={(e) => setBeside(e.target.value === '' ? null : e.target.value)}
            >
              <option value="">none</option>
              {instruments
                .filter((i) => i.symbol !== symbol)
                .map((i) => (
                  <option key={i.symbol} value={i.symbol}>
                    {i.symbol}
                  </option>
                ))}
            </select>
          </label>
        )}

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
            {timezoneLabel(instrument.exchangeTimezone, axisTimezone, zoneAt)}
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

      {series && series.hidden.length > 0 && (
        <p role="status" data-testid="hidden-stretches" className="text-sm text-subtle">
          {hiddenNotice(series.hidden)}
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
        <div className="flex flex-wrap items-start gap-2 self-start">
          <ClockToggles shown={clockShown} onToggle={toggleClock} available={drawsClock(timeframe)} />
          <SetupToggles rules={setupRules} shown={setupsShown} onToggle={toggleSetup} />
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

          <div className="flex min-w-0 flex-1 flex-col gap-3">
            <PriceChart
              heightClass={besideSymbol ? 'h-[calc(100svh-31rem)] min-h-[22rem]' : 'h-[calc(100svh-15rem)] min-h-[26rem]'}
              bars={series.bars}
              tickSize={instrument.tickSize}
              exchangeTimezone={instrument.exchangeTimezone}
              axisTimezone={axisTimezone}
              daily={timeframe === '1d'}
              adapterFactory={adapterFactory}
              recentBars={RECENT_BARS}
              overlay={(adapter) => (
                <>
                  {besideSymbol && <AdapterOut adapter={adapter} onAdapter={setLeader} />}
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
                    layer={setups?.key === setupsKey ? setups.layer : null}
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
            {besideSymbol && besideInstrument && besideSeries && (
              <CompanionChart
                companion={{ instrument: besideSymbol, tickSize: besideInstrument.tickSize, bars: besideSeries.bars }}
                leader={leader}
                exchangeTimezone={besideInstrument.exchangeTimezone}
                axisTimezone={axisTimezone}
                daily={timeframe === '1d'}
                adapterFactory={adapterFactory}
                heightClass="h-64"
                overlay={(adapter) => (
                  <SetupOverlay
                    adapter={adapter}
                    layer={smtOn && besideSetups?.key === besideKey ? besideSetups.layer : null}
                    bars={besideSeries.bars}
                    timeframe={besideSeries.timeframe}
                    shown={setupsShown}
                  />
                )}
              />
            )}
          </div>
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
