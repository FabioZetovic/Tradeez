import { useCallback, useEffect, useState } from 'react'
import { useLocation, useNavigate, useParams } from 'react-router'
import { ArrowLeft, ArrowRight, Play } from 'lucide-react'
import { useIsPhone } from '@/hooks/useIsPhone'
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
  type JournalKind,
  type JournalTrade,
  type PracticeSettings,
  type SessionResponse,
  type SessionSummary,
  type Statistics,
  type StatisticsGroup,
} from '@/lib/api/practice'
import type { ChartAdapter } from '@/lib/chart/types'
import { formatPrice } from '@/lib/chart/priceFormat'
import { formatSessionDate, formatSessionTime } from '@/lib/chart/timeAxis'
import { parseDecimal } from '@/lib/decimal'
import { formatCents, formatR, outcomeLabel, STATUS_LABEL } from '@/lib/practice/plan'
import { MARKETS, type Market } from '@/lib/markets'
import { Button, CountUp, PageHeader, Panel, Segmented, StatTile } from '../ui'
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
  const phone = useIsPhone()
  // A phone (T53): the statistics are a screen of their own.
  const [statsShown, setStatsShown] = useState(false)

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
  }, [])

  // BUG-155: the risk limit and the costs belong to a session's screen as much as to this one — opened straight from
  // its address, the session showed neither.
  useEffect(() => {
    getPracticeSettings()
      .then(setSettings)
      .catch(() => setError('Could not load your risk limit.'))
  }, [])

  // BUG-154: the first answer is handed over once. Left in the browser's history, a reload or Back brought the
  // session back as it was when made — not as it is — and asked the server nothing.
  useEffect(() => {
    if (created) void navigate(`/practice/${created.state.id}`, { replace: true, state: null })
  }, [created, navigate])

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

  if (phone && statsShown && statistics) {
    return <PhoneStatistics statistics={statistics} calibration={calibration} onBack={() => setStatsShown(false)} />
  }

  if (phone) {
    const first = statistics !== null && statistics.overall.trades === 0
    return (
      // Practice on a phone (T53, Claude Design): start a session, your record, the open sessions in a row, the journal
      // as two-line rows with filters; the statistics a screen of their own.
      <section className="flex flex-col gap-6">
        <h1 className="sr-only">Practice</h1>
        {first && <p className="text-sm text-subtle">A real day replayed bar by bar, the future hidden. Plan, execute, then review.</p>}
        <section aria-label="New session" className="rounded-card border border-line bg-surface p-4">
          <h2 className="text-lg font-semibold text-ink">{first ? 'Start your first session' : 'Start a session'}</h2>
          <p className="mt-1 text-[13px] text-subtle">
            A random {market} day from the last two years. The date stays hidden until the end.
          </p>
          <div className="mt-3.5 grid grid-cols-2 gap-2">
            <Segmented<Market> options={MARKETS} value={market} onChange={setMarket} label="Market" />
            <label className="sr-only" htmlFor="session-time">
              Session time
            </label>
            <select id="session-time" className={cx(fieldClass, 'w-full min-w-0')} value={killzone} onChange={(e) => setKillzone(e.target.value)}>
              <option value="">Any time</option>
              {KILLZONES.map((k) => (
                <option key={k.value} value={k.value}>
                  {k.label}
                </option>
              ))}
            </select>
          </div>
          <RiskLimitField
            cents={settings === undefined ? undefined : (settings.riskLimitCents ?? null)}
            onSave={(cents) => void saveLimit(cents)}
            onInvalid={() => setError(`A risk limit is $1 to ${MAX_LIMIT_DOLLARS.toLocaleString('en-US')}.`)}
          />
          <button
            type="button"
            disabled={starting}
            onClick={() => void start()}
            className="mt-4 inline-flex h-13 w-full cursor-pointer items-center justify-center gap-2 rounded-full bg-accent text-base font-semibold text-accent-ink focus-visible:outline-2 focus-visible:outline-accent disabled:opacity-40"
          >
            <Play aria-hidden className="size-4 fill-current" />
            {starting ? 'Finding a moment…' : 'Start session'}
          </button>
          {error && (
            <p role="alert" className={cx(errorClass, 'mt-2')}>
              {error}
            </p>
          )}
        </section>

        {statistics && !first && (
          <section aria-labelledby="record">
            <div className="mb-2.5 flex items-baseline justify-between">
              <h2 id="record" className="text-base font-semibold text-ink">
                Your record
              </h2>
              <button type="button" onClick={() => setStatsShown(true)} className="min-h-11 cursor-pointer text-[13px] font-semibold text-accent">
                More statistics
              </button>
            </div>
            <Tiles statistics={statistics} />
          </section>
        )}
        {first && (
          <section className="rounded-card border border-dashed border-line px-4 py-6 text-center">
            <h2 className="text-base font-semibold text-ink">Your record starts with your first trade</h2>
            <p className="mt-1.5 text-[13px] text-subtle">
              Total R, win rate, how often you held to plan and the costs paid show up here — with every trade in your journal.
            </p>
          </section>
        )}

        {open.length > 0 && (
          <section aria-labelledby="open-sessions">
            <h2 id="open-sessions" className="mb-2.5 text-base font-semibold text-ink">
              Open sessions <span className="num text-[13px] font-normal text-subtle">{open.length}</span>
            </h2>
            <ul className="-mr-4 flex gap-2 overflow-x-auto pr-4 [scrollbar-width:none]">
              {open.map((s) => (
                <li key={s.id} data-testid="open-session" className="shrink-0">
                  <button
                    type="button"
                    onClick={() => setOpenId(s.id)}
                    className="flex w-[150px] cursor-pointer flex-col items-start rounded-card border border-line bg-surface p-3.5 text-left focus-visible:outline-2 focus-visible:outline-accent"
                  >
                    <span className="rounded-full bg-sunken px-2 py-0.5 text-[11px] font-semibold text-subtle">{STATUS_LABEL[s.status]}</span>
                    <span className={cx('num mt-2.5 text-lg font-semibold', s.score.totalR > 0 ? 'text-up' : s.score.totalR < 0 ? 'text-down' : 'text-ink')}>
                      {s.score.closedTrades > 0 ? formatR(s.score.totalR) : '—'}
                    </span>
                    <span className="text-[13px] text-subtle">
                      {s.trades} {s.trades === 1 ? 'trade' : 'trades'}
                    </span>
                    <span className="mt-2.5 inline-flex items-center gap-1 text-[13px] font-semibold text-accent">
                      Resume <ArrowRight aria-hidden className="size-3.5" />
                    </span>
                  </button>
                </li>
              ))}
            </ul>
          </section>
        )}

        {trades && trades.length > 0 && (
          <PhoneJournal
            trades={trades}
            total={Math.max(statistics?.overall.trades ?? 0, trades.length)}
            onError={setError}
            onOpen={setOpenId}
          />
        )}
      </section>
    )
  }

  return (
    <section className="flex flex-col gap-6">
      <PageHeader
        title="Practice"
        description="A real day replayed bar by bar, the future hidden. Plan, execute, then review."
      />

      <section
        aria-label="New session"
        className="flex flex-wrap items-end justify-between gap-6 rounded-card border border-line bg-surface p-6 shadow-panel"
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
          <summary className="cursor-pointer text-sm font-semibold text-accent pointer-coarse:min-h-11 pointer-coarse:flex pointer-coarse:items-center">More statistics</summary>
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
    <div className="grid grid-cols-2 gap-3 sm:gap-4 xl:grid-cols-4" data-testid="practice-tiles">
      <StatTile
        label="Total"
        value={<CountUp value={overall.totalR} format={formatR} />}
        tone={overall.totalR > 0 ? 'up' : overall.totalR < 0 ? 'down' : 'neutral'}
        detail={
          n === 0
            ? 'No finished trades yet.'
            : `Average ${formatR(overall.expectancyR ?? 0)} per trade across ${trades}. Best ${formatR(bestR ?? 0)}, worst ${formatR(worstR ?? 0)}.`
        }
      />
      <StatTile
        label="Win rate"
        value={overall.winRate === undefined ? '—' : <CountUp value={overall.winRate} format={(r) => `${Math.round(r)}%`} />}
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
        value={<CountUp value={costsPaidCents} format={(c) => formatCents(Math.round(c))} />}
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
    <div className="mt-4 flex flex-col gap-1.5">
      <label htmlFor="risk-limit-field" className="text-sm text-ink">
        Risk limit per trade ($)
      </label>
      <input
        id="risk-limit-field"
        className={`${fieldClass} h-9 w-36`}
        inputMode="decimal"
        placeholder="No limit"
        aria-describedby="risk-limit-hint"
        disabled={cents === undefined}
        value={text}
        onChange={(e) => setDraft(e.target.value)}
        onBlur={commit}
        onKeyDown={(e) => {
          if (e.key === 'Enter') commit()
        }}
      />
      <p id="risk-limit-hint" className="text-xs text-subtle">
        Each scorecard says whether a trade kept it. It never blocks one.
      </p>
    </div>
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

type JournalFilter = 'All' | 'Winners' | 'Losers' | 'Long' | 'Short' | 'Ambiguous'
const JOURNAL_FILTERS: JournalFilter[] = ['All', 'Winners', 'Losers', 'Long', 'Short', 'Ambiguous']
/** BUG-157: each filter is the server's, so a match on a page not yet loaded is found. */
const KINDS: Record<JournalFilter, JournalKind | undefined> = {
  All: undefined,
  Winners: 'WINNERS',
  Losers: 'LOSERS',
  Long: 'LONG',
  Short: 'SHORT',
  Ambiguous: 'AMBIGUOUS',
}
/** Rows shown at a time on a phone; "Load more" adds as many again. */
const JOURNAL_PAGE = 20
/** Rows asked of the server at a time, from where the last page ended. */
const SERVER_PAGE = 50

/** The journal on a phone (T53): two lines a trade — the side, the entry, when and how it ended, the R and the money. */
function PhoneJournal({
  trades,
  total,
  onError,
  onOpen,
}: {
  trades: JournalTrade[]
  /** Every finished trade, the ones not loaded yet too: the server sends the newest first, a page at a time. */
  total: number
  onError: (message: string) => void
  onOpen: (sessionId: string) => void
}) {
  const [filter, setFilter] = useState<JournalFilter>('All')
  const [shown, setShown] = useState(JOURNAL_PAGE)
  // What each filter has loaded so far, and whether the server may have more of it. "All" starts from the first page.
  const [pages, setPages] = useState<Partial<Record<JournalFilter, { rows: JournalTrade[]; more: boolean }>>>({})
  const page = pages[filter] ?? (filter === 'All' ? { rows: trades, more: trades.length < total } : undefined)
  const kept = page?.rows ?? []
  const further = page?.more ?? false
  const fetchPage = (which: JournalFilter, from: JournalTrade[]) =>
    listTrades(SERVER_PAGE, from.length, KINDS[which])
      .then((next) => {
        const rows = [...from, ...next]
        // How many there are in all is known only unfiltered; a filter has more while pages come back full.
        const more = which === 'All' ? rows.length < total : next.length === SERVER_PAGE
        setPages((p) => ({ ...p, [which]: { rows, more } }))
      })
      .catch(() => onError('Could not load more of your journal.'))
  return (
    <section aria-labelledby="journal">
      <div className="mb-2.5 flex items-baseline justify-between">
        <h2 id="journal" className="text-base font-semibold text-ink">
          Journal
        </h2>
        <span className="text-[13px] text-subtle">
          {total} {total === 1 ? 'trade' : 'trades'}
        </span>
      </div>
      <div role="group" aria-label="Show" className="-mr-4 mb-2.5 flex gap-2 overflow-x-auto pr-4 [scrollbar-width:none]">
        {JOURNAL_FILTERS.map((option) => (
          <button
            key={option}
            type="button"
            aria-pressed={filter === option}
            onClick={() => {
              setFilter(option)
              setShown(JOURNAL_PAGE)
              if (option !== 'All' && pages[option] === undefined) void fetchPage(option, [])
            }}
            className={cx(
              'h-9 shrink-0 cursor-pointer rounded-full border px-3.5 text-[13px] font-medium focus-visible:outline-2 focus-visible:outline-accent',
              filter === option ? 'border-accent/60 bg-accent/20 text-ink' : 'border-line bg-surface text-subtle',
            )}
          >
            {option}
          </button>
        ))}
      </div>
      {page === undefined ? (
        <p role="status" className="text-sm text-subtle">
          Loading…
        </p>
      ) : kept.length === 0 ? (
        <p className="text-sm text-subtle">No trades of that kind yet.</p>
      ) : (
        <ul className="divide-y divide-line overflow-hidden rounded-card border border-line bg-surface">
          {kept.slice(0, shown).map((t) => (
            <li key={`${t.sessionId}-${t.number}`}>
              <button
                type="button"
                data-testid="journal-row"
                onClick={() => onOpen(t.sessionId)}
                className="flex min-h-14 w-full cursor-pointer items-center gap-3 py-2.5 pr-3.5 pl-4 text-left focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-accent"
              >
                <span
                  className={cx(
                    'w-[52px] shrink-0 rounded-full py-0.5 text-center text-[11px] font-semibold',
                    t.direction === 'LONG' ? 'bg-up/15 text-up' : 'bg-down/15 text-down',
                  )}
                >
                  {t.direction === 'LONG' ? 'Long' : 'Short'}
                </span>
                <span className="min-w-0 flex-1">
                  <span className="num block text-sm text-ink">{formatPrice(t.entryTicks, t.tickSize)}</span>
                  <span className="block truncate text-[13px] text-subtle">
                    {when(t)} · {t.outcome === 'CLOSED' ? 'Closed by hand' : outcomeLabel(t.outcome)}
                  </span>
                </span>
                <span className="text-right">
                  <span className={cx('num block font-semibold', t.r > 0 ? 'text-up' : t.r < 0 ? 'text-down' : 'text-subtle')}>{formatR(t.r)}</span>
                  <span className="num block text-[13px] text-subtle">{formatCents(t.netPnlCents)}</span>
                </span>
              </button>
            </li>
          ))}
        </ul>
      )}
      {(kept.length > shown || further) && (
        <button
          type="button"
          onClick={() => {
            if (kept.length <= shown + JOURNAL_PAGE && further) void fetchPage(filter, kept)
            setShown((n) => n + JOURNAL_PAGE)
          }}
          className="mt-2.5 h-12 w-full cursor-pointer rounded-xl border border-line bg-transparent text-[15px] font-semibold text-ink focus-visible:outline-2 focus-visible:outline-accent"
        >
          Load {further ? JOURNAL_PAGE : Math.min(JOURNAL_PAGE, kept.length - shown)} more
        </button>
      )}
    </section>
  )
}

/** Where a value sits on a line from lo to hi, as a percentage. */
const at = (value: number, lo: number, hi: number) => `${((value - lo) / (hi - lo)) * 100}%`

/**
 * The statistics on a phone (T53): the expectancy with its 95% range drawn against zero, then one way of grouping at
 * a time as a card a group, and the calibration. Each figure keeps the number of trades behind it.
 */
function PhoneStatistics({
  statistics,
  calibration,
  onBack,
}: {
  statistics: Statistics
  calibration: Calibration | null
  onBack: () => void
}) {
  const titles = statistics.dimensions.map((d) => d.title)
  const [chosen, setChosen] = useState(titles[0] ?? '')
  const dimension = statistics.dimensions.find((d) => d.title === chosen)
  const all = statistics.overall
  const tone = (n?: number) => (n === undefined ? 'text-subtle' : n > 0 ? 'text-up' : n < 0 ? 'text-down' : 'text-ink')
  const ranged = all.expectancyR !== undefined && all.low !== undefined && all.high !== undefined
  const lo = Math.min(0, all.low ?? 0) - 0.25
  const hi = Math.max(0, all.high ?? 0) + 0.25
  return (
    <section aria-label="Statistics" className="flex flex-col gap-5">
      <div className="-mt-1 flex items-center gap-1">
        <button
          type="button"
          aria-label="Back to Practice"
          onClick={onBack}
          className="-ml-3 grid size-11 cursor-pointer place-items-center rounded-xl text-ink focus-visible:outline-2 focus-visible:outline-accent"
        >
          <ArrowLeft aria-hidden className="size-5" />
        </button>
        <h1 className="flex-1 text-[17px] font-semibold text-ink">Statistics</h1>
        <span className="text-[13px] text-subtle">
          {all.trades} {all.trades === 1 ? 'trade' : 'trades'}
        </span>
      </div>
      {statistics.notes.map((note) => (
        <p key={note} className="text-sm text-subtle">
          {note}
        </p>
      ))}

      <section className="rounded-card border border-line bg-surface p-4" data-testid="statistics-overall">
        <p className="text-[13px] text-subtle">Expectancy per trade</p>
        <p className={cx('num text-[34px] font-semibold tracking-tight', tone(all.expectancyR))}>
          {all.expectancyR === undefined ? '—' : formatR(all.expectancyR)}
        </p>
        {ranged && (
          <div
            className="relative mt-2.5 h-11"
            role="img"
            aria-label={`95% range from ${formatR(all.low!)} to ${formatR(all.high!)}`}
          >
            <div className="absolute inset-x-0 top-3.5 h-1.5 rounded-full bg-line" />
            <div
              className={cx('absolute top-3.5 h-1.5 rounded-full', all.high! < 0 ? 'bg-down' : all.low! > 0 ? 'bg-up' : 'bg-subtle')}
              style={{ left: at(all.low!, lo, hi), width: `calc(${at(all.high!, lo, hi)} - ${at(all.low!, lo, hi)})` }}
            />
            <div className="absolute top-2 h-4 w-1 -translate-x-1/2 rounded bg-ink" style={{ left: at(all.expectancyR!, lo, hi) }} />
            <div className="absolute top-1 bottom-3.5 border-l border-dashed border-subtle" style={{ left: at(0, lo, hi) }} />
            <span className="num absolute top-6 -translate-x-1/2 text-xs text-subtle" style={{ left: at(all.low!, lo, hi) }}>
              {all.low!.toFixed(2)}
            </span>
            <span className="num absolute top-6 -translate-x-1/2 text-xs text-subtle" style={{ left: at(all.high!, lo, hi) }}>
              {all.high!.toFixed(2)}
            </span>
          </div>
        )}
        <p className="mt-1.5 text-[13px] text-ink/85">{all.reading}</p>
        <div className="mt-3.5 grid grid-cols-3 gap-2">
          <Mini label="Win rate" value={all.winRate === undefined ? '—' : `${Math.round(all.winRate)}%`} />
          <Mini label="Profit factor" value={all.profitFactor === undefined ? '—' : all.profitFactor.toFixed(2)} />
          <Mini label="Total" value={formatR(all.totalR)} tone={tone(all.totalR)} />
        </div>
      </section>

      {titles.length > 0 && (
        <section aria-labelledby="break-down">
          <h2 id="break-down" className="mb-2.5 text-base font-semibold text-ink">
            Break it down by
          </h2>
          <div role="group" aria-label="Group the trades by" className="-mr-4 mb-3 flex gap-2 overflow-x-auto pr-4 [scrollbar-width:none]">
            {titles.map((title) => (
              <button
                key={title}
                type="button"
                aria-pressed={chosen === title}
                onClick={() => setChosen(title)}
                className={cx(
                  'h-9 shrink-0 cursor-pointer rounded-full border px-3.5 text-[13px] font-medium whitespace-nowrap focus-visible:outline-2 focus-visible:outline-accent',
                  chosen === title ? 'border-accent/60 bg-accent/20 text-ink' : 'border-line bg-surface text-subtle',
                )}
              >
                {title}
              </button>
            ))}
          </div>
          <div className="flex flex-col gap-2">
            {dimension?.groups.map((g) => (
              <GroupCard key={g.label} group={g} tone={tone} />
            ))}
          </div>
          <p className="mt-2.5 text-xs text-subtle">
            Every row is its own small sample. The range is where expectancy plausibly lies (95%); while it spans zero,
            those trades can&apos;t be told from no edge at all. {statistics.version}
          </p>
        </section>
      )}

      {calibration && <CalibrationPanel calibration={calibration} />}
    </section>
  )
}

function Mini({ label, value, tone }: { label: string; value: string; tone?: string }) {
  return (
    <div className="rounded-xl border border-line bg-canvas px-2.5 py-2">
      <div className="text-xs text-subtle">{label}</div>
      <div className={cx('num text-sm font-semibold', tone ?? 'text-ink')}>{value}</div>
    </div>
  )
}

function GroupCard({ group: g, tone }: { group: StatisticsGroup; tone: (n?: number) => string }) {
  return (
    <article className="rounded-card border border-line bg-surface px-4 py-3.5" data-testid="statistics-row">
      <div className="flex items-baseline justify-between">
        <h3 className="text-base font-semibold text-ink">{g.label}</h3>
        <span className="text-[13px] text-subtle">
          {g.trades} {g.trades === 1 ? 'trade' : 'trades'}
        </span>
      </div>
      <dl className="mt-3 grid grid-cols-3 gap-x-2 gap-y-2.5 text-[13px]">
        {(
          [
            ['Win rate', g.winRate === undefined ? '—' : `${Math.round(g.winRate)}%`, 'text-ink'],
            ['Avg win', g.avgWinR === undefined ? '—' : formatR(g.avgWinR), tone(g.avgWinR)],
            ['Avg loss', g.avgLossR === undefined ? '—' : formatR(g.avgLossR), tone(g.avgLossR)],
            ['Expectancy', g.expectancyR === undefined ? '—' : formatR(g.expectancyR), tone(g.expectancyR)],
            ['Profit f.', g.profitFactor === undefined ? '—' : g.profitFactor.toFixed(2), 'text-ink'],
            ['Total', formatR(g.totalR), tone(g.totalR)],
          ] as const
        ).map(([label, value, color]) => (
          <div key={label}>
            <dt className="text-subtle">{label}</dt>
            <dd className={cx('num', color)}>{value}</dd>
          </div>
        ))}
      </dl>
      <p className="mt-2.5 text-[13px] text-subtle">{g.reading}</p>
    </article>
  )
}
