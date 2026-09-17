import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import App from './App'
import { FakeChartAdapter } from '@/test/fakeChartAdapter'

const USER = {
  id: 'u1',
  email: 'fabio@example.com',
  displayName: 'Fabio',
  createdAt: '2026-09-17T00:00:00Z',
}

const INSTRUMENTS = [
  {
    symbol: 'DEMO-NQ',
    displayName: 'Demo E-mini Nasdaq-100 (simulated)',
    exchange: 'DEMO',
    currency: 'USD',
    tickSize: 0.25,
    tickValueCents: 500,
    dataClass: 'SIMULATED',
    exchangeTimezone: 'America/Chicago',
  },
]

const SERIES = {
  instrument: 'DEMO-NQ',
  timeframe: '5m',
  dataClass: 'SIMULATED',
  seed: 42,
  generatorVersion: 'demo-gen-1.1.0',
  disclaimer: 'SIMULATED DATA — NOT MARKET HISTORY',
  bars: [
    { time: 1710338400, open: 80_000, high: 80_010, low: 79_990, close: 80_006, volume: 10 },
  ],
}

/** Routes fetch by URL so tests describe server behaviour rather than call order. */
function mockApi(routes: Record<string, { status?: number; body: unknown }>) {
  vi.stubGlobal(
    'fetch',
    vi.fn((url: string) => {
      const match = Object.keys(routes).find((key) => url.includes(key))
      const route = match ? routes[match] : { status: 404, body: { message: 'not found' } }
      const status = route.status ?? 200
      return Promise.resolve({
        ok: status < 400,
        status,
        statusText: String(status),
        headers: new Headers({ 'content-type': 'application/json' }),
        json: async () => route.body,
      })
    }),
  )
}

const signedOut = { '/api/me': { status: 401, body: {} }, '/api/auth/refresh': { status: 401, body: {} } }
const signedIn = {
  '/api/me': { body: USER },
  '/api/market/instruments/DEMO-NQ/bars': { body: SERIES },
  '/api/market/instruments': { body: INSTRUMENTS },
}

afterEach(() => vi.restoreAllMocks())

describe('App — signed out', () => {
  beforeEach(() => mockApi(signedOut))

  it('shows the sign-in form when no session can be restored', async () => {
    render(<App />)
    expect(await screen.findByRole('heading', { name: /sign in/i })).toBeInTheDocument()
  })

  it('does not render the chart to an unauthenticated visitor', async () => {
    render(<App />)
    await screen.findByRole('heading', { name: /sign in/i })
    expect(screen.queryByTestId('price-chart')).not.toBeInTheDocument()
  })

  it('switches to registration and asks for a display name', async () => {
    render(<App />)
    await screen.findByRole('heading', { name: /sign in/i })

    await userEvent.click(screen.getByRole('button', { name: /create an account instead/i }))

    expect(await screen.findByLabelText(/display name/i)).toBeInTheDocument()
  })

  it('surfaces the server message verbatim on a failed sign-in', async () => {
    // The backend returns the same wording for a wrong password and an unknown account so the API
    // cannot be used to discover which addresses exist. Rewording it here would undo that.
    mockApi({
      ...signedOut,
      '/api/auth/login': { status: 401, body: { message: 'Invalid email or password.' } },
    })
    render(<App />)
    await screen.findByRole('heading', { name: /sign in/i })

    await userEvent.type(screen.getByLabelText(/email/i), 'ghost@example.com')
    await userEvent.type(screen.getByLabelText(/password/i), 'whatever-i-typed')
    await userEvent.click(screen.getByRole('button', { name: /^sign in$/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Invalid email or password.')
  })
})

describe('App — signed in', () => {
  beforeEach(() => mockApi(signedIn))

  it('restores the session and shows the workspace', async () => {
    render(<App adapterFactory={() => new FakeChartAdapter()} />)
    expect(await screen.findByText('Fabio')).toBeInTheDocument()
  })

  it('renders the chart with the fetched bars', async () => {
    const adapter = new FakeChartAdapter()
    render(<App adapterFactory={() => adapter} />)

    await waitFor(() => expect(adapter.bars).toHaveLength(1))
    expect(adapter.mountOptions?.tickSize).toBe(0.25)
  })

  it('shows the simulated-data banner (DEC-033)', async () => {
    render(<App adapterFactory={() => new FakeChartAdapter()} />)
    expect(await screen.findByText(/SIMULATED DATA — NOT MARKET HISTORY/i)).toBeInTheDocument()
  })

  it('displays the last price converted from ticks, not the raw tick count', async () => {
    // 80006 ticks x 0.25 = 20001.50. Showing "80006" would be showing the storage format.
    render(<App adapterFactory={() => new FakeChartAdapter()} />)
    expect(await screen.findByTestId('last-price')).toHaveTextContent('20001.50')
  })

  it('refetches when the timeframe changes', async () => {
    const adapter = new FakeChartAdapter()
    render(<App adapterFactory={() => adapter} />)
    await waitFor(() => expect(adapter.bars).toHaveLength(1))

    await userEvent.click(screen.getByRole('button', { name: '15m' }))

    await waitFor(() => {
      const calls = (globalThis.fetch as ReturnType<typeof vi.fn>).mock.calls
      expect(calls.some(([url]) => String(url).includes('timeframe=15m'))).toBe(true)
    })
  })

  it('sends credentials on every request so the auth cookie is attached', async () => {
    // DEC-032 — without credentials: 'include' the HttpOnly cookie is simply not sent on a
    // cross-origin request, and every authenticated call would 401 in production while passing
    // locally through the Vite proxy.
    render(<App adapterFactory={() => new FakeChartAdapter()} />)
    await screen.findByText('Fabio')

    const calls = (globalThis.fetch as ReturnType<typeof vi.fn>).mock.calls
    expect(calls.length).toBeGreaterThan(0)
    for (const [, init] of calls) {
      expect((init as RequestInit).credentials).toBe('include')
    }
  })
})
