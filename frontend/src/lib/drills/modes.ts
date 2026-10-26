import type { DrillMode } from '@/lib/api/drills'

/** The mode as a learner reads it: a card's badge, the drill screen's eyebrow (T43). */
export const MODE_NAMES: Record<DrillMode, string> = {
  IDENTIFY: 'Spot',
  CLASSIFY: 'Name',
  PREDICT: 'Predict',
  MANAGE: 'Manage',
}
