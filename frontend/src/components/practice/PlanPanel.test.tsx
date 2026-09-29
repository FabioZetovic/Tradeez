import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { PlanPanel } from './PlanPanel'
import type { PlanRequest } from '@/lib/api/practice'

const REF = 80_006

function panel(plan: PlanRequest, onChangeLevel = vi.fn()) {
  return (
    <PlanPanel
      plan={plan}
      referenceTicks={REF}
      tickSize={0.25}
      tickValueCents={500}
      busy={false}
      onPlace={vi.fn()}
      onFlip={vi.fn()}
      onChangeLevel={onChangeLevel}
      onChangeContracts={vi.fn()}
      onLock={vi.fn()}
    />
  )
}

const LONG: PlanRequest = { direction: 'LONG', stopTicks: 79_966, targetTicks: 80_086, contracts: 1 }

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
