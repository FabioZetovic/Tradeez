import { useCallback, useEffect, useReducer, useRef } from 'react'
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
 */
export function useDrawings(symbol: string) {
  const [state, dispatch] = useReducer(drawingReducer, initialDrawingState)

  // What the server currently holds, so a commit can work out what actually changed rather than
  // re-uploading every shape on every gesture.
  const persistedRef = useRef<Map<string, string>>(new Map())
  const symbolRef = useRef(symbol)
  symbolRef.current = symbol

  useEffect(() => {
    let cancelled = false
    listDrawings(symbol)
      .then((drawings) => {
        if (cancelled) return
        persistedRef.current = new Map(drawings.map((d) => [d.id, JSON.stringify(d)]))
        dispatch({ type: 'load', drawings })
      })
      .catch(() => {
        // A chart that loads without its annotations is degraded but usable; one that refuses to
        // load because a secondary request failed is not.
        if (!cancelled) dispatch({ type: 'load', drawings: [] })
      })
    return () => {
      cancelled = true
    }
  }, [symbol])

  const stateRef = useRef(state)
  stateRef.current = state

  /**
   * Pushes local changes to the server.
   *
   * Diffed against the last known server state so a drag that moved one line does not re-save the
   * other forty. Called when a gesture ends, never during one.
   */
  const commit = useCallback(async () => {
    const current = stateRef.current.drawings
    const persisted = persistedRef.current
    const sym = symbolRef.current

    const currentIds = new Set(current.map((d) => d.id))
    const work: Promise<unknown>[] = []

    for (const drawing of current) {
      const serialised = JSON.stringify(drawing)
      if (persisted.get(drawing.id) !== serialised) {
        work.push(
          saveDrawing(sym, drawing)
            .then(() => persisted.set(drawing.id, serialised))
            .catch(() => {
              // Leave it out of `persisted` so the next commit retries it. Swallowing the error is
              // deliberate: an autosave that throws mid-drag would surface as a crash over a
              // transient network blip.
            }),
        )
      }
    }

    for (const id of persisted.keys()) {
      if (!currentIds.has(id)) {
        work.push(deleteDrawing(sym, id).then(() => persisted.delete(id)).catch(() => {}))
      }
    }

    await Promise.all(work)
  }, [])

  return {
    state,
    dispatch,
    commit,
    canUndo: canUndoOf(state),
    canRedo: canRedoOf(state),
  }
}
