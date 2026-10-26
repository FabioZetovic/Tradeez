import type { NewsEvent } from './api/news'

const NEW_YORK = 'America/New_York'

/** "Monday 26 October", the New York day an event falls on. */
export function newsDay(at: number): string {
  return new Intl.DateTimeFormat('en-GB', { timeZone: NEW_YORK, weekday: 'long', day: 'numeric', month: 'long' })
    .format(new Date(at * 1000))
    .replace(',', '')
}

/** "08:30", New York time. */
export function newsTime(at: number): string {
  return new Intl.DateTimeFormat('en-GB', { timeZone: NEW_YORK, hour: '2-digit', minute: '2-digit', hour12: false }).format(
    new Date(at * 1000),
  )
}

/** The events grouped by their New York day, in order. */
export function byDay(events: readonly NewsEvent[]): Array<{ day: string; events: NewsEvent[] }> {
  const days: Array<{ day: string; events: NewsEvent[] }> = []
  for (const event of events) {
    const day = newsDay(event.at)
    if (days.at(-1)?.day === day) days.at(-1)!.events.push(event)
    else days.push({ day, events: [event] })
  }
  return days
}
