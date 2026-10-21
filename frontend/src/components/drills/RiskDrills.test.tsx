import { act, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { RiskDrills } from './RiskDrills'
import type { RiskDrill } from '@/lib/api/drills'

const ASKED: RiskDrill = {
  id: 'r1',
  kind: 'CONTRACTS',
  version: 'risk-1.0.0',
  text: 'An account of $50,000, risking 1% of it on this trade. The stop is 50 ticks away, on NQ ($5 a tick). How many contracts?',
  unit: 'contracts',
  limitSeconds: 30,
}

const STREAK: RiskDrill = {
  ...ASKED,
  id: 'r2',
  kind: 'STREAK',
  text: 'Risking 2% of what the account holds on every trade, how far down is it after 8 losses in a row?',
  unit: '%',
  limitSeconds: 45,
}

interface Call {
  method: string
  url: string
  body: unknown
}

function serve(answers: Record<string, RiskDrill>) {
  const calls: Call[] = []
  vi.stubGlobal(
    'fetch',
    vi.fn((url: string, init?: RequestInit) => {
      const body = typeof init?.body === 'string' ? JSON.parse(init.body) : undefined
      calls.push({ method: init?.method ?? 'GET', url, body })
      const key = url.endsWith('/answer') ? url : `${url}:${(body as { kind?: string })?.kind}`
      const reply = answers[key]
      return Promise.resolve({
        ok: reply !== undefined,
        status: reply ? 200 : 500,
        statusText: '',
        headers: new Headers({ 'content-type': 'application/json' }),
        json: async () => reply ?? {},
        text: async () => JSON.stringify(reply ?? {}),
      })
    }),
  )
  return calls
}

afterEach(() => {
  vi.useRealTimers()
  vi.unstubAllGlobals()
})

describe('risk drills, against the clock (T26)', () => {
  it('asks a position size with the clock running, then grades it with the working and the record', async () => {
    const calls = serve({
      '/api/drills/risk:CONTRACTS': ASKED,
      '/api/drills/risk/r1/answer': {
        ...ASKED,
        result: {
          given: 2,
          answer: 2,
          correct: true,
          seconds: 4.2,
          inTime: true,
          working: ['1% of $50,000 is $500.', '50 ticks × $5 a tick = $250 a contract.', '$500 ÷ $250 = 2.00: 2 contracts.'],
          answered: 1,
          right: 1,
        },
      },
    })
    const user = userEvent.setup()
    render(<RiskDrills />)
    await user.click(screen.getByRole('button', { name: 'Ask a question' }))
    expect(await screen.findByTestId('risk-question')).toHaveTextContent('An account of $50,000')
    expect(calls[0].body).toEqual({ kind: 'CONTRACTS' })
    expect(screen.getByRole('timer')).toHaveTextContent('30 s left')
    expect(screen.queryByTestId('risk-result')).toBeNull()

    await user.type(screen.getByLabelText('Your answer (contracts)'), '2{Enter}')
    const result = await screen.findByTestId('risk-result')
    expect(calls[1]).toEqual({ method: 'POST', url: '/api/drills/risk/r1/answer', body: { value: 2 } })
    expect(result).toHaveTextContent('Right: 2 contracts')
    expect(result).toHaveTextContent('You said 2 contracts, in 4.2 s, inside the 30 s.')
    expect(within(result).getAllByRole('listitem')).toHaveLength(3)
    expect(screen.getByTestId('risk-record')).toHaveTextContent('Position size: 1 of 1 right so far. risk-1.0.0.')
    // Answered: no clock and no box.
    expect(screen.queryByRole('timer')).toBeNull()
    expect(screen.queryByLabelText('Your answer (contracts)')).toBeNull()
    expect(screen.getByRole('button', { name: 'Next question' })).toBeEnabled()
  })

  it('a losing streak, answered wrong and late: the right figure, and over the clock', async () => {
    const calls = serve({
      '/api/drills/risk:STREAK': STREAK,
      '/api/drills/risk/r2/answer': {
        ...STREAK,
        result: { given: 16, answer: 14.92, correct: false, seconds: 50.5, inTime: false, working: ['Down 14.92%.'], answered: 3, right: 1 },
      },
    })
    const user = userEvent.setup()
    render(<RiskDrills />)
    await user.click(screen.getByRole('button', { name: 'Losing streak' }))
    await user.click(screen.getByRole('button', { name: 'Ask a question' }))
    await user.type(await screen.findByLabelText('Your answer (%)'), '16%{Enter}')
    const result = await screen.findByTestId('risk-result')
    expect(calls[0].body).toEqual({ kind: 'STREAK' })
    expect(calls[1].body).toEqual({ value: 16 })
    expect(result).toHaveTextContent('Not quite: 14.92%')
    expect(result).toHaveTextContent('You said 16%, in 50.5 s — over the 45 s.')
    expect(screen.getByTestId('risk-record')).toHaveTextContent('Losing streak: 1 of 3 right so far.')
  })

  it('refuses an answer that is not a number, and keeps the question open', async () => {
    const calls = serve({ '/api/drills/risk:CONTRACTS': ASKED })
    const user = userEvent.setup()
    render(<RiskDrills />)
    await user.click(screen.getByRole('button', { name: 'Ask a question' }))
    await user.type(await screen.findByLabelText('Your answer (contracts)'), 'two{Enter}')
    expect(screen.getByRole('alert')).toHaveTextContent('An answer is a number.')
    expect(calls).toHaveLength(1)
    await user.clear(screen.getByLabelText('Your answer (contracts)'))
    await user.click(screen.getByRole('button', { name: 'Answer' }))
    expect(calls).toHaveLength(1)
  })

  it('counts down, and says when the time is up — the question still open', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
    serve({ '/api/drills/risk:CONTRACTS': ASKED })
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime })
    render(<RiskDrills />)
    await user.click(screen.getByRole('button', { name: 'Ask a question' }))
    expect(await screen.findByRole('timer')).toHaveTextContent('30 s left')
    act(() => {
      vi.advanceTimersByTime(10_000)
    })
    expect(screen.getByRole('timer')).toHaveTextContent('20 s left')
    act(() => {
      vi.advanceTimersByTime(21_000)
    })
    expect(screen.getByRole('timer')).toHaveTextContent('Time is up — answer anyway')
    expect(screen.getByRole('timer')).toHaveClass('text-down')
    expect(screen.getByLabelText('Your answer (contracts)')).toBeEnabled()
  })
})
