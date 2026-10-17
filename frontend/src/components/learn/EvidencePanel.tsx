import { useState } from 'react'
import type { Lesson } from '@/lib/api/lessons'
import { Panel, Segmented } from '../ui'
import { panelTitle } from '@/lib/styles'

/** Finest first, as the server counts them (EvidenceService.TIMEFRAMES). */
const ORDER = ['1m', '5m', '15m', '1h']

/**
 * What the concept's rules found over the whole imported history (T21, DEC-065): how often, and how their
 * zones' lives ended — the base rate to hold any one example against. Shown before the examples, and always
 * with what the numbers are not.
 */
export function EvidencePanel({ lesson }: { lesson: Lesson }) {
  const counted = lesson.rules.filter((rule) => rule.evidence.length > 0)
  const timeframes = ORDER.filter((tf) => counted.some((rule) => rule.evidence.some((e) => e.timeframe === tf)))
  const [chosen, setChosen] = useState('5m')
  const timeframe = timeframes.includes(chosen) ? chosen : timeframes[0]

  return (
    <Panel aria-label="Evidence">
      <div className="mb-3 flex flex-wrap items-center gap-3">
        <h3 className="text-base font-semibold text-ink">What it found on real {lesson.instrument}</h3>
        {timeframes.length > 1 && (
          <Segmented<string> options={timeframes} value={timeframe} onChange={setChosen} label="Evidence timeframe" />
        )}
      </div>
      {counted.length === 0 ? (
        <p className="text-sm text-subtle" data-testid="evidence-pending">
          Not counted yet. The rules are counted over the imported history in the background after the app starts;
          open this card again in a minute.
        </p>
      ) : (
        counted.map((rule) => {
          const evidence = rule.evidence.find((e) => e.timeframe === timeframe)
          if (!evidence) return null
          const lines = evidence.lines.slice(0, -1)
          const caveat = evidence.lines.at(-1)
          return (
            <div key={rule.detector} className="mb-4 last:mb-0" data-testid="evidence">
              {counted.length > 1 && <h4 className={panelTitle}>{rule.version}</h4>}
              <ul className="flex max-w-4xl flex-col gap-1 text-sm leading-6">
                {lines.map((line) => (
                  <li key={line}>{line}</li>
                ))}
              </ul>
              <p className="mt-2 text-xs text-subtle">
                {caveat} Counted by {rule.version} under {evidence.version}.
              </p>
            </div>
          )
        })
      )}
    </Panel>
  )
}
