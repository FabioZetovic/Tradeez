import { fireEvent, render, screen } from '@testing-library/react'
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

describe('PlanPanel scorecard inputs (T23)', () => {
  function limited(riskLimitCents: number | undefined, intent: PlanIntent = {}, onIntent = vi.fn()) {
    return (
      <PlanPanel
        plan={LONG}
        referenceTicks={REF}
        tickSize={0.25}
        tickValueCents={500}
        riskLimitCents={riskLimitCents}
        busy={false}
        onPlace={vi.fn()}
        onFlip={vi.fn()}
        onDiscard={vi.fn()}
        onChangeLevel={vi.fn()}
        onChangeContracts={vi.fn()}
        onLock={vi.fn()}
        intent={intent}
        onIntent={onIntent}
      />
    )
  }

  it('T27: reads the most a round trip can cost, for every contract, the exit slipping as the worse of stop and market', () => {
    const costs = { feesCents: 450, marketSlippageTicks: 1, stopSlippageTicks: 1 }
    const draft = (plan: PlanRequest, c = costs) => (
      <PlanPanel plan={plan} referenceTicks={REF} tickSize={0.25} tickValueCents={500} costs={c} busy={false}
        onPlace={vi.fn()} onFlip={vi.fn()} onDiscard={vi.fn()} onChangeLevel={vi.fn()} onChangeContracts={vi.fn()} onLock={vi.fn()} />
    )
    const { rerender } = render(draft(LONG))
    expect(screen.getByTestId('plan-costs')).toHaveTextContent('$4.50 fees · slippage 1 tick in, up to 1 out — at most $14.50')
    rerender(draft({ ...LONG, contracts: 2 }, { feesCents: 450, marketSlippageTicks: 1, stopSlippageTicks: 3 }))
    expect(screen.getByTestId('plan-costs')).toHaveTextContent('$9.00 fees · slippage 1 tick in, up to 3 out — at most $49.00')
    rerender(draft(LONG, { feesCents: 0, marketSlippageTicks: 2, stopSlippageTicks: 0 }))
    expect(screen.getByTestId('plan-costs')).toHaveTextContent('$0.00 fees · slippage 2 ticks in, up to 2 out — at most $20.00')
  })

  it("reads the draft's risk against the learner's limit — within at exactly the limit, over by a cent — and never blocks it", () => {
    // Forty ticks of risk at $5 on one contract: $200.
    const { rerender } = render(limited(20_000))
    expect(screen.getByTestId('risk-limit')).toHaveTextContent('· within your $200.00 limit')
    expect(screen.getByTestId('risk-limit')).toHaveClass('text-subtle')
    rerender(limited(19_999))
    expect(screen.getByTestId('risk-limit')).toHaveTextContent('· over your $199.99 limit')
    expect(screen.getByTestId('risk-limit')).toHaveClass('text-down')
    expect(screen.getByRole('button', { name: /place trade/i })).toBeEnabled()
    rerender(limited(undefined))
    expect(screen.queryByTestId('risk-limit')).toBeNull()
  })

  it('says how sure the learner is that the target comes first, and takes it back', async () => {
    const onIntent = vi.fn()
    const user = userEvent.setup()
    const { rerender } = render(limited(undefined, {}, onIntent))
    const sure = screen.getByLabelText('Target before stop')
    expect(sure).toHaveValue('')
    await user.selectOptions(sure, '70% sure')
    expect(onIntent).toHaveBeenLastCalledWith({ confidence: 70 })
    rerender(limited(undefined, { confidence: 70, bias: 'BULLISH' }, onIntent))
    expect(sure).toHaveValue('70')
    await user.selectOptions(sure, 'Not said')
    expect(onIntent).toHaveBeenLastCalledWith({ bias: 'BULLISH', confidence: undefined })
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

describe('PlanPanel partial (T28)', () => {
  function partialPanel(plan: PlanRequest, onChangePartial = vi.fn()) {
    return (
      <PlanPanel
        plan={plan}
        referenceTicks={REF}
        tickSize={0.25}
        tickValueCents={500}
        busy={false}
        onPlace={vi.fn()}
        onFlip={vi.fn()}
        onDiscard={vi.fn()}
        onChangeLevel={vi.fn()}
        onChangeContracts={vi.fn()}
        onChangePartial={onChangePartial}
        onLock={vi.fn()}
      />
    )
  }
  const TWO: PlanRequest = { ...LONG, contracts: 2 }
  const HALF: PlanRequest = { ...TWO, partialTicks: 80_046, partialContracts: 1, breakevenAfterPartial: false }

  it('needs two contracts or more', () => {
    render(partialPanel(LONG))
    expect(screen.queryByLabelText('Partial')).not.toBeInTheDocument()
    expect(screen.getByText(/two contracts or more/)).toBeInTheDocument()
  })

  it('takes half off at a typed price, by default, and leaves the stop where it is', async () => {
    const onChangePartial = vi.fn()
    const user = userEvent.setup()
    render(partialPanel(TWO, onChangePartial))
    expect(screen.getByLabelText('Partial')).toHaveValue('')
    await user.type(screen.getByLabelText('Partial'), '20011.50{Enter}')
    expect(onChangePartial).toHaveBeenCalledWith({ ticks: 80_046, contracts: 1, breakeven: false })
  })

  it('sets how many come off and the stop to the entry, and is removed when its price is emptied', async () => {
    const onChangePartial = vi.fn()
    const user = userEvent.setup()
    render(partialPanel({ ...HALF, contracts: 3 }, onChangePartial))
    expect(screen.getByLabelText('Partial')).toHaveValue('20011.50')
    await user.click(screen.getByRole('checkbox', { name: /stop to the entry/ }))
    expect(onChangePartial).toHaveBeenLastCalledWith({ ticks: 80_046, contracts: 1, breakeven: true })
    fireEvent.change(screen.getByLabelText('Take off'), { target: { value: '2' } })
    expect(onChangePartial).toHaveBeenLastCalledWith({ ticks: 80_046, contracts: 2, breakeven: false })
    await user.clear(screen.getByLabelText('Partial'))
    await user.tab()
    expect(onChangePartial).toHaveBeenLastCalledWith(null)
  })

  it("reads the whole position's reward — the partial to its level, the rest to the target — and refuses one past it", () => {
    const { rerender } = render(partialPanel(HALF))
    const numbers = screen.getByTestId('plan-numbers')
    // One contract 40 ticks to the partial, one 80 to the target, on 40 ticks of risk each.
    expect(screen.getByTestId('plan-reward')).toHaveTextContent('40 then 80 ticks · $600.00')
    expect(numbers).toHaveTextContent('1.50')
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()

    rerender(partialPanel({ ...HALF, partialTicks: 80_086 }))
    expect(screen.getByRole('alert')).toHaveTextContent(/between the current price and the target/)
    rerender(partialPanel({ ...HALF, partialContracts: 2 }))
    expect(screen.getByRole('alert')).toHaveTextContent(/fewer than all/)
  })
})
