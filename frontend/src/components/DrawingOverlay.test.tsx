import { act, fireEvent, render, screen } from '@testing-library/react'
import { useReducer } from 'react'
import { describe, expect, it, vi } from 'vitest'
import { DrawingOverlay } from './DrawingOverlay'
import { FakeChartAdapter } from '@/test/fakeChartAdapter'
import { recorder } from '@/test/canvasRecorder'
import { drawingReducer, initialDrawingState, type DrawingState } from '@/lib/drawing/store'
import { DEFAULT_STYLES, type Drawing, type DrawingKind } from '@/lib/drawing/types'
import type { DomainBar } from '@/lib/chart/types'

/**
 * The overlay, driven through real pointer events.
 *
 * The chart paints the drawings (T37); here a recording context stands in for its canvas. What matters most is the
 * *interaction*: which gesture starts, what lands in state, and whether the canvas lets pointer events through to
 * the chart underneath. How it looks is verified in a browser.
 */

const bars: DomainBar[] = [
  { time: 1100, open: 80_000, high: 80_050, low: 79_950, close: 80_020, volume: 1 },
  { time: 1200, open: 80_020, high: 80_090, low: 80_000, close: 80_070, volume: 1 },
]

const existing: Drawing = {
  id: 'existing',
  kind: 'horizontal-line',
  anchors: [{ time: 1200, priceTicks: 80_000 }],
  style: DEFAULT_STYLES['horizontal-line'],
}

/** Harness with a real reducer, so assertions are about actual resulting state. */
function Harness({
  adapter,
  activeTool = null,
  initial,
  onState,
  onToolFinished = () => {},
  onCommit = () => {},
}: {
  adapter: FakeChartAdapter
  activeTool?: DrawingKind | null
  initial?: DrawingState
  onState: (s: DrawingState) => void
  onToolFinished?: () => void
  onCommit?: () => void
}) {
  const [state, dispatch] = useReducer(drawingReducer, initial ?? initialDrawingState)
  onState(state)

  return (
    <div style={{ width: 800, height: 400 }}>
      <DrawingOverlay
        adapter={adapter}
        state={state}
        dispatch={dispatch}
        activeTool={activeTool}
        onToolFinished={onToolFinished}
        bars={bars}
        snapMode="off"
        tickValueCents={500}
        tickSize={0.25}
        onCommit={onCommit}
      />
    </div>
  )
}

/** jsdom gives every element a zero-size rect, so pointer maths needs a real one. */
function stubRect(canvas: HTMLElement) {
  canvas.getBoundingClientRect = () =>
    ({ left: 0, top: 0, width: 800, height: 400, right: 800, bottom: 400, x: 0, y: 0 }) as DOMRect
  // Not implemented in jsdom; the overlay calls both on every gesture.
  ;(canvas as HTMLCanvasElement).setPointerCapture = vi.fn()
  ;(canvas as HTMLCanvasElement).releasePointerCapture = vi.fn()
}

function pointer(canvas: HTMLElement, type: string, x: number, y: number) {
  // Wrapped in act() because each gesture drives a reducer: without it React batches the update
  // past the assertion and the test reads stale state.
  act(() => {
    const event = new Event(type, { bubbles: true }) as PointerEvent & { [k: string]: unknown }
    Object.assign(event, { clientX: x, clientY: y, pointerId: 1 })
    canvas.dispatchEvent(event)
  })
}

describe('DrawingOverlay', () => {
  it('creates a drawing when a tool is armed and the user clicks', () => {
    const adapter = new FakeChartAdapter()
    let latest: DrawingState = initialDrawingState
    render(
      <Harness adapter={adapter} activeTool="horizontal-line" onState={(s) => (latest = s)} />,
    )
    const canvas = screen.getByTestId('drawing-overlay')
    stubRect(canvas)

    pointer(canvas, 'pointerdown', 400, 500)

    expect(latest.drawings).toHaveLength(1)
    expect(latest.drawings[0].kind).toBe('horizontal-line')
    // y=500 maps to 80_000 ticks under the fake projection.
    expect(latest.drawings[0].anchors[0].priceTicks).toBe(80_000)
  })

  it('gives the position tool a usable bracket instead of three lines on top of each other', () => {
    // Three coincident lines read as broken. A 2R starting shape is immediately meaningful and the
    // user drags from there.
    const adapter = new FakeChartAdapter()
    let latest: DrawingState = initialDrawingState
    render(<Harness adapter={adapter} activeTool="position" onState={(s) => (latest = s)} />)
    const canvas = screen.getByTestId('drawing-overlay')
    stubRect(canvas)

    pointer(canvas, 'pointerdown', 400, 500)

    const [entry, stop, target] = latest.drawings[0].anchors
    expect(stop.priceTicks).toBeLessThan(entry.priceTicks)
    expect(target.priceTicks).toBeGreaterThan(entry.priceTicks)
    expect(target.priceTicks - entry.priceTicks).toBe(2 * (entry.priceTicks - stop.priceTicks))
  })

  it('selects an existing drawing when clicked in select mode', () => {
    const adapter = new FakeChartAdapter()
    let latest: DrawingState = initialDrawingState
    render(
      <Harness
        adapter={adapter}
        initial={{ ...initialDrawingState, drawings: [existing] }}
        onState={(s) => (latest = s)}
      />,
    )
    const canvas = screen.getByTestId('drawing-overlay')
    stubRect(canvas)

    pointer(canvas, 'pointerdown', 400, 500)

    expect(latest.selectedId).toBe('existing')
  })

  it('clears the selection when clicking empty space', () => {
    const adapter = new FakeChartAdapter()
    let latest: DrawingState = initialDrawingState
    render(
      <Harness
        adapter={adapter}
        initial={{ ...initialDrawingState, drawings: [existing], selectedId: 'existing' }}
        onState={(s) => (latest = s)}
      />,
    )
    const canvas = screen.getByTestId('drawing-overlay')
    stubRect(canvas)

    pointer(canvas, 'pointerdown', 400, 100) // far above the line

    expect(latest.selectedId).toBeNull()
  })

  it('drags a drawing without recording every intermediate position in history', () => {
    // A drag fires on every mouse move. One history entry per gesture, not per pixel.
    const adapter = new FakeChartAdapter()
    let latest: DrawingState = initialDrawingState
    render(
      <Harness
        adapter={adapter}
        initial={{ ...initialDrawingState, drawings: [existing] }}
        onState={(s) => (latest = s)}
      />,
    )
    const canvas = screen.getByTestId('drawing-overlay')
    stubRect(canvas)

    pointer(canvas, 'pointerdown', 400, 500)
    pointer(canvas, 'pointermove', 400, 480)
    pointer(canvas, 'pointermove', 400, 460)
    pointer(canvas, 'pointerup', 400, 460)

    expect(latest.drawings[0].anchors[0].priceTicks).toBe(80_040)
    expect(latest.past).toHaveLength(1)
  })

  it('BUG-038: a slow drag moves the shape as far as a fast one, on a chart that snaps to bars', () => {
    // The real chart turns a pixel into the nearest bar's time. Each bar here is 10px wide (100s).
    // Moving a drawing by re-snapping its last preview after every pointermove lost any step under
    // half a bar, so a drag of 1px per event never moved it sideways at all.
    const snapping = new FakeChartAdapter()
    snapping.getProjection = vi.fn(() => ({
      timeToX: (time: number) => (time - 1000) / 10,
      priceToY: (ticks: number) => 500 - (ticks - 80_000),
      xToTime: (x: number) => Math.round(x / 10) * 100 + 1000,
      yToPrice: (y: number) => 80_000 + (500 - Math.round(y)),
    }))
    let latest: DrawingState = initialDrawingState
    render(
      <Harness
        adapter={snapping}
        initial={{ ...initialDrawingState, drawings: [existing] }}
        onState={(s) => (latest = s)}
      />,
    )
    const canvas = screen.getByTestId('drawing-overlay')
    stubRect(canvas)

    // Grab the line (time 1200 → x 20, price 80_000 → y 500) and drag it 30px right, 1px at a time.
    pointer(canvas, 'pointerdown', 20, 500)
    for (let x = 21; x <= 50; x++) pointer(canvas, 'pointermove', x, 500)
    pointer(canvas, 'pointerup', 50, 500)

    // 30px is three bars: 1200 + 300.
    expect(latest.drawings[0].anchors[0].time).toBe(1500)
    expect(latest.drawings[0].anchors[0].priceTicks).toBe(80_000)
  })

  it('stays transparent to the pointer when there is nothing to interact with', () => {
    // The crux of overlaying a canvas on a chart: if it always captured events, panning and zooming
    // would stop working entirely.
    const adapter = new FakeChartAdapter()
    render(<Harness adapter={adapter} onState={() => {}} />)
    const canvas = screen.getByTestId('drawing-overlay')

    expect(canvas).toHaveAttribute('data-interactive', 'false')
    expect(canvas.style.pointerEvents).toBe('none')
    expect(canvas.style.touchAction).toBe('auto') // a finger pans the chart (T46)
  })

  it('captures the pointer while a tool is armed', () => {
    const adapter = new FakeChartAdapter()
    render(<Harness adapter={adapter} activeTool="rectangle" onState={() => {}} />)
    const canvas = screen.getByTestId('drawing-overlay')

    expect(canvas).toHaveAttribute('data-interactive', 'true')
    expect(canvas.style.pointerEvents).toBe('auto')
    expect(canvas.style.touchAction).toBe('none') // a finger draws, the page does not scroll (T46)
  })

  it('returns to select mode after a shape is drawn', () => {
    // Otherwise every click keeps stamping new rectangles, which is infuriating.
    const adapter = new FakeChartAdapter()
    const onToolFinished = vi.fn()
    render(
      <Harness
        adapter={adapter}
        activeTool="rectangle"
        onState={() => {}}
        onToolFinished={onToolFinished}
      />,
    )
    const canvas = screen.getByTestId('drawing-overlay')
    stubRect(canvas)

    pointer(canvas, 'pointerdown', 300, 300)
    pointer(canvas, 'pointermove', 500, 400)
    pointer(canvas, 'pointerup', 500, 400)

    expect(onToolFinished).toHaveBeenCalled()
  })

  it('is painted by the chart over everything, and repainted when the drawings change (T37, BUG-095)', () => {
    // On a canvas of its own, repainted after the chart moved, the drawings trailed the candles by a frame or more.
    const adapter = new FakeChartAdapter()
    render(<Harness adapter={adapter} activeTool="horizontal-line" onState={() => {}} />)
    expect(adapter.layers.map((layer) => layer.zOrder)).toEqual(['top'])
    const before = adapter.redraws
    const canvas = screen.getByTestId('drawing-overlay')
    stubRect(canvas)

    pointer(canvas, 'pointerdown', 400, 450)

    expect(adapter.redraws).toBeGreaterThan(before)
    const { ctx, calls } = recorder()
    adapter.paintLayers(ctx)
    // Across the plotting area, 800 wide, at the price clicked; nothing cleared — the candles are on the same canvas.
    expect(calls.filter((c) => c.op === 'lineTo').map((c) => c.args)).toEqual([[800, 450]])
    expect(calls.some((c) => c.op === 'clearRect')).toBe(false)
  })

  it('covers the whole chart to take the pointer, sized explicitly (T37)', () => {
    // Nothing sets its pixel size now that it paints nothing, and a canvas is not stretched by inset-0: it stayed
    // 300 × 150 in the corner, and a drag anywhere else panned the chart instead of drawing.
    render(<Harness adapter={new FakeChartAdapter()} onState={() => {}} />)
    expect(screen.getByTestId('drawing-overlay')).toHaveClass('size-full')
  })
})

describe('DrawingOverlay — the ICT tools (DEC-048)', () => {
  it('creates a Fibonacci by dragging along a leg, as one undo step (BUG-047)', () => {
    const adapter = new FakeChartAdapter()
    let latest: DrawingState = initialDrawingState
    render(<Harness adapter={adapter} activeTool="fibonacci" onState={(s) => (latest = s)} />)
    const canvas = screen.getByTestId('drawing-overlay')
    stubRect(canvas)

    // From a low at y=500 (80,000) to a high at y=100 (80,400).
    pointer(canvas, 'pointerdown', 100, 500)
    pointer(canvas, 'pointermove', 300, 100)
    pointer(canvas, 'pointerup', 300, 100)

    expect(latest.drawings[0].kind).toBe('fibonacci')
    expect(latest.drawings[0].anchors.map((a) => a.priceTicks)).toEqual([80_000, 80_400])
    expect(latest.past).toEqual([[]])
  })

  it('BUG-049: a click with a two-point tool, never dragged, leaves nothing behind', () => {
    for (const tool of ['fibonacci', 'zone', 'trend-line', 'rectangle'] as const) {
      const adapter = new FakeChartAdapter()
      const onCommit = vi.fn()
      let latest: DrawingState = initialDrawingState
      const { unmount } = render(
        <Harness adapter={adapter} activeTool={tool} onState={(s) => (latest = s)} onCommit={onCommit} />,
      )
      const canvas = screen.getByTestId('drawing-overlay')
      stubRect(canvas)

      pointer(canvas, 'pointerdown', 300, 300)
      pointer(canvas, 'pointerup', 300, 300)

      expect(latest.drawings, tool).toEqual([])
      expect(latest.past, tool).toEqual([])
      expect(onCommit, tool).not.toHaveBeenCalled()
      unmount()
    }
  })

  it('still gives the position tool its bracket from a single click', () => {
    const adapter = new FakeChartAdapter()
    let latest: DrawingState = initialDrawingState
    render(<Harness adapter={adapter} activeTool="position" onState={(s) => (latest = s)} />)
    const canvas = screen.getByTestId('drawing-overlay')
    stubRect(canvas)
    pointer(canvas, 'pointerdown', 300, 300)
    pointer(canvas, 'pointerup', 300, 300)
    expect(latest.drawings).toHaveLength(1)
  })

  it('places a label, asks for its words, and saves it once they are typed', () => {
    const adapter = new FakeChartAdapter()
    const onCommit = vi.fn()
    let latest: DrawingState = initialDrawingState
    render(
      <Harness adapter={adapter} activeTool="label" onState={(s) => (latest = s)} onCommit={onCommit} />,
    )
    const canvas = screen.getByTestId('drawing-overlay')
    stubRect(canvas)

    pointer(canvas, 'pointerdown', 400, 500)
    pointer(canvas, 'pointerup', 400, 500)
    // Not saved yet: a label without words is not finished.
    expect(onCommit).not.toHaveBeenCalled()

    const editor = screen.getByTestId('drawing-text-editor')
    fireEvent.change(editor, { target: { value: '  Asia high taken  ' } })
    fireEvent.keyDown(editor, { key: 'Enter' })

    expect(latest.drawings[0]).toMatchObject({ kind: 'label', text: 'Asia high taken' })
    expect(onCommit).toHaveBeenCalledTimes(1)
    // The words belong to the label's one undo step.
    expect(latest.past).toEqual([[]])
    expect(screen.queryByTestId('drawing-text-editor')).not.toBeInTheDocument()
  })

  it('leaves no trace of a label abandoned with Escape', () => {
    const adapter = new FakeChartAdapter()
    let latest: DrawingState = initialDrawingState
    render(<Harness adapter={adapter} activeTool="label" onState={(s) => (latest = s)} />)
    const canvas = screen.getByTestId('drawing-overlay')
    stubRect(canvas)

    pointer(canvas, 'pointerdown', 400, 500)
    pointer(canvas, 'pointerup', 400, 500)
    fireEvent.keyDown(screen.getByTestId('drawing-text-editor'), { key: 'Escape' })

    expect(latest.drawings).toEqual([])
    expect(latest.past).toEqual([])
  })

  it('renames a zone on double-click, as an undoable step', () => {
    const zone: Drawing = {
      id: 'zone',
      kind: 'zone',
      // x 200–300, prices 80,000–80,040: the consequent encroachment is at 80,020, y=480.
      anchors: [
        { time: 1200, priceTicks: 80_000 },
        { time: 1300, priceTicks: 80_040 },
      ],
      style: DEFAULT_STYLES.zone,
      text: 'FVG',
    }
    const adapter = new FakeChartAdapter()
    const onCommit = vi.fn()
    let latest: DrawingState = initialDrawingState
    render(
      <Harness
        adapter={adapter}
        initial={{ ...initialDrawingState, drawings: [zone] }}
        onState={(s) => (latest = s)}
        onCommit={onCommit}
      />,
    )
    const canvas = screen.getByTestId('drawing-overlay')
    stubRect(canvas)

    fireEvent.doubleClick(canvas, { clientX: 250, clientY: 480 })
    const editor = screen.getByTestId('drawing-text-editor') as HTMLInputElement
    expect(editor.value).toBe('FVG')
    fireEvent.change(editor, { target: { value: 'BISI 5m' } })
    fireEvent.keyDown(editor, { key: 'Enter' })

    expect(latest.drawings[0].text).toBe('BISI 5m')
    expect(latest.past).toHaveLength(1)
    expect(onCommit).toHaveBeenCalledTimes(1)
  })
})
