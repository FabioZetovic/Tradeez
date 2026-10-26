import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import App from './App'
import { FakeChartAdapter } from '@/test/fakeChartAdapter'
import { recorder } from '@/test/canvasRecorder'

const USER = {
  id: 'u1',
  email: 'fabio@example.com',
  displayName: 'Fabio',
  createdAt: '2026-09-17T00:00:00Z',
}

const DAY = 86_400
/** What V8 + a Massive import leave the API saying: real NQ only, two years of minutes. */
const DATA_FROM = Date.parse('2024-10-08T00:00:00Z') / 1000
const DATA_TO = Date.parse('2026-10-03T00:00:00Z') / 1000

const NQ = {
  symbol: 'NQ',
  displayName: 'E-mini Nasdaq-100 futures',
  exchange: 'CME',
  currency: 'USD',
  tickSize: 0.25,
  tickValueCents: 500,
  dataClass: 'IMPORTED_PRIVATE',
  exchangeTimezone: 'America/Chicago',
  dataFrom: DATA_FROM,
  dataTo: DATA_TO,
}
const INSTRUMENTS = [NQ]

const SERIES = {
  instrument: 'NQ',
  timeframe: '5m',
  dataClass: 'IMPORTED_PRIVATE',
  source: 'massive',
  bars: [
    { time: DATA_TO - 300, open: 80_000, high: 80_010, low: 79_990, close: 80_006, volume: 10 },
  ],
  hidden: [],
}

/** The 'to' of every bars request made so far, in order. */
function barRequestEnds(): number[] {
  return (globalThis.fetch as ReturnType<typeof vi.fn>).mock.calls
    .map(([url]) => String(url))
    .filter((url) => url.includes('/bars'))
    .map((url) => Number(new URL(url, 'http://test').searchParams.get('to')))
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

/** The New York clock over the window (T7): one killzone and the previous day's high. */
const CLOCK = {
  rules: { sessions: 'sessions-1.0.0', opens: 'opens-1.0.0', levels: 'levels-1.0.0' },
  windows: [{ kind: 'KILLZONE', label: 'NY AM', from: DATA_TO - 3600, to: DATA_TO }],
  lines: [{ kind: 'PREVIOUS_DAY', label: 'PDH', from: DATA_TO - 3600, to: DATA_TO, priceTicks: 80_100 }],
}

const signedOut = { '/api/me': { status: 401, body: {} }, '/api/auth/refresh': { status: 401, body: {} } }
const signedIn = {
  '/api/me': { body: USER },
  '/api/market/instruments/NQ/bars': { body: SERIES },
  '/api/market/instruments/NQ/clock': { body: CLOCK },
  '/api/market/instruments': { body: INSTRUMENTS },
  '/api/charts/NQ/drawings': { body: [] },
}

function requests(path: string): URL[] {
  return (globalThis.fetch as ReturnType<typeof vi.fn>).mock.calls
    .map(([url]) => new URL(String(url), 'http://test'))
    .filter((url) => url.pathname.endsWith(path))
}

// Every test starts at the root, as a fresh visit does.
beforeEach(() => window.history.replaceState(null, '', '/'))
afterEach(() => vi.restoreAllMocks())

describe('App — signed out', () => {
  beforeEach(() => mockApi(signedOut))

  it('shows the sign-in form when no session can be restored', async () => {
    render(<App />)
    expect(await screen.findByRole('heading', { name: /sign in/i })).toBeInTheDocument()
    // Never signed in, so not told a session ended.
    expect(screen.queryByTestId('session-expired')).not.toBeInTheDocument()
  })

  it('BUG-040: a session that ends while the app is open returns to sign-in, and says why', async () => {
    // Signed in at load; minutes later every request is refused and so is the refresh.
    mockApi({
      ...signedIn,
      '/api/market/instruments/NQ/bars': { status: 401, body: {} },
      '/api/auth/refresh': { status: 401, body: {} },
    })
    render(<App adapterFactory={() => new FakeChartAdapter()} />)

    expect(await screen.findByTestId('session-expired')).toHaveTextContent(/signed out/i)
    expect(screen.getByRole('heading', { name: /sign in/i })).toBeInTheDocument()
  })

  it('does not render the chart to an unauthenticated visitor', async () => {
    render(<App />)
    await screen.findByRole('heading', { name: /sign in/i })
    expect(screen.queryByTestId('price-chart')).not.toBeInTheDocument()
  })

  it('switches to registration and asks for a display name', async () => {
    render(<App />)
    await screen.findByRole('heading', { name: /sign in/i })

    await userEvent.click(screen.getByRole('link', { name: /create an account instead/i }))

    expect(await screen.findByLabelText(/display name/i)).toBeInTheDocument()
  })

  it('DEC-064: registration ticks password-1.0.0 off as it is typed, and shows the server’s refusal', async () => {
    mockApi({
      ...signedOut,
      '/api/auth/register': {
        status: 400,
        body: { message: 'Choose a different password. It has appeared in known data breaches 42 times.' },
      },
    })
    render(<App />)
    await userEvent.click(await screen.findByRole('link', { name: /create an account instead/i }))
    await userEvent.type(screen.getByLabelText(/display name/i), 'Marta')
    await userEvent.type(screen.getByLabelText(/email/i), 'marta@example.com')
    const password = screen.getByLabelText(/^password$/i)
    await userEvent.type(password, 'marta')
    expect(password).toHaveAccessibleDescription(/At least 12 characters, not yet/)
    expect(screen.getByText(/Not your email/)).toHaveTextContent(', not yet')
    await userEvent.clear(password)
    await userEvent.type(password, 'blue lanterns over the harbour')
    expect(screen.getByText(/Not your email/)).toHaveTextContent(', met')
    await userEvent.click(screen.getByRole('button', { name: 'Create account' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('appeared in known data breaches 42 times')
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
  // The Chart tab: Home is the root since T39.
  beforeEach(() => {
    mockApi(signedIn)
    window.history.replaceState(null, '', '/chart')
  })

  it('restores the session and shows the workspace', async () => {
    render(<App adapterFactory={() => new FakeChartAdapter()} />)
    expect(await screen.findByText('Fabio')).toBeInTheDocument()
  })

  it('BUG-032: the page has a definite width, so the chart cannot hold it wider than the window', async () => {
    render(<App adapterFactory={() => new FakeChartAdapter()} />)
    await screen.findByText('Fabio')
    const main = screen.getByRole('main')
    expect(main).toHaveClass('w-full')
    expect(main).toHaveClass('min-w-0')
  })

  it('renders the chart with the fetched bars', async () => {
    const adapter = new FakeChartAdapter()
    render(<App adapterFactory={() => adapter} />)

    await waitFor(() => expect(adapter.bars).toHaveLength(1))
    expect(adapter.mountOptions?.tickSize).toBe(0.25)
  })

  it('asks for the New York clock over exactly the window the bars cover (T7)', async () => {
    const adapter = new FakeChartAdapter()
    render(<App adapterFactory={() => adapter} />)
    await waitFor(() => expect(requests('/clock')).toHaveLength(1))

    const [bars] = requests('/bars')
    const [clock] = requests('/clock')
    expect(clock.searchParams.get('from')).toBe(bars.searchParams.get('from'))
    expect(clock.searchParams.get('to')).toBe(bars.searchParams.get('to'))
    // Painted by the chart under its candles (T37): once the clock arrives, the layer paints it, kept to the pane.
    await waitFor(() => {
      const { ctx, calls } = recorder()
      adapter.paintLayers(ctx)
      expect(calls.find((c) => c.op === 'rect')?.args).toEqual([0, 0, 800, 400])
    })
    expect(adapter.layers.find((l) => l.zOrder === 'bottom')).toBeDefined()
  })

  it('shows the killzones, the opens and PDH/PDL at first, and remembers a change (T38)', async () => {
    localStorage.removeItem('tradeez.clockLayers')
    render(<App adapterFactory={() => new FakeChartAdapter()} />)
    const chips = await screen.findByRole('list', { name: 'Layers on' })
    expect([...chips.querySelectorAll('li')].map((li) => li.textContent)).toEqual(['Killzones', 'Opens', 'PDH/PDL'])

    await userEvent.click(screen.getByRole('button', { name: /Layers/ }))
    const checked = (name: string) => screen.getByRole('switch', { name }).getAttribute('aria-checked')
    expect(checked('Killzones')).toBe('true')
    expect(checked('Days')).toBe('false')
    await userEvent.click(screen.getByRole('switch', { name: 'Macros' }))
    expect(checked('Macros')).toBe('true')
    expect(chips).toHaveTextContent('Macros')
    expect(JSON.parse(localStorage.getItem('tradeez.clockLayers') ?? '[]')).toContain('macros')
  })

  it('charts NQ alone: no market to choose and none beside it (T38, R-1, R-2)', async () => {
    // ES listed first, with its own tick value: the page must take NQ's.
    mockApi({ ...signedIn, '/api/market/instruments': { body: [{ ...NQ, symbol: 'ES', tickValueCents: 1250 }, NQ] } })
    render(<App adapterFactory={() => new FakeChartAdapter()} />)
    expect(await screen.findByRole('heading', { name: 'NQ' })).toBeInTheDocument()
    expect(await screen.findByText('0.25 = $5.00')).toBeInTheDocument()
    await waitFor(() => expect(requests('/bars').length).toBeGreaterThan(0))
    expect(screen.queryByLabelText('Instrument')).not.toBeInTheDocument()
    expect(screen.queryByLabelText('Beside')).not.toBeInTheDocument()
    expect(requests('/bars').every((url) => url.pathname.includes('/NQ/'))).toBe(true)
  })

  it('never asks the chart for SMT, even when an older version remembered it on (R-2)', async () => {
    localStorage.setItem('tradeez.setupLayers', JSON.stringify(['SMT', 'FVG']))
    mockApi({
      ...signedIn,
      '/api/concepts': {
        body: {
          levels: [],
          concepts: ['FVG', 'SMT'].map((detector) => ({
            concept: { id: detector, name: detector, summary: '' },
            detectors: [{ detector, version: '1.0.0' }],
          })),
        },
      },
      '/api/charts/NQ/setups': { body: { rules: [], setups: [], rolls: [] } },
    })
    render(<App adapterFactory={() => new FakeChartAdapter()} />)
    await waitFor(() => expect(requests('/setups')).toHaveLength(1))
    expect(requests('/setups')[0].searchParams.get('detectors')).toBe('FVG')
    localStorage.removeItem('tradeez.setupLayers')
  })

  it("shows the move since the previous session's close, signed and coloured", async () => {
    // Friday 2 Oct 2026: the close before is Thursday's last candle before 17:00 New York (21:00 UTC).
    const thursday = Date.parse('2026-10-01T20:55:00Z') / 1000
    const friday = Date.parse('2026-10-02T14:00:00Z') / 1000
    const bar = (time: number, close: number) => ({ time, open: close, high: close, low: close, close, volume: 1 })
    mockApi({
      ...signedIn,
      '/api/market/instruments/NQ/bars': { body: { ...SERIES, bars: [bar(thursday, 80_000), bar(friday, 79_960)] } },
    })
    render(<App adapterFactory={() => new FakeChartAdapter()} />)
    const change = await screen.findByTestId('price-change')
    // 40 ticks down = 10.00 points, of 20,000.00.
    expect(change).toHaveTextContent('−10.00 (−0.05%)')
    expect(change).toHaveClass('text-down')
  })

  it('labels real data with its source and shows no simulated-data banner (INVARIANT 7)', async () => {
    render(<App adapterFactory={() => new FakeChartAdapter()} />)
    expect(await screen.findByTestId('data-source')).toHaveTextContent('massive')
    expect(screen.queryByText(/SIMULATED DATA — NOT MARKET HISTORY/i)).not.toBeInTheDocument()
  })

  it('opens on the newest imported data', async () => {
    const adapter = new FakeChartAdapter()
    render(<App adapterFactory={() => adapter} />)
    await waitFor(() => expect(adapter.bars).toHaveLength(1))
    expect(barRequestEnds()[0]).toBe(DATA_TO)
  })

  it('jumps back in time from the newest data', async () => {
    const adapter = new FakeChartAdapter()
    render(<App adapterFactory={() => adapter} />)
    await waitFor(() => expect(adapter.bars).toHaveLength(1))

    await userEvent.click(screen.getByRole('button', { name: '1M' }))

    await waitFor(() => expect(barRequestEnds()).toContain(DATA_TO - 30 * DAY))
  })

  it("a picked date loads that day up to its 17:00 New York close", async () => {
    const adapter = new FakeChartAdapter()
    render(<App adapterFactory={() => adapter} />)
    const input = await screen.findByTestId('go-to-date')
    expect(input).toHaveAttribute('min', '2024-10-07')
    expect(input).toHaveAttribute('max', '2026-10-02')

    fireEvent.change(input, { target: { value: '2025-03-10' } })

    // 17:00 EDT on 10 March 2025 = 21:00 UTC.
    await waitFor(() => expect(barRequestEnds()).toContain(Date.parse('2025-03-10T21:00:00Z') / 1000))
  })

  it('shows New York time by default, the clock ICT is taught in (DEC-043)', async () => {
    const adapter = new FakeChartAdapter()
    render(<App adapterFactory={() => adapter} />)
    await waitFor(() => expect(adapter.bars).toHaveLength(1))
    expect(adapter.mountOptions?.axisTimezone).toBe('new-york')
    // The data ends in October 2026: daylight time, whenever the test runs.
    expect(screen.getByTestId('timezone-toggle').textContent).toBe('EDT')
  })

  it("names the zone the candles on screen were in, not today's", async () => {
    const january = Date.parse('2025-01-15T15:00:00Z') / 1000
    mockApi({ ...signedIn, '/api/market/instruments/NQ/bars': { body: { ...SERIES, bars: [{ ...SERIES.bars[0], time: january }] } } })
    const adapter = new FakeChartAdapter()
    render(<App adapterFactory={() => adapter} />)
    await waitFor(() => expect(adapter.bars).toHaveLength(1))
    expect(screen.getByTestId('timezone-toggle').textContent).toBe('EST')
  })

  it('says why candles are held back, and what brings them back (BUG-085, BUG-086)', async () => {
    const stretch = { from: DATA_TO - 7200, to: DATA_TO - 3600 }
    const cases = [
      { hidden: [{ ...stretch, why: 'PRACTICE' }], says: /practice session you are in.*End the session in Practice/ },
      { hidden: [{ ...stretch, why: 'DRILL' }], says: /drill you have not answered yet.*Answer it/ },
      {
        hidden: [{ ...stretch, why: 'PRACTICE' }, { ...stretch, why: 'DRILL' }],
        says: /End the session in Practice and answer the drill/,
      },
    ]
    for (const { hidden, says } of cases) {
      mockApi({ ...signedIn, '/api/market/instruments/NQ/bars': { body: { ...SERIES, hidden } } })
      const { unmount } = render(<App adapterFactory={() => new FakeChartAdapter()} />)
      expect(await screen.findByTestId('hidden-stretches')).toHaveTextContent(says)
      unmount()
    }
    mockApi(signedIn)
    render(<App adapterFactory={() => new FakeChartAdapter()} />)
    expect(await screen.findByTestId('last-price')).toBeInTheDocument()
    expect(screen.queryByTestId('hidden-stretches')).toBeNull()
  })

  it('says so, and loads nothing, when no history has been imported', async () => {
    mockApi({ ...signedIn, '/api/market/instruments': { body: [{ ...NQ, dataFrom: undefined, dataTo: undefined }] } })
    render(<App adapterFactory={() => new FakeChartAdapter()} />)

    expect(await screen.findByTestId('no-history')).toHaveTextContent(/No real NQ history/)
    expect(barRequestEnds()).toEqual([])
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

  it('never asks the public bars endpoint for a particular seed (DEC-038)', async () => {
    // The backend rejects a seed outright, but the client should not be written to send one: a
    // choosable seed is how a practice session's future would be read ahead of the replay.
    const adapter = new FakeChartAdapter()
    render(<App adapterFactory={() => adapter} />)
    await waitFor(() => expect(adapter.bars).toHaveLength(1))

    const barRequests = (globalThis.fetch as ReturnType<typeof vi.fn>).mock.calls
      .map(([url]) => String(url))
      .filter((url) => url.includes('/bars'))

    expect(barRequests.length).toBeGreaterThan(0)
    for (const url of barRequests) {
      expect(url).not.toMatch(/[?&]seed=/)
    }
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

  it('DEC-064: the account menu changes the password, and signs out of every device', async () => {
    mockApi({
      ...signedIn,
      '/api/auth/password': { body: { ...USER, displayName: 'Fabio' } },
      '/api/auth/logout-all': { status: 204, body: {} },
    })
    render(<App adapterFactory={() => new FakeChartAdapter()} />)
    await userEvent.click(await screen.findByRole('button', { name: 'Fabio' }))
    await userEvent.click(screen.getByRole('button', { name: 'Change password' }))
    await userEvent.type(screen.getByLabelText('Current password'), 'a-perfectly-fine-passphrase')
    await userEvent.type(screen.getByLabelText('New password'), 'blue lanterns over the harbour')
    await userEvent.click(screen.getByRole('button', { name: 'Change password' }))
    expect(await screen.findByRole('status')).toHaveTextContent('Password changed')
    const sent = (globalThis.fetch as ReturnType<typeof vi.fn>).mock.calls.find(([url]) =>
      String(url).endsWith('/api/auth/password'),
    )
    const init = sent?.[1] as RequestInit | undefined
    expect(init?.method).toBe('POST')
    expect(JSON.parse(String(init?.body))).toEqual({
      currentPassword: 'a-perfectly-fine-passphrase',
      newPassword: 'blue lanterns over the harbour',
    })

    await userEvent.click(screen.getByRole('button', { name: 'Back' }))
    await userEvent.click(screen.getByRole('button', { name: 'Sign out of every device' }))
    expect(await screen.findByRole('heading', { name: /sign in/i })).toBeInTheDocument()
    expect(requests('/api/auth/logout-all')).toHaveLength(1)
  })
})

describe('App — addresses and the shell (T36, DEC-078)', () => {
  beforeEach(() => mockApi(signedIn))

  const at = (path: string) => window.history.replaceState(null, '', path)
  const current = (name: string) => screen.getByRole('link', { name }).getAttribute('aria-current')

  it('opens every section at its own address, and marks it in the header', async () => {
    const screens = [
      { path: '/chart', nav: 'Chart', shows: () => screen.findByTestId('price-chart') },
      { path: '/learn', nav: 'Learn', shows: () => screen.findByRole('region', { name: 'Curriculum' }) },
      { path: '/drills', nav: 'Drills', shows: () => screen.findByRole('region', { name: 'Drills' }) },
      { path: '/practice', nav: 'Practice', shows: () => screen.findByRole('region', { name: 'New session' }) },
    ]
    for (const { path, nav, shows } of screens) {
      at(path)
      const { unmount } = render(<App adapterFactory={() => new FakeChartAdapter()} />)
      expect(await shows()).toBeInTheDocument()
      expect(current(nav)).toBe('page')
      unmount()
    }
  })

  it('an unknown address lands on Home; the header switches sections', async () => {
    at('/no-such-page')
    render(<App adapterFactory={() => new FakeChartAdapter()} />)
    expect(await screen.findByRole('heading', { name: /Good (morning|afternoon|evening), Fabio\./ })).toBeInTheDocument()
    expect(window.location.pathname).toBe('/')
    expect(current('Home')).toBe('page')

    // The Chart tab fills the window with a narrow gutter (T38); reading pages keep their margins.
    await userEvent.click(screen.getByRole('link', { name: 'Chart' }))
    expect(await screen.findByRole('heading', { name: 'NQ' })).toBeInTheDocument()
    expect(screen.getByRole('main')).toHaveClass('p-3')
    expect(current('Home')).toBeNull()
    await userEvent.click(screen.getByRole('link', { name: 'Learn' }))
    expect(window.location.pathname).toBe('/learn')
    expect(screen.getByRole('main')).toHaveClass('py-8')
    expect(current('Learn')).toBe('page')
    expect(current('Chart')).toBeNull()
  })

  it('signed out, any address leads to sign-in and back to that address after', async () => {
    mockApi({ ...signedIn, '/api/me': { status: 401, body: {} }, '/api/auth/refresh': { status: 401, body: {} } })
    at('/practice')
    render(<App adapterFactory={() => new FakeChartAdapter()} />)
    expect(await screen.findByRole('heading', { name: /sign in/i })).toBeInTheDocument()
    expect(window.location.pathname).toBe('/sign-in')

    mockApi({ ...signedIn, '/api/auth/login': { body: USER } })
    await userEvent.type(screen.getByLabelText(/email/i), 'fabio@example.com')
    await userEvent.type(screen.getByLabelText(/password/i), 'whatever-i-typed')
    await userEvent.click(screen.getByRole('button', { name: /^sign in$/i }))
    expect(await screen.findByRole('region', { name: 'New session' })).toBeInTheDocument()
    expect(window.location.pathname).toBe('/practice')
  })

  it('/sign-up is the registration form, with a way back to sign-in', async () => {
    mockApi(signedOut)
    at('/sign-up')
    render(<App />)
    expect(await screen.findByRole('heading', { name: 'Create an account' })).toBeInTheDocument()
    await userEvent.click(screen.getByRole('link', { name: 'Sign in instead' }))
    expect(screen.getByRole('heading', { name: 'Sign in' })).toBeInTheDocument()
  })

  it('a lesson address opens that lesson (T39)', async () => {
    at('/learn/ICT-20')
    render(<App adapterFactory={() => new FakeChartAdapter()} />)
    await waitFor(() => expect(requests('/lesson').length).toBeGreaterThan(0))
    expect(requests('/lesson')[0].pathname).toBe('/api/concepts/ICT-20/lesson')
    expect(current('Learn')).toBe('page')
  })

  it('a drill address opens that drill', async () => {
    const kind = (id: string, title: string) => ({ kind: id, mode: 'CLASSIFY', title, conceptId: 'ICT-06', detector: 'X', ruleVersion: 'x-1.0.0', instructions: '' })
    mockApi({ ...signedIn, '/api/drills/kinds': { body: [kind('FIRST_DRILL', 'First'), kind('SECOND_DRILL', 'Second')] } })
    at('/drills/SECOND_DRILL')
    render(<App adapterFactory={() => new FakeChartAdapter()} />)
    await screen.findByRole('option', { name: 'Second' })
    expect(screen.getByLabelText('Drill')).toHaveValue('SECOND_DRILL')
    expect(current('Drills')).toBe('page')
  })
})

describe('App — the guide and the disclaimer (T29, T36)', () => {
  beforeEach(() => mockApi(signedIn))

  it('greets a sign-in with Home, not a pop-up; the guide is at /help, from the account menu', async () => {
    render(<App adapterFactory={() => new FakeChartAdapter()} />)
    await screen.findByRole('heading', { name: /Fabio\./ })
    expect(screen.queryByRole('heading', { name: 'How Tradeez works' })).not.toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Fabio' }))
    await userEvent.click(screen.getByRole('button', { name: 'How it works' }))
    expect(window.location.pathname).toBe('/help')
    expect(screen.getByRole('heading', { name: 'How Tradeez works' })).toBeInTheDocument()
    expect(screen.getByText(/nothing you do here reaches a market/)).toBeInTheDocument()
    expect(screen.getByTestId('guide-not')).toHaveTextContent("taught as ICT's claims")
  })

  it('starts with Learn, or opens any step it names', async () => {
    window.history.replaceState(null, '', '/help')
    const { unmount } = render(<App adapterFactory={() => new FakeChartAdapter()} />)
    await userEvent.click(await screen.findByRole('link', { name: /Start with Learn/ }))
    expect(screen.getByRole('link', { name: 'Learn' })).toHaveAttribute('aria-current', 'page')
    unmount()

    window.history.replaceState(null, '', '/help')
    render(<App adapterFactory={() => new FakeChartAdapter()} />)
    const steps = await screen.findByRole('list')
    await userEvent.click(within(steps).getByRole('link', { name: 'Practice' }))
    expect(window.location.pathname).toBe('/practice')
  })

  it("states DEC-015's three points on every screen, and the full wording behind Read more", async () => {
    render(<App adapterFactory={() => new FakeChartAdapter()} />)
    const footer = await screen.findByRole('contentinfo')
    expect(footer).toHaveTextContent('not financial advice')
    expect(footer).toHaveTextContent('simulated on past data')
    expect(footer).toHaveTextContent('losses can exceed the money deposited')
    expect(footer).not.toHaveTextContent('never reach a market')

    await userEvent.click(within(footer).getByRole('button', { name: 'Read more' }))
    expect(footer).toHaveTextContent('never reach a market; simulated results do not guarantee live results')
    await userEvent.click(within(footer).getByRole('button', { name: 'Less' }))
    expect(footer).not.toHaveTextContent('never reach a market')
  })
})
