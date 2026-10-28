import { afterEach, describe, expect, it } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MotionGlobalConfig } from 'motion/react'
import { useState } from 'react'
import { CountUp, Segmented } from './ui'

describe('CountUp', () => {
  afterEach(() => {
    MotionGlobalConfig.skipAnimations = true
  })

  it('shows its value at once when motion is off', () => {
    render(<CountUp value={3.98} format={(n) => `${n.toFixed(2)}R`} />)
    expect(screen.getByText('3.98R')).toBeInTheDocument()
  })

  it('counts up from zero, and ends exactly on its value', async () => {
    MotionGlobalConfig.skipAnimations = false
    render(<CountUp value={75} format={(n) => `${Math.round(n)}%`} />)
    expect(screen.getByText('0%')).toBeInTheDocument()
    await waitFor(() => expect(screen.getByText('75%')).toBeInTheDocument(), { timeout: 2000 })
  })
})

describe('Segmented', () => {
  function Two() {
    const [a, setA] = useState<'1m' | '5m'>('1m')
    const [b, setB] = useState<'NQ' | 'ES'>('ES')
    return (
      <>
        <Segmented label="Timeframe" options={['1m', '5m'] as const} value={a} onChange={setA} />
        <Segmented label="Market" options={['NQ', 'ES'] as const} value={b} onChange={setB} />
      </>
    )
  }

  it('each group keeps its own highlight on its own choice', async () => {
    render(<Two />)
    await userEvent.click(screen.getByRole('button', { name: '5m' }))
    expect(screen.getByRole('button', { name: '5m' })).toHaveAttribute('aria-pressed', 'true')
    expect(screen.getByRole('button', { name: 'ES' })).toHaveAttribute('aria-pressed', 'true')
    // One highlight per group, under the pressed option.
    const pressed = screen.getAllByRole('button').filter((b) => b.getAttribute('aria-pressed') === 'true')
    expect(pressed).toHaveLength(2)
    for (const button of screen.getAllByRole('button')) {
      expect(button.querySelectorAll('span[aria-hidden]')).toHaveLength(button.getAttribute('aria-pressed') === 'true' ? 1 : 0)
    }
  })
})
