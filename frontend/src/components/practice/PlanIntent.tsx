import { useState } from 'react'
import { MODELS, type Bias, type PlanContext, type PlanIntent as Intent, type TradingModel } from '@/lib/api/practice'
import { formatPrice, priceToTicks } from '@/lib/chart/priceFormat'
import { NOT_A_PRICE, parseDecimal } from '@/lib/decimal'
import { cx, errorClass, fieldClass, labelClass } from '@/lib/styles'
import { Segmented } from '../ui'

const KILLZONE_NAMES: Record<string, string> = {
  ASIA: 'Asia',
  LONDON: 'London',
  NEW_YORK_AM: 'New York AM',
  NEW_YORK_PM: 'New York PM',
}

const BIASES = ['None', 'Bullish', 'Bearish'] as const

const CONFIDENCES = [10, 20, 30, 40, 50, 60, 70, 80, 90] as const

/**
 * What the learner means by the trade (T22, DEC-066): the ICT model it follows, their bias for the day and
 * the liquidity they expect price to reach — and, beside it, what the app sees: the killzone, the
 * daily-bias-1.0.0 suggestion with its reasons, and the model's checklist against the rules confirmed so
 * far. Nothing here blocks the trade; the checklist is adherence, kept with the plan and scored apart from
 * how it turns out.
 */
export function PlanIntent({
  intent,
  onChange,
  context,
  tickSize,
}: {
  intent: Intent
  onChange: (intent: Intent) => void
  context: PlanContext | null
  tickSize: number
}) {
  const bias = intent.bias === 'BULLISH' ? 'Bullish' : intent.bias === 'BEARISH' ? 'Bearish' : 'None'
  return (
    <section aria-label="Your reading" className="mb-4 flex flex-col gap-3 rounded-md border border-line/70 p-3">
      <div className="flex flex-wrap items-end gap-4">
        <label className={labelClass}>
          Model
          <select
            className={fieldClass}
            value={intent.model ?? ''}
            onChange={(e) => onChange({ ...intent, model: (e.target.value || undefined) as TradingModel | undefined })}
          >
            <option value="">None</option>
            {MODELS.map((m) => (
              <option key={m.value} value={m.value}>
                {m.label}
              </option>
            ))}
          </select>
        </label>
        <div className={labelClass}>
          Your bias
          <Segmented<(typeof BIASES)[number]>
            options={BIASES}
            value={bias}
            onChange={(b) => onChange({ ...intent, bias: b === 'None' ? undefined : (b.toUpperCase() as Bias) })}
            label="Your bias"
          />
        </div>
        <DrawField
          ticks={intent.dolTicks}
          tickSize={tickSize}
          onCommit={(dolTicks) => onChange({ ...intent, dolTicks })}
        />
        {/* T23: compared, across trades, with how often the target did come first (calibration). */}
        <label
          className={labelClass}
          title="How sure you are that the target fills before the stop. The journal compares it with your results."
        >
          Target before stop
          <select
            className={fieldClass}
            value={intent.confidence ?? ''}
            onChange={(e) => onChange({ ...intent, confidence: e.target.value ? Number(e.target.value) : undefined })}
          >
            <option value="">Not said</option>
            {CONFIDENCES.map((c) => (
              <option key={c} value={c}>
                {c}% sure
              </option>
            ))}
          </select>
        </label>
      </div>

      {context && (
        <div className="flex flex-col gap-1 text-xs text-subtle" data-testid="plan-context">
          <p>
            {context.killzone ? `In the ${KILLZONE_NAMES[context.killzone]} killzone.` : 'Outside the killzones.'}{' '}
            <span title={context.biasRule}>
              {context.suggestedBias
                ? `The daily-bias reading suggests ${context.suggestedBias === 'BULLISH' ? 'bullish' : 'bearish'}: `
                : ''}
              {context.biasReasoning}
            </span>{' '}
            A suggestion, not a verdict.
          </p>
        </div>
      )}

      {intent.model && context?.checklist && (
        <div data-testid="checklist">
          <p className="mb-1 text-xs font-medium text-ink">
            {context.checklist.title} checklist · {context.checklist.met} of {context.checklist.total}
            <span className="font-normal text-subtle"> · {context.checklist.version}</span>
          </p>
          <ul aria-label="Model checklist" className="flex flex-col gap-0.5 text-xs">
            {context.checklist.items.map((item) => (
              <li key={item.id} className={cx('flex gap-1.5', item.met ? 'text-up' : 'text-subtle')}>
                <span aria-hidden="true">{item.met ? '✓' : '✗'}</span>
                <span>
                  {item.text}
                  <span className="sr-only">{item.met ? ', met' : ', not met'}</span>
                </span>
              </li>
            ))}
          </ul>
          <p className="mt-1 text-xs text-subtle">
            Checked against what the rules had confirmed by now. Kept with the trade, and scored apart from how it
            turns out.
          </p>
        </div>
      )}
    </section>
  )
}

/** The liquidity the learner expects price to reach: optional, committed on Enter or blur, cleared when emptied. */
function DrawField({
  ticks,
  tickSize,
  onCommit,
}: {
  ticks: number | undefined
  tickSize: number
  onCommit: (ticks: number | undefined) => void
}) {
  const [draft, setDraft] = useState<string | null>(null)
  const [bad, setBad] = useState(false)
  const text = draft ?? (ticks === undefined ? '' : formatPrice(ticks, tickSize))
  const commit = () => {
    if (draft === null) return
    const price = parseDecimal(draft)
    if (draft.trim() === '') onCommit(undefined)
    else if (price !== null && price > 0) onCommit(priceToTicks(price, tickSize))
    else {
      // BUG-089: said, not put back in silence.
      setBad(true)
      return
    }
    setDraft(null)
  }
  return (
    <label className={labelClass}>
      Draw on liquidity
      <input
        className={`${fieldClass} w-32`}
        inputMode="decimal"
        placeholder="optional"
        value={text}
        aria-invalid={bad || undefined}
        onChange={(e) => {
          setDraft(e.target.value)
          setBad(false)
        }}
        onBlur={commit}
        onKeyDown={(e) => {
          if (e.key === 'Enter') commit()
        }}
      />
      {bad && (
        <span role="alert" className={`${errorClass} text-xs`}>
          {NOT_A_PRICE}
        </span>
      )}
    </label>
  )
}
