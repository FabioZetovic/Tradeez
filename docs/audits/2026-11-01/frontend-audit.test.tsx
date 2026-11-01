import { StrictMode } from 'react'
import { act, cleanup, render, renderHook, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { useDrawings } from '@/hooks/useDrawings'
import { useSession } from '@/hooks/useSession'
import { usePracticeSession } from '@/hooks/usePracticeSession'
import type { SessionResponse } from '@/lib/api/practice'
import { VerifyEmail } from '@/components/EmailLinks'
import { Sheet } from '@/components/ui'
import { deleteDrawing, listDrawings, saveDrawing } from '@/lib/api/drawings'
import type { Drawing } from '@/lib/drawing/types'

// Forensic probes: passing confirms the behavior described in the audit, not secure behavior.
vi.mock('@/lib/api/drawings', () => ({ listDrawings: vi.fn(), saveDrawing: vi.fn(), deleteDrawing: vi.fn() }))

const line = (priceTicks = 80000): Drawing => ({
  id: 'audit-line', kind: 'horizontal-line', anchors: [{ time: 1710338400, priceTicks }], style: { color: '#58a6ff', lineWidth: 1 },
})
function deferred<T>() {
  let resolve!: (value: T) => void
  const promise = new Promise<T>((done) => { resolve = done })
  return { promise, resolve }
}

beforeEach(() => {
  window.history.replaceState(null, '', '/')
  vi.mocked(listDrawings).mockResolvedValue([])
  vi.mocked(saveDrawing).mockResolvedValue({} as never)
  vi.mocked(deleteDrawing).mockResolvedValue(undefined)
})
afterEach(() => { cleanup(); vi.clearAllMocks(); vi.unstubAllGlobals() })

it('a new drawing deleted before its first save finishes remains saved with no warning', async () => {
  const save = deferred<Drawing>()
  vi.mocked(saveDrawing).mockReturnValueOnce(save.promise)
  const { result } = renderHook(() => useDrawings('NQ'))
  await act(async () => {})
  let pending!: Promise<boolean>
  act(() => { result.current.dispatch({ type: 'add', drawing: line() }); pending = result.current.commit() })
  await act(async () => { result.current.dispatch({ type: 'remove', id: line().id }); await result.current.commit() })
  expect(deleteDrawing).not.toHaveBeenCalled()
  await act(async () => { save.resolve(line()); await pending })
  expect(result.current.state.drawings).toEqual([])
  expect(result.current.syncError).toBeNull()
  expect(deleteDrawing).not.toHaveBeenCalled()
})

it('overlapping saves can finish in reverse order and silently persist the older position', async () => {
  const first = deferred<Drawing>(), second = deferred<Drawing>()
  let stored = line()
  vi.mocked(listDrawings).mockResolvedValueOnce([line()])
  vi.mocked(saveDrawing)
    .mockImplementationOnce(async () => { const value = await first.promise; stored = value; return value })
    .mockImplementationOnce(async () => { const value = await second.promise; stored = value; return value })
  const { result } = renderHook(() => useDrawings('NQ'))
  await waitFor(() => expect(result.current.state.drawings).toHaveLength(1))
  let one!: Promise<boolean>, two!: Promise<boolean>
  act(() => { result.current.dispatch({ type: 'update', drawing: line(81000) }); one = result.current.commit() })
  act(() => { result.current.dispatch({ type: 'update', drawing: line(82000) }); two = result.current.commit() })
  await act(async () => { second.resolve(line(82000)); await two })
  await act(async () => { first.resolve(line(81000)); await one })
  expect(result.current.state.drawings[0].anchors[0].priceTicks).toBe(82000)
  expect(stored.anchors[0].priceTicks).toBe(81000)
  expect(result.current.syncError).toBeNull()
})

it('a delayed initial drawings load overwrites annotations made while it is loading', async () => {
  const loaded = deferred<Drawing[]>()
  vi.mocked(listDrawings).mockReturnValueOnce(loaded.promise)
  const { result } = renderHook(() => useDrawings('NQ'))
  act(() => { result.current.dispatch({ type: 'add', drawing: line() }) })
  expect(result.current.state.drawings).toHaveLength(1)
  await act(async () => { loaded.resolve([]) })
  expect(result.current.state.drawings).toEqual([])
  expect(result.current.syncError).toBeNull()
})

it('switching instruments exposes the old drawing to deletion through the new instrument URL', async () => {
  const loaded = deferred<Drawing[]>()
  vi.mocked(listDrawings).mockResolvedValueOnce([line()]).mockReturnValueOnce(loaded.promise)
  const { result, rerender } = renderHook(({ symbol }) => useDrawings(symbol), { initialProps: { symbol: 'NQ' } })
  await waitFor(() => expect(result.current.state.drawings).toHaveLength(1))
  rerender({ symbol: 'ES' })
  expect(result.current.state.drawings).toHaveLength(1)
  await act(async () => { result.current.dispatch({ type: 'remove', id: line().id }); await result.current.commit() })
  expect(deleteDrawing).toHaveBeenCalledWith('ES', line().id)
  await act(async () => { loaded.resolve([]) })
})

it('a failed sign-out shows signed out, but remounting immediately restores the same server session', async () => {
  const user = { id: 'audit-user', email: 'audit@example.test', displayName: 'Audit', createdAt: '2026-11-01T00:00:00Z' }
  vi.stubGlobal('fetch', vi.fn(async (path: string) => {
    if (path === '/api/auth/logout') throw new TypeError('offline')
    return new Response(JSON.stringify(user), { headers: { 'Content-Type': 'application/json' } })
  }))
  const first = renderHook(() => useSession())
  await waitFor(() => expect(first.result.current.state.status).toBe('signedIn'))
  await act(async () => { await first.result.current.logout().catch(() => {}) })
  expect(first.result.current.state.status).toBe('signedOut')
  first.unmount()
  const second = renderHook(() => useSession())
  await waitFor(() => expect(second.result.current.state.status).toBe('signedIn'))
})

it('control: StrictMode preserves the email token and submits it exactly once', async () => {
  const user = { id: 'audit-user', email: 'audit@example.test', displayName: 'Audit', createdAt: '2026-11-01T00:00:00Z' }
  const fetchMock = vi.fn(async () => new Response(JSON.stringify(user), { headers: { 'Content-Type': 'application/json' } }))
  vi.stubGlobal('fetch', fetchMock)
  window.history.replaceState(null, '', '/verify-email#audit-token')
  const signedIn = vi.fn()
  render(<StrictMode><MemoryRouter><VerifyEmail onSignedIn={signedIn} /></MemoryRouter></StrictMode>)
  await waitFor(() => expect(signedIn).toHaveBeenCalledWith(user))
  expect(fetchMock).toHaveBeenCalledTimes(1)
})

it('a practice page remounted with the retained creation response never fetches its current server state', async () => {
  const initial = { state: { id: 'audit-session', status: 'ANALYSING', cursor: 100, timeframe: '5m', score: { closedTrades: 0 } }, bars: [] } as unknown as SessionResponse
  const fetchMock = vi.fn(async () => new Response('{}', { headers: { 'Content-Type': 'application/json' } }))
  vi.stubGlobal('fetch', fetchMock)
  const first = renderHook(() => usePracticeSession('audit-session', initial))
  await act(async () => {})
  first.unmount()
  // Browser history retains location.state.created through refresh/back; this is the same initial prop on remount.
  const second = renderHook(() => usePracticeSession('audit-session', initial))
  await act(async () => {})
  expect(fetchMock).not.toHaveBeenCalled()
  expect(second.result.current.state?.cursor).toBe(100)
})

it('the modal sheet lets Tab escape to controls behind its scrim', async () => {
  render(<><button>Outside the sheet</button><Sheet open onClose={() => {}} label="Audit sheet"><button>Inside the sheet</button></Sheet></>)
  expect(screen.getByRole('dialog')).toHaveFocus()
  await userEvent.tab()
  expect(screen.getByRole('button', { name: 'Inside the sheet' })).toHaveFocus()
  await userEvent.tab()
  // jsdom lands on body on wrap; the following tab reaches the underlying page.
  if (document.activeElement === document.body) await userEvent.tab()
  expect(screen.getByRole('button', { name: 'Outside the sheet' })).toHaveFocus()
})
