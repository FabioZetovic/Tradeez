import { useCallback, useEffect, useMemo, useReducer, useRef, useState } from 'react'
import { PriceChart } from '../PriceChart'
import { ClockOverlay } from '../ClockOverlay'
import { DrawingOverlay } from '../DrawingOverlay'
import { DrawingToolbar } from '../DrawingToolbar'
import { SetupOverlay } from '../SetupOverlay'
import { LayerChips, LayersMenu } from '../chart/LayersMenu'
import { SimulatedDataBanner } from '../SimulatedDataBanner'
import { PlanPanel, type PlanCosts } from './PlanPanel'
import { ResultCard } from './ResultCard'
import { TradeReview } from './TradeReview'
import { TradeLog } from './TradeLog'
import { SPEEDS, usePracticeSession, type Speed } from '@/hooks/usePracticeSession'
import { useChartLayers } from '@/hooks/useChartLayers'
import { drawsClock } from '@/lib/clock/layers'
import { useDrawingShortcuts } from '@/hooks/useDrawingShortcuts'
import { usePlanContext } from '@/hooks/usePlanContext'
import type { Direction, PlanIntent, PracticeState, SessionResponse } from '@/lib/api/practice'
import { getPracticeClock, type ClockLayer } from '@/lib/api/clock'
import { getPracticeSetups, type SetupLayer } from '@/lib/api/setups'
import { TIMEFRAMES } from '@/lib/api/marketData'
import type { ChartAdapter } from '@/lib/chart/types'
import { formatPrice } from '@/lib/chart/priceFormat'
import { formatSessionTime } from '@/lib/chart/timeAxis'
import { candleAt } from '@/lib/chart/timeToX'
import {
  canRedo,
  canUndo,
  drawingReducer,
  initialDrawingState,
  type DrawingAction,
} from '@/lib/drawing/store'
import type { SnapMode } from '@/lib/drawing/snap'
import type { Drawing, DrawingKind } from '@/lib/drawing/types'
import { Button, Chip, Panel, Segmented } from '../ui'
import { errorClass, keyValueClass, panelTitle } from '@/lib/styles'
import {
  closedTradeDrawing,
  defaultBracket,
  flipBracket,
  lockedBracket,
  pinEntry,
  PLAN_DRAWING_ID,
  planFromDrawing,
  STATUS_LABEL,
  TRADE_DRAWING_PREFIX,
  type PartialDraft,
} from '@/lib/practice/plan'

interface PracticeSessionProps {
  sessionId: string
  /** The creation response, so a new session is not fetched a second time. */
  initial?: SessionResponse
  onBack: () => void
  adapterFactory?: () => ChartAdapter
  /** The learner's risk limit (T23), shown against each draft; absent when none is set. */
  riskLimitCents?: number
  /** What the learner's trades cost (T27), shown against each draft. */
  costs?: PlanCosts
}

/** How much of the history the chart opens on. Roughly a session's worth on 5m. */
const RECENT_BARS = 120

const STATUS_TONE = {
  ANALYSING: 'neutral',
  IN_TRADE: 'caution',
  COMPLETE: 'good',
} as const satisfies Record<PracticeState['status'], string>

const SPEED_LABELS = SPEEDS.map((s) => `${s}×`)

/**
 * One replay: a hidden moment played forward, with trades placed along the way (DEC-046).
 *
 * Drawings here are local and unsaved. They are analysis for this moment only, and saving them to
 * the instrument's chart would put marks from a market the learner cannot otherwise see onto the
 * public one. Two kinds of drawing carry meaning: the plan — the position tool, id
 * {@link PLAN_DRAWING_ID} — whose entry is pinned to the market price and which freezes when locked;
 * and each finished trade, drawn from entry to exit, which is the record and never editable.
 */
export function PracticeSession({ sessionId, initial, onBack, adapterFactory, riskLimitCents, costs }: PracticeSessionProps) {
  const {
    state,
    bars,
    timeframe,
    switchTimeframe,
    error,
    busy,
    playing,
    setPlaying,
    speed,
    setSpeed,
    advance,
    skip,
    close,
    lockPlan,
    end,
  } = usePracticeSession(sessionId, initial)

  const [drawingState, rawDispatch] = useReducer(drawingReducer, initialDrawingState)
  const [activeTool, setActiveTool] = useState<DrawingKind | null>(null)
  const [snapMode, setSnapMode] = useState<SnapMode>('magnet')
  const [contracts, setContracts] = useState(1)
  // T28: part of the draft to take off before the target; only with two contracts or more.
  const [partial, setPartial] = useState<PartialDraft | null>(null)
  const draftPartial = contracts >= 2 ? partial : null
  // T22: the model, bias and expected liquidity of the trade being drafted; cleared once it is placed.
  const [intent, setIntent] = useState<PlanIntent>({})
  // Both remember when they were set, and lapse when the replay moves past that. A trade picked in
  // the log stays open only until another trade closes — play stops exactly so the new result can be
  // read (BUG-046). "End the session?" stays asked only until another trade is placed (BUG-045).
  const [picked, setPicked] = useState<{ number: number; closedAt: number } | null>(null)
  const [endAskedAt, setEndAskedAt] = useState<number | null>(null)

  // The New York clock up to the cursor (T7). The server says when it can next change; it is asked
  // again only once the replay gets there, not on every bar. A failure costs only the overlay.
  const [clock, setClock] = useState<ClockLayer | null>(null)
  // One choice of layers for the Chart tab and practice (T38).
  const layers = useChartLayers()
  const { clockShown, setupsShown, setupsOn } = layers
  const cursor = state?.cursor
  const clockStale =
    cursor !== undefined &&
    (clock === null || (clock.nextChange !== undefined && cursor >= clock.nextChange))
  useEffect(() => {
    if (!clockStale) return
    let cancelled = false
    getPracticeClock(sessionId)
      .then((layer) => {
        if (!cancelled) setClock(layer)
      })
      .catch(() => {})
    return () => {
      cancelled = true
    }
    // `clock` too: a layer that comes back already stale, because the replay moved on while it was
    // being built, is asked for again.
  }, [clockStale, sessionId, clock])

  // Detected setups up to the cursor (T9). Any step can confirm a new one, so they are asked for
  // whenever the cursor has moved — but one request at a time: while the replay plays faster than
  // the server answers, each answer is followed by one more for wherever the cursor has got to.
  const [setups, setSetups] = useState<{ cursor: number; key: string; layer: SetupLayer | null } | null>(
    null,
  )
  const [reviewLayer, setReviewLayer] = useState<{ number: number; layer: SetupLayer } | null>(null)
  const reviewRules = useMemo(
    () => new Set(reviewLayer?.layer.rules.map((r) => r.detector) ?? []),
    [reviewLayer],
  )
  // The timeframe the replay is looked at in (T34); setups are asked for on it.
  const view = timeframe ?? '5m'
  const setupsKey = setupsOn.length === 0 ? '' : `${view}|${setupsOn.join(',')}`
  const setupsStale =
    cursor !== undefined &&
    setupsKey !== '' &&
    (setups === null || setups.cursor !== cursor || setups.key !== setupsKey)
  const setupsAsking = useRef(false)
  useEffect(() => {
    if (!setupsStale || cursor === undefined || setupsAsking.current) return
    setupsAsking.current = true
    const asked = { cursor, key: setupsKey }
    getPracticeSetups(sessionId, setupsOn, view)
      .then((layer) => {
        setupsAsking.current = false
        setSetups({ ...asked, layer })
      })
      .catch(() => {
        // Not asked again until the cursor moves: a failure costs only the overlay.
        setupsAsking.current = false
        setSetups({ ...asked, layer: null })
      })
    // `setups` too: the answer to an older cursor is followed by a question about the current one.
  }, [setupsStale, cursor, setupsKey, setupsOn, view, sessionId, setups])

  const lastBar = bars.length > 0 ? bars[bars.length - 1] : undefined
  const status = state?.status
  const trades = state?.trades ?? []
  const openTrade = status === 'IN_TRADE' ? trades[trades.length - 1] : undefined
  // A plan can be drawn only while flat. Running or ended, the bracket is not the learner's to move.
  const locked = status !== undefined && status !== 'ANALYSING'

  // The price a market order is priced against, and the bar the bracket starts from. While flat that
  // is the latest bar; in a trade, it is what the server recorded at the lock.
  const referenceTicks = openTrade?.plan.referenceTicks ?? lastBar?.close ?? 0
  // The candle the lock happened in, on whatever timeframe is being looked at.
  const anchorTime = openTrade
    ? candleAt(openTrade.plan.lockedAt - 1, bars)
    : (lastBar?.time ?? 0)

  /**
   * Every drawing action passes through here. Actions on the plan drawing are rewritten to keep its
   * entry on the market price, and dropped entirely while it cannot change — the server would
   * refuse a change anyway (DEC-013), and a chart that let the learner drag a locked stop would be
   * showing a trade that does not exist. Finished trades are never touched at all.
   */
  const dispatch = useCallback(
    (action: DrawingAction) => {
      const id = 'drawing' in action ? action.drawing.id : 'id' in action ? action.id : null
      if (id?.startsWith(TRADE_DRAWING_PREFIX)) return
      if (id === PLAN_DRAWING_ID && locked) return

      if ('drawing' in action && action.drawing.id === PLAN_DRAWING_ID) {
        rawDispatch({ ...action, drawing: pinEntry(action.drawing, referenceTicks, anchorTime) })
        return
      }
      rawDispatch(action)
    },
    [locked, referenceTicks, anchorTime],
  )

  // What the chart shows, derived on every render rather than written back into the drawing store
  // by effects. While flat, the bracket's entry follows the market price as the replay moves. In a
  // trade, the bracket is the server's plan — not whatever was last drawn — and it stays that way
  // through undo and redo, which only ever see the learner's own edits.
  const storedPlan = drawingState.drawings.find((d) => d.id === PLAN_DRAWING_ID)
  const planDrawing: Drawing | undefined = openTrade
    ? lockedBracket(openTrade.plan, anchorTime, openTrade.entry?.ticks, openTrade.partial)
    : !locked && storedPlan
      ? { ...pinEntry(storedPlan, referenceTicks, anchorTime), partialTicks: draftPartial?.ticks }
      : undefined
  const shownState = {
    ...drawingState,
    drawings: [
      // First, so the learner's own analysis draws over them and wins every click.
      ...trades.flatMap((t) => closedTradeDrawing(t, (time) => candleAt(time, bars)) ?? []),
      ...drawingState.drawings.filter((d) => d.id !== PLAN_DRAWING_ID),
      ...(planDrawing ? [planDrawing] : []),
    ],
  }

  const deleteSelected = useCallback(() => {
    if (drawingState.selectedId) dispatch({ type: 'remove', id: drawingState.selectedId })
  }, [drawingState.selectedId, dispatch])
  const cancelTool = useCallback(() => setActiveTool(null), [])

  useDrawingShortcuts({
    selectedId: drawingState.selectedId,
    dispatch,
    onCancelTool: cancelTool,
    onDeleteSelected: deleteSelected,
  })

  const draftPlan = planDrawing && !locked ? planFromDrawing(planDrawing, contracts, draftPartial) : null
  // T22: the killzone, the bias suggestion and the model's checklist for the bracket on the chart.
  const planContext = usePlanContext(sessionId, state?.status === 'ANALYSING' ? draftPlan : null, intent.model, cursor)

  if (!state) {
    return (
      <section className="flex flex-col items-start gap-3">
        <Button variant="link" onClick={onBack}>
          ← Journal
        </Button>
        {error ? (
          <p role="alert" className={errorClass}>
            {error}
          </p>
        ) : (
          <p role="status" className="text-sm text-subtle">
            Loading session…
          </p>
        )}
      </section>
    )
  }

  const place = (direction: Direction) => {
    dispatch({ type: 'add', drawing: defaultBracket(direction, referenceTicks, anchorTime) })
  }
  const flip = () => {
    if (!planDrawing) return
    dispatch({ type: 'update', drawing: flipBracket(planDrawing) })
    // BUG-090: the partial is the bracket's too, mirrored through the same price — or a long's partial above
    // the price became a short's partial above it, on the wrong side.
    const entry = planDrawing.anchors[0].priceTicks
    setPartial((p) => (p === null ? p : { ...p, ticks: 2 * entry - p.ticks }))
  }
  // A draft is only a drawing until it is placed: throwing it away costs nothing — and keeps nothing, as placing
  // it keeps nothing (BUG-091): the next draft starts without the old partial, model, bias, draw or confidence.
  const discard = () => {
    dispatch({ type: 'remove', id: PLAN_DRAWING_ID })
    setPartial(null)
    setIntent({})
  }
  const changeLevel = (level: 'stop' | 'target', ticks: number) => {
    if (!planDrawing) return
    const anchors = [...planDrawing.anchors]
    anchors[level === 'stop' ? 1 : 2] = { ...anchors[level === 'stop' ? 1 : 2], priceTicks: ticks }
    dispatch({ type: 'update', drawing: { ...planDrawing, anchors } })
  }

  // The trade whose details are open: the one picked in the log, else the latest to finish.
  const closed = trades.filter((t) => t.result)
  const pickedTrade =
    picked && picked.closedAt === closed.length
      ? closed.find((t) => t.number === picked.number)
      : undefined
  const shownTrade = pickedTrade ?? closed[closed.length - 1]
  // The review's hits (T24) belong to the trade shown: showing another takes them off the chart.
  if (reviewLayer && reviewLayer.number !== shownTrade?.number) setReviewLayer(null)
  const drawnReview = reviewLayer?.layer ?? null
  const confirmingEnd = endAskedAt === trades.length

  return (
    <section className="flex flex-col gap-4">
      <SimulatedDataBanner dataClass={state.dataClass} />

      <div className="flex flex-wrap items-center gap-x-5 gap-y-3 rounded-lg border border-line bg-surface px-4 py-3 text-sm">
        <Button variant="link" onClick={onBack}>
          ← Journal
        </Button>
        <strong>{state.instrument}</strong>
        <Segmented<string>
          options={TIMEFRAMES}
          value={view}
          onChange={(next) => {
            if (next !== view) void switchTimeframe(next)
          }}
          label="Chart timeframe"
        />
        <Chip tone={STATUS_TONE[state.status]} data-testid="practice-status">
          {STATUS_LABEL[state.status]}
        </Chip>
        <span className="text-subtle" data-testid="practice-cursor">
          {formatSessionTime(state.cursor, state.exchangeTimezone, state.timeBasis)}
        </span>
        {/* DEC-025: on real data the date is the answer; it is revealed when the session ends. */}
        {state.timeBasis === 'NEW_YORK_WALL_CLOCK' && state.status !== 'COMPLETE' && (
          <Chip data-testid="date-hidden" title="The date is revealed when the session ends">
            date hidden
          </Chip>
        )}
        {lastBar && (
          <span className="ml-auto text-xl font-semibold tabular-nums" data-testid="last-price">
            {formatPrice(lastBar.close, state.tickSize)}
          </span>
        )}
      </div>

      <div className="flex flex-wrap items-center gap-2">
        <LayersMenu layers={layers} clockAvailable={drawsClock(view)} />
        <LayerChips layers={layers} clockAvailable={drawsClock(view)} />
      </div>

      <div className="flex min-w-0 items-start gap-2">
        <DrawingToolbar
          activeTool={activeTool}
          onSelectTool={setActiveTool}
          snapMode={snapMode}
          onToggleSnap={() => setSnapMode(snapMode === 'magnet' ? 'off' : 'magnet')}
          canUndo={canUndo(drawingState)}
          canRedo={canRedo(drawingState)}
          onUndo={() => dispatch({ type: 'undo' })}
          onRedo={() => dispatch({ type: 'redo' })}
          hasSelection={drawingState.selectedId !== null}
          onDelete={deleteSelected}
        />
        <PriceChart
          bars={bars}
          tickSize={state.tickSize}
          exchangeTimezone={state.exchangeTimezone}
          axisTimezone={state.timeBasis === 'NEW_YORK_WALL_CLOCK' ? 'wall-clock' : 'exchange'}
          daily={view === '1d'}
          adapterFactory={adapterFactory}
          recentBars={RECENT_BARS}
          overlay={(adapter) => (
            <>
              {drawsClock(view) && (
                <ClockOverlay
                  adapter={adapter}
                  layer={clock}
                  bars={bars}
                  timeframe={view}
                  tickSize={state.tickSize}
                  shown={clockShown}
                />
              )}
              <SetupOverlay
                adapter={adapter}
                layer={setups?.layer ?? null}
                bars={bars}
                timeframe={view}
                shown={setupsShown}
              />
              {drawnReview && (
                <SetupOverlay adapter={adapter} layer={drawnReview} bars={bars} timeframe={view} shown={reviewRules} />
              )}
              <DrawingOverlay
                adapter={adapter}
                state={shownState}
                dispatch={dispatch}
                activeTool={activeTool}
                onToolFinished={() => setActiveTool(null)}
                bars={bars}
                snapMode={snapMode}
                tickValueCents={state.tickValueCents}
                tickSize={state.tickSize}
                onCommit={() => {}}
              />
            </>
          )}
        />
      </div>

      <div className="grid grid-cols-[repeat(auto-fit,minmax(18rem,1fr))] items-start gap-4">
        <Panel aria-label="Replay">
          <h3 className={panelTitle}>Replay</h3>
          <div className="flex flex-wrap items-center gap-2">
            <Button disabled={busy || playing} onClick={() => void advance(1)}>
              Next bar
            </Button>
            <Button disabled={busy || playing} onClick={() => void advance(10)}>
              Next 10
            </Button>
            {status !== 'COMPLETE' && (
              <Button active={playing} aria-pressed={playing} onClick={() => setPlaying(!playing)}>
                {playing ? 'Pause' : 'Play'}
              </Button>
            )}
            {status !== 'COMPLETE' && (
              <Segmented<string>
                options={SPEED_LABELS}
                value={`${speed}×`}
                onChange={(label) => setSpeed(Number(label.slice(0, -1)) as Speed)}
                label="Replay speed, bars per second"
              />
            )}
            {status === 'IN_TRADE' && (
              <Button disabled={busy} onClick={() => void skip()}>
                Skip to result
              </Button>
            )}
          </div>

          {status === 'ANALYSING' && (
            <div className="mt-4 flex flex-wrap items-center gap-2 border-t border-line pt-3">
              {confirmingEnd ? (
                <>
                  <span className="text-sm">
                    End the session? No more trades{state.timeBasis === 'NEW_YORK_WALL_CLOCK' && ', and the date is revealed'}.
                  </span>
                  <Button
                    size="sm"
                    variant="primary"
                    disabled={busy}
                    onClick={() => {
                      setEndAskedAt(null)
                      void end()
                    }}
                  >
                    End session
                  </Button>
                  <Button size="sm" variant="ghost" onClick={() => setEndAskedAt(null)}>
                    Keep trading
                  </Button>
                </>
              ) : (
                <Button
                  size="sm"
                  variant="ghost"
                  disabled={busy}
                  onClick={() => setEndAskedAt(trades.length)}
                >
                  End session…
                </Button>
              )}
            </div>
          )}

          {error && (
            <p role="alert" className={`mt-3 ${errorClass}`}>
              {error}
            </p>
          )}
        </Panel>

        {status === 'ANALYSING' && (
          <PlanPanel
            plan={draftPlan}
            referenceTicks={referenceTicks}
            tickSize={state.tickSize}
            tickValueCents={state.tickValueCents}
            riskLimitCents={riskLimitCents}
            costs={costs}
            busy={busy}
            onPlace={place}
            onFlip={flip}
            onDiscard={discard}
            onChangeLevel={changeLevel}
            onChangeContracts={setContracts}
            onChangePartial={setPartial}
            intent={intent}
            onIntent={setIntent}
            context={planContext}
            onLock={(plan) => {
              setPlaying(false)
              void lockPlan({ ...plan, ...intent }).then((ok) => {
                // The draft became the trade; the next one starts from a fresh bracket. Forgotten
                // rather than removed, so no undo can bring it back (BUG-041).
                if (ok) {
                  rawDispatch({ type: 'forget', id: PLAN_DRAWING_ID })
                  setIntent({})
                  setPartial(null)
                }
              })
            }}
          />
        )}

        {openTrade && (
          <Panel aria-label="Trade">
            <h3 className={panelTitle}>
              Trade {openTrade.number} · {openTrade.plan.direction === 'LONG' ? 'Long' : 'Short'}{' '}
              {openTrade.plan.contracts} — placed
            </h3>
            <dl className={keyValueClass}>
              <dt>Entry</dt>
              <dd data-testid="trade-entry">
                {openTrade.entry
                  ? `${formatPrice(openTrade.entry.ticks, state.tickSize)} · ${formatSessionTime(openTrade.entry.time, state.exchangeTimezone, state.timeBasis)}`
                  : 'at the next price'}
              </dd>
              <dt>Stop</dt>
              <dd data-testid="trade-stop">
                {openTrade.plan.breakevenAfterPartial && openTrade.partial && openTrade.entry
                  ? `${formatPrice(openTrade.entry.ticks, state.tickSize)} — moved to the entry`
                  : formatPrice(openTrade.plan.stopTicks, state.tickSize)}
              </dd>
              {openTrade.plan.partialTicks !== undefined && (
                <>
                  <dt>Partial</dt>
                  <dd data-testid="trade-partial">
                    {openTrade.partial
                      ? `${openTrade.partial.contracts} taken off at ${formatPrice(openTrade.partial.ticks, state.tickSize)}`
                      : `${openTrade.plan.partialContracts} at ${formatPrice(openTrade.plan.partialTicks, state.tickSize)}${
                          openTrade.plan.breakevenAfterPartial ? ', then the stop to the entry' : ''
                        }`}
                  </dd>
                </>
              )}
              <dt>Target</dt>
              <dd>{formatPrice(openTrade.plan.targetTicks, state.tickSize)}</dd>
              <dt>Reward : risk</dt>
              <dd>{openTrade.plan.plannedRewardRisk.toFixed(2)}</dd>
            </dl>
            <p className="mt-3 text-xs text-subtle">
              Step forward to watch it play out, or close it whenever you choose — at the next price.
            </p>
            <Button className="mt-3" disabled={busy} onClick={() => void close()}>
              Close at market
            </Button>
          </Panel>
        )}
      </div>

      {/* Its own row: the scorecard's two sides need the width (T23). */}
      {shownTrade?.result && <ResultCard state={state} trade={shownTrade} result={shownTrade.result} />}
      {shownTrade?.result && (
        <TradeReview
          key={shownTrade.number}
          state={state}
          number={shownTrade.number}
          drawn={drawnReview !== null}
          onDraw={(layer) => setReviewLayer(layer && { number: shownTrade.number, layer })}
        />
      )}

      {(trades.length > 0 || status === 'COMPLETE') && (
        <TradeLog
          state={state}
          selected={shownTrade?.number}
          onSelect={(number) => setPicked({ number, closedAt: closed.length })}
        />
      )}
    </section>
  )
}
