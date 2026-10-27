import { useCallback, useEffect, useLayoutEffect, useRef, useState } from 'react'
import type { ChartAdapter, DomainBar } from '@/lib/chart/types'
import type { Projection } from '@/lib/drawing/types'
import { hitTest, project, translateAnchors } from '@/lib/drawing/geometry'
import { renderDrawings } from '@/lib/drawing/render'
import { snapAnchor, type SnapMode } from '@/lib/drawing/snap'
import { useChartLayer } from '@/hooks/useChartLayer'
import type { DrawingAction, DrawingState } from '@/lib/drawing/store'
import {
  ANCHOR_COUNT,
  DEFAULT_STYLES,
  MAX_TEXT,
  TEXT_KINDS,
  type Anchor,
  type Drawing,
  type DrawingKind,
} from '@/lib/drawing/types'

interface DrawingOverlayProps {
  adapter: ChartAdapter | null
  state: DrawingState
  dispatch: (action: DrawingAction) => void
  /** The tool armed in the toolbar, or null when the pointer is in select mode. */
  activeTool: DrawingKind | null
  onToolFinished: () => void
  bars: DomainBar[]
  snapMode: SnapMode
  tickValueCents: number
  tickSize: number
  /** Called when a gesture ends, so the change can be persisted. */
  onCommit: () => void
}

// `from` is the drawing as the gesture found it: what undo returns to (BUG-047).
type Gesture =
  | { kind: 'idle' }
  | { kind: 'creating'; drawing: Drawing; placed: number }
  // A move is measured from where the drag started, against the anchors as they were then (BUG-038).
  | { kind: 'moving'; drawingId: string; startX: number; startY: number; from: Drawing }
  | { kind: 'reshaping'; drawingId: string; anchorIndex: number; from: Drawing }

/** A label or zone whose words are being typed. `isNew`: a label just placed, gone if left empty. */
type Editing = { id: string; isNew: boolean } | null

/** A shape with more than one point whose points all coincide: created by a click, not a drag. */
function isUnstretched(drawing: Drawing): boolean {
  const [first, ...rest] = drawing.anchors
  return (
    rest.length > 0 &&
    rest.every((a) => a.time === first.time && a.priceTicks === first.priceTicks)
  )
}

/**
 * The interactive drawing surface: the drawings are painted by the chart, over everything, in the same frame as the
 * candles (T37); a transparent canvas on top of the chart takes the pointer only to draw or to move a drawing.
 *
 * Both go through the adapter (DEC-004) — a layer to paint and the four projection functions — so nothing here knows
 * Lightweight Charts, and swapping renderers means reimplementing those and nothing else.
 */
export function DrawingOverlay({
  adapter,
  state,
  dispatch,
  activeTool,
  onToolFinished,
  bars,
  snapMode,
  tickValueCents,
  tickSize,
  onCommit,
}: DrawingOverlayProps) {
  const canvasRef = useRef<HTMLCanvasElement>(null)
  const [gesture, setGesture] = useState<Gesture>({ kind: 'idle' })
  const [editing, setEditing] = useState<Editing>(null)
  // Enter closes the editor, and closing it blurs the field: without this the words would be
  // committed twice. Written only in event handlers, never during render.
  const editingRef = useRef<Editing>(null)
  const startEditing = (next: NonNullable<Editing>) => {
    editingRef.current = next
    setEditing(next)
  }

  // Read inside callbacks that must not be recreated on every state change.
  //
  // BUG-035: both are written at commit, together, never during render. A drag reads them as a pair —
  // the gesture's last pointer position and the drawing it applies the next delta to. Were one ever
  // newer than the other, the next pointermove would apply a delta to the wrong position and the
  // shape would jump. Updating them in the same layout effect keeps the pair consistent, and a render
  // React discards can no longer leave a ref holding state that never committed.
  const stateRef = useRef(state)
  const gestureRef = useRef(gesture)
  useLayoutEffect(() => {
    stateRef.current = state
    gestureRef.current = gesture
  })

  const { drawings, selectedId } = state
  const paint = useCallback(
    (ctx: CanvasRenderingContext2D, projection: Projection, pane: { width: number; height: number }) =>
      renderDrawings(ctx, drawings, projection, { width: pane.width, selectedId, tickValueCents, tickSize }),
    [drawings, selectedId, tickValueCents, tickSize],
  )
  useChartLayer(adapter, 'top', paint)


  // Whether the canvas should accept pointer events at all.
  //
  // This is the crux of overlaying a canvas on a chart. If it always accepted them, panning and
  // zooming would stop working entirely — every drag would hit the overlay instead of the chart.
  // So the canvas is transparent to the pointer by default and becomes interactive only when a
  // tool is armed or the pointer is genuinely over a drawing.
  //
  // Hit testing while transparent is possible because pointer events on the chart bubble up to the
  // shared parent, so the listener sees them even though the canvas does not.
  const [pointerOverDrawing, setPointerOverDrawing] = useState(false)

  useEffect(() => {
    const canvas = canvasRef.current
    const container = canvas?.parentElement
    if (!canvas || !container) return

    const onMove = (event: PointerEvent) => {
      const projection = adapter?.getProjection()
      if (!projection) return

      const rect = canvas.getBoundingClientRect()
      const point = { x: event.clientX - rect.left, y: event.clientY - rect.top }
      const hit = hitTest(
        point,
        stateRef.current.drawings,
        stateRef.current.selectedId,
        projection,
        adapter?.getPaneSize()?.width ?? 0,
      )
      setPointerOverDrawing(hit.type !== 'none')
    }

    container.addEventListener('pointermove', onMove)
    return () => container.removeEventListener('pointermove', onMove)
  }, [adapter])

  const interactive = activeTool !== null || pointerOverDrawing || gesture.kind !== 'idle'

  function pointerPosition(event: React.PointerEvent<HTMLCanvasElement>) {
    const rect = event.currentTarget.getBoundingClientRect()
    return { x: event.clientX - rect.left, y: event.clientY - rect.top }
  }

  function anchorAt(x: number, y: number): Anchor | null {
    const projection = adapter?.getProjection()
    if (!projection) return null
    const time = projection.xToTime(x)
    const priceTicks = projection.yToPrice(y)
    if (time === null || priceTicks === null) return null
    return snapAnchor({ time, priceTicks }, bars, snapMode).anchor
  }

  function handlePointerDown(event: React.PointerEvent<HTMLCanvasElement>) {
    const canvas = event.currentTarget
    const point = pointerPosition(event)
    const projection = adapter?.getProjection()
    if (!projection) return

    canvas.setPointerCapture(event.pointerId)

    if (activeTool) {
      const anchor = anchorAt(point.x, point.y)
      if (!anchor) return

      // Every anchor starts at the click point; subsequent clicks or the drag place the rest.
      const drawing: Drawing = {
        id: crypto.randomUUID(),
        kind: activeTool,
        anchors: Array.from({ length: ANCHOR_COUNT[activeTool] }, () => ({ ...anchor })),
        style: DEFAULT_STYLES[activeTool],
      }

      if (activeTool === 'position') {
        // Seed a sensible bracket so the tool is immediately meaningful: stop 40 ticks against,
        // target 80 in favour. A 2R starting shape is a better default than three lines on top of
        // each other, which reads as broken.
        drawing.anchors[1] = { ...anchor, priceTicks: anchor.priceTicks - 40 }
        drawing.anchors[2] = { ...anchor, priceTicks: anchor.priceTicks + 80 }
      }

      dispatch({ type: 'add', drawing })
      setGesture({ kind: 'creating', drawing, placed: 1 })
      return
    }

    const hit = hitTest(point, state.drawings, state.selectedId, projection, adapter?.getPaneSize()?.width ?? 0)

    switch (hit.type) {
      case 'handle': {
        const grabbed = state.drawings.find((d) => d.id === hit.drawingId)
        if (!grabbed) break
        dispatch({ type: 'select', id: hit.drawingId })
        setGesture({
          kind: 'reshaping',
          drawingId: hit.drawingId,
          anchorIndex: hit.anchorIndex,
          from: grabbed,
        })
        break
      }
      case 'body': {
        const grabbed = state.drawings.find((d) => d.id === hit.drawingId)
        if (!grabbed) break
        dispatch({ type: 'select', id: hit.drawingId })
        setGesture({
          kind: 'moving',
          drawingId: hit.drawingId,
          startX: point.x,
          startY: point.y,
          from: grabbed,
        })
        break
      }
      case 'none':
        dispatch({ type: 'select', id: null })
        break
    }
  }

  function handlePointerMove(event: React.PointerEvent<HTMLCanvasElement>) {
    const current = gestureRef.current
    if (current.kind === 'idle') return

    const point = pointerPosition(event)
    const projection = adapter?.getProjection()
    if (!projection) return

    const find = (id: string) => stateRef.current.drawings.find((d) => d.id === id)

    if (current.kind === 'creating') {
      const anchor = anchorAt(point.x, point.y)
      const drawing = find(current.drawing.id)
      if (!anchor || !drawing) return

      // A one-anchor shape (a horizontal line, a label) is finished on click; the others follow the
      // pointer with their last anchor until released.
      if (ANCHOR_COUNT[drawing.kind] === 1) return

      const anchors = [...drawing.anchors]
      anchors[anchors.length - 1] = anchor
      dispatch({ type: 'preview', drawing: { ...drawing, anchors } })
      return
    }

    if (current.kind === 'moving') {
      const drawing = find(current.drawingId)
      if (!drawing) return

      // BUG-038: always offset the anchors the drag started from by the total distance travelled.
      // Translating the previous preview by each step's few pixels re-snapped the shape to the
      // nearest bar every time, so a slow drag — under half a bar per event — never moved sideways,
      // and a faster one drifted by the accumulated rounding.
      const anchors = translateAnchors(
        current.from.anchors,
        point.x - current.startX,
        point.y - current.startY,
        projection,
      )
      dispatch({ type: 'preview', drawing: { ...drawing, anchors } })
      return
    }

    if (current.kind === 'reshaping') {
      const drawing = find(current.drawingId)
      const anchor = anchorAt(point.x, point.y)
      if (!drawing || !anchor) return

      const anchors = [...drawing.anchors]
      anchors[current.anchorIndex] = anchor
      dispatch({ type: 'preview', drawing: { ...drawing, anchors } })
    }
  }

  function handlePointerUp(event: React.PointerEvent<HTMLCanvasElement>) {
    const current = gestureRef.current
    event.currentTarget.releasePointerCapture(event.pointerId)

    if (current.kind === 'creating') {
      // The add already recorded the undo step; the previews only shaped what it added.
      onToolFinished()
      const made = stateRef.current.drawings.find((d) => d.id === current.drawing.id)
      if (made && isUnstretched(made)) {
        // A click with a two-point tool, never dragged: a shape of no size, invisible and useless —
        // a Fibonacci of no length would print all ten levels at one price. Not kept (BUG-049).
        dispatch({ type: 'forget', id: made.id })
      } else if (current.drawing.kind === 'label') {
        // Saved once it has words: the editor commits, or forgets a label left empty.
        startEditing({ id: current.drawing.id, isNew: true })
      } else {
        onCommit()
      }
    } else if (current.kind !== 'idle') {
      // One history entry per gesture, recording where the drawing was before it (BUG-047).
      const drawing = stateRef.current.drawings.find((d) => d.id === current.drawingId)
      if (drawing) {
        dispatch({ type: 'update', drawing, from: current.from })
      }
      onCommit()
    }

    setGesture({ kind: 'idle' })
  }

  // Double-click a label or a zone to change its words.
  function handleDoubleClick(event: React.MouseEvent<HTMLCanvasElement>) {
    const projection = adapter?.getProjection()
    if (!projection) return
    const rect = event.currentTarget.getBoundingClientRect()
    const point = { x: event.clientX - rect.left, y: event.clientY - rect.top }
    const hit = hitTest(point, state.drawings, state.selectedId, projection, event.currentTarget.width)
    if (hit.type === 'none') return
    const drawing = state.drawings.find((d) => d.id === hit.drawingId)
    if (drawing && TEXT_KINDS.has(drawing.kind)) startEditing({ id: drawing.id, isNew: false })
  }

  const edited = editing ? state.drawings.find((d) => d.id === editing.id) : undefined

  function finishEditing(value: string | null) {
    const current = editingRef.current
    editingRef.current = null
    setEditing(null)
    if (!current || !edited) return
    const text = value?.trim().slice(0, MAX_TEXT) || undefined
    if (value === null) {
      // Escape: nothing changes — and a label just placed, with no words yet, is not kept.
      if (current.isNew) dispatch({ type: 'forget', id: current.id })
      return
    }
    if (edited.kind === 'label' && !text) {
      // A label is its words: emptied, it goes. A new one leaves no trace in history either.
      dispatch(current.isNew ? { type: 'forget', id: current.id } : { type: 'remove', id: current.id })
    } else if (current.isNew) {
      // Its add is the undo step; the words belong to it rather than being a second step.
      dispatch({ type: 'preview', drawing: { ...edited, text } })
    } else if (text !== edited.text) {
      dispatch({ type: 'update', drawing: { ...edited, text }, from: edited })
    }
    onCommit()
  }

  const projectionNow = edited ? adapter?.getProjection() : null
  const editorAt = edited && projectionNow ? project(edited.anchors[0], projectionNow) : null

  return (
    <>
      <canvas
        ref={canvasRef}
        // A canvas is sized by its own width and height (300 × 150 by default), not by inset-0: the size is explicit.
        className="absolute inset-0 z-[2] size-full"
        data-testid="drawing-overlay"
        data-interactive={interactive}
        style={{
          // See the comment on `interactive`: transparent to the pointer unless there is something
          // to interact with, so the chart keeps its own panning and zooming.
          pointerEvents: interactive ? 'auto' : 'none',
          // A finger drawing on a phone (T46) draws: the browser neither scrolls nor zooms under it.
          touchAction: interactive ? 'none' : 'auto',
          cursor: activeTool ? 'crosshair' : pointerOverDrawing ? 'move' : 'default',
        }}
        onPointerDown={handlePointerDown}
        onPointerMove={handlePointerMove}
        onPointerUp={handlePointerUp}
        onDoubleClick={handleDoubleClick}
      />
      {edited && editorAt && (
        <input
          // Keyed by drawing, so its starting value is that drawing's words.
          key={edited.id}
          aria-label={edited.kind === 'label' ? 'Label text' : 'Zone name'}
          data-testid="drawing-text-editor"
          autoFocus
          maxLength={MAX_TEXT}
          defaultValue={edited.text ?? ''}
          placeholder={edited.kind === 'label' ? 'Label' : 'Name (optional)'}
          className="absolute z-[3] h-7 w-56 rounded border border-accent bg-canvas px-2 text-xs text-ink shadow focus:outline-none"
          style={{ left: editorAt.x + 8, top: editorAt.y - 14 }}
          onKeyDown={(e) => {
            if (e.key === 'Enter') finishEditing(e.currentTarget.value)
            if (e.key === 'Escape') finishEditing(null)
          }}
          onBlur={(e) => finishEditing(e.currentTarget.value)}
        />
      )}
    </>
  )
}
