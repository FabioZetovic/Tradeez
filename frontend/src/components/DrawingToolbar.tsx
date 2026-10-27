import type { ReactNode } from 'react'
import {
  ArrowUpDown,
  Magnet,
  Minus,
  MousePointer2,
  Redo2,
  Rows4,
  Slash,
  Square,
  SquareSplitVertical,
  Trash2,
  Type,
  Undo2,
} from 'lucide-react'
import type { SnapMode } from '@/lib/drawing/snap'
import type { DrawingKind } from '@/lib/drawing/types'
import { cx } from '@/lib/styles'

/** Across the bar: upright between tools in a row (phones, T46), flat between tools in a column. */
const Divider = () => <span aria-hidden className="my-1.5 w-px shrink-0 bg-line lg:mx-1.5 lg:my-1 lg:h-px lg:w-auto" />

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
const TOOLS: Array<{ kind: DrawingKind; icon: ReactNode; hint: string }> = [
  { kind: 'horizontal-line', icon: <Minus />, hint: 'Horizontal line — mark a level' },
  { kind: 'trend-line', icon: <Slash />, hint: 'Trend line' },
  { kind: 'rectangle', icon: <Square />, hint: 'Rectangle' },
  { kind: 'position', icon: <ArrowUpDown />, hint: 'Position — risk/reward, entry, stop and target' },
  {
    kind: 'fibonacci',
    icon: <Rows4 />,
    hint: 'ICT Fibonacci — drag from the start of a leg to its end: OTE 0.62–0.79, targets −0.27 to −2',
  },
  {
    kind: 'zone',
    icon: <SquareSplitVertical />,
    hint: 'Zone — a gap or a block, with its 50% (consequent encroachment). Double-click to name it',
  },
  { kind: 'label', icon: <Type />, hint: 'Label — a note on the chart. Double-click to edit' },
]

/** One square tool button: 36 px, the icon only, its name as the tooltip and the accessible name. */
function Tool({
  label,
  pressed,
  disabled,
  onClick,
  children,
}: {
  label: string
  pressed?: boolean
  disabled?: boolean
  onClick: () => void
  children: ReactNode
}) {
  return (
    <button
      type="button"
      aria-label={label}
      title={label}
      aria-pressed={pressed}
      disabled={disabled}
      onClick={onClick}
      className={cx(
        'grid size-9 cursor-pointer place-items-center rounded-lg transition-colors [&_svg]:size-4',
        'focus-visible:outline-2 focus-visible:outline-accent disabled:cursor-not-allowed disabled:opacity-35',
        pressed ? 'bg-accent/15 text-accent' : 'text-subtle hover:bg-sunken hover:text-ink',
      )}
    >
      {children}
    </button>
  )
}

/** The slim drawing bar on the chart's left (T38). */
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
      aria-orientation="vertical"
      // A row under 1024 px, scrolling if the tools do not fit; a column beside the chart above it (T46).
      className="flex shrink-0 flex-row gap-0.5 self-stretch overflow-x-auto rounded-card border border-line bg-surface p-1 shadow-panel lg:flex-col lg:self-start lg:overflow-visible"
    >
      <Tool label="Select and move (Esc)" pressed={activeTool === null} onClick={() => onSelectTool(null)}>
        <MousePointer2 />
      </Tool>

      <Divider />

      {TOOLS.map((tool) => (
        <Tool
          key={tool.kind}
          label={tool.hint}
          pressed={activeTool === tool.kind}
          onClick={() => onSelectTool(activeTool === tool.kind ? null : tool.kind)}
        >
          {tool.icon}
        </Tool>
      ))}

      <Divider />

      <Tool
        label="Magnet — snap to bar highs, lows, opens and closes"
        pressed={snapMode === 'magnet'}
        onClick={onToggleSnap}
      >
        <Magnet />
      </Tool>

      <Divider />

      <Tool label="Undo (Ctrl+Z)" disabled={!canUndo} onClick={onUndo}>
        <Undo2 />
      </Tool>
      <Tool label="Redo (Ctrl+Shift+Z)" disabled={!canRedo} onClick={onRedo}>
        <Redo2 />
      </Tool>
      <Tool label="Delete selected (Del)" disabled={!hasSelection} onClick={onDelete}>
        <Trash2 />
      </Tool>
    </div>
  )
}
