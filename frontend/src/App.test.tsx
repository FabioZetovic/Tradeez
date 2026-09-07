import { render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import App from './App'

afterEach(() => {
  vi.restoreAllMocks()
})

function mockHealth(body: unknown, ok = true) {
  vi.stubGlobal(
    'fetch',
    vi.fn().mockResolvedValue({
      ok,
      status: ok ? 200 : 503,
      statusText: ok ? 'OK' : 'Service Unavailable',
      headers: new Headers({ 'content-type': 'application/json' }),
      json: async () => body,
    }),
  )
}

describe('App', () => {
  it('renders backend and database status once health resolves', async () => {
    mockHealth({
      status: 'UP',
      database: 'UP',
      timestamp: '2026-08-27T12:00:00Z',
      application: 'tradeez-backend',
    })

    render(<App />)

    expect(await screen.findByTestId('backend-status')).toHaveTextContent('UP')
    expect(screen.getByTestId('database-status')).toHaveTextContent('UP')
  })

  it('always shows the simulated-data banner (DEC-033)', () => {
    mockHealth({ status: 'UP', database: 'UP', timestamp: '', application: '' })
    render(<App />)
    expect(screen.getByText(/SIMULATED DATA — NOT MARKET HISTORY/i)).toBeInTheDocument()
  })

  it('surfaces an error instead of failing silently when the backend is down', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('connection refused')))
    render(<App />)
    expect(await screen.findByRole('alert')).toHaveTextContent(/connection refused/i)
  })
})
