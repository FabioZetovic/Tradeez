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
 * The four shapes of the DEC-004 drawing proof, and the three ICT tools DEC-048 added — no more.
 *
 * The scope limit is the point. DEC-004 names this layer as the project's largest schedule risk, and
 * DEC-037 kept it at four shapes until a curriculum needed more. ICT's needed three: its Fibonacci
 * levels (ICT-42, ICT-43), the consequent encroachment of every zone (ICT-21), and words on a chart.
 */
const TOOLS: Array<{ kind: DrawingKind; label: string; hint: string }> = [
  { kind: 'horizontal-line', label: '─', hint: 'Horizontal line — mark a level' },
  { kind: 'trend-line', label: '╱', hint: 'Trend line' },
  { kind: 'rectangle', label: '▭', hint: 'Rectangle' },
  { kind: 'position', label: '⇅', hint: 'Position — risk/reward, entry, stop and target' },
  {
    kind: 'fibonacci',
    label: 'Φ',
    hint: 'ICT Fibonacci — drag from the start of a leg to its end: OTE 0.62–0.79, targets −0.27 to −2',
  },
  {
    kind: 'zone',
    label: '▤',
    hint: 'Zone — a gap or a block, with its 50% (consequent encroachment). Double-click to name it',
  },
  { kind: 'label', label: 'T', hint: 'Label — a note on the chart. Double-click to edit' },
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
