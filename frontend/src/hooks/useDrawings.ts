import { useCallback, useEffect, useLayoutEffect, useReducer, useRef, useState } from 'react'
import { deleteDrawing, listDrawings, saveDrawing } from '@/lib/api/drawings'
import {
  canRedo as canRedoOf,
  canUndo as canUndoOf,
  drawingReducer,
  initialDrawingState,
  type DrawingAction,
} from '@/lib/drawing/store'

/**
 * Drawing state for one instrument, with persistence.
 *
 * The local reducer is the source of truth while the user is working; the server is synchronised
 * afterwards. Doing it the other way round — awaiting a round trip before the shape moves — would
 * make dragging feel broken on any connection worse than a LAN.
 *
 * BUG-023 — a failed load or save is reported through `syncError`, never swallowed. The original
 * version caught every failure silently so a network blip could not crash a drag. That goal was
 * right and the method was not: it hid a completely broken save path for a whole session, because a
 * chart whose drawings are not being saved looks exactly like one whose drawings are. Failures are
 * still non-fatal and still retried; they are just no longer invisible.
 */
/**
 * A failure, tagged with the chart it happened on. Tagging rather than clearing on every symbol
 * change is what lets a switch of instrument hide another chart's error without an effect having to
 * reset state before the new chart has even loaded.
 */
interface SyncProblem {
  symbol: string
  message: string
}

export function useDrawings(symbol: string) {
  const [state, dispatch] = useReducer(drawingReducer, initialDrawingState)
  const [loadError, setLoadError] = useState<SyncProblem | null>(null)
  const [saveError, setSaveError] = useState<SyncProblem | null>(null)
  // Bumped to run the load again after it failed.
  const [loadAttempt, setLoadAttempt] = useState(0)

  // What the server currently holds, so a commit can work out what actually changed rather than
  // re-uploading every shape on every gesture.
  const persistedRef = useRef<Map<string, string>>(new Map())
  const symbolRef = useRef(symbol)
  const stateRef = useRef(state)
  // BUG-149, BUG-150: the commit running now; the next waits for it. Side by side, an earlier drag's save could land
  // after a later one's, and a delete straight after a creation found nothing saved yet to delete.
  const commitsRef = useRef<Promise<unknown>>(Promise.resolve())

  // Latest values for commit(), which runs later and asynchronously. Written at commit rather than
  // during render (BUG-035), so a discarded render cannot leave them pointing at state that never was.
  useLayoutEffect(() => {
    symbolRef.current = symbol
    stateRef.current = state
  })

  /**
   * BUG-096 — also brings the drawings commit() reads up to date at once, in the event handler: the toolbar's undo
   * and delete commit straight after dispatching, before React renders, and saved the drawings as they were before.
   * The reducer is pure, so working the change out here gives exactly the state React will render.
   */
  const dispatchNow = useCallback((action: DrawingAction) => {
    stateRef.current = drawingReducer(stateRef.current, action)
    dispatch(action)
  }, [])

  /**
   * Pushes local changes to the server.
   *
   * Diffed against the last known server state so a drag that moved one line does not re-save the
   * other forty. Called when a gesture ends, never during one.
   *
   * A shape that fails to save is left out of the persisted map, so the next commit retries it.
   * Resolves to whether everything is now in sync.
   */
  const commitNow = useCallback(async (): Promise<boolean> => {
    const current = stateRef.current.drawings
    const persisted = persistedRef.current
    const sym = symbolRef.current

    const currentIds = new Set(current.map((d) => d.id))
    const work: Promise<boolean>[] = []

    for (const drawing of current) {
      // A label still waiting for its words is not finished: the server would refuse it, and the
      // editor commits it the moment it has some.
      if (drawing.kind === 'label' && !drawing.text) continue
      const serialised = JSON.stringify(drawing)
      if (persisted.get(drawing.id) !== serialised) {
        work.push(
          saveDrawing(sym, drawing)
            .then(() => {
              persisted.set(drawing.id, serialised)
              return true
            })
            .catch(() => false),
        )
      }
    }

    for (const id of persisted.keys()) {
      if (!currentIds.has(id)) {
        work.push(
          deleteDrawing(sym, id)
            .then(() => {
              persisted.delete(id)
              return true
            })
            .catch(() => false),
        )
      }
    }

    const ok = (await Promise.all(work)).every(Boolean)
    // A symbol switch while the saves were in flight makes this result about a chart that is no
    // longer on screen; reporting it there would blame the wrong chart.
    if (sym === symbolRef.current) {
      setSaveError(
        ok
          ? null
          : {
              symbol: sym,
              message: 'Some drawings could not be saved. They will be retried with your next change.',
            },
      )
    }
    return ok
  }, [])

  /** One commit at a time, each diffing the drawings as they are when its turn comes (BUG-149, BUG-150). */
  const commit = useCallback((): Promise<boolean> => {
    const run = commitsRef.current.then(commitNow)
    commitsRef.current = run.catch(() => undefined)
    return run
  }, [commitNow])

  // BUG-152: the chart whose drawings these are. Another market's are dropped the moment the market changes, not when
  // its drawings arrive — until then, deleting one sent the old market's id to the new market's address.
  const loadedSymbolRef = useRef(symbol)

  useEffect(() => {
    let cancelled = false
    if (loadedSymbolRef.current !== symbol) {
      loadedSymbolRef.current = symbol
      persistedRef.current = new Map()
      dispatchNow({ type: 'load', drawings: [] })
    }
    listDrawings(symbol)
      .then((drawings) => {
        if (cancelled) return
        persistedRef.current = new Map(drawings.map((d) => [d.id, JSON.stringify(d)]))
        // BUG-151: what was drawn while the load was on its way stays — the server's snapshot predates it.
        const onServer = new Set(drawings.map((d) => d.id))
        const drawnMeanwhile = stateRef.current.drawings.filter((d) => !onServer.has(d.id))
        dispatchNow({ type: 'load', drawings: [...drawings, ...drawnMeanwhile] })
        setLoadError(null)
        setSaveError(null)
        if (drawnMeanwhile.length > 0) void commit()
      })
      .catch(() => {
        // A chart that loads without its annotations is degraded but usable; one that refuses to
        // load because a secondary request failed is not. But the user is told: an empty chart
        // would otherwise read as "my drawings are gone".
        if (cancelled) return
        // Whatever is drawn here stays (BUG-151) and is saved with the next change.
        persistedRef.current = new Map()
        setSaveError(null)
        setLoadError({
          symbol,
          message: 'Your saved drawings could not be loaded. New drawings will still be saved.',
        })
      })
    return () => {
      cancelled = true
    }
  }, [symbol, loadAttempt, dispatchNow, commit])

  /**
   * Retries whatever failed. A failed save first: it is about work the user has done and would
   * lose. Reloading afterwards is safe only once nothing local is waiting to be saved, because a
   * load replaces the local drawings with the server's.
   */
  const currentSaveError = saveError?.symbol === symbol ? saveError.message : null
  const currentLoadError = loadError?.symbol === symbol ? loadError.message : null

  const retry = useCallback(async () => {
    if (currentSaveError) {
      const saved = await commit()
      if (!saved) return
    }
    if (currentLoadError) setLoadAttempt((n) => n + 1)
  }, [currentSaveError, currentLoadError, commit])

  return {
    state,
    dispatch: dispatchNow,
    commit,
    retry,
    // A failed save is the more urgent message: it is about work being lost from now on.
    syncError: currentSaveError ?? currentLoadError,
    canUndo: canUndoOf(state),
    canRedo: canRedoOf(state),
  }
}
