import { useCallback, useEffect, useReducer, useRef, useState } from 'react'
import { deleteDrawing, listDrawings, saveDrawing } from '@/lib/api/drawings'
import {
  canRedo as canRedoOf,
  canUndo as canUndoOf,
  drawingReducer,
  initialDrawingState,
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
export function useDrawings(symbol: string) {
  const [state, dispatch] = useReducer(drawingReducer, initialDrawingState)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [saveError, setSaveError] = useState<string | null>(null)
  // Bumped to run the load again after it failed.
  const [loadAttempt, setLoadAttempt] = useState(0)

  // What the server currently holds, so a commit can work out what actually changed rather than
  // re-uploading every shape on every gesture.
  const persistedRef = useRef<Map<string, string>>(new Map())
  const symbolRef = useRef(symbol)
  symbolRef.current = symbol

  useEffect(() => {
    let cancelled = false
    setLoadError(null)
    setSaveError(null)
    listDrawings(symbol)
      .then((drawings) => {
        if (cancelled) return
        persistedRef.current = new Map(drawings.map((d) => [d.id, JSON.stringify(d)]))
        dispatch({ type: 'load', drawings })
      })
      .catch(() => {
        // A chart that loads without its annotations is degraded but usable; one that refuses to
        // load because a secondary request failed is not. But the user is told: an empty chart
        // would otherwise read as "my drawings are gone".
        if (cancelled) return
        persistedRef.current = new Map()
        dispatch({ type: 'load', drawings: [] })
        setLoadError('Your saved drawings could not be loaded. New drawings will still be saved.')
      })
    return () => {
      cancelled = true
    }
  }, [symbol, loadAttempt])

  const stateRef = useRef(state)
  stateRef.current = state

  /**
   * Pushes local changes to the server.
   *
   * Diffed against the last known server state so a drag that moved one line does not re-save the
   * other forty. Called when a gesture ends, never during one.
   *
   * A shape that fails to save is left out of the persisted map, so the next commit retries it.
   * Resolves to whether everything is now in sync.
   */
  const commit = useCallback(async (): Promise<boolean> => {
    const current = stateRef.current.drawings
    const persisted = persistedRef.current
    const sym = symbolRef.current

    const currentIds = new Set(current.map((d) => d.id))
    const work: Promise<boolean>[] = []

    for (const drawing of current) {
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
        ok ? null : 'Some drawings could not be saved. They will be retried with your next change.',
      )
    }
    return ok
  }, [])

  /**
   * Retries whatever failed. A failed save first: it is about work the user has done and would
   * lose. Reloading afterwards is safe only once nothing local is waiting to be saved, because a
   * load replaces the local drawings with the server's.
   */
  const retry = useCallback(async () => {
    if (saveError) {
      const saved = await commit()
      if (!saved) return
    }
    if (loadError) setLoadAttempt((n) => n + 1)
  }, [saveError, loadError, commit])

  return {
    state,
    dispatch,
    commit,
    retry,
    // A failed save is the more urgent message: it is about work being lost from now on.
    syncError: saveError ?? loadError,
    canUndo: canUndoOf(state),
    canRedo: canRedoOf(state),
  }
}
