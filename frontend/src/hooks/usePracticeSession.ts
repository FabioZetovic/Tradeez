import { useCallback, useEffect, useRef, useState } from 'react'
import { ApiError } from '@/lib/api/client'
import {
  advanceSession,
  closeTrade,
  endSession,
  getSession,
  skipToResult,
  submitPlan,
  type PlanRequest,
  type PracticeState,
  type SessionResponse,
} from '@/lib/api/practice'
import type { DomainBar } from '@/lib/chart/types'

/** Playback speeds: bars per second. One a second is slow enough to read each candle form. */
export const SPEEDS = [1, 2, 4, 8] as const
export type Speed = (typeof SPEEDS)[number]

/**
 * One practice session: what has been revealed, and the actions that reveal more.
 *
 * The server owns everything (DEC-031). This hook holds a copy of what it has been told and never
 * computes anything the server would disagree with — in particular it never works out an outcome
 * from the bars, even though it could, because that result would have no authority.
 *
 * One request at a time. The session row carries an optimistic lock, so a double-clicked "Next"
 * would otherwise earn a 409 for the second click; ignoring input while a request is in flight is
 * both simpler and what the learner meant.
 */
export function usePracticeSession(sessionId: string, initial?: SessionResponse) {
  // A session that was just created arrives with its history already, so it is not fetched twice —
  // each fetch regenerates days of one-second data on the server.
  const seeded = initial?.state.id === sessionId ? initial : undefined
  const [state, setState] = useState<PracticeState | null>(seeded?.state ?? null)
  const [bars, setBars] = useState<DomainBar[]>(seeded?.bars ?? [])
  // The timeframe the replay is being looked at in (T34): the session's own until switched.
  const [timeframe, setTimeframe] = useState<string | null>(seeded?.state.timeframe ?? null)
  const seededRef = useRef(seeded)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const [playing, setPlaying] = useState(false)
  const [speed, setSpeed] = useState<Speed>(2)

  const busyRef = useRef(false)
  // How many trades had closed at the last response, so play can stop the moment another does.
  const closedRef = useRef(seeded?.state.score.closedTrades ?? 0)

  // Callers mount one instance per session (keyed by id), so the seed never has to be forgotten —
  // and must not be: StrictMode runs this effect twice, and clearing it on the first run would send
  // the second one off to fetch what is already here.
  useEffect(() => {
    if (seededRef.current?.state.id === sessionId) return
    let cancelled = false
    setState(null)
    setBars([])
    setError(null)
    setPlaying(false)
    getSession(sessionId)
      .then((response) => {
        if (cancelled) return
        closedRef.current = response.state.score.closedTrades
        setState(response.state)
        setTimeframe(response.state.timeframe)
        setBars(response.bars)
      })
      .catch((e) => {
        if (!cancelled) setError(messageOf(e, 'Could not load this session.'))
      })
    return () => {
      cancelled = true
    }
  }, [sessionId])

  /** Runs one request exclusively; returns false if another was already in flight. */
  const exclusive = useCallback(async (work: () => Promise<void>): Promise<boolean> => {
    if (busyRef.current) return false
    busyRef.current = true
    setBusy(true)
    setError(null)
    try {
      await work()
      return true
    } catch (e) {
      setError(messageOf(e, 'Something went wrong. Try again.'))
      setPlaying(false)
      return false
    } finally {
      busyRef.current = false
      setBusy(false)
    }
  }, [])

  const reveal = useCallback((response: { state: PracticeState; bars: DomainBar[] }) => {
    setState(response.state)
    // Appended, never replaced: the chart extends the series in place so the learner's zoom and
    // pan survive every step of the replay. The first bar sent back may be the candle that was still
    // forming (T34): it takes the place of the part shown so far.
    if (response.bars.length > 0) setBars((current) => mergeBars(current, response.bars))
    // A trade just ended, or the whole replay did: stop, so the result is read rather than
    // scrolled past.
    const closed = response.state.score.closedTrades
    if (closed > closedRef.current || response.state.status === 'COMPLETE') setPlaying(false)
    closedRef.current = closed
  }, [])

  const viewed = timeframe ?? undefined
  const advance = useCallback(
    (count: number) =>
      exclusive(async () => reveal(await advanceSession(sessionId, count, viewed))),
    [exclusive, reveal, sessionId, viewed],
  )

  const skip = useCallback(
    () => exclusive(async () => reveal(await skipToResult(sessionId, viewed))),
    [exclusive, reveal, sessionId, viewed],
  )

  /** Closes the running trade at market: the fill is revealed with the step it takes (DEC-057). */
  const close = useCallback(
    () =>
      exclusive(async () => {
        setPlaying(false)
        reveal(await closeTrade(sessionId, viewed))
      }),
    [exclusive, reveal, sessionId, viewed],
  )

  /**
   * Looks at the replay in another timeframe (T34): the same moment, its candles rebuilt by the
   * server — the last one perhaps still forming. Takes effect only once they have arrived.
   */
  const switchTimeframe = useCallback(
    (next: string) =>
      exclusive(async () => {
        const response = await getSession(sessionId, next)
        setState(response.state)
        setBars(response.bars)
        setTimeframe(next)
      }),
    [exclusive, sessionId],
  )

  const lockPlan = useCallback(
    (plan: PlanRequest) =>
      exclusive(async () => {
        setState(await submitPlan(sessionId, plan))
      }),
    [exclusive, sessionId],
  )

  const end = useCallback(
    () =>
      exclusive(async () => {
        setPlaying(false)
        setState(await endSession(sessionId))
      }),
    [exclusive, sessionId],
  )

  // Playing is a chain of timeouts, each scheduled after the previous step finished — never an
  // interval, which would stack requests up behind a slow response. At high speed the server's
  // response time, not the timer, sets the pace.
  useEffect(() => {
    if (!playing) return
    const interval = 1000 / speed
    let cancelled = false
    let timer: ReturnType<typeof setTimeout>
    const step = async () => {
      if (cancelled) return
      // Rescheduled even when the step did not run: a manual "Next" in flight is not a reason to
      // stop playing. A failure or a closed trade stops play by clearing `playing`, which cancels
      // this chain through the cleanup below.
      await advance(1)
      if (!cancelled) timer = setTimeout(step, interval)
    }
    timer = setTimeout(step, interval)
    return () => {
      cancelled = true
      clearTimeout(timer)
    }
  }, [playing, speed, advance])

  return {
    state,
    bars,
    timeframe: timeframe ?? state?.timeframe ?? null,
    switchTimeframe,
    error,
    busy,
    playing,
    setPlaying,
    speed,
    setSpeed,
    advance,
    skip,
    close,
    lockPlan,
    end,
  }
}

/** The bars so far, with those sent back laid over them from the first one's time on. */
function mergeBars(current: DomainBar[], revealed: DomainBar[]): DomainBar[] {
  const from = revealed[0].time
  let keep = current.length
  while (keep > 0 && current[keep - 1].time >= from) keep--
  return [...current.slice(0, keep), ...revealed]
}

function messageOf(error: unknown, fallback: string): string {
  if (error instanceof ApiError) {
    const body = error.body as { message?: unknown } | null
    if (body && typeof body.message === 'string') return body.message
  }
  return fallback
}
