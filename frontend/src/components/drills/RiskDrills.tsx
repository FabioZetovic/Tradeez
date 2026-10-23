import { useEffect, useState } from 'react'
import { answerRiskDrill, startRiskDrill, RISK_KINDS, type RiskDrill, type RiskKind } from '@/lib/api/drills'
import { parseDecimal } from '@/lib/decimal'
import { cx, errorClass, fieldClass, labelClass, panelTitle } from '@/lib/styles'
import { Button, Panel, Segmented } from '../ui'

const LABELS = RISK_KINDS.map((k) => k.label)

/** "2 contracts", "14.92%". */
function amount(value: number, unit: string): string {
  if (unit === '%') return `${value}%`
  return `${value} ${value === 1 ? unit.replace(/s$/, '') : unit}`
}

/**
 * Risk drills, mode F (T26, DEC-070): the arithmetic a trader does before every trade or after a bad run —
 * position size, a losing streak, the gain a drawdown needs — against a clock. The clock is pressure, not a
 * rule: an answer after it still counts, and the result says how long it took.
 */
export function RiskDrills() {
  const [label, setLabel] = useState<string>(LABELS[0])
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

  const ask = async () => {
    const kind = RISK_KINDS.find((k) => k.label === label)?.value as RiskKind
    setBusy(true)
    setError(null)
    try {
      setDrill(await startRiskDrill(kind))
      setValue('')
      setStartedAt(Date.now())
      setNow(Date.now())
    } catch {
      setError('Could not ask a question.')
    } finally {
      setBusy(false)
    }
  }

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
    <Panel aria-label="Risk drills">
      <h3 className={panelTitle}>Risk, against the clock</h3>
      <p className="mb-3 max-w-3xl text-sm text-subtle">
        The sums a trader does before every trade and after a bad run: how many contracts a stop allows, how deep a
        losing streak goes, what gain a drawdown needs. Answer in your head before the clock runs out.
      </p>
      <div className="flex flex-wrap items-center gap-3">
        <Segmented<string> options={LABELS} value={label} onChange={setLabel} label="Risk drill" />
        <Button variant="primary" disabled={busy} onClick={() => void ask()}>
          {drill ? 'Next question' : 'Ask a question'}
        </Button>
      </div>

      {drill && (
        <div className="mt-4 flex flex-col gap-3">
          <p className="max-w-3xl text-base text-ink" data-testid="risk-question">
            {drill.text}
          </p>
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
                  className={`${fieldClass} w-40`}
                  inputMode="decimal"
                  autoFocus
                  value={value}
                  onChange={(e) => setValue(e.target.value)}
                />
              </label>
              <Button type="submit" variant="primary" disabled={busy}>
                Answer
              </Button>
              <span
                className={cx('pb-2 text-sm tabular-nums', left === 0 ? 'text-down' : 'text-subtle')}
                data-testid="risk-clock"
                role="timer"
              >
                {left > 0 ? `${left} s left` : 'Time is up — answer anyway'}
              </span>
            </form>
          )}
          {result && (
            <div className="flex flex-col gap-2" data-testid="risk-result">
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
                {label}: {result.right} of {result.answered} right so far. {drill.version}.
              </p>
            </div>
          )}
        </div>
      )}
      {error && (
        <p role="alert" className={`mt-3 ${errorClass}`}>
          {error}
        </p>
      )}
    </Panel>
  )
}
