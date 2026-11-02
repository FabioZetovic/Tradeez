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

describe('useDrawings — BUG-096: a commit straight after a change saves that change', () => {
  // The toolbar's undo and delete dispatch and commit in one handler, before React renders. The commit read the
  // drawings as they were before: an undone or deleted drawing stayed on the server, and came back on reload.
  it('deletes on the server a drawing removed in the same handler', async () => {
    vi.mocked(listDrawings).mockResolvedValue([line('gone')])
    const { result } = renderHook(() => useDrawings('DEMO-NQ'))
    await waitFor(() => expect(result.current.state.drawings).toHaveLength(1))

    await act(async () => {
      result.current.dispatch({ type: 'remove', id: 'gone' })
      await result.current.commit()
    })
    expect(deleteDrawing).toHaveBeenCalledWith('DEMO-NQ', 'gone')
  })

  it('undoes on the server what undo undid, step by step', async () => {
    const { result } = renderHook(() => useDrawings('DEMO-NQ'))
    await waitFor(() => expect(listDrawings).toHaveBeenCalled())
    for (const id of ['first', 'second']) {
      await act(async () => {
        result.current.dispatch({ type: 'add', drawing: line(id) })
        await result.current.commit()
      })
    }
    expect(vi.mocked(saveDrawing).mock.calls.map(([, d]) => d.id)).toEqual(['first', 'second'])

    await act(async () => {
      result.current.dispatch({ type: 'undo' })
      await result.current.commit()
    })
    expect(vi.mocked(deleteDrawing).mock.calls).toEqual([['DEMO-NQ', 'second']])
    await act(async () => {
      result.current.dispatch({ type: 'undo' })
      await result.current.commit()
    })
    expect(vi.mocked(deleteDrawing).mock.calls).toEqual([['DEMO-NQ', 'second'], ['DEMO-NQ', 'first']])
  })
})

/** A promise and the hands to settle it, for answers that arrive when a test says. */
function later<T>() {
  let resolve!: (value: T) => void
  const promise = new Promise<T>((r) => (resolve = r))
  return { promise, resolve }
}

describe("useDrawings — Codex's audit: slow answers", () => {
  it('BUG-149 a later drag is saved after an earlier one, so the last position is what the server keeps', async () => {
    vi.mocked(listDrawings).mockResolvedValue([line('l', 80_000)])
    const { result } = renderHook(() => useDrawings('DEMO-NQ'))
    await waitFor(() => expect(result.current.state.drawings).toHaveLength(1))
    const toA = later<never>()
    vi.mocked(saveDrawing).mockReturnValueOnce(toA.promise)

    let first!: Promise<boolean>
    let second!: Promise<boolean>
    act(() => {
      result.current.dispatch({ type: 'update', drawing: line('l', 80_100) })
      first = result.current.commit()
    })
    await waitFor(() => expect(saveDrawing).toHaveBeenCalledTimes(1))
    act(() => {
      result.current.dispatch({ type: 'update', drawing: line('l', 80_200) })
      second = result.current.commit()
    })
    // Only A is out; B waits for it.
    await Promise.resolve()
    expect(vi.mocked(saveDrawing).mock.calls.map(([, d]) => d.anchors[0].priceTicks)).toEqual([80_100])
    await act(async () => {
      toA.resolve({} as never)
      await first
      await second
    })
    expect(vi.mocked(saveDrawing).mock.calls.map(([, d]) => d.anchors[0].priceTicks)).toEqual([80_100, 80_200])
  })

  it('BUG-150 a drawing deleted while its first save is out is deleted on the server once that save lands', async () => {
    const { result } = renderHook(() => useDrawings('DEMO-NQ'))
    await waitFor(() => expect(listDrawings).toHaveBeenCalled())
    const created = later<never>()
    vi.mocked(saveDrawing).mockReturnValueOnce(created.promise)
    let save!: Promise<boolean>
    let commits!: Promise<boolean[]>
    act(() => {
      result.current.dispatch({ type: 'add', drawing: line('brief') })
      save = result.current.commit()
    })
    await waitFor(() => expect(saveDrawing).toHaveBeenCalledTimes(1))
    act(() => {
      result.current.dispatch({ type: 'remove', id: 'brief' })
      commits = Promise.all([save, result.current.commit()])
    })
    await act(async () => {
      created.resolve({} as never)
      await commits
    })
    expect(deleteDrawing).toHaveBeenCalledWith('DEMO-NQ', 'brief')
  })

  it('BUG-151 a line drawn while the saved drawings load is still there after they arrive, and is saved', async () => {
    const load = later<Drawing[]>()
    vi.mocked(listDrawings).mockReturnValueOnce(load.promise)
    const { result } = renderHook(() => useDrawings('DEMO-NQ'))
    act(() => result.current.dispatch({ type: 'add', drawing: line('new') }))
    await act(async () => {
      load.resolve([line('saved')])
      await load.promise
    })
    expect(result.current.state.drawings.map((d) => d.id).sort()).toEqual(['new', 'saved'])
    await waitFor(() => expect(vi.mocked(saveDrawing).mock.calls.map(([, d]) => d.id)).toContain('new'))
  })

  it("BUG-152 switching market drops the old market's drawings at once, so none can be sent to the new one", async () => {
    vi.mocked(listDrawings).mockResolvedValueOnce([line('nq-line')])
    const { result, rerender } = renderHook(({ symbol }) => useDrawings(symbol), { initialProps: { symbol: 'NQ' } })
    await waitFor(() => expect(result.current.state.drawings).toHaveLength(1))
    vi.mocked(listDrawings).mockReturnValueOnce(later<Drawing[]>().promise)
    rerender({ symbol: 'ES' })
    expect(result.current.state.drawings).toEqual([])
    await act(async () => {
      await result.current.commit()
    })
    expect(deleteDrawing).not.toHaveBeenCalled()
    expect(saveDrawing).not.toHaveBeenCalled()
  })
})
