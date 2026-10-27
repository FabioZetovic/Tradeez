import { useCallback, useEffect, useState } from 'react'
import { useLocation, useNavigate, useParams } from 'react-router'
import { Play } from 'lucide-react'
import { PracticeSession } from './PracticeSession'
import { StatisticsPanel } from './StatisticsPanel'
import { ApiError } from '@/lib/api/client'
import {
  createSession,
  getCalibration,
  getPracticeSettings,
  getStatistics,
  listSessions,
  listTrades,
  saveRiskLimit,
  KILLZONES,
  type Calibration,
  type JournalTrade,
  type PracticeSettings,
  type SessionResponse,
  type SessionSummary,
  type Statistics,
} from '@/lib/api/practice'
import type { ChartAdapter } from '@/lib/chart/types'
import { formatPrice } from '@/lib/chart/priceFormat'
import { formatSessionDate, formatSessionTime } from '@/lib/chart/timeAxis'
import { parseDecimal } from '@/lib/decimal'
import { formatCents, formatR, outcomeLabel, STATUS_LABEL } from '@/lib/practice/plan'
import { MARKETS, type Market } from '@/lib/markets'
import { Button, PageHeader, Panel, Segmented, StatTile } from '../ui'
import { cx, errorClass, fieldClass, panelTitle } from '@/lib/styles'

const tableClass =
  'w-full border-collapse text-sm tabular-nums [&_td]:border-b [&_td]:border-line/60 [&_td]:px-2 [&_td]:py-2.5 [&_th]:border-b [&_th]:border-line [&_th]:px-2 [&_th]:py-2 [&_th]:text-left [&_th]:text-xs [&_th]:font-medium [&_th]:tracking-wide [&_th]:text-subtle [&_th]:uppercase'

/** A risk limit is $1 to $1,000,000 (V20). */
const MAX_LIMIT_DOLLARS = 1_000_000

/** Open sessions listed before "Show all". */
const OPEN_SHOWN = 5

const NEW_YORK = 'America/New_York'

interface PracticeViewProps {
  adapterFactory?: () => ChartAdapter
}

/**
 * The Practice tab (T41, Lovable's): start a session with one choice — the session time — the four figures that sum
 * the practice up, the open sessions, and the journal, one row per trade. The rest of the statistics behind a click.
 *
 * The loop it serves is the one the whole product is built around — look at a market you have not seen, commit to a
 * plan, watch what happens, and keep a record you cannot edit afterwards.
 */
export function PracticeView({ adapterFactory }: PracticeViewProps) {
  // A session has its own address (T42): /practice/<id>. One just made is handed over with its first answer, so it is
  // not asked for twice.
  const openId = useParams().sessionId ?? null
  const created = (useLocation().state as { created?: SessionResponse } | null)?.created
  const navigate = useNavigate()
  const setOpenId = (id: string) => void navigate(`/practice/${id}`)
  const [sessions, setSessions] = useState<SessionSummary[] | null>(null)
  const [trades, setTrades] = useState<JournalTrade[] | null>(null)
  const [calibration, setCalibration] = useState<Calibration | null>(null)
  const [statistics, setStatistics] = useState<Statistics | null>(null)
  // undefined while loading.
  const [settings, setSettings] = useState<PracticeSettings | undefined>(undefined)
  // Empty means "any": the server then picks from the whole history.
  const [killzone, setKillzone] = useState('')
  // T47: NQ or ES — a real day of either.
  const [market, setMarket] = useState<Market>('NQ')
  const [starting, setStarting] = useState(false)
  const [allOpen, setAllOpen] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const load = useCallback(() => {
    listSessions()
      .then(setSessions)
      .catch(() => setError('Could not load your sessions.'))
    listTrades()
      .then(setTrades)
      .catch(() => setError('Could not load your journal.'))
    getStatistics()
      .then(setStatistics)
      .catch(() => setError('Could not load your statistics.'))
    getCalibration()
      .then(setCalibration)
      .catch(() => setError('Could not load your calibration.'))
    getPracticeSettings()
      .then(setSettings)
      .catch(() => setError('Could not load your risk limit.'))
  }, [])

  const saveLimit = async (cents: number | null) => {
    setError(null)
    try {
      setSettings(await saveRiskLimit(cents ?? undefined))
    } catch {
      setError('Could not save your risk limit.')
    }
  }

  useEffect(() => {
    if (openId === null) load()
  }, [openId, load])

  const start = async () => {
    setStarting(true)
    setError(null)
    try {
      const session = await createSession({ instrument: market, ...(killzone && { killzone }) })
      void navigate(`/practice/${session.state.id}`, { state: { created: session } })
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
        riskLimitCents={settings?.riskLimitCents}
        costs={settings}
        onBack={() => void navigate('/practice')}
      />
    )
  }

  const open = sessions?.filter((s) => s.status !== 'COMPLETE') ?? []
  // The newest few: a learner with many left open should still reach the journal without scrolling past them all.
  const openShown = allOpen ? open : open.slice(0, OPEN_SHOWN)

  return (
    <section className="flex flex-col gap-6">
      <PageHeader
        eyebrow="Step 3"
        title="Practice"
        description="A real day replayed bar by bar, the future hidden. Plan, execute, then review."
      />

      <section
        aria-label="New session"
        className="flex flex-wrap items-end justify-between gap-6 rounded-card bg-accent/15 p-6 ring-1 ring-accent/30"
      >
        <div className="max-w-xl">
          <h2 className="font-display text-2xl font-bold tracking-tight text-ink">Start a session</h2>
          <p className="mt-1 text-sm text-subtle">
            A random {market} day from the last two years. You won&apos;t see the date until the end.
          </p>
          <RiskLimitField
            cents={settings === undefined ? undefined : (settings.riskLimitCents ?? null)}
            onSave={(cents) => void saveLimit(cents)}
            onInvalid={() => setError(`A risk limit is $1 to $${MAX_LIMIT_DOLLARS.toLocaleString('en-US')}.`)}
          />
        </div>
        <div className="flex flex-wrap items-center gap-3">
          <Segmented<Market> options={MARKETS} value={market} onChange={setMarket} label="Market" />
          <label className="sr-only" htmlFor="session-time">
            Session time
          </label>
          <select id="session-time" className={fieldClass} value={killzone} onChange={(e) => setKillzone(e.target.value)}>
            <option value="">Any time</option>
            {KILLZONES.map((k) => (
              <option key={k.value} value={k.value}>
                {k.label}
              </option>
            ))}
          </select>
          <Button variant="primary" size="lg" disabled={starting} onClick={() => void start()}>
            <Play aria-hidden className="size-4 fill-current" />
            {starting ? 'Finding a moment…' : 'Start'}
          </Button>
        </div>
        {error && (
          <p role="alert" className={`w-full ${errorClass}`}>
            {error}
          </p>
        )}
      </section>

      {statistics && <Tiles statistics={statistics} />}

      {open.length > 0 && (
        <Panel aria-label="Open sessions">
          <h3 className={panelTitle}>Open sessions</h3>
          <ul className="flex flex-col">
            {openShown.map((s) => (
              <li key={s.id} className="flex items-center gap-4 border-b border-line/60 py-2.5 text-sm last:border-b-0" data-testid="open-session">
                <span className="w-20 text-ink">{STATUS_LABEL[s.status]}</span>
                <span className="num w-24 text-subtle">
                  {s.trades} {s.trades === 1 ? 'trade' : 'trades'}
                </span>
                <span className={cx('num w-24', s.score.totalR > 0 ? 'text-up' : s.score.totalR < 0 ? 'text-down' : 'text-subtle')}>
                  {s.score.closedTrades > 0 ? formatR(s.score.totalR) : '—'}
                </span>
                <Button variant="link" className="ml-auto" onClick={() => setOpenId(s.id)}>
                  Resume
                </Button>
              </li>
            ))}
          </ul>
          {open.length > OPEN_SHOWN && (
            <Button variant="link" className="mt-2" onClick={() => setAllOpen((a) => !a)}>
              {allOpen ? 'Show fewer' : `Show all ${open.length}`}
            </Button>
          )}
        </Panel>
      )}

      <Panel aria-label="Journal">
        <h3 className={panelTitle}>Journal</h3>
        {trades === null && (
          <p role="status" className="text-sm text-subtle">
            Loading…
          </p>
        )}
        {trades?.length === 0 && <p className="text-sm text-subtle">No finished trades yet — start a session.</p>}
        {trades && trades.length > 0 && (
          <table className={tableClass}>
            <thead>
              <tr>
                <th>When</th>
                <th>Side</th>
                <th>Entry</th>
                <th>Result</th>
                <th>R</th>
                <th>P&amp;L</th>
                <th>Model</th>
              </tr>
            </thead>
            <tbody>
              {trades.map((t) => (
                <tr
                  key={`${t.sessionId}-${t.number}`}
                  data-testid="journal-row"
                  tabIndex={0}
                  onClick={() => setOpenId(t.sessionId)}
                  onKeyDown={(e) => {
                    if (e.key === 'Enter') setOpenId(t.sessionId)
                  }}
                  className="cursor-pointer hover:bg-sunken/60 focus-visible:outline-2 focus-visible:outline-accent"
                  title="Open this session"
                >
                  <td className="num text-subtle">{when(t)}</td>
                  <td className={cx('font-medium', t.direction === 'LONG' ? 'text-up' : 'text-down')}>
                    {t.direction === 'LONG' ? 'Long' : 'Short'}
                  </td>
                  <td className="num">{formatPrice(t.entryTicks, t.tickSize)}</td>
                  <td>{t.outcome === 'CLOSED' ? 'Closed by hand' : outcomeLabel(t.outcome)}</td>
                  <td className={cx('num font-semibold', t.r > 0 ? 'text-up' : t.r < 0 ? 'text-down' : 'text-subtle')}>
                    {formatR(t.r)}
                  </td>
                  <td className="num">{formatCents(t.netPnlCents)}</td>
                  <td className="text-subtle">{t.model ?? '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </Panel>

      {(statistics || calibration) && (
        <details className="group">
          <summary className="cursor-pointer text-sm font-semibold text-accent">More statistics</summary>
          <div className="mt-4 flex flex-col gap-4">
            {statistics && <StatisticsPanel statistics={statistics} />}
            {calibration && <CalibrationPanel calibration={calibration} />}
          </div>
        </details>
      )}
    </section>
  )
}

/** "Tue 09:05 NY" while the session keeps its date (DEC-025); the real date and time once it is complete. */
function when(t: JournalTrade): string {
  if (t.timeBasis === 'NEW_YORK_WALL_CLOCK') return formatSessionTime(t.at, NEW_YORK, t.timeBasis)
  return `${formatSessionDate(t.at, NEW_YORK)} · ${formatSessionTime(t.at, NEW_YORK)}`
}

/** The four figures (T41), each opening the sentence that says what it means. */
function Tiles({ statistics }: { statistics: Statistics }) {
  const { overall, heldToPlan, costsPaidCents, bestR, worstR } = statistics
  const n = overall.trades
  const trades = `${n} ${n === 1 ? 'trade' : 'trades'}`
  return (
    <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4" data-testid="practice-tiles">
      <StatTile
        label="Total"
        value={formatR(overall.totalR)}
        tone={overall.totalR > 0 ? 'up' : overall.totalR < 0 ? 'down' : 'neutral'}
        detail={
          n === 0
            ? 'No finished trades yet.'
            : `Average ${formatR(overall.expectancyR ?? 0)} per trade across ${trades}. Best ${formatR(bestR ?? 0)}, worst ${formatR(worstR ?? 0)}.`
        }
      />
      <StatTile
        label="Win rate"
        value={overall.winRate === undefined ? '—' : `${Math.round(overall.winRate)}%`}
        detail={n === 0 ? 'No finished trades yet.' : `${overall.wins} of ${trades} made money after costs. ${overall.reading}`}
      />
      <StatTile
        label="Held to plan"
        value={n === 0 ? '—' : `${heldToPlan} / ${n}`}
        detail={
          n === 0
            ? 'No finished trades yet.'
            : `${n - heldToPlan} closed by hand before the stop or target. Process and outcome are scored separately.`
        }
      />
      <StatTile
        label="Costs paid"
        value={formatCents(costsPaidCents)}
        detail="Every trade pays the standard costs: a tick of slippage in, a tick at the stop, $4.50 a round trip."
      />
    </div>
  )
}

/** The most one trade may risk, in dollars: committed on Enter or blur, cleared when emptied. */
function RiskLimitField({
  cents,
  onSave,
  onInvalid,
}: {
  cents: number | null | undefined
  onSave: (cents: number | null) => void
  onInvalid: () => void
}) {
  const [draft, setDraft] = useState<string | null>(null)
  const text = draft ?? (cents == null ? '' : String(cents / 100))
  const commit = () => {
    if (draft === null) return
    const dollars = parseDecimal(draft, '$')
    if (draft.trim() === '') onSave(null)
    else if (dollars !== null && dollars >= 1 && dollars <= MAX_LIMIT_DOLLARS) onSave(Math.round(dollars * 100))
    else onInvalid()
    setDraft(null)
  }
  return (
    <label className="mt-4 flex flex-wrap items-center gap-2 text-xs text-subtle">
      Your risk limit per trade: $
      <input
        className={`${fieldClass} h-8 w-28`}
        inputMode="decimal"
        placeholder="none"
        aria-label="Risk limit per trade ($)"
        disabled={cents === undefined}
        value={text}
        onChange={(e) => setDraft(e.target.value)}
        onBlur={commit}
        onKeyDown={(e) => {
          if (e.key === 'Enter') commit()
        }}
      />
      <span>— your rule: each scorecard says whether a trade kept it. It never blocks one.</span>
    </label>
  )
}

/**
 * Calibration (T23): how sure the learner said they were, against how often the target came first — by
 * confidence, with the number of trades behind every figure, and read against the range the results allow.
 */
function CalibrationPanel({ calibration }: { calibration: Calibration }) {
  return (
    <Panel aria-label="Calibration">
      <h3 className={panelTitle}>Calibration</h3>
      <p className="mb-2 text-sm text-subtle">{calibration.question}</p>
      <div className="flex max-w-3xl flex-col gap-1 text-sm" data-testid="calibration-summary">
        {calibration.summary.map((sentence) => (
          <p key={sentence}>{sentence}</p>
        ))}
      </div>
      {calibration.buckets.length > 0 && (
        <table className={`${tableClass} mt-3 max-w-2xl`}>
          <thead>
            <tr>
              <th>You said</th>
              <th>Trades</th>
              <th>Target first</th>
              <th>Rate</th>
            </tr>
          </thead>
          <tbody>
            {calibration.buckets.map((b) => (
              <tr key={b.confidence} data-testid="calibration-row">
                <td>{b.confidence}%</td>
                <td>{b.trades}</td>
                <td>{b.hits}</td>
                <td>{Math.round((100 * b.hits) / b.trades)}%</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
      <p className="mt-3 text-xs text-subtle">
        Each row is only as good as its number of trades: ten trades at one confidence can miss the truth by twenty
        points or more. {calibration.version}.
      </p>
    </Panel>
  )
}
