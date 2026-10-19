import { useState } from 'react'
import type { Statistics, StatisticsGroup } from '@/lib/api/practice'
import { formatR } from '@/lib/practice/plan'
import { panelTitle } from '@/lib/styles'
import { Panel, Segmented } from '../ui'

const tableClass =
  'w-full border-collapse text-sm tabular-nums [&_td]:border-b [&_td]:border-line/60 [&_td]:px-2 [&_td]:py-2 [&_th]:border-b [&_th]:border-line [&_th]:px-2 [&_th]:py-2 [&_th]:text-left [&_th]:font-medium [&_th]:text-subtle'

/** Whole percent, as the journal shows a win rate. */
const percent = (value: number | undefined) => (value === undefined ? '—' : `${Math.round(value)}%`)

/** "+0.38R (−1.19R to +1.94R)": the expectancy with its range, or alone below two trades. */
function expectancy(g: StatisticsGroup): string {
  if (g.expectancyR === undefined) return '—'
  return g.low === undefined || g.high === undefined
    ? formatR(g.expectancyR)
    : `${formatR(g.expectancyR)} (${formatR(g.low)} to ${formatR(g.high)})`
}

/**
 * Journal statistics (T25, DEC-069): the learner's finished trades added up, overall and grouped one way at a
 * time, with the number of trades first on every row — a figure is only as good as the trades behind it.
 */
export function StatisticsPanel({ statistics }: { statistics: Statistics }) {
  const titles = statistics.dimensions.map((d) => d.title)
  const [chosen, setChosen] = useState(titles[0] ?? '')
  const dimension = statistics.dimensions.find((d) => d.title === chosen)
  const all = statistics.overall

  return (
    <Panel aria-label="Statistics">
      <h3 className={panelTitle}>Statistics</h3>
      {statistics.notes.map((note) => (
        <p key={note} className="text-sm text-subtle">
          {note}
        </p>
      ))}
      {all.trades > 0 && (
        <>
          <p className="text-sm" data-testid="statistics-overall">
            {all.trades} {all.trades === 1 ? 'trade' : 'trades'} · win rate {percent(all.winRate)} · expectancy {expectancy(all)}
            {all.profitFactor !== undefined && ` · profit factor ${all.profitFactor.toFixed(2)}`} · total{' '}
            {formatR(all.totalR)}
          </p>
          <p className="mb-3 text-xs text-subtle">{all.reading}</p>
          <Segmented<string> options={titles} value={chosen} onChange={setChosen} label="Group the trades by" />
          {dimension && (
            <table className={`${tableClass} mt-3`}>
              <thead>
                <tr>
                  <th>{dimension.title}</th>
                  <th>Trades</th>
                  <th>Win rate</th>
                  <th>Avg win</th>
                  <th>Avg loss</th>
                  <th title="The mean result in R, and its 95% range">Expectancy</th>
                  <th>Profit factor</th>
                  <th>Total</th>
                </tr>
              </thead>
              <tbody>
                {dimension.groups.map((g) => (
                  <tr key={g.label} data-testid="statistics-row" title={g.reading}>
                    <td>{g.label}</td>
                    <td>{g.trades}</td>
                    <td>{percent(g.winRate)}</td>
                    <td>{g.avgWinR === undefined ? '—' : formatR(g.avgWinR)}</td>
                    <td>{g.avgLossR === undefined ? '—' : formatR(g.avgLossR)}</td>
                    <td>{expectancy(g)}</td>
                    <td>{g.profitFactor === undefined ? '—' : g.profitFactor.toFixed(2)}</td>
                    <td>{formatR(g.totalR)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </>
      )}
      <p className="mt-3 text-xs text-subtle">
        Read the trades first: every row is its own small sample. The range is where the expectancy plausibly lies
        (95%); while it spans zero, those trades cannot be told from no edge at all. {statistics.version}.
      </p>
    </Panel>
  )
}
