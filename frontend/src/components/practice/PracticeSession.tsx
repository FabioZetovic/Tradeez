import { useCallback, useEffect, useReducer, useState } from 'react'
import { PriceChart } from '../PriceChart'
import { ClockOverlay } from '../ClockOverlay'
import { ClockToggles } from '../ClockToggles'
import { DrawingOverlay } from '../DrawingOverlay'
import { DrawingToolbar } from '../DrawingToolbar'
import { SimulatedDataBanner } from '../SimulatedDataBanner'
import { PlanPanel } from './PlanPanel'
import { ResultCard } from './ResultCard'
import { TradeLog } from './TradeLog'
import { SPEEDS, usePracticeSession, type Speed } from '@/hooks/usePracticeSession'
import { useClockLayers } from '@/hooks/useClockLayers'
import { useDrawingShortcuts } from '@/hooks/useDrawingShortcuts'
import type { Direction, PracticeState, SessionResponse } from '@/lib/api/practice'
import { getPracticeClock, type ClockLayer } from '@/lib/api/clock'
import type { ChartAdapter } from '@/lib/chart/types'
import { formatPrice } from '@/lib/chart/priceFormat'
import { formatSessionTime } from '@/lib/chart/timeAxis'
import { timeframeSeconds } from '@/lib/chart/dateNavigation'
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
} from '@/lib/practice/plan'

interface PracticeSessionProps {
  sessionId: string
  /** The creation response, so a new session is not fetched a second time. */
  initial?: SessionResponse
  onBack: () => void
  adapterFactory?: () => ChartAdapter
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
export function PracticeSession({ sessionId, initial, onBack, adapterFactory }: PracticeSessionProps) {
  const {
    state,
    bars,
    error,
    busy,
    playing,
    setPlaying,
    speed,
    setSpeed,
    advance,
    skip,
    lockPlan,
    end,
  } = usePracticeSession(sessionId, initial)

  const [drawingState, rawDispatch] = useReducer(drawingReducer, initialDrawingState)
  const [activeTool, setActiveTool] = useState<DrawingKind | null>(null)
  const [snapMode, setSnapMode] = useState<SnapMode>('magnet')
  const [contracts, setContracts] = useState(1)
  const [selectedTrade, setSelectedTrade] = useState<number | undefined>(undefined)
  const [confirmingEnd, setConfirmingEnd] = useState(false)

  // The New York clock up to the cursor (T7). The server says when it can next change; it is asked
  // again only once the replay gets there, not on every bar. A failure costs only the overlay.
  const [clock, setClock] = useState<ClockLayer | null>(null)
  const { shown: clockShown, toggle: toggleClock } = useClockLayers()
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

  const lastBar = bars.length > 0 ? bars[bars.length - 1] : undefined
  const status = state?.status
  const trades = state?.trades ?? []
  const openTrade = status === 'IN_TRADE' ? trades[trades.length - 1] : undefined
  // A plan can be drawn only while flat. Running or ended, the bracket is not the learner's to move.
  const locked = status !== undefined && status !== 'ANALYSING'

  // The price a market order is priced against, and the bar the bracket starts from. While flat that
  // is the latest bar; in a trade, it is what the server recorded at the lock.
  const referenceTicks = openTrade?.plan.referenceTicks ?? lastBar?.close ?? 0
  const anchorTime =
    openTrade && state
      ? openTrade.plan.lockedAt - timeframeSeconds(state.timeframe)
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
    ? lockedBracket(openTrade.plan, anchorTime)
    : !locked && storedPlan
      ? pinEntry(storedPlan, referenceTicks, anchorTime)
      : undefined
  const shownState = {
    ...drawingState,
    drawings: [
      // First, so the learner's own analysis draws over them and wins every click.
      ...(state ? trades.flatMap((t) => closedTradeDrawing(t, state.timeframe) ?? []) : []),
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

  const draftPlan = planDrawing && !locked ? planFromDrawing(planDrawing, contracts) : null

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
  const flip = () => planDrawing && dispatch({ type: 'update', drawing: flipBracket(planDrawing) })
  const changeLevel = (level: 'stop' | 'target', ticks: number) => {
    if (!planDrawing) return
    const anchors = [...planDrawing.anchors]
    anchors[level === 'stop' ? 1 : 2] = { ...anchors[level === 'stop' ? 1 : 2], priceTicks: ticks }
    dispatch({ type: 'update', drawing: { ...planDrawing, anchors } })
  }

  // The trade whose details are open: the one picked in the log, else the latest to finish.
  const closed = trades.filter((t) => t.result)
  const shownTrade = closed.find((t) => t.number === selectedTrade) ?? closed[closed.length - 1]

  return (
    <section className="flex flex-col gap-4">
      <SimulatedDataBanner dataClass={state.dataClass} />

      <div className="flex flex-wrap items-center gap-x-5 gap-y-3 rounded-lg border border-line bg-surface px-4 py-3 text-sm">
        <Button variant="link" onClick={onBack}>
          ← Journal
        </Button>
        <span>
          <strong>{state.instrument}</strong> · {state.timeframe}
        </span>
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

      <div className="self-start">
        <ClockToggles shown={clockShown} onToggle={toggleClock} />
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
          adapterFactory={adapterFactory}
          recentBars={RECENT_BARS}
          overlay={(adapter) => (
            <>
              <ClockOverlay
                adapter={adapter}
                layer={clock}
                bars={bars}
                timeframe={state.timeframe}
                tickSize={state.tickSize}
                shown={clockShown}
              />
              <DrawingOverlay
                adapter={adapter}
                state={shownState}
                dispatch={dispatch}
                activeTool={activeTool}
                onToolFinished={() => setActiveTool(null)}
                bars={bars}
                snapMode={snapMode}
                tickValueCents={state.tickValueCents}
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
                      setConfirmingEnd(false)
                      void end()
                    }}
                  >
                    End session
                  </Button>
                  <Button size="sm" variant="ghost" onClick={() => setConfirmingEnd(false)}>
                    Keep trading
                  </Button>
                </>
              ) : (
                <Button size="sm" variant="ghost" disabled={busy} onClick={() => setConfirmingEnd(true)}>
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
            busy={busy}
            onPlace={place}
            onFlip={flip}
            onChangeLevel={changeLevel}
            onChangeContracts={setContracts}
            onLock={(plan) => {
              setPlaying(false)
              void lockPlan(plan).then((ok) => {
                // The draft became the trade; the next one starts from a fresh bracket.
                if (ok) rawDispatch({ type: 'remove', id: PLAN_DRAWING_ID })
              })
            }}
          />
        )}

        {openTrade && (
          <Panel aria-label="Trade">
            <h3 className={panelTitle}>
              Trade {openTrade.number} · {openTrade.plan.direction === 'LONG' ? 'Long' : 'Short'}{' '}
              {openTrade.plan.contracts} — locked
            </h3>
            <dl className={keyValueClass}>
              <dt>Entry</dt>
              <dd data-testid="trade-entry">
                {openTrade.entry
                  ? `${formatPrice(openTrade.entry.ticks, state.tickSize)} · ${formatSessionTime(openTrade.entry.time, state.exchangeTimezone, state.timeBasis)}`
                  : 'at the next price'}
              </dd>
              <dt>Stop</dt>
              <dd>{formatPrice(openTrade.plan.stopTicks, state.tickSize)}</dd>
              <dt>Target</dt>
              <dd>{formatPrice(openTrade.plan.targetTicks, state.tickSize)}</dd>
              <dt>Reward : risk</dt>
              <dd>{openTrade.plan.plannedRewardRisk.toFixed(2)}</dd>
            </dl>
            <p className="mt-3 text-xs text-subtle">
              The outcome was decided when you locked the plan. Step forward to watch it happen.
            </p>
          </Panel>
        )}

        {shownTrade?.result && (
          <ResultCard state={state} trade={shownTrade} result={shownTrade.result} />
        )}
      </div>

      {(trades.length > 0 || status === 'COMPLETE') && (
        <TradeLog state={state} selected={shownTrade?.number} onSelect={setSelectedTrade} />
      )}
    </section>
  )
}
