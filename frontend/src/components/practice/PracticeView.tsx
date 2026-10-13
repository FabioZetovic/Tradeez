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
import { Button, Panel, Segmented } from '../ui'
import { errorClass, panelTitle } from '@/lib/styles'

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
    <section className="flex flex-col gap-4">
      <Panel aria-label="New session">
        <h2 className="mb-2 text-lg font-semibold">Practice</h2>
        <p className="max-w-3xl text-sm text-subtle">
          You get a chart that stops at a random, hidden moment. Read it, decide, and lock a trade
          with a stop and a target — or step forward and wait. Then watch what the market did. The
          result is recorded as it happened, and cannot be changed.
        </p>
        <div className="mt-4 flex flex-wrap items-center gap-3">
          <Segmented<string>
            options={PRACTICE_TIMEFRAMES}
            value={timeframe}
            onChange={setTimeframe}
            label="Practice timeframe"
          />
          <Button variant="primary" disabled={starting} onClick={() => void start()}>
            {starting ? 'Finding a moment…' : 'Start a session'}
          </Button>
        </div>
        {error && (
          <p role="alert" className={`mt-3 ${errorClass}`}>
            {error}
          </p>
        )}
      </Panel>

      <Panel aria-label="Journal">
        <h3 className={panelTitle}>Journal</h3>
        {journal === null && (
          <p role="status" className="text-sm text-subtle">
            Loading…
          </p>
        )}
        {journal?.length === 0 && <p className="text-sm text-subtle">No sessions yet.</p>}
        {journal && journal.length > 0 && (
          <table className="w-full border-collapse text-sm tabular-nums [&_td]:border-b [&_td]:border-line/60 [&_td]:px-2 [&_td]:py-2 [&_th]:border-b [&_th]:border-line [&_th]:px-2 [&_th]:py-2 [&_th]:text-left [&_th]:font-medium [&_th]:text-subtle">
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
                    <Button variant="link" onClick={() => setOpenId(row.id)}>
                      {row.status === 'COMPLETE' ? 'Review' : 'Resume'}
                    </Button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </Panel>
    </section>
  )
}
