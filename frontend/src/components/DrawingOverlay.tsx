import { useCallback, useEffect, useLayoutEffect, useRef, useState } from 'react'
import type { ChartAdapter, DomainBar } from '@/lib/chart/types'
import { hitTest, translateAnchors } from '@/lib/drawing/geometry'
import { renderDrawings } from '@/lib/drawing/render'
import { snapAnchor, type SnapMode } from '@/lib/drawing/snap'
import { useOverlayCanvas } from '@/hooks/useOverlayCanvas'
import type { DrawingAction, DrawingState } from '@/lib/drawing/store'
import {
  ANCHOR_COUNT,
  DEFAULT_STYLES,
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
  /** Called when a gesture ends, so the change can be persisted. */
  onCommit: () => void
}

type Gesture =
  | { kind: 'idle' }
  | { kind: 'creating'; drawing: Drawing; placed: number }
  // A move is measured from where the drag started, against the anchors as they were then (BUG-038).
  | { kind: 'moving'; drawingId: string; startX: number; startY: number; origin: Anchor[] }
  | { kind: 'reshaping'; drawingId: string; anchorIndex: number }

/**
 * The interactive drawing surface: a transparent canvas sitting on top of the chart.
 *
 * An overlay rather than a chart plugin. The plugin API would tie every tool to Lightweight Charts
 * and make DEC-004's escape hatch theoretical — this way the whole layer needs only the four
 * projection functions, and swapping renderers means reimplementing those and nothing else.
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
  onCommit,
}: DrawingOverlayProps) {
  const canvasRef = useRef<HTMLCanvasElement>(null)
  const [gesture, setGesture] = useState<Gesture>({ kind: 'idle' })

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

  const draw = useCallback(() => {
    const canvas = canvasRef.current
    const projection = adapter?.getProjection()
    if (!canvas || !projection) return

    const ctx = canvas.getContext('2d')
    if (!ctx) return

    renderDrawings(ctx, stateRef.current.drawings, projection, {
      width: canvas.width,
      height: canvas.height,
      selectedId: stateRef.current.selectedId,
      tickValueCents,
    })
  }, [adapter, tickValueCents])

  // Sized with its container and repainted as the chart moves. A canvas that drifted out of the
  // chart's coordinate space would make every hit test subtly wrong.
  useOverlayCanvas(canvasRef, adapter, draw)

  useEffect(draw, [draw, state])

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
        canvas.width,
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

    const hit = hitTest(point, state.drawings, state.selectedId, projection, canvas.width)

    switch (hit.type) {
      case 'handle':
        dispatch({ type: 'select', id: hit.drawingId })
        setGesture({ kind: 'reshaping', drawingId: hit.drawingId, anchorIndex: hit.anchorIndex })
        break
      case 'body': {
        const grabbed = state.drawings.find((d) => d.id === hit.drawingId)
        if (!grabbed) break
        dispatch({ type: 'select', id: hit.drawingId })
        setGesture({
          kind: 'moving',
          drawingId: hit.drawingId,
          startX: point.x,
          startY: point.y,
          origin: grabbed.anchors,
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

      // A horizontal line has one anchor and is finished on click; the others follow the pointer
      // with their last anchor until released.
      if (drawing.kind === 'horizontal-line') return

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
        current.origin,
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

    if (current.kind !== 'idle') {
      // One history entry per gesture: the drag emitted previews, this records the result.
      const drawing = stateRef.current.drawings.find(
        (d) => d.id === (current.kind === 'creating' ? current.drawing.id : current.drawingId),
      )
      if (drawing) {
        dispatch({ type: 'update', drawing })
      }
      if (current.kind === 'creating') {
        onToolFinished()
      }
      onCommit()
    }

    setGesture({ kind: 'idle' })
  }

  return (
    <canvas
      ref={canvasRef}
      className="absolute inset-0 z-[2]"
      data-testid="drawing-overlay"
      data-interactive={interactive}
      style={{
        // See the comment on `interactive`: transparent to the pointer unless there is something to
        // interact with, so the chart keeps its own panning and zooming.
        pointerEvents: interactive ? 'auto' : 'none',
        cursor: activeTool ? 'crosshair' : pointerOverDrawing ? 'move' : 'default',
      }}
      onPointerDown={handlePointerDown}
      onPointerMove={handlePointerMove}
      onPointerUp={handlePointerUp}
    />
  )
}
