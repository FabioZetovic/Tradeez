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
import { revealedDate, takeaways } from '@/lib/practice/session'
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
import type { ResultView, TradeView } from '@/lib/api/practice'
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
import { ArrowLeft, Ellipsis, Pause, PencilLine, Play, StepForward, TrendingDown, TrendingUp, X } from 'lucide-react'
import { Button, Chip, Panel, Segmented, Sheet } from '../ui'
import { PhonePlan } from './PhonePlan'
import { useIsPhone } from '@/hooks/useIsPhone'
import { cx, errorClass, keyValueClass, panelTitle } from '@/lib/styles'
import {
  closedTradeDrawing,
  defaultBracket,
  flipBracket,
  lockedBracket,
  pinEntry,
  PLAN_DRAWING_ID,
  planFromDrawing,
  formatCents,
  formatR,
  outcomeLabel,
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
  // A phone (T53): the drawing tools behind a button, a trade's scorecard as a screen of its own, "end session" behind
  // a "more" button.
  const phone = useIsPhone()
  const [toolsShown, setToolsShown] = useState(false)
  const [viewing, setViewing] = useState<number | null>(null)
  const [seen, setSeen] = useState<number | null>(null)
  const [more, setMore] = useState(false)

  // The New York clock up to the cursor (T7). The server says when it can next change; it is asked
  // again only once the replay gets there, not on every bar. A failure costs only the overlay.
  const [clock, setClock] = useState<ClockLayer | null>(null)
  // One choice of layers for the Chart tab and practice (T38).
  const layers = useChartLayers('practice')
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
          ← Practice
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

  const score = state.score
  const overlay = (adapter: ChartAdapter | null) => (
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
  )

  if (phone) {
    // Opening a session again does not bring back a scorecard already seen; a trade closing from here does.
    if (seen === null) setSeen(closed.length)
    else if (closed.length > seen) {
      setSeen(closed.length)
      setViewing(closed[closed.length - 1].number)
    }
    const scored = viewing === null ? undefined : closed.find((t) => t.number === viewing)
    const replay = status !== 'COMPLETE' && (
      <div className="flex items-center gap-2">
        <button type="button" aria-label="Next bar" disabled={busy || playing} onClick={() => void advance(1)} className={cx(QUIET, 'w-12')}>
          <StepForward aria-hidden className="size-5" />
        </button>
        <button
          type="button"
          aria-label={playing ? 'Pause' : 'Play'}
          aria-pressed={playing}
          onClick={() => setPlaying(!playing)}
          className={cx(PRIMARY, 'w-14')}
        >
          {playing ? <Pause aria-hidden className="size-5" /> : <Play aria-hidden className="size-5 fill-current" />}
        </button>
        {status === 'ANALYSING' && (
          <button type="button" disabled={busy || playing} onClick={() => void advance(10)} className={cx(QUIET, 'flex-1')}>
            Next 10
          </button>
        )}
        <button
          type="button"
          aria-label={`Replay speed ${speed}×, tap to change`}
          onClick={() => setSpeed(SPEEDS[(SPEEDS.indexOf(speed) + 1) % SPEEDS.length])}
          className={cx(QUIET, 'num w-14')}
        >
          {speed}×
        </button>
        {status === 'ANALYSING' ? (
          <button type="button" aria-label="More: end session" onClick={() => setMore(true)} className={cx(QUIET, 'w-12')}>
            <Ellipsis aria-hidden className="size-5" />
          </button>
        ) : (
          <button type="button" disabled={busy} onClick={() => void skip()} className={cx(QUIET, 'flex-1')}>
            Skip to result
          </button>
        )}
      </div>
    )

    if (scored?.result) {
      // A trade's scorecard (T53): its own screen — the outcome and the process — then back to the chart or the end.
      return (
        <section aria-label="Scorecard" className="flex min-h-svh flex-col pb-40">
          <header className="sticky top-0 z-20 flex h-14 items-center gap-1 bg-canvas/95 pr-3 pl-1 backdrop-blur">
            <button type="button" aria-label="Back to the chart" onClick={() => setViewing(null)} className={ICON}>
              <ArrowLeft aria-hidden className="size-5" />
            </button>
            <div className="min-w-0 flex-1">
              <h1 className="text-[17px] font-semibold text-ink">Trade {scored.number} · Scorecard</h1>
              <p className="num text-[13px] text-subtle">
                {state.instrument} · {formatSessionTime(scored.result.exitTime, state.exchangeTimezone, state.timeBasis)}
                {state.timeBasis === 'NEW_YORK_WALL_CLOCK' && status !== 'COMPLETE' && ' · date hidden'}
              </p>
            </div>
          </header>
          <div className="flex flex-col gap-4 px-4 pt-3">
            <ResultCard state={state} trade={scored} result={scored.result} />
            <TradeReview
              key={scored.number}
              state={state}
              number={scored.number}
              drawn={drawnReview !== null}
              onDraw={(layer) => setReviewLayer(layer && { number: scored.number, layer })}
            />
          </div>
          <div className={DOCK}>
            <p className="mb-2.5 flex justify-between text-[13px]">
              <span className="text-subtle">Session</span>
              <SessionScore score={score} />
            </p>
            <div className="grid grid-cols-2 gap-2">
              <button type="button" onClick={() => setViewing(null)} className={QUIET}>
                {status === 'COMPLETE' ? 'Back' : 'Keep replaying'}
              </button>
              {status === 'ANALYSING' ? (
                <button
                  type="button"
                  disabled={busy}
                  onClick={() => {
                    setViewing(null)
                    void end()
                  }}
                  className={PRIMARY}
                >
                  End session
                </button>
              ) : (
                <button type="button" onClick={onBack} className={PRIMARY}>
                  Back to Practice
                </button>
              )}
            </div>
          </div>
        </section>
      )
    }

    if (status === 'COMPLETE') {
      return <PhoneEnded state={state} closed={closed} onOpen={setViewing} onBack={onBack} />
    }

    return (
      // The replay on a phone (T53, Claude Design): the chart most of the screen; planning a sheet over its foot; in a
      // trade a strip with Close; the replay at the thumb.
      <section aria-label="Practice session" className={cx('flex min-h-svh flex-col', draftPlan ? 'pb-[62svh]' : 'pb-40')}>
        <header className="sticky top-0 z-20 flex h-14 items-center gap-1 bg-canvas/95 px-1 backdrop-blur">
          <button type="button" aria-label="Leave session (it stays open)" onClick={onBack} className={ICON}>
            <ArrowLeft aria-hidden className="size-5" />
          </button>
          <div className="min-w-0 flex-1">
            <div className="flex items-baseline gap-2">
              <h1 className="text-[17px] font-bold text-ink">{state.instrument}</h1>
              {lastBar && (
                <span className="num text-[17px] font-semibold text-ink" data-testid="last-price">
                  {formatPrice(lastBar.close, state.tickSize)}
                </span>
              )}
            </div>
            <p className="num truncate text-[13px] text-subtle">
              <span data-testid="practice-cursor">{formatSessionTime(state.cursor, state.exchangeTimezone, state.timeBasis)}</span>
              {state.timeBasis === 'NEW_YORK_WALL_CLOCK' && <span data-testid="date-hidden"> · date hidden</span>}
            </p>
          </div>
          <button
            type="button"
            aria-label="Drawing tools"
            aria-pressed={toolsShown}
            onClick={() => setToolsShown((t) => !t)}
            className={cx(ICON, toolsShown && 'bg-accent/15 text-accent')}
          >
            <PencilLine aria-hidden className="size-5" />
          </button>
          <LayersMenu layers={layers} clockAvailable={drawsClock(view)} />
        </header>
        {toolsShown && (
          <div className="px-3 pb-2">
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
          </div>
        )}
        <div className="px-3 pb-2">
          <Segmented<string>
            options={TIMEFRAMES}
            value={view}
            onChange={(next) => {
              if (next !== view) void switchTimeframe(next)
            }}
            label="Chart timeframe"
          />
        </div>
        <div className="flex items-center justify-between border-t border-line px-4 py-1.5 text-[13px]">
          <Chip tone={STATUS_TONE[state.status]} data-testid="practice-status">
            {STATUS_LABEL[state.status]}
          </Chip>
          <span className="text-subtle">
            Session <SessionScore score={score} />
          </span>
        </div>
        <LayerChips layers={layers} clockAvailable={drawsClock(view)} />
        <SimulatedDataBanner dataClass={state.dataClass} />
        <div className="border-t border-line bg-surface">
          <PriceChart
            heightClass={draftPlan ? 'h-[30svh]' : 'h-[52svh]'}
            bars={bars}
            tickSize={state.tickSize}
            exchangeTimezone={state.exchangeTimezone}
            axisTimezone={state.timeBasis === 'NEW_YORK_WALL_CLOCK' ? 'wall-clock' : 'exchange'}
            daily={view === '1d'}
            adapterFactory={adapterFactory}
            recentBars={RECENT_BARS}
            overlay={overlay}
          />
        </div>
        {trades.length > 0 && (
          <details className="px-4 pt-2">
            <summary className="cursor-pointer py-2 text-sm font-semibold text-ink">Trades ({trades.length})</summary>
            <TradeLog state={state} selected={shownTrade?.number} onSelect={(number) => setViewing(number)} />
          </details>
        )}

        {draftPlan && status === 'ANALYSING' ? (
          <PhonePlan
            plan={draftPlan}
            referenceTicks={referenceTicks}
            tickSize={state.tickSize}
            tickValueCents={state.tickValueCents}
            riskLimitCents={riskLimitCents}
            costs={costs}
            busy={busy}
            onFlip={flip}
            onDiscard={discard}
            onChangeLevel={changeLevel}
            onChangeContracts={setContracts}
            intent={intent}
            onIntent={setIntent}
            context={planContext}
            onLock={(plan) => {
              setPlaying(false)
              void lockPlan({ ...plan, ...intent }).then((ok) => {
                if (ok) {
                  rawDispatch({ type: 'forget', id: PLAN_DRAWING_ID })
                  setIntent({})
                  setPartial(null)
                }
              })
            }}
          />
        ) : (
          <div aria-label="Replay" role="group" className={DOCK}>
            {error && (
              <p role="alert" className={cx(errorClass, 'mb-2')}>
                {error}
              </p>
            )}
            {status === 'ANALYSING' && (
              <div className="mb-2 grid grid-cols-2 gap-2">
                <button type="button" onClick={() => place('LONG')} className={cx(BASE, 'justify-center border-up/40 bg-up/10 text-up')}>
                  <TrendingUp aria-hidden className="size-5" />
                  Plan long
                </button>
                <button type="button" onClick={() => place('SHORT')} className={cx(BASE, 'justify-center border-down/40 bg-down/10 text-down')}>
                  <TrendingDown aria-hidden className="size-5" />
                  Plan short
                </button>
              </div>
            )}
            {openTrade && (
              <section
                aria-label="Trade"
                className="mb-2 flex items-center gap-3 rounded-2xl border border-up/30 bg-up/10 py-2.5 pr-2.5 pl-3.5"
              >
                <div className="min-w-0 flex-1">
                  <p className="flex items-center gap-2">
                    <span className="rounded-full bg-up/15 px-2 py-0.5 text-[11px] font-semibold text-up">
                      {openTrade.plan.direction === 'LONG' ? 'Long' : 'Short'} {openTrade.plan.contracts}
                    </span>
                    <span className="text-[13px] text-up/80" data-testid="trade-entry">
                      {openTrade.entry
                        ? `in at ${formatPrice(openTrade.entry.ticks, state.tickSize)}`
                        : 'placed · fills at the next price'}
                    </span>
                  </p>
                  <p className="num mt-1 text-[13px]">
                    <span className="text-down" data-testid="trade-stop">
                      SL {formatPrice(openTrade.plan.stopTicks, state.tickSize)}
                    </span>
                    {' · '}
                    <span className="text-up">TP {formatPrice(openTrade.plan.targetTicks, state.tickSize)}</span>
                    {' · '}
                    {openTrade.plan.plannedRewardRisk.toFixed(2)}R
                  </p>
                </div>
                <button type="button" disabled={busy} onClick={() => void close()} className={cx(BASE, 'h-11 border-down/40 bg-down/10 px-3 text-sm text-down')}>
                  Close
                </button>
              </section>
            )}
            {replay}
          </div>
        )}

        <Sheet open={more} onClose={() => setMore(false)} label="End session">
          <h2 className="text-lg font-semibold text-ink">End the session?</h2>
          <p className="mt-1 text-sm text-subtle">
            No more trades{state.timeBasis === 'NEW_YORK_WALL_CLOCK' && ', and the date is revealed'}. It stays open otherwise
            — you can come back to it from Practice.
          </p>
          <div className="mt-4 grid grid-cols-2 gap-2">
            <button type="button" onClick={() => setMore(false)} className={QUIET}>
              Keep trading
            </button>
            <button
              type="button"
              disabled={busy}
              onClick={() => {
                setMore(false)
                void end()
              }}
              className={PRIMARY}
            >
              End session
            </button>
          </div>
        </Sheet>
      </section>
    )
  }

  return (
    <section
      aria-label="Practice session"
      className="flex flex-col gap-3 lg:h-[calc(100svh-8.25rem)] lg:min-h-[36rem] lg:flex-row"
    >
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

      {/* Under 1024 px (T46) the chart takes most of the screen and the trading column follows it, the replay held at
          the screen's foot as you scroll. */}
      <div className="flex h-[70svh] min-w-0 flex-col lg:flex-1 gap-3 rounded-card border border-line bg-surface p-3 shadow-panel lg:h-auto">
        <div className="flex flex-wrap items-center gap-x-4 gap-y-2">
          <Button variant="ghost" size="sm" onClick={onBack}>
            <ArrowLeft aria-hidden className="size-4" />
            Practice
          </Button>
          <h1 className="font-display text-xl font-bold tracking-tight text-ink">{state.instrument}</h1>
          <Segmented<string>
            options={TIMEFRAMES}
            value={view}
            onChange={(next) => {
              if (next !== view) void switchTimeframe(next)
            }}
            label="Chart timeframe"
          />
          <span className="num text-sm text-subtle" data-testid="practice-cursor">
            {formatSessionTime(state.cursor, state.exchangeTimezone, state.timeBasis)}
          </span>
          {/* DEC-025: on real data the date is the answer; it is revealed when the session ends. */}
          {state.timeBasis === 'NEW_YORK_WALL_CLOCK' && state.status !== 'COMPLETE' && (
            <Chip data-testid="date-hidden" title="The date is revealed when the session ends">
              date hidden
            </Chip>
          )}
          <Chip tone={STATUS_TONE[state.status]} data-testid="practice-status">
            {STATUS_LABEL[state.status]}
          </Chip>
          {lastBar && (
            <span className="num text-lg font-semibold text-ink" data-testid="last-price">
              {formatPrice(lastBar.close, state.tickSize)}
            </span>
          )}
          <div className="ml-auto">
            <LayersMenu layers={layers} clockAvailable={drawsClock(view)} />
          </div>
        </div>
        <LayerChips layers={layers} clockAvailable={drawsClock(view)} />
        <SimulatedDataBanner dataClass={state.dataClass} />

        <div className="flex min-h-0 flex-1 flex-col">
          <PriceChart
            heightClass="h-full"
            bars={bars}
            tickSize={state.tickSize}
            exchangeTimezone={state.exchangeTimezone}
            axisTimezone={state.timeBasis === 'NEW_YORK_WALL_CLOCK' ? 'wall-clock' : 'exchange'}
            daily={view === '1d'}
            adapterFactory={adapterFactory}
            recentBars={RECENT_BARS}
            overlay={overlay}
          />
        </div>


        {/* The session's trades, a strip under the chart: picking one shows it there (T42). */}
        {(trades.length > 0 || status === 'COMPLETE') && (
          <details className="group max-h-[40%] shrink-0 overflow-y-auto" open={status === 'COMPLETE'}>
            <summary className="cursor-pointer text-sm font-semibold text-ink">Trades ({trades.length})</summary>
            <div className="mt-2">
              <TradeLog
                state={state}
                selected={shownTrade?.number}
                onSelect={(number) => setPicked({ number, closedAt: closed.length })}
              />
            </div>
          </details>
        )}
      </div>

      {/* The trading column (T42, R-18): the score, the plan or the trade, its result and review, and the replay at
          its foot — everything a trade needs, beside a chart that keeps its size. */}
      <aside
        aria-label="Trading"
        className="relative flex w-full shrink-0 flex-col gap-3 lg:w-[22rem] lg:overflow-y-auto lg:pr-1"
      >
        <section aria-label="Score" className="flex items-baseline justify-between rounded-card border border-line bg-surface px-4 py-3 shadow-panel">
          <span className="text-sm font-medium text-subtle">Session</span>
          <span className="num text-sm text-subtle" data-testid="session-score">
            {score.closedTrades} {score.closedTrades === 1 ? 'trade' : 'trades'}
            {score.closedTrades > 0 && (
              <>
                {' · '}
                <span className={score.totalR > 0 ? 'text-up' : score.totalR < 0 ? 'text-down' : ''}>
                  {formatR(score.totalR)}
                </span>
                {' · '}
                {formatCents(score.netPnlCents)}
              </>
            )}
          </span>
        </section>

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
              <dd data-testid="trade-stop" className="text-down">
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
              <dd className="text-up">{formatPrice(openTrade.plan.targetTicks, state.tickSize)}</dd>
              <dt>Reward : risk</dt>
              <dd>{openTrade.plan.plannedRewardRisk.toFixed(2)}</dd>
            </dl>
            <p className="mt-3 text-xs text-subtle">
              Step forward to watch it play out, or close it whenever you choose — at the next price.
            </p>
            <Button className="mt-3 w-full" variant="sell" disabled={busy} onClick={() => void close()}>
              Close at market
            </Button>
          </Panel>
        )}

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

        {/* Always in view at the column's foot, however long the scorecard above it (T42). On a phone it is one short
            rows — no title, the step's label an icon, "End session…" beside the speeds — so it does not cover a third of the
            screen (BUG-113). */}
        <Panel aria-label="Replay" className="sticky bottom-0 z-10 mt-auto max-lg:flex max-lg:flex-wrap max-lg:items-center max-lg:gap-2 max-lg:p-3">
          <h3 className={cx(panelTitle, 'max-lg:sr-only')}>Replay</h3>
          <div className="flex flex-wrap items-center gap-2 max-lg:contents">
            <Button disabled={busy || playing} onClick={() => void advance(1)} aria-label="Next bar">
              <StepForward aria-hidden className="size-4" />
              <span className="max-sm:hidden">Next bar</span>
            </Button>
            <Button disabled={busy || playing} onClick={() => void advance(10)}>
              Next 10
            </Button>
            {status !== 'COMPLETE' && (
              <Button active={playing} aria-pressed={playing} onClick={() => setPlaying(!playing)}>
                {playing ? <Pause aria-hidden className="size-4" /> : <Play aria-hidden className="size-4" />}
                <span className="max-sm:sr-only">{playing ? 'Pause' : 'Play'}</span>
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
            <div className="mt-4 flex flex-wrap items-center gap-2 border-t border-line pt-3 max-lg:contents">
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
                <Button size="sm" variant="ghost" disabled={busy} onClick={() => setEndAskedAt(trades.length)}>
                  End session…
                </Button>
              )}
            </div>
          )}

          {status === 'COMPLETE' && (
            <Button variant="link" className="mt-3" onClick={onBack}>
              Back to Practice
            </Button>
          )}

          {error && (
            <p role="alert" className={`mt-3 ${errorClass}`}>
              {error}
            </p>
          )}
        </Panel>
      </aside>
    </section>
  )
}

/** The phone's controls (T53). Colours are one complete set per button: a utility never laid over another. */
const BASE =
  'inline-flex h-12 cursor-pointer items-center gap-2 rounded-xl border text-[15px] font-semibold focus-visible:outline-2 focus-visible:outline-accent disabled:cursor-not-allowed disabled:opacity-40'
const QUIET = cx(BASE, 'justify-center border-line bg-raised text-ink')
const PRIMARY = cx(BASE, 'justify-center border-transparent bg-accent text-accent-ink')
const ICON = 'grid size-11 shrink-0 cursor-pointer place-items-center rounded-xl text-ink focus-visible:outline-2 focus-visible:outline-accent'
const DOCK =
  'fixed inset-x-0 bottom-0 z-30 border-t border-line bg-canvas/95 px-4 pt-3 pb-[calc(1rem+env(safe-area-inset-bottom))] backdrop-blur'

function SessionScore({ score }: { score: PracticeState['score'] }) {
  return (
    <span className="num text-ink" data-testid="session-score">
      {score.closedTrades} {score.closedTrades === 1 ? 'trade' : 'trades'}
      {score.closedTrades > 0 && (
        <>
          {' · '}
          <span className={score.totalR > 0 ? 'text-up' : score.totalR < 0 ? 'text-down' : ''}>{formatR(score.totalR)}</span>
          {' · '}
          {formatCents(score.netPnlCents)}
        </>
      )}
    </span>
  )
}

/** The session over, on a phone (T53): the date revealed, four figures, its trades, what to take from it. */
function PhoneEnded({
  state,
  closed,
  onOpen,
  onBack,
}: {
  state: PracticeState
  closed: TradeView[]
  onOpen: (number: number) => void
  onBack: () => void
}) {
  const score = state.score
  const lessons = takeaways(closed)
  const tone = (n: number) => (n > 0 ? 'text-up' : n < 0 ? 'text-down' : 'text-ink')
  return (
    <section aria-label="Session over" className="flex min-h-svh flex-col pb-32">
      <header className="flex h-14 items-center gap-1 pr-3 pl-1">
        <button type="button" aria-label="Close" onClick={onBack} className={ICON}>
          <X aria-hidden className="size-5" />
        </button>
        <h1 className="flex-1 text-[17px] font-semibold text-ink">Session over</h1>
        <Chip tone="good" data-testid="practice-status">
          {STATUS_LABEL.COMPLETE}
        </Chip>
      </header>
      <div className="flex flex-col gap-4 px-4 pt-2">
        {state.revealedStartTime !== undefined && (
          <section className="rounded-card border border-accent/30 bg-accent/10 px-4 py-5 text-center" data-testid="revealed-date">
            <p className="text-xs font-semibold tracking-wide text-accent uppercase">The hidden date was</p>
            <p className="mt-2 text-2xl font-semibold text-ink">{revealedDate(state.revealedStartTime)}</p>
            <p className="mt-0.5 text-sm text-subtle">New York · {state.instrument}</p>
          </section>
        )}
        <section className="grid grid-cols-2 gap-2">
          <Stat label="Total" value={formatR(score.totalR)} tone={tone(score.totalR)} />
          <Stat label="Net" value={formatCents(score.netPnlCents)} tone={tone(score.netPnlCents)} />
          <Stat label="Closed" value={String(score.closedTrades)} />
          <Stat label="Won" value={score.closedTrades === 0 ? '—' : `${Math.round((score.wins / score.closedTrades) * 100)}%`} />
        </section>
        {closed.length > 0 && (
          <section aria-labelledby="ended-trades">
            <h2 id="ended-trades" className="mb-2.5 text-base font-semibold text-ink">
              Trades
            </h2>
            <ul className="divide-y divide-line overflow-hidden rounded-card border border-line bg-surface">
              {closed.map((trade) => (
                <li key={trade.number}>
                  <EndedRow state={state} trade={trade} result={trade.result!} onOpen={() => onOpen(trade.number)} />
                </li>
              ))}
            </ul>
          </section>
        )}
        {lessons.length > 0 && (
          <section className="rounded-card border border-line bg-surface p-4" aria-labelledby="ended-takeaways">
            <h2 id="ended-takeaways" className="text-base font-semibold text-ink">
              What to take from it
            </h2>
            <ul className="mt-2.5 flex list-disc flex-col gap-1.5 pl-4 text-sm text-ink/85">
              {lessons.map((line) => (
                <li key={line}>{line}</li>
              ))}
            </ul>
            <p className="mt-2 text-xs text-subtle">From your scorecards, as they were.</p>
          </section>
        )}
      </div>
      <div className={DOCK}>
        <button type="button" onClick={onBack} className={cx(PRIMARY, 'w-full')}>
          Back to Practice
        </button>
      </div>
    </section>
  )
}

function Stat({ label, value, tone }: { label: string; value: string; tone?: string }) {
  return (
    <div className="rounded-card border border-line bg-surface px-3.5 py-3">
      <div className="text-[13px] text-subtle">{label}</div>
      <div className={cx('num mt-0.5 text-xl font-semibold', tone ?? 'text-ink')}>{value}</div>
    </div>
  )
}

function EndedRow({ state, trade, result, onOpen }: { state: PracticeState; trade: TradeView; result: ResultView; onOpen: () => void }) {
  return (
    <button
      type="button"
      onClick={onOpen}
      className="flex min-h-14 w-full cursor-pointer items-center gap-3 px-4 py-2.5 text-left focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-accent"
    >
      <span className="num w-4 text-subtle">{trade.number}</span>
      <span
        className={cx(
          'rounded-full px-2 py-0.5 text-[11px] font-semibold',
          trade.plan.direction === 'LONG' ? 'bg-up/15 text-up' : 'bg-down/15 text-down',
        )}
      >
        {trade.plan.direction === 'LONG' ? 'Long' : 'Short'}
      </span>
      <span className="min-w-0 flex-1">
        <span className="num block text-sm text-ink">
          {formatPrice(result.entryTicks, state.tickSize)} → {formatPrice(result.exitTicks, state.tickSize)}
        </span>
        <span className="block text-[13px] text-subtle">{outcomeLabel(result.outcome)}</span>
      </span>
      <span className={cx('num font-semibold', result.rMultiple > 0 ? 'text-up' : result.rMultiple < 0 ? 'text-down' : 'text-ink')}>
        {formatR(result.rMultiple)}
      </span>
    </button>
  )
}
