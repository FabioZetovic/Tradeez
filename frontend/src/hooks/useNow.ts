import { useEffect, useState } from 'react'

/**
 * The time, moved on once a minute (BUG-163): a page left open across a release, midnight or a new week shows them
 * passing — what has happened greys out, "Today" moves — instead of the moment it was opened.
 */
export function useNow(everyMs = 60_000): Date {
  const [now, setNow] = useState(() => new Date())
  useEffect(() => {
    const tick = window.setInterval(() => setNow(new Date()), everyMs)
    return () => window.clearInterval(tick)
  }, [everyMs])
  return now
}
