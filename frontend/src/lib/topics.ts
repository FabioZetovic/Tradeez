import {
  Calculator,
  ChartCandlestick,
  Clock,
  Compass,
  Droplets,
  Layers,
  Scale,
  Shapes,
  Waypoints,
  Workflow,
  type LucideIcon,
} from 'lucide-react'

/** Each of Learn's topics (DEC-081) drawn the same on Learn and on Drills; one the server adds later gets a plain shape. */
const ICONS: Record<string, LucideIcon> = {
  Structure: Waypoints,
  Liquidity: Droplets,
  'Gaps & imbalances': ChartCandlestick,
  'Premium & discount': Scale,
  Blocks: Layers,
  Bias: Compass,
  Models: Workflow,
  'Risk & management': Calculator,
  'Time & sessions': Clock,
}

export const topicIcon = (topic: string): LucideIcon => ICONS[topic] ?? Shapes
