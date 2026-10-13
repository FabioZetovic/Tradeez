import type { SnapMode } from '@/lib/drawing/snap'
import type { DrawingKind } from '@/lib/drawing/types'
import { Button } from './ui'

const Divider = () => <span className="mx-1 my-0.5 h-px bg-line" />

interface DrawingToolbarProps {
  activeTool: DrawingKind | null
  onSelectTool: (tool: DrawingKind | null) => void
  snapMode: SnapMode
  onToggleSnap: () => void
  canUndo: boolean
  canRedo: boolean
  onUndo: () => void
  onRedo: () => void
  hasSelection: boolean
  onDelete: () => void
}

/**
 * The five tools of the DEC-004 drawing proof — and no more.
 *
 * The scope limit is the point. DEC-004 names this layer as the project's largest schedule risk,
 * so the plan is deliberately four shapes plus the shared machinery (select, drag, reshape, snap,
 * undo, persist), then a decision about whether to keep building by hand or adopt KLineChart. A
 * half-finished catalogue of sixty-eight tools would be worth less than a finished Practice loop.
 */
const TOOLS: Array<{ kind: DrawingKind; label: string; hint: string }> = [
  { kind: 'horizontal-line', label: '─', hint: 'Horizontal line — mark a level' },
  { kind: 'trend-line', label: '╱', hint: 'Trend line' },
  { kind: 'rectangle', label: '▭', hint: 'Rectangle — order blocks and fair value gaps' },
  { kind: 'position', label: '⇅', hint: 'Position — risk/reward, entry, stop and target' },
]

export function DrawingToolbar({
  activeTool,
  onSelectTool,
  snapMode,
  onToggleSnap,
  canUndo,
  canRedo,
  onUndo,
  onRedo,
  hasSelection,
  onDelete,
}: DrawingToolbarProps) {
  return (
    <div
      role="toolbar"
      aria-label="Drawing tools"
      className="flex flex-col gap-1 rounded-lg border border-line bg-surface p-1"
    >
      <Button
        variant="ghost"
        size="icon"
        active={activeTool === null}
        aria-pressed={activeTool === null}
        title="Select and move (Esc)"
        onClick={() => onSelectTool(null)}
      >
        ⌖
      </Button>

      <Divider />

      {TOOLS.map((tool) => (
        <Button
          key={tool.kind}
          variant="ghost"
          size="icon"
          active={activeTool === tool.kind}
          aria-pressed={activeTool === tool.kind}
          aria-label={tool.hint}
          title={tool.hint}
          onClick={() => onSelectTool(activeTool === tool.kind ? null : tool.kind)}
        >
          {tool.label}
        </Button>
      ))}

      <Divider />

      <Button
        variant="ghost"
        size="icon"
        active={snapMode === 'magnet'}
        aria-pressed={snapMode === 'magnet'}
        title="Magnet — snap to bar highs, lows, opens and closes"
        onClick={onToggleSnap}
      >
        🧲
      </Button>

      <Divider />

      <Button variant="ghost" size="icon" disabled={!canUndo} title="Undo (Ctrl+Z)" onClick={onUndo}>
        ↶
      </Button>
      <Button variant="ghost" size="icon" disabled={!canRedo} title="Redo (Ctrl+Shift+Z)" onClick={onRedo}>
        ↷
      </Button>
      <Button variant="ghost" size="icon" disabled={!hasSelection} title="Delete selected (Del)" onClick={onDelete}>
        🗑
      </Button>
    </div>
  )
}
