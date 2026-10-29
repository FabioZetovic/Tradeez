import type { ReactNode } from 'react'
import { cx } from '@/lib/styles'

/** Candles a glyph is drawn from: x, then open/close as top and bottom of the body, and the wick's ends. */
function Candle({ x, top, bottom, high, low, solid = false }: { x: number; top: number; bottom: number; high: number; low: number; solid?: boolean }) {
  return (
    <>
      <line x1={x + 2} x2={x + 2} y1={high} y2={low} />
      <rect x={x} y={top} width={4} height={bottom - top} rx={0.6} className={solid ? 'fill-current' : 'fill-current/25'} />
    </>
  )
}

/**
 * Each Learn topic as a small picture of its own pattern (T55): swings for structure, a sweep of a level for liquidity,
 * three candles and their gap for gaps — so a card is known by what it teaches before its name is read. Drawn in
 * currentColor: the topic's colour comes from the caller.
 */
const GLYPHS: Record<string, ReactNode> = {
  Structure: <polyline points="2,26 11,14 18,20 28,6 35,12 46,3" fill="none" />,
  Liquidity: (
    <>
      <line x1={2} x2={46} y1={9} y2={9} strokeDasharray="2 2.5" />
      <polyline points="2,25 12,18 20,22 29,14 33,4 37,17 46,27" fill="none" />
    </>
  ),
  'Gaps & imbalances': (
    <>
      <rect x={2} y={13} width={44} height={5} className="fill-current/20" stroke="none" />
      <Candle x={8} top={18} bottom={26} high={17} low={29} />
      <Candle x={22} top={5} bottom={24} high={3} low={26} solid />
      <Candle x={36} top={5} bottom={12} high={3} low={13} />
    </>
  ),
  'Premium & discount': (
    <>
      <rect x={4} y={4} width={40} height={11} className="fill-current/20" stroke="none" />
      <rect x={4} y={4} width={40} height={24} rx={1.5} fill="none" />
      <line x1={4} x2={44} y1={16} y2={16} strokeDasharray="2 2.5" />
    </>
  ),
  Blocks: (
    <>
      <rect x={4} y={13} width={12} height={13} rx={1} className="fill-current/20" strokeDasharray="2 2" />
      <Candle x={8} top={15} bottom={24} high={13} low={26} solid />
      <Candle x={22} top={8} bottom={20} high={6} low={22} />
      <Candle x={34} top={3} bottom={12} high={2} low={14} />
    </>
  ),
  Bias: (
    <>
      <polyline points="3,27 15,18 22,22 42,5" fill="none" />
      <polyline points="34,5 42,5 42,13" fill="none" />
    </>
  ),
  Models: (
    <>
      <rect x={26} y={4} width={18} height={11} className="fill-current/20" stroke="none" />
      <rect x={26} y={15} width={18} height={8} className="fill-current/10" stroke="none" />
      <line x1={26} x2={44} y1={15} y2={15} />
      <polyline points="3,24 10,19 16,22 24,15" fill="none" />
    </>
  ),
  'Risk & management': (
    <>
      <rect x={6} y={4} width={36} height={13} rx={1} className="fill-current/20" stroke="none" />
      <rect x={6} y={17} width={36} height={8} rx={1} className="fill-current/10" stroke="none" />
      <line x1={6} x2={42} y1={17} y2={17} />
      <line x1={6} x2={42} y1={4} y2={4} strokeDasharray="2 2" />
      <line x1={6} x2={42} y1={25} y2={25} strokeDasharray="2 2" />
    </>
  ),
  'Time & sessions': (
    <>
      <rect x={4} y={3} width={10} height={26} className="fill-current/15" stroke="none" />
      <rect x={20} y={3} width={8} height={26} className="fill-current/25" stroke="none" />
      <rect x={34} y={3} width={10} height={26} className="fill-current/15" stroke="none" />
      <polyline points="2,20 12,16 22,22 32,10 46,14" fill="none" />
    </>
  ),
}

export function TopicGlyph({ topic, className }: { topic: string; className?: string }) {
  return (
    <svg
      viewBox="0 0 48 32"
      aria-hidden
      className={cx('shrink-0 stroke-current', className)}
      strokeWidth={1.8}
      strokeLinecap="round"
      strokeLinejoin="round"
    >
      {GLYPHS[topic] ?? <circle cx={24} cy={16} r={9} fill="none" />}
    </svg>
  )
}
