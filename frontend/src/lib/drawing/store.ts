import type { Drawing } from './types'

/**
 * Drawing state with undo/redo.
 *
 * A reducer over immutable snapshots rather than a mutable list with an inverse-operation log.
 * Inverse operations are where undo implementations go wrong — every new tool needs its own undo,
 * and the one nobody wrote is the one that corrupts the document. Snapshots cost a little memory
 * and are correct by construction for every operation, including ones not yet invented.
 *
 * The history is capped so a long session cannot grow without bound.
 */

const MAX_HISTORY = 100

export interface DrawingState {
  drawings: Drawing[]
  selectedId: string | null
  /** Snapshots older than the present, most recent last. */
  past: Drawing[][]
  /** Snapshots undone but not yet discarded, most recently undone first. */
  future: Drawing[][]
}

export const initialDrawingState: DrawingState = {
  drawings: [],
  selectedId: null,
  past: [],
  future: [],
}

export type DrawingAction =
  | { type: 'add'; drawing: Drawing }
  /**
   * `from`: the drawing as it was before a gesture whose previews have already moved it. History
   * records that, not the last preview (BUG-047) — and nothing at all if the gesture changed nothing.
   */
  | { type: 'update'; drawing: Drawing; from?: Drawing }
  /** Mid-drag updates: the shape changes but no history entry is created. */
  | { type: 'preview'; drawing: Drawing }
  | { type: 'remove'; id: string }
  | { type: 'select'; id: string | null }
  | { type: 'undo' }
  | { type: 'redo' }
  /** Replaces everything without touching history — used when loading from the server. */
  | { type: 'load'; drawings: Drawing[] }
  /**
   * Removes a drawing from the present and from every step of history, adding none: for a drawing
   * that has stopped being the user's to edit — a practice plan, once locked (BUG-041).
   */
  | { type: 'forget'; id: string }

function pushHistory(state: DrawingState): Drawing[][] {
  const past = [...state.past, state.drawings]
  return past.length > MAX_HISTORY ? past.slice(past.length - MAX_HISTORY) : past
}

export function drawingReducer(state: DrawingState, action: DrawingAction): DrawingState {
  switch (action.type) {
    case 'add':
      return {
        drawings: [...state.drawings, action.drawing],
        selectedId: action.drawing.id,
        past: pushHistory(state),
        // Any new edit discards the redo branch. Keeping it would let undo/redo walk into a state
        // that never existed.
        future: [],
      }

    case 'update': {
      const { drawing, from } = action
      const replace = (drawings: Drawing[], next: Drawing) =>
        drawings.map((d) => (d.id === next.id ? next : d))
      if (from && JSON.stringify(from.anchors) === JSON.stringify(drawing.anchors) && from.text === drawing.text) {
        // A click, or a drag back to where it started: no step to undo.
        return { ...state, drawings: replace(state.drawings, drawing) }
      }
      return {
        ...state,
        drawings: replace(state.drawings, drawing),
        past: pushHistory(from ? { ...state, drawings: replace(state.drawings, from) } : state),
        future: [],
      }
    }

    case 'preview':
      // Deliberately no history. A drag fires on every mouse move, and recording each one would
      // mean the user pressing undo fifty times to reverse a single gesture.
      return {
        ...state,
        drawings: state.drawings.map((d) => (d.id === action.drawing.id ? action.drawing : d)),
      }

    case 'remove':
      return {
        drawings: state.drawings.filter((d) => d.id !== action.id),
        selectedId: state.selectedId === action.id ? null : state.selectedId,
        past: pushHistory(state),
        future: [],
      }

    case 'select':
      // Selection is not an edit, so it does not enter history.
      return { ...state, selectedId: action.id }

    case 'undo': {
      if (state.past.length === 0) return state
      const previous = state.past[state.past.length - 1]
      return {
        drawings: previous,
        // A selection pointing at something that no longer exists would leave handles floating over
        // nothing, so it is cleared.
        selectedId: previous.some((d) => d.id === state.selectedId) ? state.selectedId : null,
        past: state.past.slice(0, -1),
        future: [state.drawings, ...state.future],
      }
    }

    case 'redo': {
      if (state.future.length === 0) return state
      const next = state.future[0]
      return {
        drawings: next,
        selectedId: next.some((d) => d.id === state.selectedId) ? state.selectedId : null,
        past: pushHistory(state),
        future: state.future.slice(1),
      }
    }

    case 'load':
      return { drawings: action.drawings, selectedId: null, past: [], future: [] }

    case 'forget': {
      const without = (drawings: Drawing[]) => drawings.filter((d) => d.id !== action.id)
      const present = without(state.drawings)
      // Steps that differed only by the forgotten drawing are now the same as their neighbour. They
      // are dropped, or undo would spend clicks changing nothing.
      const past = [...state.past.map(without), present]
      const future = [present, ...state.future.map(without)]
      return {
        drawings: present,
        selectedId: state.selectedId === action.id ? null : state.selectedId,
        past: past.slice(0, -1).filter((step, i) => !sameDrawings(step, past[i + 1])),
        future: future.slice(1).filter((step, i) => !sameDrawings(step, future[i])),
      }
    }

    default:
      return state
  }
}

/** The same drawings in the same order — the same objects, since edits replace rather than mutate. */
function sameDrawings(a: Drawing[], b: Drawing[]): boolean {
  return a.length === b.length && a.every((drawing, i) => drawing === b[i])
}

export const canUndo = (state: DrawingState) => state.past.length > 0
export const canRedo = (state: DrawingState) => state.future.length > 0
