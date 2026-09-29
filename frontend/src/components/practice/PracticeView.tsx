import { useCallback, useEffect, useState } from 'react'
import { PracticeSession } from './PracticeSession'
import { ApiError } from '@/lib/api/client'
import {
  createSession,
  listSessions,
  PRACTICE_TIMEFRAMES,
  type SessionResponse,
  type SessionSummary,
} from '@/lib/api/practice'
import type { ChartAdapter } from '@/lib/chart/types'
import { formatCents, formatR, outcomeLabel } from '@/lib/practice/plan'

interface PracticeViewProps {
  adapterFactory?: () => ChartAdapter
}

/**
 * The Practice tab: a journal of past sessions, and the way into a new one.
 *
 * The loop it serves is the one the whole product is built around — look at a market you have not
 * seen, commit to a plan, watch what happens, and keep a record you cannot edit afterwards.
 */
export function PracticeView({ adapterFactory }: PracticeViewProps) {
  const [openId, setOpenId] = useState<string | null>(null)
  const [created, setCreated] = useState<SessionResponse | undefined>(undefined)
  const [journal, setJournal] = useState<SessionSummary[] | null>(null)
  const [timeframe, setTimeframe] = useState<string>('5m')
  const [starting, setStarting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const loadJournal = useCallback(() => {
    listSessions()
      .then(setJournal)
      .catch(() => setError('Could not load your practice journal.'))
  }, [])

  useEffect(() => {
    if (openId === null) loadJournal()
  }, [openId, loadJournal])

  const start = async () => {
    setStarting(true)
    setError(null)
    try {
      const session = await createSession({ instrument: 'DEMO-NQ', timeframe })
      setCreated(session)
      setOpenId(session.state.id)
    } catch (e) {
      const body = e instanceof ApiError ? (e.body as { message?: string } | null) : null
      setError(body?.message ?? 'Could not start a session.')
    } finally {
      setStarting(false)
    }
  }

  if (openId) {
    return (
      <PracticeSession
        key={openId}
        sessionId={openId}
        initial={created}
        adapterFactory={adapterFactory}
        onBack={() => {
          setOpenId(null)
          setCreated(undefined)
        }}
      />
    )
  }

  return (
    <section className="practice">
      <section className="panel" aria-label="New session">
        <h2>Practice</h2>
        <p className="muted">
          You get a chart that stops at a random, hidden moment. Read it, decide, and lock a trade
          with a stop and a target — or step forward and wait. Then watch what the market did. The
          result is recorded as it happened, and cannot be changed.
        </p>
        <div className="button-row">
          <div className="timeframes" role="group" aria-label="Practice timeframe">
            {PRACTICE_TIMEFRAMES.map((tf) => (
              <button
                key={tf}
                type="button"
                aria-pressed={tf === timeframe}
                className={tf === timeframe ? 'tf tf--active' : 'tf'}
                onClick={() => setTimeframe(tf)}
              >
                {tf}
              </button>
            ))}
          </div>
          <button type="button" className="primary" disabled={starting} onClick={() => void start()}>
            {starting ? 'Finding a moment…' : 'Start a session'}
          </button>
        </div>
        {error && (
          <p role="alert" className="error">
            {error}
          </p>
        )}
      </section>

      <section className="panel" aria-label="Journal">
        <h3>Journal</h3>
        {journal === null && <p role="status">Loading…</p>}
        {journal?.length === 0 && <p className="muted">No sessions yet.</p>}
        {journal && journal.length > 0 && (
          <table className="journal">
            <thead>
              <tr>
                <th>Started</th>
                <th>Chart</th>
                <th>Trade</th>
                <th>Outcome</th>
                <th>R</th>
                <th>Net</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {journal.map((row) => (
                <tr key={row.id} data-testid="journal-row">
                  <td>{new Date(row.createdAt * 1000).toLocaleString()}</td>
                  <td>
                    {row.instrument} · {row.timeframe}
                  </td>
                  <td>{row.direction ? row.direction.toLowerCase() : '—'}</td>
                  {/* The server leaves these out until COMPLETE. An unfinished trade shows its
                      status here, never a partial or guessed outcome. */}
                  <td>
                    {row.outcome
                      ? outcomeLabel(row.outcome)
                      : row.status === 'IN_TRADE'
                        ? 'In trade'
                        : 'Analysing'}
                  </td>
                  <td>{row.rMultiple !== undefined ? formatR(row.rMultiple) : ''}</td>
                  <td>{row.netPnlCents !== undefined ? formatCents(row.netPnlCents) : ''}</td>
                  <td>
                    <button type="button" className="link" onClick={() => setOpenId(row.id)}>
                      {row.status === 'COMPLETE' ? 'Review' : 'Resume'}
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>
    </section>
  )
}
