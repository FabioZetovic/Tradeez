import { useCallback, useReducer, useState } from 'react'
import { PriceChart } from '../PriceChart'
import { DrawingOverlay } from '../DrawingOverlay'
import { DrawingToolbar } from '../DrawingToolbar'
import { SimulatedDataBanner } from '../SimulatedDataBanner'
import { PlanPanel } from './PlanPanel'
import { ResultCard } from './ResultCard'
import { usePracticeSession } from '@/hooks/usePracticeSession'
import { useDrawingShortcuts } from '@/hooks/useDrawingShortcuts'
import type { Direction, PlanView, PracticeState, SessionResponse } from '@/lib/api/practice'
import type { ChartAdapter } from '@/lib/chart/types'
import { formatPrice } from '@/lib/chart/priceFormat'
import { formatSessionTime, type AxisTimezone } from '@/lib/chart/timeAxis'
import {
  canRedo,
  canUndo,
  drawingReducer,
  initialDrawingState,
  type DrawingAction,
} from '@/lib/drawing/store'
import type { SnapMode } from '@/lib/drawing/snap'
import type { Drawing, DrawingKind } from '@/lib/drawing/types'
import {
  defaultBracket,
  flipBracket,
  pinEntry,
  PLAN_DRAWING_ID,
  planFromDrawing,
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

const STATUS_LABEL: Record<PracticeState['status'], string> = {
  ANALYSING: 'Analysing',
  IN_TRADE: 'In trade',
  COMPLETE: 'Complete',
}

/**
 * One practice session: the hidden moment, the plan, the replay, the result.
 *
 * Drawings here are local and unsaved. They are analysis for this moment only, and saving them to
 * the instrument's chart would put marks from a market the learner cannot otherwise see onto the
 * public one. The one drawing with meaning is the plan — the position tool, id
 * {@link PLAN_DRAWING_ID} — whose entry is pinned to the market price and which freezes when locked.
 */
export function PracticeSession({ sessionId, initial, onBack, adapterFactory }: PracticeSessionProps) {
  const { state, bars, error, busy, playing, setPlaying, advance, skip, lockPlan } =
    usePracticeSession(sessionId, initial)

  const [drawingState, rawDispatch] = useReducer(drawingReducer, initialDrawingState)
  const [activeTool, setActiveTool] = useState<DrawingKind | null>(null)
  const [snapMode, setSnapMode] = useState<SnapMode>('magnet')
  const [axisTimezone] = useState<AxisTimezone>('exchange')
  const [contracts, setContracts] = useState(1)

  const lastBar = bars.length > 0 ? bars[bars.length - 1] : undefined
  const status = state?.status
  const locked = status !== undefined && status !== 'ANALYSING'

  // The price a market order is priced against, and the bar the bracket starts from. Before the
  // plan is locked that is the latest bar; after, it is what the server recorded at the lock.
  const referenceTicks = state?.plan?.referenceTicks ?? lastBar?.close ?? 0
  const anchorTime =
    state?.plan && state.timeframe
      ? state.plan.lockedAt - timeframeSeconds(state.timeframe)
      : (lastBar?.time ?? 0)

  /**
   * Every drawing action passes through here. Actions on the plan drawing are rewritten to keep its
   * entry on the market price, and dropped entirely once the plan is locked — the server would
   * refuse a change anyway (DEC-013), and a chart that let the learner drag a locked stop would be
   * showing a trade that does not exist.
   */
  const dispatch = useCallback(
    (action: DrawingAction) => {
      const touchesPlan =
        ('drawing' in action && action.drawing.id === PLAN_DRAWING_ID) ||
        ('id' in action && action.id === PLAN_DRAWING_ID)
      if (touchesPlan && locked) return

      if ('drawing' in action && action.drawing.id === PLAN_DRAWING_ID) {
        rawDispatch({ ...action, drawing: pinEntry(action.drawing, referenceTicks, anchorTime) })
        return
      }
      rawDispatch(action)
    },
    [locked, referenceTicks, anchorTime],
  )

  // What the chart shows, derived on every render rather than written back into the drawing store
  // by effects. While analysing, the bracket's entry follows the market price as the replay moves.
  // Once locked, the bracket is the server's plan — not whatever was last drawn — and it stays that
  // way through undo and redo, which only ever see the learner's own edits.
  const serverPlan = state?.plan
  const storedPlan = drawingState.drawings.find((d) => d.id === PLAN_DRAWING_ID)
  const planDrawing: Drawing | undefined = serverPlan
    ? lockedBracket(serverPlan, anchorTime)
    : storedPlan && pinEntry(storedPlan, referenceTicks, anchorTime)
  const shownState = {
    ...drawingState,
    drawings: [
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
      <section className="practice">
        <button type="button" className="link" onClick={onBack}>
          ← Journal
        </button>
        {error ? (
          <p role="alert" className="error">
            {error}
          </p>
        ) : (
          <p role="status">Loading session…</p>
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

  return (
    <section className="practice">
      <SimulatedDataBanner dataClass={state.dataClass} />

      <div className="toolbar">
        <button type="button" className="link" onClick={onBack}>
          ← Journal
        </button>
        <span>
          <strong>{state.instrument}</strong> · {state.timeframe}
        </span>
        <span className={`chip chip--${state.status.toLowerCase()}`} data-testid="practice-status">
          {STATUS_LABEL[state.status]}
        </span>
        <span className="muted" data-testid="practice-cursor">
          {formatSessionTime(state.cursor, state.exchangeTimezone)}
        </span>
        {lastBar && (
          <span className="last-price" data-testid="last-price">
            {formatPrice(lastBar.close, state.tickSize)}
          </span>
        )}
      </div>

      <div className="chart-area">
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
          axisTimezone={axisTimezone}
          adapterFactory={adapterFactory}
          recentBars={RECENT_BARS}
          overlay={(adapter) => (
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
          )}
        />
      </div>

      <div className="practice__panels">
        <section className="panel" aria-label="Replay">
          <h3>Replay</h3>
          <div className="button-row">
            <button type="button" disabled={busy || playing} onClick={() => void advance(1)}>
              Next bar
            </button>
            <button type="button" disabled={busy || playing} onClick={() => void advance(10)}>
              Next 10
            </button>
            {status !== 'COMPLETE' && (
              <button type="button" aria-pressed={playing} onClick={() => setPlaying(!playing)}>
                {playing ? 'Pause' : 'Play'}
              </button>
            )}
            {status === 'IN_TRADE' && (
              <button type="button" disabled={busy} onClick={() => void skip()}>
                Skip to result
              </button>
            )}
          </div>
          {error && (
            <p role="alert" className="error">
              {error}
            </p>
          )}
        </section>

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
              void lockPlan(plan)
            }}
          />
        )}

        {status === 'IN_TRADE' && state.plan && (
          <section className="panel" aria-label="Trade">
            <h3>
              {state.plan.direction === 'LONG' ? 'Long' : 'Short'} {state.plan.contracts} — locked
            </h3>
            <dl className="kv small">
              <dt>Entry</dt>
              <dd data-testid="trade-entry">
                {state.entry
                  ? `${formatPrice(state.entry.ticks, state.tickSize)} · ${formatSessionTime(state.entry.time, state.exchangeTimezone)}`
                  : 'at the next price'}
              </dd>
              <dt>Stop</dt>
              <dd>{formatPrice(state.plan.stopTicks, state.tickSize)}</dd>
              <dt>Target</dt>
              <dd>{formatPrice(state.plan.targetTicks, state.tickSize)}</dd>
              <dt>Reward : risk</dt>
              <dd>{state.plan.plannedRewardRisk.toFixed(2)}</dd>
            </dl>
            <p className="muted small">
              The outcome was decided when you locked the plan. Step forward to watch it happen.
            </p>
          </section>
        )}

        {status === 'COMPLETE' && state.result && (
          <ResultCard state={state} result={state.result} />
        )}
      </div>
    </section>
  )
}

/** The locked plan as a position drawing, anchored to the bar that was last visible at the lock. */
function lockedBracket(plan: PlanView, time: number): Drawing {
  return {
    ...defaultBracket(plan.direction, plan.referenceTicks, time),
    anchors: [
      { time, priceTicks: plan.referenceTicks },
      { time, priceTicks: plan.stopTicks },
      { time, priceTicks: plan.targetTicks },
    ],
  }
}

function timeframeSeconds(code: string): number {
  const unit = code.slice(-1)
  const n = Number(code.slice(0, -1))
  return unit === 'm' ? n * 60 : unit === 'h' ? n * 3600 : unit === 's' ? n : n * 86_400
}
