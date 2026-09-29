import { useEffect } from 'react'
import type { DrawingAction } from '@/lib/drawing/store'

/**
 * Keyboard shortcuts for a drawing surface: Esc, Delete, Ctrl/Cmd+Z and Ctrl/Cmd+Shift+Z.
 *
 * Bound on the window rather than the canvas because a canvas cannot hold focus without a tabindex,
 * and a chart that steals focus from the page is worse than one that shares its shortcuts. Typing
 * in a form field is left alone, or deleting a character in a price box would delete a drawing.
 *
 * Shared by the free chart and the practice chart, so the two behave identically.
 */
export function useDrawingShortcuts(options: {
  selectedId: string | null
  dispatch: (action: DrawingAction) => void
  onCancelTool: () => void
  onDeleteSelected: () => void
  /** Called after undo or redo, e.g. to persist. */
  onHistoryChange?: () => void
}) {
  const { selectedId, dispatch, onCancelTool, onDeleteSelected, onHistoryChange } = options

  useEffect(() => {
    function onKeyDown(event: KeyboardEvent) {
      const target = event.target as HTMLElement | null
      if (target && /^(INPUT|TEXTAREA|SELECT)$/.test(target.tagName)) return

      if (event.key === 'Escape') {
        onCancelTool()
        dispatch({ type: 'select', id: null })
        return
      }
      if ((event.key === 'Delete' || event.key === 'Backspace') && selectedId) {
        event.preventDefault()
        onDeleteSelected()
        return
      }
      if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'z') {
        event.preventDefault()
        dispatch({ type: event.shiftKey ? 'redo' : 'undo' })
        onHistoryChange?.()
      }
    }

    window.addEventListener('keydown', onKeyDown)
    return () => window.removeEventListener('keydown', onKeyDown)
  }, [selectedId, dispatch, onCancelTool, onDeleteSelected, onHistoryChange])
}
