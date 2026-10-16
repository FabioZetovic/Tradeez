import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { PlanPanel } from './PlanPanel'
import type { PlanRequest } from '@/lib/api/practice'

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
