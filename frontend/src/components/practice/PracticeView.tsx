import { useCallback, useEffect, useState } from 'react'
import { PracticeSession } from './PracticeSession'
import { StatisticsPanel } from './StatisticsPanel'
import { ApiError } from '@/lib/api/client'
import {
  createSession,
  getCalibration,
  getPracticeSettings,
  getStatistics,
  listSessions,
  savePracticeSettings,
  KILLZONES,
  PRACTICE_TIMEFRAMES,
  WEEKDAYS,
  type Calibration,
  type PracticeSettings,
  type SettingsUpdate,
  type SessionResponse,
  type SessionSummary,
  type Statistics,
} from '@/lib/api/practice'
import type { ChartAdapter } from '@/lib/chart/types'
import { formatCents, formatR, STATUS_LABEL } from '@/lib/practice/plan'
import { Button, Panel, Segmented } from '../ui'
import { errorClass, fieldClass, labelClass, panelTitle } from '@/lib/styles'

const tableClass =
  'w-full border-collapse text-sm tabular-nums [&_td]:border-b [&_td]:border-line/60 [&_td]:px-2 [&_td]:py-2 [&_th]:border-b [&_th]:border-line [&_th]:px-2 [&_th]:py-2 [&_th]:text-left [&_th]:font-medium [&_th]:text-subtle'

/** A risk limit is $1 to $1,000,000 (V20). */
const MAX_LIMIT_DOLLARS = 1_000_000

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
  const [calibration, setCalibration] = useState<Calibration | null>(null)
  const [statistics, setStatistics] = useState<Statistics | null>(null)
  // undefined while loading.
  const [settings, setSettings] = useState<PracticeSettings | undefined>(undefined)
  const [timeframe, setTimeframe] = useState<string>('5m')
  // Empty means "any": the server then picks from the whole history.
  const [killzone, setKillzone] = useState('')
  const [weekday, setWeekday] = useState('')
  const [starting, setStarting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const loadJournal = useCallback(() => {
    listSessions()
      .then(setJournal)
      .catch(() => setError('Could not load your practice journal.'))
    getStatistics()
      .then(setStatistics)
      .catch(() => setError('Could not load your statistics.'))
    getCalibration()
      .then(setCalibration)
      .catch(() => setError('Could not load your calibration.'))
    getPracticeSettings()
      .then(setSettings)
      .catch(() => setError('Could not load your risk limit and costs.'))
  }, [])

  // Every rule is saved at once (T27): the ones not being changed are sent as they are.
  const save = async (update: SettingsUpdate) => {
    if (!settings) return
    setError(null)
    try {
      const { fillModel: _, ...current } = settings
      setSettings(await savePracticeSettings({ ...current, ...update }))
    } catch {
      setError('Could not save your settings.')
    }
  }

  useEffect(() => {
    if (openId === null) loadJournal()
  }, [openId, loadJournal])

  const start = async () => {
    setStarting(true)
    setError(null)
    try {
      const session = await createSession({
        instrument: 'NQ',
        timeframe,
        ...(killzone && { killzone }),
        ...(weekday && { weekday }),
      })
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
        riskLimitCents={settings?.riskLimitCents}
        costs={settings}
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
          You get real NQ history that stops at a random, hidden moment — its date hidden too, because
          knowing the date means knowing what happened. Then replay it bar by bar, at your own pace.
          Whenever you see a setup, lock a trade with a stop and a target and watch it play out; when
          it is over, look for the next. End the session to see your score in R and the date. Every
          trade is recorded, and the record cannot be changed.
        </p>
        <div className="mt-4 flex flex-wrap items-center gap-3">
          <Segmented<string>
            options={PRACTICE_TIMEFRAMES}
            value={timeframe}
            onChange={setTimeframe}
            label="Practice timeframe"
          />
          <label className="flex items-center gap-2 text-xs font-medium text-subtle">
            Session
            <select className={fieldClass} value={killzone} onChange={(e) => setKillzone(e.target.value)}>
              <option value="">Any time</option>
              {KILLZONES.map((k) => (
                // The market shuts at 17:00 on Fridays, before Asia's 20:00 (BUG-067).
                <option key={k.value} value={k.value} disabled={k.value === 'ASIA' && weekday === 'FRIDAY'}>
                  {k.label}
                </option>
              ))}
            </select>
          </label>
          <label className="flex items-center gap-2 text-xs font-medium text-subtle">
            Day
            <select className={fieldClass} value={weekday} onChange={(e) => setWeekday(e.target.value)}>
              <option value="">Any day</option>
              {WEEKDAYS.map((d) => (
                <option key={d} value={d} disabled={d === 'FRIDAY' && killzone === 'ASIA'}>
                  {d.charAt(0) + d.slice(1).toLowerCase()}
                </option>
              ))}
            </select>
          </label>
          <Button variant="primary" disabled={starting} onClick={() => void start()}>
            {starting ? 'Finding a moment…' : 'Start a session'}
          </Button>
        </div>
        <div className="mt-4 flex flex-wrap items-end gap-3">
          <RiskLimitField
            cents={settings === undefined ? undefined : (settings.riskLimitCents ?? null)}
            onSave={(cents) => void save({ riskLimitCents: cents ?? undefined })}
            onInvalid={() => setError(`A risk limit is $1 to $${MAX_LIMIT_DOLLARS.toLocaleString('en-US')}.`)}
          />
          <p className="max-w-xl pb-2 text-xs text-subtle">
            Your rule, set before you trade: each trade's scorecard says whether its risk kept it. It never blocks a
            trade, and changing it later does not rewrite old ones.
          </p>
        </div>
        {settings && <CostsFields settings={settings} onSave={(update) => void save(update)} />}
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
          <table className={tableClass}>
            <thead>
              <tr>
                <th>Started</th>
                <th>Chart</th>
                <th>Status</th>
                <th>Trades</th>
                <th>Win rate</th>
                <th>R</th>
                <th>Net</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {journal.map((row) => {
                // The score counts closed trades only: a running trade's outcome is the future.
                const { closedTrades, wins, totalR, netPnlCents } = row.score
                return (
                  <tr key={row.id} data-testid="journal-row">
                    <td>{new Date(row.createdAt * 1000).toLocaleString()}</td>
                    <td>
                      {row.instrument} · {row.timeframe}
                    </td>
                    <td>{STATUS_LABEL[row.status]}</td>
                    <td>{row.trades}</td>
                    <td>{closedTrades > 0 ? `${Math.round((100 * wins) / closedTrades)}%` : ''}</td>
                    <td>{closedTrades > 0 ? formatR(totalR) : ''}</td>
                    <td>{closedTrades > 0 ? formatCents(netPnlCents) : ''}</td>
                    <td>
                      <Button variant="link" onClick={() => setOpenId(row.id)}>
                        {row.status === 'COMPLETE' ? 'Review' : 'Resume'}
                      </Button>
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        )}
      </Panel>

      {statistics && <StatisticsPanel statistics={statistics} />}
      {calibration && <CalibrationPanel calibration={calibration} />}
    </section>
  )
}

const TICKS = ['0', '1', '2', '3', '4'] as const
const TARGET_FILLS = ['On the touch', 'Traded through'] as const

/**
 * What trading costs (T27, DEC-072): fees per round trip, the ticks a market order and a stop slip, and whether a
 * target fills on the touch or once price trades through it. Used for trades placed from now on; each result keeps
 * the costs it was made with.
 */
function CostsFields({ settings, onSave }: { settings: PracticeSettings; onSave: (update: SettingsUpdate) => void }) {
  const [draft, setDraft] = useState<string | null>(null)
  const fees = draft ?? String(settings.feesCents / 100)
  const commitFees = () => {
    if (draft === null) return
    const dollars = Number(draft.replace(/[$,\s]/g, ''))
    if (Number.isFinite(dollars) && dollars >= 0 && dollars <= 50) onSave({ feesCents: Math.round(dollars * 100) })
    setDraft(null)
  }
  return (
    <section aria-label="Your costs" className="mt-4 flex flex-wrap items-end gap-4">
      <label className={labelClass}>
        Fees per round trip ($)
        <input
          className={`${fieldClass} w-28`}
          inputMode="decimal"
          value={fees}
          onChange={(e) => setDraft(e.target.value)}
          onBlur={commitFees}
          onKeyDown={(e) => {
            if (e.key === 'Enter') commitFees()
          }}
        />
      </label>
      <div className={labelClass}>
        Slippage in, ticks
        <Segmented<(typeof TICKS)[number]>
          options={TICKS}
          value={String(settings.marketSlippageTicks) as (typeof TICKS)[number]}
          onChange={(t) => onSave({ marketSlippageTicks: Number(t) })}
          label="Slippage on a market order, ticks"
        />
      </div>
      <div className={labelClass}>
        At the stop, ticks
        <Segmented<(typeof TICKS)[number]>
          options={TICKS}
          value={String(settings.stopSlippageTicks) as (typeof TICKS)[number]}
          onChange={(t) => onSave({ stopSlippageTicks: Number(t) })}
          label="Slippage at the stop, ticks"
        />
      </div>
      <div className={labelClass}>
        Targets fill
        <Segmented<(typeof TARGET_FILLS)[number]>
          options={TARGET_FILLS}
          value={settings.limitThroughTicks === 0 ? 'On the touch' : 'Traded through'}
          onChange={(f) => onSave({ limitThroughTicks: f === 'On the touch' ? 0 : 1 })}
          label="Targets fill"
        />
      </div>
      <p className="max-w-md pb-2 text-xs text-subtle" data-testid="costs-model">
        For trades placed from now: each keeps the costs it was made with ({settings.fillModel}). The standard: $4.50, a
        tick in, a tick at the stop, a target filled once traded through.
      </p>
    </section>
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
    const dollars = Number(draft.replace(/[$,\s]/g, ''))
    if (draft.trim() === '') onSave(null)
    else if (Number.isFinite(dollars) && dollars >= 1 && dollars <= MAX_LIMIT_DOLLARS) onSave(Math.round(dollars * 100))
    else onInvalid()
    setDraft(null)
  }
  return (
    <label className={labelClass}>
      Risk limit per trade ($)
      <input
        className={`${fieldClass} w-40`}
        inputMode="decimal"
        placeholder="none"
        disabled={cents === undefined}
        value={text}
        onChange={(e) => setDraft(e.target.value)}
        onBlur={commit}
        onKeyDown={(e) => {
          if (e.key === 'Enter') commit()
        }}
      />
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
