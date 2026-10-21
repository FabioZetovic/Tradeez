import { render } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { DrillOverlay } from './DrillOverlay'
import { FakeChartAdapter } from '@/test/fakeChartAdapter'
import { recorder } from '@/test/canvasRecorder'
import type { QuestionView, ResultView } from '@/lib/api/drills'

/**
 * What the drill overlay paints (the fake chart puts time t at x = t − 1000, and 80,000 ticks at
 * y = 500, a tick a pixel). A recording context stands in for the canvas jsdom does not have.
 */

const T0 = 1_710_338_400
const BARS = Array.from({ length: 40 }, (_, i) => ({
  time: T0 + i * 300,
  open: 80_000,
  high: 80_010,
  low: 79_990,
  close: 80_005,
  volume: 1,
}))
const MOMENT = T0 + 40 * 300

const QUESTION: QuestionView = {
  version: 'predict-1.0.0',
  definition: 'predict-1.0.0.',
  closeTicks: 80_005,
  targets: [
    { value: 'BSL', role: 'ERL', shape: 'LEVEL', side: 'ABOVE', fromTime: T0 + 20 * 300, lowTicks: 80_030, highTicks: 80_030, distanceTicks: 25, obstacleSwings: 1, obstacleGaps: 0, label: 'Buy-side at 20007.50' },
    { value: 'SSL', role: 'ERL', shape: 'LEVEL', side: 'BELOW', fromTime: T0 + 22 * 300, lowTicks: 79_980, highTicks: 79_980, distanceTicks: 25, obstacleSwings: 0, obstacleGaps: 0, label: 'Sell-side at 19995.00' },
  ],
  context: [
    { what: 'IRL_BULLISH_GAP', shape: 'ZONE', fromTime: T0 + 15 * 300, lowTicks: 79_990, highTicks: 79_995, label: 'IRL · bullish gap' },
  ],
}

let painted: ReturnType<typeof recorder>

beforeEach(() => {
  painted = recorder()
  vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockReturnValue(painted.ctx as never)
})
afterEach(() => vi.restoreAllMocks())

function paint(props: Partial<Parameters<typeof DrillOverlay>[0]> = {}) {
  render(
    <div>
      <DrillOverlay adapter={new FakeChartAdapter()} bars={BARS} marks={[]} {...props} />
    </div>,
  )
  return painted.calls
}

const texts = (calls: typeof painted.calls) => calls.filter((c) => c.op === 'fillText').map((c) => c.args[0])

describe('the drill overlay', () => {
  it('draws both targets of a prediction with their names, and the context with its own', () => {
    const calls = paint({ question: QUESTION, moment: MOMENT })
    expect(texts(calls)).toEqual(expect.arrayContaining(['Buy-side at 20007.50', 'Sell-side at 19995.00', 'IRL · bullish gap']))
    // Each level is a line at its price: 30 pixels above 80,000, and 20 below.
    const ys = calls.filter((c) => c.op === 'lineTo').map((c) => c.args[1])
    expect(ys).toEqual(expect.arrayContaining([470, 520]))
    // Before the answer nothing says where the chart ended — there is nothing after it yet.
    expect(texts(calls)).not.toContain('chart ended')
  })

  it('T26: draws an open trade — target green, stop red, entry plain — across the chart, past the moment', () => {
    const trade: QuestionView = {
      version: 'manage-1.0.0',
      definition: 'manage-1.0.0.',
      closeTicks: 80_009,
      targets: [],
      context: [
        { what: 'TRADE_TARGET', shape: 'LEVEL', fromTime: T0 + 15 * 300, lowTicks: 80_024, highTicks: 80_024, label: 'Target 20006.00' },
        { what: 'TRADE_ENTRY', shape: 'LEVEL', fromTime: T0 + 15 * 300, lowTicks: 80_000, highTicks: 80_000, label: 'Long from 20000.00' },
        { what: 'TRADE_STOP', shape: 'LEVEL', fromTime: T0 + 15 * 300, lowTicks: 79_992, highTicks: 79_992, label: 'Stop 19998.00' },
      ],
    }
    // Answered, with the future on the chart: the lines run on past the moment to the last candle.
    const bars = [...BARS, { ...BARS[0], time: MOMENT + 600 }]
    const calls = paint({ question: trade, moment: MOMENT, bars })
    const lines = calls.filter((c) => c.op === 'lineTo')
    expect(lines.map((c) => [c.args[1], c.stroke])).toEqual(
      expect.arrayContaining([
        [476, '#3fb950'],
        [508, '#f85149'],
        [500, '#e6edf3'],
      ]),
    )
    // Each from the entry's candle to the last candle and on: past where the chart stopped.
    expect(lines.filter((c) => c.args[1] === 476).every((c) => (c.args[0] as number) > MOMENT - 1000)).toBe(true)
    expect(texts(calls)).toEqual(expect.arrayContaining(['Target 20006.00', 'Long from 20000.00', 'Stop 19998.00']))
    const label = calls.find((c) => c.op === 'fillText' && c.args[0] === 'Stop 19998.00')
    expect(label?.fill).toBe('#f85149')
  })

  it('once answered, marks where the chart ended and rings the candle that decided it', () => {
    const decided = MOMENT + 300
    const result: ResultView = { answer: 'SSL', correct: false, setups: [], outcomeTime: decided, candlesAfter: 2 }
    const bars = [...BARS, { ...BARS[0], time: MOMENT }, { ...BARS[0], time: decided }]
    const calls = paint({ question: QUESTION, moment: MOMENT, result, bars })
    expect(texts(calls)).toContain('chart ended')
    const momentLine = calls.find((c) => c.op === 'moveTo' && c.args[0] === MOMENT - 1000 && c.args[1] === 0)
    expect(momentLine).toBeDefined()
    // The ring sits on the sell-side, at the deciding candle.
    expect(calls.some((c) => c.op === 'arc' && c.args[0] === decided - 1000 && c.args[1] === 520)).toBe(true)
  })

  it('draws a mark above each marked candle, and a cross for one the rule did not have', () => {
    const marked = BARS[5].time
    const extra = BARS[9].time
    const calls = paint({ marks: [marked, extra], result: { setups: [], extraMarks: [extra] } })
    expect(calls.some((c) => c.op === 'arc' && c.args[0] === marked - 1000)).toBe(true)
    const crossed = calls.filter((c) => c.op === 'moveTo' && (c.args[0] as number) < extra - 1000 + 10 && (c.args[0] as number) > extra - 1000 - 10)
    expect(crossed.length).toBeGreaterThanOrEqual(2)
  })
})
