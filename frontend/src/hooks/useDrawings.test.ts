import { act, renderHook, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { useDrawings } from './useDrawings'
import { deleteDrawing, listDrawings, saveDrawing } from '@/lib/api/drawings'
import { DEFAULT_STYLES, type Drawing } from '@/lib/drawing/types'

vi.mock('@/lib/api/drawings', () => ({
  listDrawings: vi.fn(),
  saveDrawing: vi.fn(),
  deleteDrawing: vi.fn(),
}))

const line = (id: string, priceTicks = 80_000): Drawing => ({
  id,
  kind: 'horizontal-line',
  anchors: [{ time: 1_710_338_400, priceTicks }],
  style: DEFAULT_STYLES['horizontal-line'],
})

beforeEach(() => {
  vi.mocked(listDrawings).mockResolvedValue([])
  vi.mocked(saveDrawing).mockResolvedValue({} as never)
  vi.mocked(deleteDrawing).mockResolvedValue(undefined)
})

afterEach(() => vi.clearAllMocks())

describe('useDrawings — BUG-023: failures are reported, not swallowed', () => {
  it('says so when saved drawings could not be loaded', async () => {
    vi.mocked(listDrawings).mockRejectedValue(new Error('offline'))
    const { result } = renderHook(() => useDrawings('DEMO-NQ'))

    await waitFor(() => expect(result.current.syncError).toMatch(/could not be loaded/))
    // Still usable: an empty chart the user can draw on.
    expect(result.current.state.drawings).toEqual([])
  })

  it('reports a failed save, and clears it once a retry succeeds', async () => {
    const { result } = renderHook(() => useDrawings('DEMO-NQ'))
    await waitFor(() => expect(listDrawings).toHaveBeenCalled())

    vi.mocked(saveDrawing).mockRejectedValueOnce(new Error('503'))
    act(() => result.current.dispatch({ type: 'add', drawing: line('a') }))

    let ok: boolean | undefined
    await act(async () => {
      ok = await result.current.commit()
    })
    expect(ok).toBe(false)
    expect(result.current.syncError).toMatch(/could not be saved/)

    // The failed shape was not marked as persisted, so the next commit retries it.
    await act(async () => {
      ok = await result.current.commit()
    })
    expect(ok).toBe(true)
    expect(saveDrawing).toHaveBeenCalledTimes(2)
    expect(result.current.syncError).toBeNull()
  })

  it('reports a failed delete too', async () => {
    vi.mocked(listDrawings).mockResolvedValue([line('gone')])
    const { result } = renderHook(() => useDrawings('DEMO-NQ'))
    await waitFor(() => expect(result.current.state.drawings).toHaveLength(1))

    vi.mocked(deleteDrawing).mockRejectedValueOnce(new Error('503'))
    act(() => result.current.dispatch({ type: 'remove', id: 'gone' }))
    await act(async () => {
      await result.current.commit()
    })
    expect(result.current.syncError).toMatch(/could not be saved/)
  })

  it('retry after a failed load loads again', async () => {
    vi.mocked(listDrawings).mockRejectedValueOnce(new Error('offline'))
    const { result } = renderHook(() => useDrawings('DEMO-NQ'))
    await waitFor(() => expect(result.current.syncError).toMatch(/could not be loaded/))

    vi.mocked(listDrawings).mockResolvedValueOnce([line('back')])
    await act(async () => {
      await result.current.retry()
    })

    await waitFor(() => expect(result.current.state.drawings.map((d) => d.id)).toEqual(['back']))
    expect(result.current.syncError).toBeNull()
  })

  it('retry saves unsaved work before reloading, and does not reload over it if the save fails', async () => {
    vi.mocked(listDrawings).mockRejectedValueOnce(new Error('offline'))
    const { result } = renderHook(() => useDrawings('DEMO-NQ'))
    await waitFor(() => expect(result.current.syncError).toMatch(/could not be loaded/))

    vi.mocked(saveDrawing).mockRejectedValue(new Error('503'))
    act(() => result.current.dispatch({ type: 'add', drawing: line('unsaved') }))
    await act(async () => {
      await result.current.commit()
    })
    const loadsBefore = vi.mocked(listDrawings).mock.calls.length

    await act(async () => {
      await result.current.retry()
    })

    // The save failed again, so no reload: it would have replaced the unsaved drawing.
    expect(vi.mocked(listDrawings).mock.calls.length).toBe(loadsBefore)
    expect(result.current.state.drawings.map((d) => d.id)).toEqual(['unsaved'])
  })

  it("does not show one chart's failure on another after switching instrument", async () => {
    vi.mocked(listDrawings).mockRejectedValueOnce(new Error('offline'))
    const { result, rerender } = renderHook(({ symbol }) => useDrawings(symbol), {
      initialProps: { symbol: 'DEMO-NQ' },
    })
    await waitFor(() => expect(result.current.syncError).toMatch(/could not be loaded/))

    // Hidden immediately, before the other instrument's drawings have even arrived.
    rerender({ symbol: 'DEMO-MNQ' })
    expect(result.current.syncError).toBeNull()
  })

  it('does not let a later successful save hide the fact that loading failed', async () => {
    vi.mocked(listDrawings).mockRejectedValue(new Error('offline'))
    const { result } = renderHook(() => useDrawings('DEMO-NQ'))
    await waitFor(() => expect(result.current.syncError).toMatch(/could not be loaded/))

    act(() => result.current.dispatch({ type: 'add', drawing: line('b') }))
    await act(async () => {
      await result.current.commit()
    })
    expect(result.current.syncError).toMatch(/could not be loaded/)
  })
})
