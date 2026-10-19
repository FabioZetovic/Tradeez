import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { PlanPanel } from './PlanPanel'
import type { PlanContext, PlanIntent, PlanRequest } from '@/lib/api/practice'

const REF = 80_006

function panel(plan: PlanRequest | null, onChangeLevel = vi.fn(), onDiscard = vi.fn()) {
  return (
    <PlanPanel
      plan={plan}
      referenceTicks={REF}
      tickSize={0.25}
      tickValueCents={500}
      busy={false}
      onPlace={vi.fn()}
      onFlip={vi.fn()}
      onDiscard={onDiscard}
      onChangeLevel={onChangeLevel}
      onChangeContracts={vi.fn()}
      onLock={vi.fn()}
    />
  )
}

const LONG: PlanRequest = { direction: 'LONG', stopTicks: 79_966, targetTicks: 80_086, contracts: 1 }

describe('PlanPanel draft', () => {
  it('says that nothing is placed yet', () => {
    // A bracket on the chart looks like a position. Until it is placed, it is not one.
    render(panel(LONG))
    expect(screen.getByTestId('draft-title')).toHaveTextContent(/draft · long at market/i)
    expect(screen.getByTestId('draft-title')).toHaveTextContent(/not placed/i)
    expect(screen.getByRole('button', { name: /place trade/i })).toBeEnabled()
  })

  it('can be thrown away', async () => {
    const onDiscard = vi.fn()
    const user = userEvent.setup()
    render(panel(LONG, vi.fn(), onDiscard))

    await user.click(screen.getByRole('button', { name: /discard/i }))
    expect(onDiscard).toHaveBeenCalledOnce()
  })

  it('is only drafted, not placed, from the empty panel', () => {
    render(panel(null))
    expect(screen.getByRole('button', { name: /plan long/i })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /plan short/i })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /place trade/i })).not.toBeInTheDocument()
  })
})

const CONTEXT: PlanContext = {
  killzone: 'NEW_YORK_AM',
  suggestedBias: 'BEARISH',
  biasReasoning: 'Yesterday traded above the day before\'s high but closed back inside.',
  biasRule: 'daily-bias-1.0.0',
  checklist: {
    model: 'MODEL_2022',
    title: 'The 2022 model',
    version: 'model-2022-1.0.0',
    items: [
      { id: 'killzone', text: 'Inside a killzone: New York AM, 08:30–11:00.', met: true },
      { id: 'shift', text: 'No bullish market structure shift in the last 30 candles.', met: false },
    ],
    met: 1,
    total: 2,
  },
}

describe('PlanPanel reading (T22)', () => {
  function reading(intent: PlanIntent, onIntent = vi.fn(), context: PlanContext | null = CONTEXT) {
    return (
      <PlanPanel
        plan={LONG}
        referenceTicks={REF}
        tickSize={0.25}
        tickValueCents={500}
        busy={false}
        onPlace={vi.fn()}
        onFlip={vi.fn()}
        onDiscard={vi.fn()}
        onChangeLevel={vi.fn()}
        onChangeContracts={vi.fn()}
        onLock={vi.fn()}
        intent={intent}
        onIntent={onIntent}
        context={context}
      />
    )
  }

  it('says what the app sees: the killzone and the daily-bias suggestion, as a suggestion', () => {
    render(reading({}))
    expect(screen.getByTestId('plan-context')).toHaveTextContent(
      "In the New York AM killzone. The daily-bias reading suggests bearish: Yesterday traded above the day before's high but closed back inside. A suggestion, not a verdict.",
    )
    // No model chosen: no checklist, though the server answered one.
    expect(screen.queryByTestId('checklist')).toBeNull()
  })

  it('names a model, a bias and the expected liquidity through the callback', async () => {
    const onIntent = vi.fn()
    const user = userEvent.setup()
    render(reading({}, onIntent))
    await user.selectOptions(screen.getByLabelText('Model'), 'MODEL_2022')
    expect(onIntent).toHaveBeenLastCalledWith({ model: 'MODEL_2022' })
    await user.click(screen.getByRole('button', { name: 'Bearish' }))
    expect(onIntent).toHaveBeenLastCalledWith({ bias: 'BEARISH' })
    await user.type(screen.getByLabelText('Draw on liquidity'), '20030{Enter}')
    expect(onIntent).toHaveBeenLastCalledWith({ dolTicks: 80_120 })
  })

  it("shows the chosen model's checklist, met and not, and clears expected liquidity when emptied", async () => {
    const onIntent = vi.fn()
    const user = userEvent.setup()
    render(reading({ model: 'MODEL_2022', bias: 'BULLISH', dolTicks: 80_120 }, onIntent))
    const checklist = screen.getByTestId('checklist')
    expect(checklist).toHaveTextContent('The 2022 model checklist · 1 of 2 · model-2022-1.0.0')
    expect(screen.getByText(/Inside a killzone/)).toHaveTextContent(', met')
    expect(screen.getByText(/No bullish market structure shift/)).toHaveTextContent(', not met')
    expect(screen.getByRole('button', { name: 'Bullish' })).toHaveAttribute('aria-pressed', 'true')
    await user.clear(screen.getByLabelText('Draw on liquidity'))
    await user.tab()
    expect(onIntent).toHaveBeenLastCalledWith({ model: 'MODEL_2022', bias: 'BULLISH', dolTicks: undefined })
  })

  it('outside the killzones, and with no reading, it says so', () => {
    render(reading({}, vi.fn(), { biasReasoning: 'Yesterday stayed inside the day before\'s range.', biasRule: 'daily-bias-1.0.0' }))
    expect(screen.getByTestId('plan-context')).toHaveTextContent('Outside the killzones. Yesterday stayed inside')
  })
})

describe('PlanPanel price boxes', () => {
  it('follow the bracket when it is dragged on the chart', () => {
    // The chart and the panel edit the same drawing. The box has no copy of its own to go stale.
    const { rerender } = render(panel(LONG))
    expect(screen.getByLabelText('Stop')).toHaveValue('19991.50')

    rerender(panel({ ...LONG, stopTicks: 79_950 }))
    expect(screen.getByLabelText('Stop')).toHaveValue('19987.50')
  })

  it('keep what is being typed until it is committed', async () => {
    const onChangeLevel = vi.fn()
    const user = userEvent.setup()
    render(panel(LONG, onChangeLevel))
    const stop = screen.getByLabelText('Stop')

    await user.clear(stop)
    await user.type(stop, '19990')
    expect(stop).toHaveValue('19990')
    expect(onChangeLevel).not.toHaveBeenCalled()

    await user.keyboard('{Enter}')
    expect(onChangeLevel).toHaveBeenCalledWith('stop', 79_960)
  })

  it('put the previous price back when the input is not a price', async () => {
    const onChangeLevel = vi.fn()
    const user = userEvent.setup()
    render(panel(LONG, onChangeLevel))
    const stop = screen.getByLabelText('Stop')

    await user.clear(stop)
    await user.type(stop, 'abc')
    await user.tab()

    expect(onChangeLevel).not.toHaveBeenCalled()
    expect(stop).toHaveValue('19991.50')
  })
})
