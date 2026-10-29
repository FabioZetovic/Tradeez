import type { ScoreLine, TradeView } from '@/lib/api/practice'

/**
 * What to take from a session (T53): its trades' process lines gathered — the learner's own rules counted kept and
 * broken, the rest as the last scorecard put it. Only what the scorecards said; nothing added, nothing advised.
 */
export function takeaways(closed: TradeView[]): string[] {
  const lines = new Map<string, { label: string; text: string; kept: number; broken: number }>()
  for (const trade of closed) {
    for (const line of trade.scorecard?.process ?? ([] as ScoreLine[])) {
      const seen = lines.get(line.id) ?? { label: line.label, text: line.text, kept: 0, broken: 0 }
      seen.text = line.text
      if (line.verdict === 'KEPT') seen.kept++
      if (line.verdict === 'BROKEN') seen.broken++
      lines.set(line.id, seen)
    }
  }
  return [...lines.values()].map(({ label, text, kept, broken }) =>
    kept + broken > 0 ? `${label}: kept on ${kept} of ${kept + broken} ${kept + broken === 1 ? 'trade' : 'trades'}.` : `${label}: ${text}`,
  )
}


/**
 * "Monday 7 April 2025, 10:05" — the real moment, in New York time. Assembled from parts: how Intl
 * joins them (a comma, "at") differs between ICU versions.
 */
export function revealedDate(epochSeconds: number): string {
  const parts = new Intl.DateTimeFormat('en-GB', {
    timeZone: 'America/New_York',
    weekday: 'long',
    day: 'numeric',
    month: 'long',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    hourCycle: 'h23',
  }).formatToParts(new Date(epochSeconds * 1000))
  const part = (type: Intl.DateTimeFormatPartTypes) => parts.find((p) => p.type === type)?.value
  return `${part('weekday')} ${part('day')} ${part('month')} ${part('year')}, ${part('hour')}:${part('minute')}`
}
