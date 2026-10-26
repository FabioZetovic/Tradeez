import { StrictMode } from 'react'
import { act, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { MemoryRouter } from 'react-router'
import { RiskDrills } from './RiskDrills'
import { RISK_KINDS, type RiskDrill } from '@/lib/api/drills'

/** The screen as its card opens it (T43): the first question is asked on arrival. */
const open = (kind: RiskDrill['kind']) =>
  render(
    <MemoryRouter>
      <RiskDrills kind={RISK_KINDS.find((k) => k.value === kind)!} />
    </MemoryRouter>,
  )

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
    open('CONTRACTS')
    expect(await screen.findByTestId('risk-question')).toHaveTextContent('An account of $50,000')
    expect(calls[0].body).toEqual({ kind: 'CONTRACTS' })
    expect(screen.getByRole('timer')).toHaveTextContent('30 s left')
    expect(screen.getByRole('timer')).not.toHaveClass('text-down')
    expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent('Position size')
    // The figures stand out (Lovable's card).
    expect(within(screen.getByTestId('risk-question')).getByText('$50,000')).toHaveClass('num')
    expect(within(screen.getByTestId('risk-question')).getByText('50')).toHaveClass('font-bold')
    expect(screen.queryByRole('button', { name: 'Next question' })).toBeNull()
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
    expect(result).toHaveClass('bg-up/10')
    await user.click(screen.getByRole('button', { name: 'Next question' }))
    await waitFor(() => expect(calls).toHaveLength(3))
    expect(calls[2]).toEqual({ method: 'POST', url: '/api/drills/risk', body: { kind: 'CONTRACTS' } })
    expect(await screen.findByLabelText('Your answer (contracts)')).toHaveValue('')
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
    open('STREAK')
    await user.type(await screen.findByLabelText('Your answer (%)'), '16%{Enter}')
    const result = await screen.findByTestId('risk-result')
    expect(calls[0].body).toEqual({ kind: 'STREAK' })
    expect(calls[1].body).toEqual({ value: 16 })
    expect(result).toHaveTextContent('Not quite: 14.92%')
    expect(result).toHaveClass('bg-down/10')
    expect(result).toHaveTextContent('You said 16%, in 50.5 s — over the 45 s.')
    expect(screen.getByTestId('risk-record')).toHaveTextContent('Losing streak: 1 of 3 right so far.')
  })

  it('BUG-089: reads a decimal comma as the decimal point — 14,92 is fourteen point nine two, not 1,492', async () => {
    const calls = serve({
      '/api/drills/risk:STREAK': STREAK,
      '/api/drills/risk/r2/answer': {
        ...STREAK,
        result: { given: 14.92, answer: 14.92, correct: true, seconds: 20, inTime: true, working: ['Down 14.92%.'], answered: 1, right: 1 },
      },
    })
    const user = userEvent.setup()
    open('STREAK')
    await user.type(await screen.findByLabelText('Your answer (%)'), '14,92 %{Enter}')
    await screen.findByTestId('risk-result')
    expect(calls[1].body).toEqual({ value: 14.92 })
  })

  it('refuses an answer that is not a number, and keeps the question open', async () => {
    const calls = serve({ '/api/drills/risk:CONTRACTS': ASKED })
    const user = userEvent.setup()
    open('CONTRACTS')
    await user.type(await screen.findByLabelText('Your answer (contracts)'), 'two{Enter}')
    expect(screen.getByRole('alert')).toHaveTextContent('An answer is a number, like 2.5 or 2,5.')
    expect(calls).toHaveLength(1)
    await user.clear(screen.getByLabelText('Your answer (contracts)'))
    await user.click(screen.getByRole('button', { name: 'Answer' }))
    expect(calls).toHaveLength(1)
  })

  it('counts down, and says when the time is up — the question still open', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
    serve({ '/api/drills/risk:CONTRACTS': ASKED })
    open('CONTRACTS')
    expect(await screen.findByRole('timer')).toHaveTextContent('30 s left')
    // The clock's ticking starts in an effect after the question shows.
    await act(async () => {})
    act(() => {
      vi.advanceTimersByTime(10_000)
    })
    expect(screen.getByRole('timer')).toHaveTextContent('20 s left')
    expect(screen.getByRole('timer')).not.toHaveClass('text-down')
    act(() => {
      vi.advanceTimersByTime(11_000)
    })
    // Under ten seconds the pill turns red.
    expect(screen.getByRole('timer')).toHaveTextContent('9 s left')
    expect(screen.getByRole('timer')).toHaveClass('text-down')
    act(() => {
      vi.advanceTimersByTime(10_000)
    })
    expect(screen.getByRole('timer')).toHaveTextContent('Time is up — answer anyway')
    expect(screen.getByRole('timer')).toHaveClass('text-down')
    expect(screen.getByLabelText('Your answer (contracts)')).toBeEnabled()
  })

  it("asks one question, not two, when React's strict mode mounts the screen twice", async () => {
    const calls = serve({ '/api/drills/risk:CONTRACTS': ASKED })
    render(
      <StrictMode>
        <MemoryRouter>
          <RiskDrills kind={RISK_KINDS[0]} />
        </MemoryRouter>
      </StrictMode>,
    )
    await screen.findByTestId('risk-question')
    expect(calls).toHaveLength(1)
  })

  it('says so when no question can be asked, and tries again', async () => {
    const calls = serve({})
    const user = userEvent.setup()
    open('RECOVERY')
    expect(await screen.findByRole('alert')).toHaveTextContent('Could not ask a question.')
    await user.click(screen.getByRole('button', { name: 'Try again' }))
    await waitFor(() => expect(calls).toHaveLength(2))
    expect(calls[1].body).toEqual({ kind: 'RECOVERY' })
  })
})
