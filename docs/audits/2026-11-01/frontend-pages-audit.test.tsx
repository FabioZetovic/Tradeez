import { cleanup, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { PracticeView } from '@/components/practice/PracticeView'
import { ChartPage } from '@/components/chart/ChartPage'
import { onPhone } from '@/test/phone'
import type { BarSeries, Instrument } from '@/lib/api/marketData'

const probes = vi.hoisted(() => ({ practice: vi.fn(), chart: vi.fn() }))
vi.mock('@/components/practice/PracticeSession', () => ({ PracticeSession: (props: unknown) => { probes.practice(props); return <div>Audit session</div> } }))
vi.mock('@/components/PriceChart', () => ({ PriceChart: (props: unknown) => { probes.chart(props); return <div>Audit chart</div> } }))
vi.mock('@/components/DrawingOverlay', () => ({ DrawingOverlay: () => null }))

beforeEach(() => { probes.practice.mockClear(); probes.chart.mockClear(); localStorage.clear() })
afterEach(() => { cleanup(); vi.unstubAllGlobals() })

function response(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}
const settings = { riskLimitCents: 10000, feesCents: 450, marketSlippageTicks: 1, stopSlippageTicks: 1, limitThroughTicks: 1, fillModel: 'mkt-1t/slip-1t/thru-1t/comm-450c' }

it('opening a practice session directly never loads the saved risk limit or execution costs', async () => {
  const fetchMock = vi.fn(async () => response(settings))
  vi.stubGlobal('fetch', fetchMock)
  render(<MemoryRouter initialEntries={['/practice/audit-session']}><Routes><Route path="/practice/:sessionId" element={<PracticeView />} /></Routes></MemoryRouter>)
  await waitFor(() => expect(probes.practice).toHaveBeenCalled())
  const props = probes.practice.mock.calls.at(-1)![0] as { riskLimitCents?: number; costs?: unknown }
  expect(props.riskLimitCents).toBeUndefined()
  expect(props.costs).toBeUndefined()
  expect(fetchMock).not.toHaveBeenCalled()
})

it('the phone journal asks past the API limit and its filtered view cannot load older matches', async () => {
  onPhone()
  const trades = Array.from({ length: 190 }, (_, i) => ({ sessionId: 'audit-session', number: i + 1, at: 1710338400, timeBasis: 'UTC', instrument: 'NQ', tickSize: 0.25, direction: 'LONG', entryTicks: 80000, outcome: 'TARGET', r: 1, netPnlCents: 50000 }))
  const calls: string[] = []
  vi.stubGlobal('fetch', vi.fn(async (path: string) => {
    calls.push(String(path))
    if (String(path).startsWith('/api/practice/trades')) return response(trades)
    if (path === '/api/practice/sessions') return response([])
    if (path === '/api/practice/statistics') return response({ overall: { trades: 250, totalR: 250, wins: 250, losses: 0, netPnlCents: 12500000, winRate: 1 } })
    if (path === '/api/practice/calibration') return response({ summary: [], buckets: [] })
    if (path === '/api/practice/settings') return response(settings)
    return response({})
  }))
  render(<MemoryRouter initialEntries={['/practice']}><Routes><Route path="/practice" element={<PracticeView />} /></Routes></MemoryRouter>)
  await waitFor(() => expect(screen.getAllByTestId('journal-row')).toHaveLength(20))
  await userEvent.click(screen.getByRole('button', { name: 'Losers' }))
  expect(screen.getByText('No trades of that kind yet.')).toBeInTheDocument()
  expect(screen.queryByRole('button', { name: /Load.*more/ })).not.toBeInTheDocument()
  await userEvent.click(screen.getByRole('button', { name: 'All', exact: true }))
  for (let n = 0; n < 9; n++) await userEvent.click(screen.getByRole('button', { name: /Load.*more/ }))
  expect(calls.some(path => path.includes('limit=210'))).toBe(true)
})

it('switching markets keeps the previous candles under the new market heading until its response arrives', async () => {
  const end = 1750000000
  const instruments: Instrument[] = ['NQ', 'ES'].map(symbol => ({ symbol, displayName: symbol, exchange: 'CME', currency: 'USD', tickSize: 0.25, tickValueCents: symbol === 'NQ' ? 500 : 1250, dataClass: 'IMPORTED_PRIVATE', exchangeTimezone: 'America/Chicago', dataFrom: end - 2000000, dataTo: end }))
  const series: BarSeries = { instrument: 'NQ', timeframe: '5m', dataClass: 'IMPORTED_PRIVATE', source: 'audit', disclaimer: null, hidden: [], bars: [{ time: end - 300, open: 80000, high: 80000, low: 80000, close: 80000, volume: 1 }] }
  vi.stubGlobal('fetch', vi.fn(async (path: string) => {
    const url = String(path)
    if (url === '/api/market/instruments') return response(instruments)
    if (url.includes('/instruments/NQ/bars')) return response(series)
    if (url.includes('/instruments/ES/bars')) return new Promise<Response>(() => {})
    if (url.includes('/drawings')) return response([])
    if (url === '/api/concepts') return response({ concepts: [] })
    if (url.includes('/clock')) return response({ windows: [], lines: [], rules: [], nextChange: end + 300 })
    return response({})
  }))
  render(<ChartPage />)
  await screen.findByText('Audit chart')
  await userEvent.selectOptions(screen.getByRole('combobox', { name: 'Market' }), 'ES')
  expect(screen.getByRole('heading', { name: 'ES' })).toBeInTheDocument()
  expect(screen.getByTestId('last-price')).toHaveTextContent('20000.00')
  const props = probes.chart.mock.calls.at(-1)![0] as { bars: typeof series.bars }
  expect(props.bars[0].close).toBe(80000)
})
