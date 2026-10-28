import { useCallback, useEffect, useRef, useState } from 'react'
import { Timer } from 'lucide-react'
import { answerRiskDrill, startRiskDrill, RISK_KINDS, type RiskDrill } from '@/lib/api/drills'
import { parseDecimal } from '@/lib/decimal'
import { cx, errorClass, fieldClass, labelClass } from '@/lib/styles'
import { Button, Panel } from '../ui'
import { AllDrills } from './DrillView'

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
