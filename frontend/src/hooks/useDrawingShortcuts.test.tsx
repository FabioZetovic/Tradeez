import { fireEvent, render, renderHook } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { useDrawingShortcuts } from './useDrawingShortcuts'

/** The chart's keyboard: Esc, Delete or Backspace, Ctrl/Cmd+Z, Ctrl/Cmd+Shift+Z — and never while typing. */
function setup(selectedId: string | null = 'd1') {
  const options = {
    selectedId,
    dispatch: vi.fn(),
    onCancelTool: vi.fn(),
    onDeleteSelected: vi.fn(),
    onHistoryChange: vi.fn(),
  }
  const hook = renderHook((props: typeof options) => useDrawingShortcuts(props), { initialProps: options })
  return { ...options, hook }
}

const press = (key: string, extra: Partial<KeyboardEventInit> = {}, target: Element | Window = window) =>
  fireEvent.keyDown(target, { key, ...extra })

describe('drawing shortcuts', () => {
  it('Escape puts the tool down and clears the selection', () => {
    const s = setup()
    press('Escape')
    expect(s.onCancelTool).toHaveBeenCalledOnce()
    expect(s.dispatch).toHaveBeenCalledWith({ type: 'select', id: null })
  })

  it('Delete and Backspace remove the selected drawing, and only when one is selected', () => {
    const s = setup()
    press('Delete')
    press('Backspace')
    expect(s.onDeleteSelected).toHaveBeenCalledTimes(2)

    const none = setup(null)
    press('Delete')
    expect(none.onDeleteSelected).not.toHaveBeenCalled()
  })

  it('Ctrl+Z undoes, Ctrl+Shift+Z redoes, Cmd works as Ctrl, and each is told to the caller', () => {
    const s = setup()
    press('z', { ctrlKey: true })
    press('Z', { ctrlKey: true, shiftKey: true })
    press('z', { metaKey: true })
    expect(s.dispatch.mock.calls.map((c) => c[0])).toEqual([{ type: 'undo' }, { type: 'redo' }, { type: 'undo' }])
    expect(s.onHistoryChange).toHaveBeenCalledTimes(3)
    press('z') // a plain z is just a letter
    expect(s.dispatch).toHaveBeenCalledTimes(3)
  })

  it('leaves typing alone: Backspace in a price box deletes a character, not the drawing', () => {
    const s = setup()
    const { getByRole, getByLabelText } = render(
      <div>
        <input aria-label="Stop" />
        <textarea aria-label="Label" />
        <select aria-label="Drill">
          <option>one</option>
        </select>
      </div>,
    )
    for (const field of [getByLabelText('Stop'), getByRole('textbox', { name: 'Label' }), getByLabelText('Drill')]) {
      press('Backspace', {}, field)
      press('z', { ctrlKey: true }, field)
      press('Escape', {}, field)
    }
    expect(s.onDeleteSelected).not.toHaveBeenCalled()
    expect(s.dispatch).not.toHaveBeenCalled()
    expect(s.onCancelTool).not.toHaveBeenCalled()
  })

  it('stops listening once the chart is gone', () => {
    const s = setup()
    s.hook.unmount()
    press('Delete')
    expect(s.onDeleteSelected).not.toHaveBeenCalled()
  })
})
