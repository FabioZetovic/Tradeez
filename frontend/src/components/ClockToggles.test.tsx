import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { ClockToggles } from './ClockToggles'

describe('the New York clock switches', () => {
  it('switch a part of the clock on and off', async () => {
    const onToggle = vi.fn()
    render(<ClockToggles shown={new Set(['days'])} onToggle={onToggle} />)
    expect(screen.getByRole('button', { name: 'Days' })).toHaveAttribute('aria-pressed', 'true')
    expect(screen.getByRole('button', { name: 'Killzones' })).toHaveAttribute('aria-pressed', 'false')
    await userEvent.click(screen.getByRole('button', { name: 'Killzones' }))
    expect(onToggle).toHaveBeenCalledWith('killzones')
  })

  it('T35: on a daily chart, which draws no clock, say why instead of offering switches that do nothing', () => {
    render(<ClockToggles shown={new Set(['days'])} onToggle={vi.fn()} available={false} />)
    const group = screen.getByRole('group', { name: 'New York clock' })
    expect(group).toHaveTextContent('On intraday charts, up to 4h')
    expect(screen.queryAllByRole('button')).toHaveLength(0)
  })
})
