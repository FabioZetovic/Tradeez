import { useCallback, useEffect, useRef, useState } from 'react'
import { ApiError } from '@/lib/api/client'
import {
  advanceSession,
  getSession,
  skipToResult,
  submitPlan,
  type PlanRequest,
  type PracticeState,
  type SessionResponse,
} from '@/lib/api/practice'
import type { DomainBar } from '@/lib/chart/types'

/** Milliseconds between bars while playing. Slow enough to watch, quick enough not to bore. */
export const PLAY_INTERVAL_MS = 600

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
  const seededRef = useRef(seeded)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const [playing, setPlaying] = useState(false)

  const busyRef = useRef(false)
  const idRef = useRef(sessionId)
  idRef.current = sessionId

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
        setState(response.state)
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
    // pan survive every step of the replay.
    if (response.bars.length > 0) setBars((current) => [...current, ...response.bars])
    if (response.state.status === 'COMPLETE') setPlaying(false)
  }, [])

  const advance = useCallback(
    (count: number) =>
      exclusive(async () => reveal(await advanceSession(idRef.current, count))),
    [exclusive, reveal],
  )

  const skip = useCallback(
    () => exclusive(async () => reveal(await skipToResult(idRef.current))),
    [exclusive, reveal],
  )

  const lockPlan = useCallback(
    (plan: PlanRequest) =>
      exclusive(async () => {
        setState(await submitPlan(idRef.current, plan))
      }),
    [exclusive],
  )

  // Playing is a chain of timeouts, each scheduled after the previous step finished — never an
  // interval, which would stack requests up behind a slow response.
  useEffect(() => {
    if (!playing) return
    let cancelled = false
    let timer: ReturnType<typeof setTimeout>
    const step = async () => {
      if (cancelled) return
      // Rescheduled even when the step did not run: a manual "Next" in flight is not a reason to
      // stop playing. A failure or a finished trade stops play by clearing `playing`, which cancels
      // this chain through the cleanup below.
      await advance(1)
      if (!cancelled) timer = setTimeout(step, PLAY_INTERVAL_MS)
    }
    timer = setTimeout(step, PLAY_INTERVAL_MS)
    return () => {
      cancelled = true
      clearTimeout(timer)
    }
  }, [playing, advance])

  return { state, bars, error, busy, playing, setPlaying, advance, skip, lockPlan }
}

function messageOf(error: unknown, fallback: string): string {
  if (error instanceof ApiError) {
    const body = error.body as { message?: unknown } | null
    if (body && typeof body.message === 'string') return body.message
  }
  return fallback
}
