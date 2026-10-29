import { useCallback, useEffect, useRef, useState } from 'react'
import { Delete, Timer, X } from 'lucide-react'
import { Link } from 'react-router'
import { answerRiskDrill, startRiskDrill, RISK_KINDS, type RiskDrill } from '@/lib/api/drills'
import { parseDecimal } from '@/lib/decimal'
import { cx, errorClass, fieldClass, labelClass } from '@/lib/styles'
import { Button, Panel } from '../ui'
import { AllDrills } from './DrillView'
import { useIsPhone } from '@/hooks/useIsPhone'

/** "2 contracts", "14.92%". */
function amount(value: number, unit: string): string {
  if (unit === '%') return `${value}%`
  return `${value} ${value === 1 ? unit.replace(/s$/, '') : unit}`
}

/** The question with its figures picked out — "$50,000", "1%", "50 ticks" — the way Lovable's card shows them. */
function Figures({ text }: { text: string }) {
  return text.split(/(\$?\d+(?:[.,]\d+)*%?)/).map((part, i) =>
    i % 2 === 1 ? (
      <strong key={i} className="num font-bold text-ink">
        {part}
      </strong>
    ) : (
      part
    ),
  )
}

/**
 * A risk drill, mode F (T26, DEC-070, T43's screen): the arithmetic a trader does before every trade or after a bad
 * run — position size, a losing streak, the gain a drawdown needs — against a clock. The clock is pressure, not a
 * rule: an answer after it still counts, and the result says how long it took. The card that opened it was the click:
 * the first question is asked on arrival.
 */
export function RiskDrills({ kind }: { kind: (typeof RISK_KINDS)[number] }) {
  const [drill, setDrill] = useState<RiskDrill | null>(null)
  const [value, setValue] = useState('')
  const [startedAt, setStartedAt] = useState(0)
  const [now, setNow] = useState(0)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const phone = useIsPhone()

  const open = drill !== null && drill.result === undefined
  useEffect(() => {
    if (!open) return
    const timer = window.setInterval(() => setNow(Date.now()), 250)
    return () => window.clearInterval(timer)
  }, [open])

  const ask = useCallback(async () => {
    setBusy(true)
    setError(null)
    try {
      setDrill(await startRiskDrill(kind.value))
      setValue('')
      setStartedAt(Date.now())
      setNow(Date.now())
    } catch {
      setError('Could not ask a question.')
    } finally {
      setBusy(false)
    }
  }, [kind.value])

  // Once on arrival, not again on a strict-mode second mount.
  const asked = useRef(false)
  useEffect(() => {
    if (asked.current) return
    asked.current = true
    void ask()
  }, [ask])

  const submit = async () => {
    if (!drill) return
    const number = parseDecimal(value, '%')
    if (number === null) {
      setError('An answer is a number, like 2.5 or 2,5.')
      return
    }
    setBusy(true)
    setError(null)
    try {
      setDrill(await answerRiskDrill(drill.id, number))
    } catch {
      setError('Could not send the answer.')
    } finally {
      setBusy(false)
    }
  }

  const left = drill ? Math.max(0, Math.ceil(drill.limitSeconds - (now - startedAt) / 1000)) : 0
  const result = drill?.result

  if (phone) {
    // The sums on a phone (T53, Claude Design): the question large, the clock a ring, and a number pad of its own so the
    // keyboard never covers the question while the clock runs.
    const press = (key: string) =>
      setValue((v) => (key === 'del' ? v.slice(0, -1) : key === '.' && v.includes('.') ? v : (v + key).slice(0, 9)))
    const share = drill ? Math.min(1, left / drill.limitSeconds) : 0
    return (
      <section aria-label="Risk drill" className="flex min-h-svh flex-col">
        <header className="flex h-14 items-center gap-1 pr-3 pl-1">
          <Link
            to="/drills"
            aria-label="Exit drill"
            className="grid size-11 place-items-center rounded-xl text-ink focus-visible:outline-2 focus-visible:outline-accent"
          >
            <X aria-hidden className="size-5" />
          </Link>
          <div className="min-w-0 flex-1">
            <h1 className="truncate text-base font-semibold text-ink">{kind.label}</h1>
            <p className="text-[13px] text-subtle">Sums · in your head, against the clock</p>
          </div>
          {open && (
            <span
              role="timer"
              data-testid="risk-clock"
              aria-label={left > 0 ? `${left} seconds left` : 'Time is up — answer anyway'}
              className="grid size-12 shrink-0 place-items-center rounded-full"
              style={{ background: `conic-gradient(var(--color-${left < 10 ? 'down' : 'accent'}) 0 ${share * 100}%, var(--color-line) 0)` }}
            >
              <span className="num grid size-10 place-items-center rounded-full bg-canvas text-sm font-semibold text-ink">{left}</span>
            </span>
          )}
        </header>

        <main className="flex flex-1 flex-col gap-4 px-4 pt-5">
          {!drill && busy && (
            <p role="status" className="text-sm text-subtle">
              Asking…
            </p>
          )}
          {drill && (
            <p className="text-[22px] leading-snug font-medium text-ink" data-testid="risk-question">
              <Figures text={drill.text} />
            </p>
          )}
          {open && (
            <div className="flex items-center justify-between rounded-card border border-accent/60 bg-surface px-4 py-3.5 ring-3 ring-accent/15">
              <span className="text-sm text-subtle">Your answer ({drill.unit})</span>
              <output aria-live="polite" className="num text-3xl font-semibold text-ink">
                {value || ' '}
              </output>
            </div>
          )}
          {result && drill && (
            <div
              className={cx('flex flex-col gap-2 rounded-card border p-4', result.correct ? 'border-up/40 bg-up/10' : 'border-down/40 bg-down/10')}
              data-testid="risk-result"
            >
              <p className={cx('text-lg font-semibold', result.correct ? 'text-up' : 'text-down')}>
                {result.correct ? 'Right' : 'Not quite'}: {amount(result.answer, drill.unit)}
              </p>
              <p className="text-sm">
                You said {amount(result.given, drill.unit)}, in {result.seconds} s
                {result.inTime ? `, inside the ${drill.limitSeconds} s.` : ` — over the ${drill.limitSeconds} s.`}
              </p>
              <ol className="list-decimal pl-5 text-sm text-subtle">
                {result.working.map((step) => (
                  <li key={step}>{step}</li>
                ))}
              </ol>
              <p className="text-xs text-subtle" data-testid="risk-record">
                {kind.label}: {result.right} of {result.answered} right so far. {drill.version}.
              </p>
            </div>
          )}
          {error && (
            <p role="alert" className={errorClass}>
              {error}
            </p>
          )}
        </main>

        <div className="sticky bottom-0 border-t border-line bg-surface px-3 pt-3 pb-[calc(1.25rem+env(safe-area-inset-bottom))]">
          {open ? (
            <div role="group" aria-label="Number pad" className="grid grid-cols-3 gap-2">
              {['1', '2', '3', '4', '5', '6', '7', '8', '9', '.', '0', 'del'].map((key) => (
                <button
                  key={key}
                  type="button"
                  aria-label={key === 'del' ? 'Delete' : key === '.' ? 'Decimal point' : key}
                  onClick={() => press(key)}
                  className="num grid h-13 cursor-pointer place-items-center rounded-xl border border-line bg-raised text-xl text-ink focus-visible:outline-2 focus-visible:outline-accent"
                >
                  {key === 'del' ? <Delete aria-hidden className="size-5" /> : key}
                </button>
              ))}
              <button
                type="button"
                disabled={busy || value === ''}
                onClick={() => void submit()}
                className="col-span-3 h-13 cursor-pointer rounded-full bg-accent text-base font-semibold text-accent-ink focus-visible:outline-2 focus-visible:outline-accent disabled:opacity-40"
              >
                Answer
              </button>
            </div>
          ) : (
            (result || (!drill && error)) && (
              <button
                type="button"
                disabled={busy}
                onClick={() => void ask()}
                className="h-13 w-full cursor-pointer rounded-full bg-accent text-base font-semibold text-accent-ink focus-visible:outline-2 focus-visible:outline-accent disabled:opacity-40"
              >
                {drill ? 'Next question' : 'Try again'}
              </button>
            )
          )}
        </div>
      </section>
    )
  }

  return (
    <section aria-label="Risk drill" className="mx-auto flex max-w-3xl flex-col gap-4">
      <AllDrills />
      <header>
        <h1 className="font-display text-3xl font-bold tracking-tight text-ink">{kind.label}</h1>
        <p className="mt-2 text-base text-subtle">{kind.line} Answer in your head before the clock runs out.</p>
      </header>

      <Panel aria-label="Question" className="flex flex-col gap-4">
        {!drill && busy && (
          <p role="status" className="text-sm text-subtle">
            Asking…
          </p>
        )}
        {drill && (
          <>
            <div className="flex items-start justify-between gap-4">
              <p className="text-lg leading-relaxed text-subtle" data-testid="risk-question">
                <Figures text={drill.text} />
              </p>
              {open && (
                <span
                  className={cx(
                    'num inline-flex shrink-0 items-center gap-1.5 rounded-full border px-3 py-1 text-sm',
                    left < 10 ? 'border-down/50 bg-down/10 text-down' : 'border-line text-subtle',
                  )}
                  data-testid="risk-clock"
                  role="timer"
                >
                  <Timer aria-hidden className="size-3.5" />
                  {left > 0 ? `${left} s left` : 'Time is up — answer anyway'}
                </span>
              )}
            </div>
            {open && (
              <form
                className="flex flex-wrap items-end gap-3"
                onSubmit={(e) => {
                  e.preventDefault()
                  void submit()
                }}
              >
                <label className={labelClass}>
                  Your answer ({drill.unit})
                  <input
                    className={`${fieldClass} num w-40`}
                    inputMode="decimal"
                    autoFocus
                    value={value}
                    onChange={(e) => setValue(e.target.value)}
                  />
                </label>
                <Button type="submit" variant="primary" disabled={busy}>
                  Answer
                </Button>
              </form>
            )}
            {result && (
              <div
                className={cx(
                  'flex flex-col gap-2 rounded-lg border p-4',
                  result.correct ? 'border-up/40 bg-up/10' : 'border-down/40 bg-down/10',
                )}
                data-testid="risk-result"
              >
                <p className={cx('text-lg font-semibold', result.correct ? 'text-up' : 'text-down')}>
                  {result.correct ? 'Right' : 'Not quite'}: {amount(result.answer, drill.unit)}
                </p>
                <p className="text-sm">
                  You said {amount(result.given, drill.unit)}, in {result.seconds} s
                  {result.inTime ? `, inside the ${drill.limitSeconds} s.` : ` — over the ${drill.limitSeconds} s.`}
                </p>
                <ol className="list-decimal pl-5 text-sm text-subtle">
                  {result.working.map((step) => (
                    <li key={step}>{step}</li>
                  ))}
                </ol>
                <p className="text-xs text-subtle" data-testid="risk-record">
                  {kind.label}: {result.right} of {result.answered} right so far. {drill.version}.
                </p>
              </div>
            )}
          </>
        )}
        {error && (
          <p role="alert" className={errorClass}>
            {error}
          </p>
        )}
        {(result || (!drill && error)) && (
          <div>
            <Button variant="primary" disabled={busy} onClick={() => void ask()}>
              {drill ? 'Next question' : 'Try again'}
            </Button>
          </div>
        )}
      </Panel>
    </section>
  )
}
